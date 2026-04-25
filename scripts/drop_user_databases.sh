#!/bin/bash

# PostgreSQL连接信息
PGHOST="localhost"
PGPORT="5432"
PGUSER="root"
PGPASSWORD="password"
PGDATABASE="tpcc"

# 系统数据库，不应删除
SYSTEM_DBS=("postgres" "template0" "template1" "tpcc")

# 导出密码环境变量
export PGPASSWORD="$PGPASSWORD"

echo "=== PostgreSQL 数据库批量删除脚本 ==="
echo "连接信息: ${PGUSER}@${PGHOST}:${PGPORT}"
echo ""

# 获取所有用户创建的数据库
echo "正在获取数据库列表..."
ALL_DBS=$(psql -h"$PGHOST" -p"$PGPORT" -U"$PGUSER" -d"$PGDATABASE" -t -A -c "
SELECT datname FROM pg_database
WHERE datistemplate = false
AND datname NOT IN ('postgres')
ORDER BY datname;
")

# 过滤掉系统数据库
USER_DBS=()
for db in $ALL_DBS; do
    skip=false
    for sys_db in "${SYSTEM_DBS[@]}"; do
        if [ "$db" == "$sys_db" ]; then
            skip=true
            break
        fi
    done
    if [ "$skip" = false ]; then
        USER_DBS+=("$db")
    fi
done

# 检查是否有用户数据库
if [ ${#USER_DBS[@]} -eq 0 ]; then
    echo "没有找到需要删除的用户数据库。"
    unset PGPASSWORD
    exit 0
fi

# 显示将要删除的数据库
echo "以下数据库将被删除:"
echo "----------------------------------------"
for db in "${USER_DBS[@]}"; do
    echo "  - $db"
done
echo "----------------------------------------"
echo "共 ${#USER_DBS[@]} 个数据库"
echo ""

# 确认删除
read -p "确认删除以上数据库? (y/N): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "操作已取消。"
    unset PGPASSWORD
    exit 0
fi

# 执行删除
echo ""
echo "开始删除数据库..."
DELETED=0
FAILED=0

for db in "${USER_DBS[@]}"; do
    echo -n "正在删除数据库: $db ... "

    # 先断开所有连接
    psql -h"$PGHOST" -p"$PGPORT" -U"$PGUSER" -d"$PGDATABASE" -c "
    SELECT pg_terminate_backend(pid)
    FROM pg_stat_activity
    WHERE datname = '$db' AND pid <> pg_backend_pid();
    " > /dev/null 2>&1

    # 删除数据库
    result=$(psql -h"$PGHOST" -p"$PGPORT" -U"$PGUSER" -d"$PGDATABASE" -c "DROP DATABASE \"$db\";" 2>&1)

    if [ $? -eq 0 ]; then
        echo "成功"
        ((DELETED++))
    else
        echo "失败"
        echo "  错误: $result"
        ((FAILED++))
    fi
done

# 清理环境变量
unset PGPASSWORD

# 输出统计
echo ""
echo "=== 删除完成 ==="
echo "成功删除: $DELETED 个"
echo "删除失败: $FAILED 个"