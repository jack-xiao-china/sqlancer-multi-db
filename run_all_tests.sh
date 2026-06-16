#!/bin/bash
# =============================================================================
# SQLancer 一键全量测试脚本
# 支持：MySQL、PostgreSQL、GaussDB-M、GaussDB-A、GaussDB-PG
# 用法：
#   ./run_all_tests.sh --dbms mysql --host localhost --port 3306 --username tpcc --password xxx [--oracle ORACLE] [--duration SECONDS] [--num-tries N] [--target-database DB]
#   ./run_all_tests.sh --dbms gaussdb-m --host 121.37.186.131 --port 19995 --username xxx --password xxx --target-database testm
#   ./run_all_tests.sh --all   # 运行所有 DBMS 的全量 Oracle 组合
# =============================================================================

set -euo pipefail

# ── 颜色定义 ──
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ── 项目根目录 ──
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
JAR_NAME="sqlancer-2.6.0.jar"
JAR_PATH="${PROJECT_DIR}/target/${JAR_NAME}"
LIB_PATH="${PROJECT_DIR}/target/lib"
OG_JAR="${PROJECT_DIR}/lib/opengauss-jdbc-6.0.2-og.jar"

# ── 默认参数 ──
DBMS=""
HOST=""
PORT=-1
USERNAME="sqlancer"
PASSWORD="sqlancer"
ORACLE=""
DURATION=300        # 默认 5 分钟
NUM_TRIES=100        # 默认每个 oracle 100 次尝试
NUM_THREADS=4       # 默认 4 线程
TARGET_DATABASE=""
RUN_ALL=false
VERBOSE=false
SKIP_BUILD=false
FUCCI_TYPES="DT MT CS ALL"

# ── Oracle 清单（按 DBMS）──
MYSQL_ORACLES="AGGREGATE CERT CODDTEST DISTINCT DQE DQP EDC_DATA EDC_RADAR EET EET_DELETE EET_INSERT_SELECT EET_UPDATE FUZZER GROUP_BY HAVING JIR NOREC PQS QUERY_PARTITIONING SONAR TLP_WHERE"
# 注意：FUCCI/TX_INFER/WRITE_CHECK/WRITE_CHECK_REPRODUCE 需要事务支持，单独运行

MYSQL_TX_ORACLES="FUCCI TX_INFER WRITE_CHECK WRITE_CHECK_REPRODUCE"

POSTGRES_ORACLES="AGGREGATE CERT CODDTEST DISTINCT DQE DQP EDC_DATA EDC_RADAR EET EET_DELETE EET_INSERT_SELECT EET_UPDATE FUZZER GROUP_BY HAVING JIR NOREC PQS QUERY_PARTITIONING SONAR TLP_WHERE"
POSTGRES_TX_ORACLES="FUCCI TX_INFER WRITE_CHECK WRITE_CHECK_REPRODUCE"

GAUSSDBM_ORACLES="AGGREGATE CERT CODDTEST DISTINCT DQE DQP EDC_DATA EDC_RADAR EET EET_DELETE EET_INSERT_SELECT EET_UPDATE FUZZER GROUP_BY HAVING JIR NOREC PQS QUERY_PARTITIONING SONAR TLP_WHERE"
GAUSSDBM_TX_ORACLES="FUCCI TX_INFER WRITE_CHECK WRITE_CHECK_REPRODUCE"

# GaussDB-A: 无 CODDTEST/EDC_RADAR/SONAR
GAUSSDBA_ORACLES="AGGREGATE CERT DISTINCT DQE DQP EDC_DATA EET EET_DELETE EET_INSERT_SELECT EET_UPDATE FUZZER GROUP_BY HAVING JIR NOREC PQS QUERY_PARTITIONING TLP_WHERE"
GAUSSDBA_TX_ORACLES="FUCCI TX_INFER WRITE_CHECK WRITE_CHECK_REPRODUCE"

# GaussDB-PG: 仅 12 个 Oracle（CERT/DQP/DQE 是占位符）
GAUSSDBPG_ORACLES="AGGREGATE CERT DISTINCT DQE DQP FUZZER GROUP_BY HAVING NOREC PQS QUERY_PARTITIONING TLP_WHERE"
GAUSSDBPG_TX_ORACLES=""  # 无事务 Oracle

# ── DBMS 默认端口 ──
MYSQL_DEFAULT_PORT=3306
POSTGRES_DEFAULT_PORT=5432
GAUSSDB_DEFAULT_PORT=19995

