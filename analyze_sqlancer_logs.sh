#!/bin/bash

# SQLancer Log Analysis Script
# Analyzes SQLancer logs to detect and reproduce logical errors

set -e

# Configuration
SQLANCER_JAR="/d/Jack.Xiao/dbtools/sqlancer-main/sqlancer-main/target/sqlancer-2.0.0.jar"
DBMS_HOST="localhost"
DBMS_PORT="5432"
DBMS_USER="root"
DBMS_PASS="password"
DBMS_TYPE="postgres"
ANALYSIS_DIR="logs/analysis"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    echo "SQLancer Log Analysis Tool"
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  scan              Scan all log directories for errors"
    echo "  analyze <dir>     Analyze a specific log directory"
    echo "  reproduce <dir>   Generate reproduction script for a log directory"
    echo "  compare <dir>     Compare query results from error log"
    echo "  summary           Generate summary of all logs"
    echo ""
    echo "Options:"
    echo "  --verbose         Show detailed output"
    echo "  --db-type <type>  Database type (postgres/mysql/gaussdb-m)"
    echo "  --output <dir>    Output directory for analysis results"
    echo ""
    echo "Examples:"
    echo "  $0 scan                                     # Scan all logs for errors"
    echo "  $0 analyze logs/postgres/eet_2026_0414_1117  # Analyze specific directory"
    echo "  $0 reproduce logs/postgres/eet_2026_0414_1117 # Generate reproduction script"
    exit 1
}

# Function to parse -cur.log file
parse_cur_log() {
    local log_file="$1"
    local output_file="$2"

    echo "=== Parsing execution log: $log_file ==="

    # Extract header information
    local seed=$(grep "seed value" "$log_file" | head -1 | sed 's/.*seed value: //')
    local db_name=$(grep "Database:" "$log_file" | head -1 | sed 's/.*Database: //')
    local db_version=$(grep "Database version:" "$log_file" | head -1 | sed 's/.*Database version: //')
    local timestamp=$(grep "Time:" "$log_file" | head -1 | sed 's/.*Time: //')

    echo "Seed: $seed"
    echo "Database: $db_name"
    echo "Version: $db_version"
    echo "Timestamp: $timestamp"

    if [ -n "$output_file" ]; then
        echo "# SQLancer Reproduction Script" > "$output_file"
        echo "# Generated from: $log_file" >> "$output_file"
        echo "# Seed: $seed" >> "$output_file"
        echo "# Database: $db_name" >> "$output_file"
        echo "# Version: $db_version" >> "$output_file"
        echo "# Timestamp: $timestamp" >> "$output_file"
        echo "" >> "$output_file"

        # Extract SQL statements (skip header lines and time annotations)
        grep -v "^-- Time:" "$log_file" | \
        grep -v "^-- Database:" "$log_file" | \
        grep -v "^-- Database version:" "$log_file" | \
        grep -v "^-- seed value:" "$log_file" | \
        grep -v "^$" >> "$output_file"
    fi

    return 0
}

# Function to parse main .log file (error log)
parse_error_log() {
    local log_file="$1"

    echo "=== Parsing error log: $log_file ==="

    if [ ! -f "$log_file" ]; then
        echo "No error log file found"
        return 1
    fi

    # Check for AssertionErrors
    local assertion_errors=$(grep -c "AssertionError" "$log_file" 2>/dev/null || echo "0")
    if [ "$assertion_errors" -gt 0 ]; then
        echo -e "${RED}Found $assertion_errors AssertionError(s)${NC}"

        # Extract error details
        grep -A 20 "AssertionError" "$log_file" | head -50

        # Extract seed value
        local seed=$(grep "seed value" "$log_file" | sed 's/.*seed value: //')
        echo -e "${YELLOW}Reproduction seed: $seed${NC}"

        return 2  # Return 2 for logical error found
    fi

    # Check for other exceptions
    local exceptions=$(grep -E "Exception|Error" "$log_file" | grep -v "WARNING" | grep -v "sun.misc" | head -10)
    if [ -n "$exceptions" ]; then
        echo -e "${YELLOW}Found exceptions:${NC}"
        echo "$exceptions"
        return 3
    fi

    echo -e "${GREEN}No errors found in log${NC}"
    return 0
}

