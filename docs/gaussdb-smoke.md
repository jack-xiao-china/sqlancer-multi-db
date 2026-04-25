# GaussDB-M（M-Compatibility）冒烟与验收口径

## 前置条件

- 已将 **GaussDB 官方 JDBC 驱动**加入 classpath（例如放到 `target/lib/` 并确保运行时可加载），或通过 `--jdbc-driver-class` 显式加载驱动类。
- 已知可用的连接 URL（示例形态，按你的驱动实际为准）：`jdbc:gaussdb://<host>:<port>/<db>`

## 基本用法（单 oracle）

子命令为 **`gaussdb-m`**（与旧文档中的 `gaussdb` 不同）。

```bash
java -jar target/sqlancer-2.0.0.jar ^
  --connection-url "jdbc:gaussdb://127.0.0.1:8000/test" ^
  --username root ^
  --password password ^
  --num-tries 1 ^
  --timeout-seconds 60 ^
  --num-queries 2000 ^
  gaussdb-m --oracle TLP_WHERE
```

可选参数：

- `--jdbc-driver-class <driverClass>`：当 DriverManager 无法自动发现驱动时使用
- `--jdbc-properties "k=v;k2=v2"`：透传驱动参数
- `--use-create-database=true`：优先用 `CREATE/DROP DATABASE` 做隔离；若权限/语法不支持可关闭，走 `CREATE/DROP SCHEMA` 隔离

## `--oracle` 可选项（与 `GaussDBMOracleFactory` 一致）

`AGGREGATE`、`HAVING`、`GROUP_BY`、`DISTINCT`、`NOREC`、`TLP_WHERE`、`PQS`、`CERT`、`FUZZER`、`DQP`、`DQE`、`EET`、`CODDTEST`、`QUERY_PARTITIONING`

## 冒烟脚本（覆盖多个 oracle）

```powershell
.\scripts\run-gaussdb-oracles-smoke.ps1 `
  -ConnectionUrl "jdbc:gaussdb://127.0.0.1:8000/test" `
  -Username "sqlancer" `
  -Password "sqlancer" `
  -TimeoutSeconds 60 `
  -NumQueries 2000 `
  -NumThreads 1
```

## 最小验收标准

- **连接层**：`--num-tries=1` 能成功启动并创建隔离空间（database 或 schema），可执行最少一轮建表/插入/查询。
- **每个 oracle**（上表所列全部名称）：建议运行至少 1 次 smoke（例如 60 秒 / 2000 queries）。
  - **不出现**工具侧崩溃或明显无效 SQL（脚本分类为 `TOOL_ILLEGAL`）。
  - 若出现 `java.lang.AssertionError`，视为“发现潜在 DB 逻辑 bug”，脚本分类为 `DB_BUG_FOUND`，可再交复现与最小化。