# ── 辅助函数 ──
log_info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_err()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${CYAN}[STEP]${NC} $1"; }

usage() {
    cat <<'EOF'
SQLancer 一键全量测试脚本

用法:
  ./run_all_tests.sh [选项]

选项:
  --dbms DBMS           目标数据库类型: mysql | postgres | gaussdb-m | gaussdb-a | gaussdb-pg
  --host HOST           数据库主机地址 (MySQL/PG 默认: localhost)
  --port PORT           数据库端口 (MySQL: 3306, PG: 5432, GaussDB: 19995)
  --username USER       数据库用户名 (默认: sqlancer)
  --password PASS       数据库密码 (默认: sqlancer)
  --target-database DB  GaussDB 兼容数据库 (GaussDB-M/A/PG 必填)
  --oracle ORACLE       只运行指定 Oracle (例如: EDC_DATA, FUCCI)
  --duration SECONDS    总运行时间上限 (默认: 300秒)
  --num-tries N         每个 Oracle 的尝试次数 (默认: 50)
  --num-threads N       并发线程数 (默认: 4)
  --all                 运行所有 DBMS 的全量 Oracle 组合
  --skip-build          跳过 mvn package (需要已构建 JAR)
  --verbose             详细输出
  --help                显示此帮助

注意: SQLancer 使用 JCommander 子命令模式，全局参数(--host/--port/--num-tries等)
      必须放在 DBMS 子命令前，DBMS 特定参数(--oracle/--fucci-oracle-type等)放在子命令后。
      脚本内部会自动处理参数顺序。

示例:
  # 单 DBMS 全量测试
  ./run_all_tests.sh --dbms mysql --host localhost --port 3306 --username tpcc --password xxx

  # 单 Oracle 测试
  ./run_all_tests.sh --dbms gaussdb-m --host 121.37.186.131 --port 19995 \
    --username sqlbuilder1 --password xxx --target-database testm --oracle EDC_DATA

  # FUCCI Oracle 多参数组合
  ./run_all_tests.sh --dbms postgres --host localhost --port 5432 \
    --username tpcc --password xxx --oracle FUCCI

  # 全 DBMS 全量测试 (需要配置所有连接信息)
  ./run_all_tests.sh --all

Oracle 参数组合说明:
  FUCCI:     --fucci-oracle-type DT/MT/CS/ALL  × --fucci-isolation-level RANDOM/READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE
  CODDTEST:  --coddtest-model RANDOM/EXPRESSION/SUBQUERY
  GaussDB:   --target-database 必填 (M: DBCOMPATIBILITY 'M', A: DBCOMPATIBILITY 'A', PG: DBCOMPATIBILITY 'pg')
EOF
    exit 0
}

# ── 解析参数 ──
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dbms)          DBMS="$2"; shift 2 ;;
        --host)          HOST="$2"; shift 2 ;;
        --port)          PORT="$2"; shift 2 ;;
        --username)      USERNAME="$2"; shift 2 ;;
        --password)      PASSWORD="$2"; shift 2 ;;
        --target-database) TARGET_DATABASE="$2"; shift 2 ;;
        --oracle)        ORACLE="$2"; shift 2 ;;
        --duration)      DURATION="$2"; shift 2 ;;
        --num-tries)     NUM_TRIES="$2"; shift 2 ;;
        --num-threads)   NUM_THREADS="$2"; shift 2 ;;
        --all)           RUN_ALL=true; shift ;;
        --skip-build)    SKIP_BUILD=true; shift ;;
        --verbose)       VERBOSE=true; shift ;;
        --help|-h)       usage ;;
        *)               log_err "未知参数: $1"; usage ;;
    esac
done

# ── 构建 JAR ──
build_jar() {
    if [[ "$SKIP_BUILD" == "true" ]]; then
        log_info "跳过构建，使用已有 JAR"
    else
        log_step "构建 sqlancer JAR..."
        cd "$PROJECT_DIR"
        mvn package -q -DskipTests 2>&1 | tail -5
        if [[ ! -f "$JAR_PATH" ]]; then
            log_err "构建失败：未找到 $JAR_PATH"
            exit 1
        fi
        log_ok "构建完成：$JAR_PATH"
    fi

    # 验证 JAR 和依赖
    if [[ ! -f "$JAR_PATH" ]]; then
        log_err "JAR 不存在: $JAR_PATH，请先运行 mvn package 或使用 --skip-build"
        exit 1
    fi
    if [[ ! -d "$LIB_PATH" ]]; then
        log_err "依赖目录不存在: $LIB_PATH，请先运行 mvn package"
        exit 1
    fi
    if [[ ! -f "$OG_JAR" ]]; then
        log_warn "OpenGauss JDBC 驱动不存在: $OG_JAR（GaussDB 测试将失败）"
    fi
}

