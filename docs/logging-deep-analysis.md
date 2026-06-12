# SQLancer 日志系统深度分析

## 1. 概述

SQLancer 采用 **双层日志架构**：底层依赖 SLF4J-Simple 处理第三方库（JDBC 驱动等）的日志输出，上层自建 `StateLogger` + `LoggableFactory` 体系负责核心业务日志的生成、格式化与持久化。

**关键结论：项目当前没有任何日志轮换（rotation）机制，日志文件按每次运行生成独立目录，无大小限制、无自动清理。**

---

## 2. 日志框架选型

### 2.1 SLF4J-Simple（底层框架）

| 属性 | 值 |
|------|-----|
| 依赖 | `org.slf4j:slf4j-simple:2.0.6`（pom.xml:369-372） |
| 作用 | 仅控制第三方库（JDBC驱动、连接池等）的日志噪声 |
| 日志级别 | ERROR（硬编码于 `Main.java:72`） |
| 配置方式 | 系统属性 `org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY` |
| 配置文件 | 无（无 log4j.xml/logback.xml/simplelogger.properties） |

**配置代码**（`Main.java:71-76`）：
```java
static {
    System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "ERROR");
    if (!LOG_DIRECTORY.exists()) {
        LOG_DIRECTORY.mkdir();
    }
}
```

**设计意图**：SLF4J-Simple 是最轻量的 SLF4J 绑定——只输出到 `System.err`，无文件写入、无轮换、无格式化。其唯一用途是屏蔽 JDBC 驱动的大量 DEBUG/INFO 输出，不影响核心业务日志。

### 2.2 自建日志体系（上层框架）

核心类关系图：

```
DatabaseProvider (接口)
  └─ getLoggableFactory() → LoggableFactory
      ├─ SQLLoggableFactory（默认，所有 SQL 数据库）
      ├─ CnosDBLoggableFactory（时序数据库专用）
      └─ [可扩展其他数据库类型]

LoggableFactory (抽象工厂)
  ├─ createLoggable(input, suffix) → Loggable
  ├─ getInfo(dbName, version, seed) → Loggable
  ├─ convertStacktraceToLoggable(Throwable) → Loggable
  ├─ getQueryForStateToReproduce(queryString) → Query
  └─ commentOutQuery(query) → Query

Loggable (接口)
  └─ LoggedString（唯一实现）

Main.StateLogger（文件写入器）
  ├─ logFileWriter → <db>.log（错误日志）
  ├─ currentFileWriter → <db>-cur.log（执行日志）
  ├─ queryPlanFileWriter → <db>-plan.log（QPG 日志）
  ├─ reduceFileWriter → <db>-reduce.log（Reducer 日志）
  └─ reproduceFilePath → <db>.ser（序列化状态）
```

---

## 3. 日志文件生成方式

### 3.1 目录结构与命名规则

```
logs/                                          ← LOG_DIRECTORY（Main.java:63）
  <dbms-name>/                                 ← provider.getDBMSName()
    <oracle>_YYYY_MMDD_HHMM/                   ← computeRunDirectoryName()
      <database>.log                            ← 错误/异常日志（Always）
      <database>-cur.log                        ← 语句执行日志（--log-each-select=true）
      <database>-plan.log                       ← QPG 查询计划日志（--qpg-log-query-plan=true）
      reduce/
        <database>-reduce.log                   ← Reducer 缩减日志（--use-reducer=true）
      reproduce/
        <database>.ser                          ← 序列化复现状态（--serialize-reproduce-state=true）
```

**实际示例**：
```
logs/mysql/eet_2026_0409_1453/database0.log
logs/mysql/eet_2026_0409_1453/database0-cur.log
logs/gaussdb-m/norec_2026_0410_0900/database1.log
```

### 3.2 运行目录命名算法

（`Main.java:598-615`）：
```java
static String computeRunDirectoryName(DBMSSpecificOptions<?> command) {
    String oracleName = "oracle";
    List<?> oracles = command.getTestOracleFactory();
    if (oracles != null && !oracles.isEmpty()) {
        if (oracles.size() == 1) {
            oracleName = String.valueOf(oracles.get(0));
        } else {
            oracleName = String.valueOf(oracles.get(0)) + "_plus";
        }
    }
    oracleName = oracleName.toLowerCase();
    DateFormat fmt = new SimpleDateFormat("yyyy_MMdd_HHmm");
    String ts = fmt.format(new Date());
    return oracleName + "_" + ts;
}
```