# Function to extract comparison queries from error log
extract_comparison_queries() {
    local log_file="$1"
    local output_dir="$2"

    echo "=== Extracting comparison queries ==="

    # Look for cardinality mismatch pattern
    if grep -q "cardinality" "$log_file"; then
        echo "Found cardinality mismatch error"

        # Extract original query
        local first_query=$(grep -A 1 "First query:" "$log_file" | tail -1)
        local second_query=$(grep -A 1 "Second query:" "$log_file" | tail -1)

        echo "Original query: $first_query"
        echo "Comparison query: $second_query"

        if [ -n "$output_dir" ]; then
            echo "$first_query" > "$output_dir/original_query.sql"
            echo "$second_query" > "$output_dir/comparison_query.sql"
        fi
    fi

    # Look for content mismatch pattern
    if grep -q "content of the result sets mismatch" "$log_file"; then
        echo "Found content mismatch error"

        # Extract queries with missing results
        grep -B 5 "content of the result sets mismatch" "$log_file" | \
        grep "Query:" > "$output_dir/mismatch_queries.txt" 2>/dev/null || true
    fi

    return 0
}

# Function to scan all log directories
scan_all_logs() {
    local dbms="$1"
    local log_base="logs"

    if [ -n "$dbms" ]; then
        log_base="logs/$dbms"
    fi

    echo -e "${BLUE}=== Scanning log directories: $log_base ===${NC}"
    echo ""

    local total_dirs=0
    local error_dirs=0
    local logical_errors=0

    for dir in $(find "$log_base" -type d -name "*_*" 2>/dev/null | sort); do
        total_dirs=$((total_dirs + 1))
        local dir_name=$(basename "$dir")
        local oracle_type=$(echo "$dir_name" | cut -d'_' -f1)

        # Check for .log file (error log)
        local log_file=$(ls "$dir"/*.log 2>/dev/null | grep -v "cur.log" | grep -v "plan.log" | grep -v "reduce.log" | head -1)

        if [ -f "$log_file" ]; then
            # Check for AssertionError
            if grep -q "AssertionError" "$log_file"; then
                echo -e "${RED}[ERROR] $dir_name - Logical error detected!${NC}"
                error_dirs=$((error_dirs + 1))
                logical_errors=$((logical_errors + 1))

                # Extract error summary
                grep "AssertionError" "$log_file" | head -1
            elif grep -qE "Exception|Error" "$log_file" | grep -v "WARNING"; then
                echo -e "${YELLOW}[WARN] $dir_name - Exception found${NC}"
                error_dirs=$((error_dirs + 1))
            else
                echo -e "${GREEN}[OK] $dir_name${NC}"
            fi
        else
            echo -e "${GREEN}[OK] $dir_name (no error log)${NC}"
        fi
    done

    echo ""
    echo -e "${BLUE}=== Scan Summary ===${NC}"
    echo "Total directories: $total_dirs"
    echo "Directories with errors: $error_dirs"
    echo "Logical errors (AssertionError): $logical_errors"

    return $logical_errors
}

# Function to analyze a specific log directory
analyze_log_dir() {
    local log_dir="$1"
    local output_dir="$2"

    if [ ! -d "$log_dir" ]; then
        echo -e "${RED}Error: Directory $log_dir does not exist${NC}"
        return 1
    fi

    echo -e "${BLUE}=== Analyzing: $log_dir ===${NC}"

    # Create output directory
    if [ -n "$output_dir" ]; then
        mkdir -p "$output_dir"
    else
        output_dir="$log_dir/analysis"
        mkdir -p "$output_dir"
    fi

    # List all log files in the directory
    echo "Log files found:"
    ls -la "$log_dir"/*.log 2>/dev/null || echo "No .log files found"

    # Parse -cur.log file (execution log)
    local cur_log=$(ls "$log_dir"/*-cur.log 2>/dev/null | head -1)
    if [ -f "$cur_log" ]; then
        parse_cur_log "$cur_log" "$output_dir/reproduction.sql"
    fi

    # Parse main .log file (error log if exists)
    local error_log=$(ls "$log_dir"/*.log 2>/dev/null | grep -v "cur.log" | grep -v "plan.log" | grep -v "reduce.log" | head -1)
    if [ -f "$error_log" ]; then
        parse_error_log "$error_log"
        extract_comparison_queries "$error_log" "$output_dir"

        # Copy error log to analysis directory
        cp "$error_log" "$output_dir/error_details.log"
    fi

    # Check for serialized reproduction state
    local ser_file=$(ls "$log_dir/reproduce/*.ser" 2>/dev/null | head -1)
    if [ -f "$ser_file" ]; then
        echo "Found serialized reproduction state: $ser_file"
        echo "To deserialize, use: java -jar $SQLANCER_JAR --reproduce-state $ser_file"
    fi

    # Check for reduce log
    local reduce_log=$(ls "$log_dir/reduce/*.log" 2>/dev/null | head -1)
    if [ -f "$reduce_log" ]; then
        echo "Found reducer log: $reduce_log"
        cp "$reduce_log" "$output_dir/reducer_output.log"
    fi

    echo ""
    echo -e "${GREEN}Analysis complete. Results saved to: $output_dir${NC}"

    return 0
}

# Function to generate reproduction command
generate_reproduce_command() {
    local log_dir="$1"
    local output_file="$2"

    echo -e "${BLUE}=== Generating reproduction script ===${NC}"

    # Parse cur.log to get seed and oracle type
    local cur_log=$(ls "$log_dir"/*-cur.log 2>/dev/null | head -1)
    if [ ! -f "$cur_log" ]; then
        echo -e "${RED}No execution log found${NC}"
        return 1
    fi

    local seed=$(grep "seed value" "$cur_log" | head -1 | sed 's/.*seed value: //')
    local db_name=$(grep "Database:" "$cur_log" | head -1 | sed 's/.*Database: //')
    local dir_name=$(basename "$log_dir")
    local oracle_type=$(echo "$dir_name" | cut -d'_' -f1)

    # Generate reproduction SQL script
    local sql_script="$log_dir/reproduction.sql"
    parse_cur_log "$cur_log" "$sql_script"

    # Generate reproduction command
    local cmd_file="$log_dir/reproduce.sh"

    echo "#!/bin/bash" > "$cmd_file"
    echo "# SQLancer Reproduction Script" >> "$cmd_file"
    echo "# Generated from: $log_dir" >> "$cmd_file"
    echo "# Oracle: $oracle_type" >> "$cmd_file"
    echo "# Seed: $seed" >> "$cmd_file"
    echo "" >> "$cmd_file"
    echo "SQLANCER_JAR=\"$SQLANCER_JAR\"" >> "$cmd_file"
    echo "HOST=\"$DBMS_HOST\"" >> "$cmd_file"
    echo "PORT=\"$DBMS_PORT\"" >> "$cmd_file"
    echo "USER=\"$DBMS_USER\"" >> "$cmd_file"
    echo "PASS=\"$DBMS_PASS\"" >> "$cmd_file"
    echo "" >> "$cmd_file"
    echo "# Run SQLancer with same seed" >> "$cmd_file"
    echo "java -jar \$SQLANCER_JAR \\" >> "$cmd_file"
    echo "    --host \$HOST \\" >> "$cmd_file"
    echo "    --port \$PORT \\" >> "$cmd_file"
    echo "    --username \$USER \\" >> "$cmd_file"
    echo "    --password \$PASS \\" >> "$cmd_file"
    echo "    --database-prefix reproduce_${oracle_type}_ \\" >> "$cmd_file"
    echo "    --random-seed $seed \\" >> "$cmd_file"
    echo "    --num-tries 1 \\" >> "$cmd_file"
    echo "    --log-each-select \\" >> "$cmd_file"
    echo "    $DBMS_TYPE \\" >> "$cmd_file"
    echo "    --oracle $oracle_type" >> "$cmd_file"
    echo "" >> "$cmd_file"
    echo "# Or execute reproduction SQL script directly" >> "$cmd_file"
    echo "# psql -h \$HOST -p \$PORT -U \$USER -f $sql_script" >> "$cmd_file"

    chmod +x "$cmd_file"

    echo -e "${GREEN}Generated reproduction script: $cmd_file${NC}"
    echo ""
    echo "Reproduction command:"
    cat "$cmd_file"

    return 0
}

# Function to compare query results
compare_query_results() {
    local log_dir="$1"
    local analysis_dir="$log_dir/analysis"

    echo -e "${BLUE}=== Comparing query results ===${NC}"

    local original_query="$analysis_dir/original_query.sql"
    local comparison_query="$analysis_dir/comparison_query.sql"

    if [ ! -f "$original_query" ] || [ ! -f "$comparison_query" ]; then
        echo "No comparison queries extracted from log"
        return 1
    fi

    # Connect to database and execute queries
    echo "Executing original query..."
    echo "Query: $(cat $original_query)"

    local original_result=$(PGPASSWORD="$DBMS_PASS" psql -h "$DBMS_HOST" -p "$DBMS_PORT" -U "$DBMS_USER" \
        -d test -t -c "$(cat $original_query)" 2>&1)

    echo "Executing comparison query..."
    echo "Query: $(cat $comparison_query)"

    local comparison_result=$(PGPASSWORD="$DBMS_PASS" psql -h "$DBMS_HOST" -p "$DBMS_PORT" -U "$DBMS_USER" \
        -d test -t -c "$(cat $comparison_query)" 2>&1)

    echo ""
    echo -e "${YELLOW}=== Original Query Result ===${NC}"
    echo "$original_result"

    echo ""
    echo -e "${YELLOW}=== Comparison Query Result ===${NC}"
    echo "$comparison_result"

    # Compare results
    if [ "$original_result" = "$comparison_result" ]; then
        echo -e "${GREEN}Results match - no logical error confirmed${NC}"
    else
        echo -e "${RED}Results differ - logical error confirmed!${NC}"

        # Save results for further analysis
        echo "$original_result" > "$analysis_dir/original_result.txt"
        echo "$comparison_result" > "$analysis_dir/comparison_result.txt"
    fi

    return 0
}

# Function to generate summary report
generate_summary() {
    local dbms="$1"
    local output_file="$2"

    if [ -z "$output_file" ]; then
        output_file="logs/analysis_summary.md"
    fi

    echo -e "${BLUE}=== Generating summary report ===${NC}"

    echo "# SQLancer Log Analysis Summary" > "$output_file"
    echo "" >> "$output_file"
    echo "Generated: $(date)" >> "$output_file"
    echo "" >> "$output_file"

    echo "## Directory Overview" >> "$output_file"
    echo "" >> "$output_file"

    local log_base="logs"
    if [ -n "$dbms" ]; then
        log_base="logs/$dbms"
    fi

    # Count by oracle type
    echo "| Oracle | Total Runs | Errors | Logical Errors |" >> "$output_file"
    echo "|--------|------------|--------|----------------|" >> "$output_file"

    for oracle in NOREC PQS WHERE TLP_WHERE HAVING AGGREGATE DISTINCT GROUP_BY QUERY_PARTITIONING CERT FUZZER DQP DQE EET CODDTEST; do
        local oracle_lower=$(echo "$oracle" | tr '[:upper:]' '[:lower:]')
        local total=$(find "$log_base" -type d -name "${oracle_lower}_*" 2>/dev/null | wc -l)
        local errors=0
        local logical=0

        for dir in $(find "$log_base" -type d -name "${oracle_lower}_*" 2>/dev/null); do
            local log_file=$(ls "$dir"/*.log 2>/dev/null | grep -v "cur.log" | grep -v "plan.log" | grep -v "reduce.log" | head -1)
            if [ -f "$log_file" ]; then
                if grep -q "AssertionError" "$log_file"; then
                    logical=$((logical + 1))
                    errors=$((errors + 1))
                elif grep -qE "Exception|Error" "$log_file"; then
                    errors=$((errors + 1))
                fi
            fi
        done

        if [ "$total" -gt 0 ]; then
            echo "| $oracle | $total | $errors | $logical |" >> "$output_file"
        fi
    done

    echo "" >> "$output_file"
    echo "## Error Details" >> "$output_file"
    echo "" >> "$output_file"

    # List all errors
    for dir in $(find "$log_base" -type d -name "*_*" 2>/dev/null | sort); do
        local log_file=$(ls "$dir"/*.log 2>/dev/null | grep -v "cur.log" | grep -v "plan.log" | grep -v "reduce.log" | head -1)
        if [ -f "$log_file" ]; then
            if grep -q "AssertionError" "$log_file"; then
                local dir_name=$(basename "$dir")
                local seed=$(grep "seed value" "$log_file" | sed 's/.*seed value: //')
                local error_msg=$(grep "AssertionError" "$log_file" | head -1)

                echo "### $dir_name" >> "$output_file"
                echo "- Seed: $seed" >> "$output_file"
                echo "- Error: $error_msg" >> "$output_file"
                echo "- Log: $log_file" >> "$output_file"
                echo "" >> "$output_file"
            fi
        fi
    done

    echo -e "${GREEN}Summary saved to: $output_file${NC}"
    cat "$output_file"

    return 0
}

# Main command handler
case "${1:-}" in
    scan)
        scan_all_logs "$2"
        ;;
    analyze)
        if [ -z "$2" ]; then
            usage
        fi
        analyze_log_dir "$2" "$3"
        ;;
    reproduce)
        if [ -z "$2" ]; then
            usage
        fi
        generate_reproduce_command "$2" "$3"
        ;;
    compare)
        if [ -z "$2" ]; then
            usage
        fi
        compare_query_results "$2"
        ;;
    summary)
        generate_summary "$2" "$3"
        ;;
    *)
        usage
        ;;
esac

exit 0