# ── 生成 Java 命令 ──
# 使用 java -jar 方式运行（manifest 内嵌 classpath，避免 Windows 路径问题）
# GaussDB 需要 OpenGauss JDBC 驱动在 target/lib/ 目录下（构建时自动复制）

# ── 获取 DBMS 默认端口 ──
get_default_port() {
    case "$1" in
        mysql)      echo $MYSQL_DEFAULT_PORT ;;
        postgres)   echo $POSTGRES_DEFAULT_PORT ;;
        gaussdb-m|gaussdb-a|gaussdb-pg) echo $GAUSSDB_DEFAULT_PORT ;;
        *)          echo -1 ;;
    esac
}

# ── 获取 DBMS Oracle 列表 ──
get_oracles() {
    case "$1" in
        mysql)      echo "$MYSQL_ORACLES" ;;
        postgres)   echo "$POSTGRES_ORACLES" ;;
        gaussdb-m)  echo "$GAUSSDBM_ORACLES" ;;
        gaussdb-a)  echo "$GAUSSDBA_ORACLES" ;;
        gaussdb-pg) echo "$GAUSSDBPG_ORACLES" ;;
        *)          echo "" ;;
    esac
}

get_tx_oracles() {
    case "$1" in
        mysql)      echo "$MYSQL_TX_ORACLES" ;;
        postgres)   echo "$POSTGRES_TX_ORACLES" ;;
        gaussdb-m)  echo "$GAUSSDBM_TX_ORACLES" ;;
        gaussdb-a)  echo "$GAUSSDBA_TX_ORACLES" ;;
        gaussdb-pg) echo "$GAUSSDBPG_TX_ORACLES" ;;
        *)          echo "" ;;
    esac
}

