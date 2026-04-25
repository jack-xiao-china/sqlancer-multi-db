#!/usr/bin/env python3
"""
SQLancer Log Analysis Tool

This tool analyzes SQLancer logs to:
1. Detect logical errors (AssertionError)
2. Extract reproducible test cases
3. Compare query results for verification
4. Generate analysis reports
"""

import os
import sys
import re
import json
import argparse
import subprocess
from pathlib import Path
from datetime import datetime
from collections import defaultdict
import psycopg2
try:
    import psycopg2
except ImportError:
    psycopg2 = None

# ANSI colors
class Colors:
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    MAGENTA = '\033[0;35m'
    CYAN = '\033[0;36m'
    NC = '\033[0m'  # No Color

def color_print(color, text):
    """Print colored text to console."""
    print(f"{color}{text}{Colors.NC}")

class SQLancerLogAnalyzer:
    """Analyzer for SQLancer log files."""

    # Patterns for parsing logs
    HEADER_PATTERN = re.compile(r'^--\s*(Time|Database|Database version|seed value):\s*(.+)$')
    SQL_STATEMENT_PATTERN = re.compile(r'^[^-].*;.*--.*ms;$')
    ASSERTION_ERROR_PATTERN = re.compile(r'java\.lang\.AssertionError')
    CARDINALITY_MISMATCH_PATTERN = re.compile(r'The size of the result sets mismatch')
    CONTENT_MISMATCH_PATTERN = re.compile(r'The content of the result sets mismatch')
    QUERY_PATTERN = re.compile(r'-- Query: "(.+?)"')
    CARDINALITY_PATTERN = re.compile(r'-- cardinality: (\d+)')

    def __init__(self, db_config=None):
        """
        Initialize analyzer.

        Args:
            db_config: Database connection configuration dict
        """
        self.db_config = db_config or {
            'host': 'localhost',
            'port': 5432,
            'user': 'root',
            'password': 'password',
            'database': 'test'
        }
        self.results = {
            'total_logs': 0,
            'errors': [],
            'logical_errors': [],
            'exceptions': [],
            'by_oracle': defaultdict(lambda: {'total': 0, 'errors': 0, 'logical': 0})
        }

    def parse_cur_log(self, log_path):
        """
        Parse a -cur.log file (execution log).

        Args:
            log_path: Path to the log file

        Returns:
            dict: Parsed log information
        """
        result = {
            'file': str(log_path),
            'seed': None,
            'database': None,
            'version': None,
            'timestamp': None,
            'statements': []
        }

        if not os.path.exists(log_path):
            return result

        with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # Extract header information
        for match in self.HEADER_PATTERN.finditer(content):
            key = match.group(1).lower().replace(' ', '_')
            value = match.group(2).strip()
            if key == 'seed_value':
                result['seed'] = value
            elif key == 'database':
                result['database'] = value
            elif key == 'database_version':
                result['version'] = value
            elif key == 'time':
                result['timestamp'] = value

        # Extract SQL statements
        lines = content.split('\n')
        for line in lines:
            line = line.strip()
            # Skip header lines and empty lines
            if line.startswith('--') and 'ms;' not in line:
                continue
            if not line:
                continue
            # This is a SQL statement
            if line.endswith(';') or '--' in line:
                result['statements'].append(line)

        return result

    def parse_error_log(self, log_path):
        """
        Parse a main .log file (error log).

        Args:
            log_path: Path to the log file

        Returns:
            dict: Error information
        """
        result = {
            'file': str(log_path),
            'has_error': False,
            'error_type': None,
            'error_message': None,
            'seed': None,
            'queries': {
                'original': None,
                'comparison': None,
                'original_cardinality': None,
                'comparison_cardinality': None
            },
            'missing_results': {
                'original': [],
                'comparison': []
            },
            'stack_trace': None
        }

        if not os.path.exists(log_path):
            return result

        with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # Extract seed
        seed_match = re.search(r'seed value:\s*(\d+)', content)
        if seed_match:
            result['seed'] = seed_match.group(1)

        # Check for AssertionError (logical error)
        if self.ASSERTION_ERROR_PATTERN.search(content):
            result['has_error'] = True
            result['error_type'] = 'AssertionError'

            # Extract error message
            error_match = re.search(r'AssertionError:\s*(.+?)(?:\n|$)', content)
            if error_match:
                result['error_message'] = error_match.group(1).strip()

            # Check for cardinality mismatch
            if self.CARDINALITY_MISMATCH_PATTERN.search(content):
                result['error_type'] = 'CardinalityMismatch'

                # Extract queries and cardinalities
                lines = content.split('\n')
                for i, line in enumerate(lines):
                    if 'First query:' in line:
                        # Look for the query in the next line(s)
                        for j in range(i+1, min(i+5, len(lines))):
                            if lines[j].strip().startswith('--'):
                                continue
                            query_match = re.search(r'"(.+?)".*?cardinality:\s*(\d+)', lines[j])
                            if query_match:
                                result['queries']['original'] = query_match.group(1)
                                result['queries']['original_cardinality'] = int(query_match.group(2))
                                break
                    if 'Second query:' in line:
                        for j in range(i+1, min(i+5, len(lines))):
                            if lines[j].strip().startswith('--'):
                                continue
                            query_match = re.search(r'"(.+?)".*?cardinality:\s*(\d+)', lines[j])
                            if query_match:
                                result['queries']['comparison'] = query_match.group(1)
                                result['queries']['comparison_cardinality'] = int(query_match.group(2))
                                break

            # Check for content mismatch
            elif self.CONTENT_MISMATCH_PATTERN.search(content):
                result['error_type'] = 'ContentMismatch'

                # Extract missing results
                for match in self.QUERY_PATTERN.finditer(content):
                    query = match.group(1)
                    if 'It misses:' in content[match.end():match.end()+100]:
                        # This query has missing results
                        miss_match = re.search(r'It misses:\s*"(.+?)"', content[match.end():match.end()+200])
                        if miss_match:
                            result['missing_results']['comparison'].append(miss_match.group(1))

            # Extract stack trace
            stack_match = re.search(r'AssertionError.*?(?:\n\s+at\s+.+)+', content)
            if stack_match:
                result['stack_trace'] = stack_match.group(0)

        # Check for other exceptions
        elif 'Exception' in content and 'WARNING' not in content.split('Exception')[0][-50:]:
            result['has_error'] = True
            result['error_type'] = 'Exception'

            # Extract exception type and message
            exc_match = re.search(r'(\w+Exception):\s*(.+?)(?:\n|$)', content)
            if exc_match:
                result['error_message'] = f"{exc_match.group(1)}: {exc_match.group(2)}"

        return result

    def analyze_directory(self, dir_path):
        """
        Analyze a SQLancer log directory.

        Args:
            dir_path: Path to the log directory

        Returns:
            dict: Analysis results
        """
        dir_path = Path(dir_path)
        result = {
            'directory': str(dir_path),
            'oracle_type': None,
            'execution_log': None,
            'error_log': None,
            'has_logical_error': False,
            'error_details': None,
            'reproduction_info': None
        }

        # Determine oracle type from directory name
        dir_name = dir_path.name
        oracle_match = re.match(r'([a-z]+)_\d{4}_\d{4}_\d{4}', dir_name)
        if oracle_match:
            result['oracle_type'] = oracle_match.group(1).upper()

        # Find execution log (-cur.log)
        cur_logs = list(dir_path.glob('*-cur.log'))
        if cur_logs:
            result['execution_log'] = self.parse_cur_log(cur_logs[0])

        # Find error log (*.log excluding -cur.log, -plan.log, -reduce.log)
        error_logs = [f for f in dir_path.glob('*.log')
                     if not f.name.endswith('-cur.log')
                     and not f.name.endswith('-plan.log')
                     and not f.name.endswith('-reduce.log')]
        if error_logs:
            result['error_log'] = self.parse_error_log(error_logs[0])
            result['has_logical_error'] = result['error_log']['error_type'] == 'AssertionError'
            if result['has_logical_error']:
                result['error_details'] = result['error_log']

        # Check for serialized reproduction state
        ser_files = list(dir_path.glob('reproduce/*.ser'))
        if ser_files:
            result['serialized_state'] = str(ser_files[0])

        # Check for reducer log
        reduce_logs = list(dir_path.glob('reduce/*.log'))
        if reduce_logs:
            result['reducer_log'] = str(reduce_logs[0])

        return result

    def scan_all_logs(self, base_path='logs/postgres'):
        """
        Scan all log directories.

        Args:
            base_path: Base path for log directories

        Returns:
            dict: Scan results
        """
        base_path = Path(base_path)
        results = []

        for dir_path in sorted(base_path.glob('*_*')):
            if dir_path.is_dir():
                result = self.analyze_directory(dir_path)
                results.append(result)

                # Update statistics
                self.results['total_logs'] += 1
                oracle_type = result['oracle_type'] or 'UNKNOWN'
                self.results['by_oracle'][oracle_type]['total'] += 1

                if result['error_log'] and result['error_log']['has_error']:
                    self.results['by_oracle'][oracle_type]['errors'] += 1
                    if result['has_logical_error']:
                        self.results['by_oracle'][oracle_type]['logical'] += 1
                        self.results['logical_errors'].append(result)
                    else:
                        self.results['exceptions'].append(result)

        return results

    def generate_reproduction_script(self, dir_path, output_path=None):
        """
        Generate reproduction script for a log directory.

        Args:
            dir_path: Path to the log directory
            output_path: Output path for the script

        Returns:
            str: Generated script content
        """
        analysis = self.analyze_directory(dir_path)

        if not analysis['execution_log']:
            return None

        exec_log = analysis['execution_log']
        oracle_type = analysis['oracle_type'] or 'UNKNOWN'

        script = f"""-- SQLancer Reproduction Script
-- Generated: {datetime.now().isoformat()}
-- Oracle: {oracle_type}
-- Seed: {exec_log['seed']}
-- Database: {exec_log['database']}
-- Version: {exec_log['version']}
-- Source: {exec_log['file']}

-- Setup connection
\\c test;

-- Drop existing database if exists
DROP DATABASE IF EXISTS {exec_log['database']};
CREATE DATABASE {exec_log['database']};
\\c {exec_log['database']};

-- Execute statements
"""
        for stmt in exec_log['statements']:
            # Format statement for SQL script
            if not stmt.startswith('--'):
                script += f"{stmt}\n"

        if output_path:
            with open(output_path, 'w') as f:
                f.write(script)

        return script

    def generate_bash_reproduce_script(self, dir_path, output_path=None):
        """
        Generate bash script to reproduce the error.

        Args:
            dir_path: Path to the log directory
            output_path: Output path for the script

        Returns:
            str: Generated script content
        """
        analysis = self.analyze_directory(dir_path)

        if not analysis['execution_log']:
            return None

        exec_log = analysis['execution_log']
        oracle_type = analysis['oracle_type'] or 'UNKNOWN'

        script = f"""#!/bin/bash
# SQLancer Reproduction Script
# Generated: {datetime.now().isoformat()}
# Oracle: {oracle_type}
# Seed: {exec_log['seed']}

SQLANCER_JAR="/d/Jack.Xiao/dbtools/sqlancer-main/sqlancer-main/target/sqlancer-2.0.0.jar"
HOST="{self.db_config['host']}"
PORT="{self.db_config['port']}"
USER="{self.db_config['user']}"
PASS="{self.db_config['password']}"

# Run SQLancer with the same seed
java -jar $SQLANCER_JAR \
    --host $HOST \
    --port $PORT \
    --username $USER \
    --password $PASS \
    --database-prefix reproduce_{oracle_type.lower()}_ \
    --random-seed {exec_log['seed']} \
    --num-tries 1 \
    --log-each-select \
    postgres \
    --oracle {oracle_type}
"""

        if output_path:
            with open(output_path, 'w') as f:
                f.write(script)
            os.chmod(output_path, 0o755)

        return script

    def compare_query_results(self, queries, db_config=None):
        """
        Compare query results to verify logical error.

        Args:
            queries: dict with 'original' and 'comparison' query strings
            db_config: Database connection config (optional)

        Returns:
            dict: Comparison results
        """
        if psycopg2 is None:
            color_print(Colors.YELLOW, "psycopg2 not installed, cannot compare query results")
            return {'error': 'psycopg2 not installed'}

        config = db_config or self.db_config
        result = {
            'original_result': None,
            'comparison_result': None,
            'match': False,
            'difference': None
        }

        if not queries.get('original') or not queries.get('comparison'):
            result['error'] = 'Missing query strings'
            return result

        try:
            conn = psycopg2.connect(
                host=config['host'],
                port=config['port'],
                user=config['user'],
                password=config['password'],
                database=config['database']
            )

            # Execute original query
            try:
                cur = conn.cursor()
                cur.execute(queries['original'])
                result['original_result'] = cur.fetchall()
                cur.close()
            except Exception as e:
                result['original_error'] = str(e)

            # Execute comparison query
            try:
                cur = conn.cursor()
                cur.execute(queries['comparison'])
                result['comparison_result'] = cur.fetchall()
                cur.close()
            except Exception as e:
                result['comparison_error'] = str(e)

            # Compare results
            if result['original_result'] and result['comparison_result']:
                result['match'] = result['original_result'] == result['comparison_result']
                if not result['match']:
                    result['difference'] = {
                        'original_count': len(result['original_result']),
                        'comparison_count': len(result['comparison_result'])
                    }

            conn.close()

        except Exception as e:
            result['connection_error'] = str(e)

        return result

    def generate_report(self, results=None, output_format='markdown', output_path=None):
        """
        Generate analysis report.

        Args:
            results: Analysis results (optional, uses self.results if None)
            output_format: Output format ('markdown', 'json', 'html')
            output_path: Output file path (optional)

        Returns:
            str: Generated report
        """
        results = results or self.results

        if output_format == 'json':
            report = json.dumps(results, indent=2, default=str)
        elif output_format == 'markdown':
            report = self._generate_markdown_report(results)
        elif output_format == 'html':
            report = self._generate_html_report(results)
        else:
            raise ValueError(f"Unknown output format: {output_format}")

        if output_path:
            with open(output_path, 'w') as f:
                f.write(report)

        return report

    def _generate_markdown_report(self, results):
        """Generate markdown format report."""
        report = f"""# SQLancer Log Analysis Report

Generated: {datetime.now().isoformat()}

## Summary

- Total log directories analyzed: {results['total_logs']}
- Logical errors (AssertionError): {len(results['logical_errors'])}
- Other exceptions: {len(results['exceptions'])}

## Statistics by Oracle

| Oracle | Total | Errors | Logical Errors |
|--------|-------|--------|----------------|
"""
        for oracle, stats in sorted(results['by_oracle'].items()):
            if stats['total'] > 0:
                report += f"| {oracle} | {stats['total']} | {stats['errors']} | {stats['logical']} |\n"

        # Add logical error details
        if results['logical_errors']:
            report += "\n## Logical Errors Detected\n\n"
            for error in results['logical_errors']:
                report += f"""### {error['directory']}

- Oracle: {error['oracle_type']}
- Seed: {error['execution_log']['seed'] if error['execution_log'] else 'N/A'}
- Error Type: {error['error_details']['error_type']}
- Error Message: {error['error_details']['error_message']}

"""
                if error['error_details']['queries']['original']:
                    report += f"""**Original Query:**
```sql
{error['error_details']['queries']['original']}
```
- Cardinality: {error['error_details']['queries']['original_cardinality']}

"""
                if error['error_details']['queries']['comparison']:
                    report += f"""**Comparison Query:**
```sql
{error['error_details']['queries']['comparison']}
```
- Cardinality: {error['error_details']['queries']['comparison_cardinality']}

"""
                report += "---\n\n"

        return report

    def _generate_html_report(self, results):
        """Generate HTML format report."""
        md_report = self._generate_markdown_report(results)
        # Simple conversion to HTML
        html = f"""<!DOCTYPE html>
<html>
<head>
    <title>SQLancer Log Analysis Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        h1 { color: #333; }
        h2 { color: #666; border-bottom: 1px solid #ccc; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
        .error { background-color: #ffebee; }
        .logical-error { background-color: #ffcdd2; }
        pre { background-color: #f5f5f5; padding: 10px; overflow-x: auto; }
    </style>
</head>
<body>
"""
        # Convert markdown to HTML (simple conversion)
        lines = md_report.split('\n')
        for line in lines:
            if line.startswith('# '):
                html += f"<h1>{line[2:]}</h1>\n"
            elif line.startswith('## '):
                html += f"<h2>{line[3:]}</h2>\n"
            elif line.startswith('### '):
                html += f"<h3>{line[4:]}</h3>\n"
            elif line.startswith('| '):
                cells = line.split('|')[1:-1]
                if '---' in line:
                    html += "</thead><tbody>\n"
                else:
                    html += "<tr>" + "".join(f"<td>{c.strip()}</td>" for c in cells) + "</tr>\n"
            elif line.startswith('```'):
                if 'sql' in line:
                    html += "<pre class='sql'>\n"
                else:
                    html += "<pre>\n"
            elif line.startswith('---'):
                html += "<hr>\n"
            elif line.strip():
                html += f"<p>{line}</p>\n"

        html += "</body></html>"
        return html


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description='SQLancer Log Analysis Tool')
    parser.add_argument('command', choices=['scan', 'analyze', 'reproduce', 'compare', 'report'],
                       help='Command to execute')
    parser.add_argument('path', nargs='?', help='Path to log directory or base directory')
    parser.add_argument('--db-host', default='localhost', help='Database host')
    parser.add_argument('--db-port', type=int, default=5432, help='Database port')
    parser.add_argument('--db-user', default='root', help='Database user')
    parser.add_argument('--db-pass', default='password', help='Database password')
    parser.add_argument('--db-name', default='test', help='Database name')
    parser.add_argument('--output', '-o', help='Output file path')
    parser.add_argument('--format', '-f', choices=['markdown', 'json', 'html'], default='markdown',
                       help='Output format for report')
    parser.add_argument('--verbose', '-v', action='store_true', help='Verbose output')

    args = parser.parse_args()

    db_config = {
        'host': args.db_host,
        'port': args.db_port,
        'user': args.db_user,
        'password': args.db_pass,
        'database': args.db_name
    }

    analyzer = SQLancerLogAnalyzer(db_config)

    if args.command == 'scan':
        base_path = args.path or 'logs/postgres'
        color_print(Colors.BLUE, f"Scanning log directories: {base_path}")

        results = analyzer.scan_all_logs(base_path)

        # Print summary
        color_print(Colors.BLUE, "\n=== Scan Summary ===")
        print(f"Total directories: {analyzer.results['total_logs']}")
        print(f"Logical errors: {len(analyzer.results['logical_errors'])}")
        print(f"Other exceptions: {len(analyzer.results['exceptions'])}")

        # Print by oracle
        print("\nBy Oracle:")
        for oracle, stats in sorted(analyzer.results['by_oracle'].items()):
            if stats['total'] > 0:
                status = Colors.GREEN if stats['logical'] == 0 else Colors.RED
                color_print(status, f"  {oracle}: {stats['total']} runs, {stats['errors']} errors, {stats['logical']} logical")

        # Generate report if output specified
        if args.output:
            analyzer.generate_report(format=args.format, output_path=args.output)
            color_print(Colors.GREEN, f"Report saved to: {args.output}")

    elif args.command == 'analyze':
        if not args.path:
            color_print(Colors.RED, "Error: path argument required for analyze command")
            sys.exit(1)

        color_print(Colors.BLUE, f"Analyzing: {args.path}")
        result = analyzer.analyze_directory(args.path)

        # Print details
        if result['execution_log']:
            color_print(Colors.CYAN, "\nExecution Log Details:")
            print(f"  Seed: {result['execution_log']['seed']}")
            print(f"  Database: {result['execution_log']['database']}")
            print(f"  Version: {result['execution_log']['version']}")
            print(f"  Statements: {len(result['execution_log']['statements'])}")

        if result['error_log']:
            if result['has_logical_error']:
                color_print(Colors.RED, "\nLogical Error Detected!")
                print(f"  Type: {result['error_details']['error_type']}")
                print(f"  Message: {result['error_details']['error_message']}")
                if result['error_details']['queries']['original']:
                    print(f"  Original Query: {result['error_details']['queries']['original'][:100]}...")
                    print(f"  Original Cardinality: {result['error_details']['queries']['original_cardinality']}")
                if result['error_details']['queries']['comparison']:
                    print(f"  Comparison Query: {result['error_details']['queries']['comparison'][:100]}...")
                    print(f"  Comparison Cardinality: {result['error_details']['queries']['comparison_cardinality']}")
            elif result['error_log']['has_error']:
                color_print(Colors.YELLOW, "\nException Detected:")
                print(f"  Message: {result['error_log']['error_message']}")
            else:
                color_print(Colors.GREEN, "\nNo errors found")

        # Generate reproduction script if logical error
        if result['has_logical_error'] and args.output:
            analyzer.generate_reproduction_script(args.path, args.output + '.sql')
            analyzer.generate_bash_reproduce_script(args.path, args.output + '.sh')
            color_print(Colors.GREEN, f"Reproduction scripts saved to: {args.output}.sql and {args.output}.sh")

    elif args.command == 'reproduce':
        if not args.path:
            color_print(Colors.RED, "Error: path argument required for reproduce command")
            sys.exit(1)

        output_path = args.output or args.path + '/reproduce'

        # Generate SQL reproduction script
        sql_script = analyzer.generate_reproduction_script(args.path, output_path + '.sql')
        if sql_script:
            color_print(Colors.GREEN, f"SQL reproduction script saved to: {output_path}.sql")

        # Generate bash reproduction script
        bash_script = analyzer.generate_bash_reproduce_script(args.path, output_path + '.sh')
        if bash_script:
            color_print(Colors.GREEN, f"Bash reproduction script saved to: {output_path}.sh")

    elif args.command == 'compare':
        if not args.path:
            color_print(Colors.RED, "Error: path argument required for compare command")
            sys.exit(1)

        result = analyzer.analyze_directory(args.path)

        if not result['has_logical_error']:
            color_print(Colors.YELLOW, "No logical error found in this directory")
            sys.exit(0)

        if not result['error_details']['queries']['original']:
            color_print(Colors.YELLOW, "No comparison queries extracted from error log")
            sys.exit(0)

        color_print(Colors.BLUE, "Comparing query results...")

        comparison = analyzer.compare_query_results(result['error_details']['queries'])

        print("\nOriginal Query Result:")
        print(f"  Rows: {len(comparison.get('original_result', []))}")
        if comparison.get('original_error'):
            print(f"  Error: {comparison['original_error']}")

        print("\nComparison Query Result:")
        print(f"  Rows: {len(comparison.get('comparison_result', []))}")
        if comparison.get('comparison_error'):
            print(f"  Error: {comparison['comparison_error']}")

        if comparison['match']:
            color_print(Colors.GREEN, "\nResults match - no logical error confirmed")
        else:
            color_print(Colors.RED, "\nResults differ - logical error confirmed!")
            print(f"  Difference: {comparison.get('difference')}")

    elif args.command == 'report':
        base_path = args.path or 'logs/postgres'
        color_print(Colors.BLUE, f"Generating report from: {base_path}")

        analyzer.scan_all_logs(base_path)

        output_path = args.output or f'logs/analysis_report_{datetime.now().strftime("%Y%m%d_%H%M%S")}.{args.format}'
        report = analyzer.generate_report(format=args.format, output_path=output_path)

        color_print(Colors.GREEN, f"Report saved to: {output_path}")

        if args.verbose:
            print(report)


if __name__ == '__main__':
    main()