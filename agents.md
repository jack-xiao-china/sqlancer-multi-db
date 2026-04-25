# SQLancer 项目分析与代理执行指南

## 概述

- 本项目是一个用于自动化测试数据库管理系统（DBMS）的工具，核心目标是通过自动生成 SQL、执行并比对结果来发现数据库实现中的漏洞与异常行为。
- 主要特性包括多种测试生成策略（如 PQS、NoREC、TLP、DQE、CERT 等）、对比/回归测试的测试用例和测试流控制，以及对多种数据库的适配层（DBMS 驱动/适配器）。

## 架构要点

- Core（核心引擎）：控制测试流程（参数解析、测试循环、测试用例管理、结果收集与日志输出）
- DBMS 适配层（Adapters）：对各 DBMS 的连接、语法特性、结果校验等做适配
- 测试生成器与测试 oracle（Oracles）：NoREC、TLP、PQS、DQE、CERT 等
- 实验日志与调试：主要日志默认在 `target/logs/`

## 快速入门

### 构建/编译

```bash
mvn clean package -DskipTests
```

### 运行

```bash
cd target
java -jar sqlancer-2.0.0.jar --host localhost --port 3306 --username root --password password --num-tries 1 --timeout-seconds 100 --log-each-select true --num-threads 2 mysql --oracle QUERY_PARTITIONING
```

## 输出与日志指南

- 主要日志默认在 `target/logs/`
- 当出现断言错误（AssertionError）且日志中有对应的对比输出，通常代表检测到潜在逻辑错误
- 若遇到崩溃，日志中会有崩溃现场信息，需配合重现脚本进一步定位

## MySQL Oracles Smoke（工具健康检查）

目标：

- 允许发现 MySQL 逻辑错误（AssertionError mismatch）
- 不允许 SQLancer 自身生成非法 SQL/崩溃（如 `SQLSyntaxErrorException`、NPE、内部 `AssertionError: DATE`）

执行：

```bash
mvn package -DskipTests
```

```powershell
.\scripts\run-mysql-oracles-smoke.ps1 -DbHost localhost -Port 3306 -Username root -Password password
```

在 Linux 环境（bash）运行：

```bash
export DB_HOST=localhost DB_PORT=3306 DB_USER=root DB_PASS='password'
export TIMEOUT_SECONDS=25 NUM_QUERIES=5000 NUM_THREADS=1
./scripts/run-mysql-oracles-smoke.sh
```

输出：

- 汇总：`target/logs/mysql-oracles-smoke-results.csv`
- 详细失败（仅非 OK）：`target/logs/mysql-oracles-smoke-summary.txt`

## 参考与链接

- 项目主页/代码库：`https://github.com/sqlancer/sqlancer`
- 文档与论文：`docs/` 目录及 `https://github.com/sqlancer/sqlancer/blob/documentation`