# ── 运行单个 Oracle 测试 ──
# 参数: dbms oracle [额外DBMS参数] [custom_num_tries]
# 注意: --num-tries/--num-threads/--timeout-seconds 等全局参数必须在子命令前
#       只有 --oracle/--target-database/--fucci-*/--coddtest-model 等 DBMS 参数在子命令后
run_oracle() {
    local dbms="$1"
    local oracle="$2"
    local extra_params="${3:-}"
    local custom_tries="${4:-}"
    local resolved_port="${PORT}"
    local resolved_host="${HOST}"

    # 设置默认端口
    if [[ "$resolved_port" == "-1" ]]; then
        resolved_port=$(get_default_port "$dbms")
    fi

    # 设置默认主机
    if [[ -z "$resolved_host" ]]; then
        case "$dbms" in
            mysql|postgres) resolved_host="localhost" ;;
            gaussdb-m|gaussdb-a|gaussdb-pg)
                log_err "GaussDB 必须指定 --host"
                return 1
                ;;
        esac
    fi

    # GaussDB 必须指定 --target-database
    local target_db_param=""
    local target_db_value=""
    if [[ "$dbms" =~ gaussdb ]]; then
        if [[ -z "$TARGET_DATABASE" ]]; then
            log_err "GaussDB 必须指定 --target-database"
            return 1
        fi
        target_db_param="--target-database"
        target_db_value="${TARGET_DATABASE}"
    fi

    # 构建超时参数（每个 oracle 分配合理时间）
    local timeout_param=""
    if [[ "$DURATION" -gt 0 ]]; then
        timeout_param="--timeout-seconds"
        local timeout_value="${DURATION}"
    fi

    # PostgreSQL 特定参数
    local pg_param_name=""
    local pg_param_value=""
    if [[ "$dbms" == "postgres" ]]; then
        pg_param_name="--test-tablespaces"
        pg_param_value="false"
    fi

    # JCommander 参数顺序：全局参数在子命令前，DBMS参数在子命令后
    local effective_tries="${NUM_TRIES}"
    if [[ -n "$custom_tries" ]]; then
        effective_tries="$custom_tries"
    fi
    local cmd_args=(
        --host "${resolved_host}"
        --port "${resolved_port}"
        --username "${USERNAME}"
        --password "${PASSWORD}"
        --num-tries "${effective_tries}"
        --num-threads "${NUM_THREADS}"
    )
    if [[ -n "$timeout_param" ]]; then
        cmd_args+=( "$timeout_param" "$timeout_value" )
    fi
    # 子命令
    cmd_args+=( "${dbms}" )
    # DBMS 特定参数
    cmd_args+=( --oracle "${oracle}" )
    if [[ -n "$target_db_param" ]]; then
        cmd_args+=( "$target_db_param" "$target_db_value" )
    fi
    if [[ -n "$pg_param_name" ]]; then
        cmd_args+=( "$pg_param_name" "$pg_param_value" )
    fi
    if [[ -n "$extra_params" ]]; then
        # extra_params 可能含多个空格分隔的参数
        read -ra extra_arr <<< "$extra_params"
        cmd_args+=( "${extra_arr[@]}" )
    fi

    log_step "运行 ${dbms}/${oracle}"
    if [[ "$VERBOSE" == "true" ]]; then
        echo "  命令: java -jar ${JAR_PATH} ${cmd_args[*]}"
    fi

    local start_time=$(date +%s)
    local exit_code=0

    # 执行命令，输出到日志文件，最后显示摘要
    # 日志文件名包含 oracle 名称 + extra_params 摘要（避免 FUCCI/CODDTEST 组合日志覆盖）
    local log_suffix=""
    if [[ -n "$extra_params" ]]; then
        # 提取关键参数值用于文件名：--fucci-oracle-type DT --fucci-isolation-level RC → "_DT_RC"
        local param_values=""
        read -ra ep_arr <<< "$extra_params"
        local i=0
        while [[ $i -lt ${#ep_arr[@]} ]]; do
            local key="${ep_arr[$i]}"
            local val="${ep_arr[$i+1]:-}"
            # 只取值，跳过参数名前缀的长字符串
            case "$key" in
                --fucci-oracle-type)  param_values="${param_values}_${val}" ;;
                --fucci-isolation-level) param_values="${param_values}_${val}" ;;
                --fucci-schedule-count) param_values="${param_values}_sc${val}" ;;
                --coddtest-model)    param_values="${param_values}_${val}" ;;
                --target-database)   ;;  # 不编入文件名，太长
                --test-tablespaces)  ;;  # 不编入文件名
                *)                    ;;  # 其他参数不编入文件名
            esac
            i=$((i + 2))
        done
        log_suffix="${param_values}"
    fi
    local log_file="${PROJECT_DIR}/logs/test_${dbms}_${oracle}${log_suffix}.log"
    mkdir -p "${PROJECT_DIR}/logs"
    java -jar "${JAR_PATH}" "${cmd_args[@]}" > "${log_file}" 2>&1 || exit_code=$?

    # 显示日志尾部摘要
    tail -5 "${log_file}"

    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))

    # Java exit code: 0=正常, -1=发现Bug(bash映射为255或127), 其他=异常
    # bash 将 Java -1 映射为 255 (unsigned) 或 127 (Windows Git Bash)
    local java_exit="${exit_code}"
    if [[ "$java_exit" -eq 255 || "$java_exit" -eq 127 ]]; then
        # Java 返回了 -1 (errorExitCode)，表示发现 Bug
        java_exit=1
    fi

    if [[ "$java_exit" -eq 0 ]]; then
        log_ok "${dbms}/${oracle} 完成 (${elapsed}秒, exit=0)"
    elif [[ "$java_exit" -eq 1 ]]; then
        log_warn "${dbms}/${oracle} 发现 Bug (exit=1) (${elapsed}秒)"
    else
        log_err "${dbms}/${oracle} 异常退出 (exit=${java_exit}) (${elapsed}秒)"
    fi

    # 返回修正后的退出码（1=发现Bug, 0=通过, 其他=异常）
    return $java_exit
}

# ── 运行 FUCCI Oracle 的所有参数组合 ──
run_fucci_combinations() {
    local dbms="$1"
    local isolation_levels="RANDOM READ_COMMITTED REPEATABLE_READ SERIALIZABLE"

    # MySQL 使用 READ_UNCOMMITTED
    if [[ "$dbms" == "mysql" ]]; then
        isolation_levels="RANDOM READ_UNCOMMITTED READ_COMMITTED REPEATABLE_READ SERIALIZABLE"
    fi

    for ftype in $FUCCI_TYPES; do
        for iso in $isolation_levels; do
            local extra="--fucci-oracle-type ${ftype} --fucci-isolation-level ${iso} --fucci-schedule-count 5"
            run_oracle "$dbms" "FUCCI" "$extra" || true
        done
    done
}