**关键特性**：
- 同一次进程启动的所有线程共享同一个 `runDirectoryName`（`DBMSExecutorFactory` 缓存）
- 精度到分钟级（`HHmm`），同一分钟内的多次运行会覆盖同一目录
- 多 Oracle 组合时使用第一个 Oracle 名 + `_plus` 后缀

### 3.3 日志文件类型与写入策略

| 文件类型 | FileWriter 创建方式 | 写入模式 | Flush 策略 | 何时创建 |
|----------|---------------------|----------|------------|----------|
| `<db>.log` | `AlsoWriteToConsoleFileWriter` | 覆盖（new） | 每次写入后 flush | 延迟初始化（首次 logException） |
| `<db>-cur.log` | `FileWriter(curFile, false)` | 覆盖（false） | 每次写入后 flush | 延迟初始化（首次 writeCurrent） |
| `<db>-plan.log` | `FileWriter(queryPlanFile, true)` | **追加（true）** | 每次写入后 flush | 延迟初始化（首次 writeQueryPlan） |
| `<db>-reduce.log` | `FileWriter(reduceFile, false)` | 覆盖（false） | 每次写入后 flush | 延迟初始化（首次 logReducer） |
| `<db>.ser` | `ObjectOutputStream` | 覆盖 | 一次性写入 | 延迟初始化（serialize 时） |

**特殊设计：AlsoWriteToConsoleFileWriter**（`Main.java:99-116`）

这是 `<db>.log` 的专用 FileWriter，同时写入文件和 `System.err`。当检测到 bug（AssertionError/异常）时，错误信息既持久化到文件，又实时打印到控制台，方便运维人员即时发现问题。

```java
private static final class AlsoWriteToConsoleFileWriter extends FileWriter {
    @Override
    public Writer append(CharSequence arg0) throws IOException {
        System.err.println(arg0);
        return super.append(arg0);
    }
    @Override
    public void write(String str) throws IOException {
        System.err.println(str);
        super.write(str);
    }
}
```

### 3.4 日志内容格式化

#### SQLLoggableFactory（默认）

- 自动补分号：`input` 不以 `;` 结尾则追加
- 转义换行符：`\n` → `\\n`，`\r` → `\\r`（保证单行 SQL 可读）
- 头部信息：时间、数据库名、版本号、种子值，以 SQL 注释格式输出
- 异常堆栈：每行前缀 `--`，确保堆栈与 SQL 语句视觉分离

```
-- Time: 2026/04/09 14:53:22
-- Database: database0
-- Database version: 8.0.32
-- seed value: 1234567890
CREATE TABLE t0 (c0 INT);\nINSERT INTO t0 VALUES (1);
--java.lang.AssertionError: The size of the result sets mismatch
--    at sqlancer.mysql.oracle.MySQLEETOracle.check(...)
```

#### CnosDBLoggableFactory（差异）

与 SQLLoggableFactory 几乎一致，唯一区别：**不转义换行符**。原因是 CnosDB 是时序数据库，其查询语句天然多行，转义换行反而破坏可读性。

### 3.5 日志写入调用链

```
GlobalState.executeStatement()
  └─ executePrologue() → StateLogger.writeCurrent() / writeCurrentNoLineBreak()
       └─ LoggableFactory.createLoggable() → LoggedString.getLogString()
       └─ FileWriter.write() + flush()

DBMSExecutor.run() → catch (Throwable)
  └─ StateLogger.logException(Throwable, StateToReproduce)
       └─ LoggableFactory.convertStacktraceToLoggable() → LoggedString.getLogString()
       └─ AlsoWriteToConsoleFileWriter.write() → System.err + 文件
       └─ printState() → 写入头部信息 + 所有累积 SQL 语句
```

---

## 4. 日志轮换机制分析

### 4.1 当前状况：**无轮换机制**

项目不存在任何形式的日志轮换：
- ❌ 无基于大小的轮换（Log4j RollingFileAppender / Logback SizeAndTimeRollingPolicy）
- ❌ 无基于时间的轮换（按天/按小时切分）
- ❌ 无最大文件数限制
- ❌ 无自动清理旧日志
- ❌ 无压缩归档

### 4.2 现有的"伪轮换"设计

项目采用 **按运行生成目录** 的策略，本质上是一种"逻辑分割"而非"物理轮换"：

- 每次进程启动生成一个 `<oracle>_YYYY_MMDD_HHMM` 目录
- 同一次运行的所有数据库线程共享该目录
- 不同运行产生不同目录，自然隔离