# ── 运行 CODDTEST 的所有参数组合 ──
# MySQL/PostgreSQL/GaussDB-M 均支持 --coddtest-model
run_coddtest_combinations() {
    local dbms="$1"
    local models="RANDOM EXPRESSION SUBQUERY"
    for model in $models; do
        local extra="--coddtest-model ${model}"
        run_oracle "$dbms" "CODDTEST" "$extra" || true
    done
}

# ── 运行单个 DBMS 的全量测试 ──
run_dbms_all() {
    local dbms="$1"
    local oracles=$(get_oracles "$dbms")
    local tx_oracles=$(get_tx_oracles "$dbms")

    log_info "=== 开始 ${dbms} 全量测试 ==="
    log_info "查询 Oracle: $(echo $oracles | wc -w) 个"
    log_info "事务 Oracle: $(echo $tx_oracles | wc -w | tr -d ' ') 个"

    local total=0
    local passed=0
    local failed=0
    local bugs=0

    # ── 1. 查询类 Oracle（逐个运行）──
    for oracle in $oracles; do
        # QUERY_PARTITIONING 是组合 Oracle，单独处理
        if [[ "$oracle" == "QUERY_PARTITIONING" ]]; then
            continue
        fi
        # CODDTEST 有参数组合，单独处理
        if [[ "$oracle" == "CODDTEST" ]]; then
            continue
        fi
        # FUZZER 速度很快但检测率低，减少 num-tries
        local tries="$NUM_TRIES"
        if [[ "$oracle" == "FUZZER" ]]; then
            tries=20
        fi

        total=$((total + 1))
        if run_oracle "$dbms" "$oracle" "" "${tries}"; then
            passed=$((passed + 1))
        else
            local ec=$?
            if [[ "$ec" -eq 1 ]]; then
                bugs=$((bugs + 1))
            else
                failed=$((failed + 1))
            fi
        fi
    done

    # ── 2. CODDTEST ──
    # MySQL/PostgreSQL/GaussDB-M 均支持 --coddtest-model 参数
    if echo "$oracles" | grep -qw "CODDTEST"; then
        log_info "运行 CODDTEST 参数组合 (3 模式: RANDOM/EXPRESSION/SUBQUERY)"
        for model in RANDOM EXPRESSION SUBQUERY; do
            total=$((total + 1))
            if run_oracle "$dbms" "CODDTEST" "--coddtest-model ${model}" "${NUM_TRIES}"; then
                passed=$((passed + 1))
            else
                local ec=$?
                if [[ "$ec" -eq 1 ]]; then bugs=$((bugs + 1)); else failed=$((failed + 1)); fi
            fi
        done
    fi

    # ── 3. QUERY_PARTITIONING（组合 Oracle，单次运行）──
    if echo "$oracles" | grep -qw "QUERY_PARTITIONING"; then
        total=$((total + 1))
        if run_oracle "$dbms" "QUERY_PARTITIONING" "" "${NUM_TRIES}"; then
            passed=$((passed + 1))
        else
            local ec=$?
            if [[ "$ec" -eq 1 ]]; then bugs=$((bugs + 1)); else failed=$((failed + 1)); fi
        fi
    fi

    # ── 4. FUCCI 参数组合 ──
    if echo "$tx_oracles" | grep -qw "FUCCI"; then
        log_info "运行 FUCCI 参数组合 (4 类型 × 4 隔离级别 = 16 组合)"
        local isolation_levels="RANDOM READ_COMMITTED REPEATABLE_READ SERIALIZABLE"
        if [[ "$dbms" == "mysql" ]]; then
            isolation_levels="RANDOM READ_UNCOMMITTED READ_COMMITTED REPEATABLE_READ SERIALIZABLE"
        fi
        for ftype in $FUCCI_TYPES; do
            for iso in $isolation_levels; do
                total=$((total + 1))
                if run_oracle "$dbms" "FUCCI" "--fucci-oracle-type ${ftype} --fucci-isolation-level ${iso} --fucci-schedule-count 5" "20"; then
                    passed=$((passed + 1))
                else
                    local ec=$?
                    if [[ "$ec" -eq 1 ]]; then bugs=$((bugs + 1)); else failed=$((failed + 1)); fi
                fi
            done
        done
    fi

    # ── 5. TX_INFER ──
    if echo "$tx_oracles" | grep -qw "TX_INFER"; then
        total=$((total + 1))
        if run_oracle "$dbms" "TX_INFER" "" "20"; then
            passed=$((passed + 1))
        else
            local ec=$?
            if [[ "$ec" -eq 1 ]]; then bugs=$((bugs + 1)); else failed=$((failed + 1)); fi
        fi
    fi

    # ── 6. WRITE_CHECK / WRITE_CHECK_REPRODUCE ──
    if echo "$tx_oracles" | grep -qw "WRITE_CHECK"; then
        total=$((total + 1))
        if run_oracle "$dbms" "WRITE_CHECK" "" "20"; then
            passed=$((passed + 1))
        else
            local ec=$?
            if [[ "$ec" -eq 1 ]]; then bugs=$((bugs + 1)); else failed=$((failed + 1)); fi
        fi
    fi
    if echo "$tx_oracles" | grep -qw "WRITE_CHECK_REPRODUCE"; then
        total=$((total + 1))
        if run_oracle "$dbms" "WRITE_CHECK_REPRODUCE" "" "10"; then
            passed=$((passed + 1))
        else
            local ec=$?
            if [[ "$ec" -eq 1 ]]; then bugs=$((bugs + 1)); else failed=$((failed + 1)); fi
        fi
    fi

    # ── 结果汇总 ──
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  ${dbms} 测试结果汇总                   ║${NC}"
    echo -e "${CYAN}╠══════════════════════════════════════════╣${NC}"
    echo -e "${CYAN}║  总计: ${total}                           ║${NC}"
    echo -e "${GREEN}║  通过: ${passed}                           ║${NC}"
    echo -e "${RED}║  发现Bug: ${bugs}                        ║${NC}"
    echo -e "${YELLOW}║  失败: ${failed}                           ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════╝${NC}"
    echo ""
}

# ── 运行指定 DBMS 的单个 Oracle ──
run_single_oracle() {
    local dbms="$1"
    local oracle="$2"

    # FUCCI 参数组合
    if [[ "$oracle" == "FUCCI" ]]; then
        run_fucci_combinations "$dbms"
        return
    fi

    # CODDTEST 参数组合
    if [[ "$oracle" == "CODDTEST" ]]; then
        run_coddtest_combinations "$dbms"
        return
    fi

    # 其他 Oracle 直接运行
    run_oracle "$dbms" "$oracle"
}

# ── 全量测试（所有 DBMS）──
run_all_dbms() {
    log_info "=== 全量测试：所有 DBMS ==="
    echo ""

    # MySQL
    if [[ -n "$HOST" || "$DBMS" == "mysql" ]]; then
        run_dbms_all "mysql"
    else
        log_warn "MySQL: 需要配置连接信息 (--host/--port/--username/--password)"
    fi

    # PostgreSQL
    if [[ -n "$HOST" || "$DBMS" == "postgres" ]]; then
        run_dbms_all "postgres"
    else
        log_warn "PostgreSQL: 需要配置连接信息"
    fi

    # GaussDB-M
    if [[ -n "$HOST" && -n "$TARGET_DATABASE" ]]; then
        run_dbms_all "gaussdb-m"
    else
        log_warn "GaussDB-M: 需要 --host 和 --target-database"
    fi

    # GaussDB-A
    if [[ -n "$HOST" && -n "$TARGET_DATABASE" ]]; then
        run_dbms_all "gaussdb-a"
    else
        log_warn "GaussDB-A: 需要 --host 和 --target-database"
    fi

    # GaussDB-PG
    if [[ -n "$HOST" && -n "$TARGET_DATABASE" ]]; then
        run_dbms_all "gaussdb-pg"
    else
        log_warn "GaussDB-PG: 需要 --host 和 --target-database"
    fi
}

# ── 主逻辑 ──
build_jar

if [[ "$RUN_ALL" == "true" ]]; then
    run_all_dbms
elif [[ -n "$ORACLE" ]]; then
    if [[ -z "$DBMS" ]]; then
        log_err "指定 Oracle 时必须同时指定 --dbms"
        exit 1
    fi
    run_single_oracle "$DBMS" "$ORACLE"
elif [[ -n "$DBMS" ]]; then
    run_dbms_all "$DBMS"
else
    log_err "必须指定 --dbms 或 --all"
    usage
fi

log_ok "测试完成！"