**局限性**：

| 问题 | 影响 |
|------|------|
| 分钟级时间戳 | 同一分钟内重启会覆盖上一轮的日志 |
| 无清理机制 | 长期运行后 `logs/` 目录会无限膨胀 |
| 单文件无大小限制 | `cur.log` 在 `--num-queries=100000` 默认配置下可达数十 MB |
| 多线程同目录 | 16 个线程（默认）各自写入 `database0.log`、`database1.log` 等，文件数随线程增长 |
| 无归档压缩 | 磁盘空间浪费 |

### 4.3 文件关闭策略

日志文件的关闭时机：

| 场景 | 关闭行为 |
|------|----------|
| 正常完成 | `DBMSExecutor.run()` 结束时关闭 `currentFileWriter` |
| 异常捕获 | `finally` 块中关闭 `currentFileWriter`（`Main.java:773-782`） |
| Reducer 完成 | 关闭 `reduceFileWriter`（`Main.java:514-518`） |
| `logFileWriter` | **从不显式关闭**——仅在异常时设为 null（`Main.java:765`） |
| 进程退出 | JVM shutdown hook 无日志清理逻辑 |

**潜在风险**：`logFileWriter`（错误日志）从不显式关闭，依赖 JVM 退出时 GC 回收。虽然 `AlsoWriteToConsoleFileWriter` 继承 `FileWriter`，最终会由 GC 调用 `close()`，但在高并发场景下可能出现缓冲区数据丢失。

---

## 5. 命令行日志选项

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `--log-each-select` | true | 记录每条执行语句（核心开关） |
| `--log-execution-time` | true | 记录执行耗时（依赖 log-each-select） |
| `--print-failed` | true | 记录失败语句 |
| `--log-dir` | null | 自定义日志根目录 |
| `--qpg-log-query-plan` | false | 记录查询计划 |
| `--serialize-reproduce-state` | false | 序列化复现状态 |
| `--use-reducer` | false | 启用 Reducer |
| `--print-statements` | false | 打印语句到 stdout |
| `--print-succeeding-statements` | false | 打印成功语句到 stdout |

---

## 6. 日志分析工具

项目附带两个日志分析脚本：

- `analyze_sqlancer_logs.py`：Python 实现，功能完整（扫描、分析、复现脚本生成、查询结果对比、报告生成）
- `analyze_sqlancer_logs.sh`：Shell 脚本简化版

Python 分析器支持的命令：
- `scan`：扫描所有日志目录，统计错误
- `analyze`：分析单个目录，提取错误详情
- `reproduce`：生成 SQL/bash 复现脚本
- `compare`：连接数据库验证逻辑错误
- `report`：生成 Markdown/JSON/HTML 报告

---

## 7. 问题总结与改进方向

### 7.1 当前问题清单

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| P1 | **无日志轮换** | 高 | 长期自动化测试场景下日志无限增长，磁盘耗尽风险 |
| P2 | **无自动清理** | 高 | 旧日志目录无过期删除机制 |
| P3 | **分钟级时间戳碰撞** | 中 | 同一分钟重启覆盖旧日志，丢失历史数据 |
| P4 | **logFileWriter 不显式关闭** | 中 | 异常场景下可能丢失最后几条日志 |
| P5 | **无缓冲写入** | 低 | 每次 write 后立即 flush，高并发下 I/O 开销大 |
| P6 | **AlsoWriteToConsole 双写** | 低 | 错误日志同时写 System.err，多线程下控制台输出混乱 |
| P7 | **QPG 日志追加模式** | 低 | `plan.log` 用 append 模式，跨数据库累积，与其他文件的覆盖模式不一致 |

### 7.2 改进建议（仅列出方向，不涉及具体实现）

1. **引入日志轮换**：可考虑替换 SLF4J-Simple 为 Logback/Log4j2，利用其 RollingFileAppender 机制实现大小+时间双维轮换
2. **自动清理策略**：添加 `--log-retention-days` 选项，启动时清理超过指定天数的旧日志目录
3. **时间戳精度**：将 `HHmm` 提升到 `HHmmss` 或加入进程 PID，避免碰撞
4. **FileWriter 生命周期管理**：在 `DBMSExecutor` 中统一 close 所有 FileWriter
5. **缓冲写入**：使用 `BufferedWriter` 包装 `FileWriter`，按周期或条数批量 flush
6. **日志级别分层**：将 `AlsoWriteToConsole` 改为可配置（`--log-also-console`），而非硬编码双写