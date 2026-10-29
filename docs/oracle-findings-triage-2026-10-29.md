# Oracle 发现类研判报告（2026-10-29）

> 前置：v2.7.12 已修复全部确诊工具类问题（见 `oracle-full-matrix-assessment-2026-10-29.md` 附记）。本报告研判剩余的 oracle 产出发现：判定**真 DB bug 候选 / oracle 语义缺口 / 噪声**，并给出最小复现与处置建议。
> 方法：失败 SQL 对提取 → 状态重放（scratch 库逐条容错重放 DDL/DML）→ 双查询对比 → 逐分支/逐行归因 → 微测试钉死。

## 一、已验证真 DB Bug（含最小复现）

### MYSQL-BUG-1：JSON 比较的三值逻辑自相矛盾（MySQL 8.4.8）

**来源**：MySQL TLP_WHERE 发现（"size mismatch (10 and 9)"）。状态重放后双查询确定性复现（10 vs 9）。

**现象**：同一谓词 `p = (JSON_ARRAY()) IN (LEAST(c0, c0))`，对零 TIMESTAMP 行：
- 投影求值：`p = NULL`
- 包装求值：`p IS NULL = FALSE(0)`

按三值逻辑，`p` 为 NULL 则 `p IS NULL` 必为 TRUE——两上下文返回矛盾真值。TLP 三分区（p / NOT p / p IS NULL）因此漏掉该行（三分支均不满足：p≠TRUE、NOT NULL≠TRUE、p IS NULL=FALSE），基数差 1。

**最小复现**：
```sql
CREATE TABLE t (c0 TIMESTAMP NULL);
INSERT IGNORE INTO t VALUES ('0000-00-00 00:00:00');  -- 严格模式下经 IGNORE 存入零日期
SELECT (JSON_ARRAY()) IN (LEAST(c0,c0)) AS p,          -- NULL
       ((JSON_ARRAY()) IN (LEAST(c0,c0))) IS NULL      -- 0（矛盾！）
FROM t;
```
字面量直测（`JSON_ARRAY() IN ('0000-00-00 ...')`）不触发（0/0 一致）——矛盾需要**列引用 + LEAST 的类型化 TIMESTAMP**参与 JSON 比较。推断：IS NULL 求值路径对 JSON×TIMESTAMP 强转失败的处理与直接投影路径不一致。

**影响面**：MySQL TLP_WHERE/GROUP_BY 的"差一/差多"失配大概率同源（表中含零日期行 + JSON 谓词）。**处置建议**：向 MySQL/Oracle 官方报告（bug.oracle.com），注明 8.4.8 复现路径。

## 二、按模式分类的其余发现

| 类别 | 数量 | 分类 | 依据 |
|------|------|------|------|
| GaussDB-M AGGREGATE 失配 | 12 | **10 = 非确定性假阳性**（投影含 `NOW()/CURTIME()`，两次执行时点不同）；2 待验 | 失败 SQL 逐条检查，10/12 含非确定函数 |
| GaussDB-M GROUP_BY 失配（4-vs-0） | 4 | **待验证**（分区谓词为算术表达式 `(...)/CAST(...)`，三分区全空） | 需 GaussDB 侧状态重放（psql 协议受限，建议用 sqlancer `--seed` 复现） |
| GaussDB-M CERT 估计单调性 | 1 | **优化器发现候选**（弱谓词估计反降 1945→1081） | oracle 按设计工作；需优化器团队研判 |
| GaussDB-A CERT ×10 / MySQL CERT ×2 | 12 | 同上（A 侧最多） | |
| GaussDB-A/M EET row-state mismatch | ~35 | **专项**（4 库共性，EET DML 已知难点） | PR#15 曾修 GaussDB-M 的一类（FORCE RLS）；需专项分析 |
| TX_INFER 终态不一致（M/A/PG） | ~19 | **专项**（3 库共性高频） | 事务终态比较的语义深度问题 |
| MySQL DQE 剩余不对称 | ~6 | 混合：`3146 vs 3141`（同因不同码）为 MySQL 语句上下文差异——**候选**；"SELECT has errors but UPDATE does not" 需逐例验证 | v2.7.12 诊断信息已带错误详情 |
| MySQL/GaussDB-M EDC_RADAR 失配 | ~8 | **候选**（等价库结果集不等——v2.7.12 后崩溃清零，剩余为 oracle 本体产出） | 需逐例状态重放 |
| PG EDC_DATA ×4 / PG FUCCI ×10 | 14 | **候选**（函数期望值失配 / MVCC 违反） | 需逐例验证 |
| GaussDB 驱动级（radix-16/类型缓存） | 3 | **非 DBMS 问题**（openGauss JDBC 驱动缺陷） | 驱动栈异常 |

## 三、工具侧遗留项（研判中发现的）

1. **TLP 族缺非确定性投影守卫**（GaussDB-M AGGREGATE 10/12 假阳性的直接原因）——建议 v2.7.13：对 M/MySQL TLP 族查询做 `NOW()/CURTIME()` 等Contains 检查跳过（同 v2.7.11 MySQL CODDTEST 的方案）
2. GaussDB-M TLP 的分区谓词可为**算术表达式**（非布尔）——若 GaussDB-M 对数值 WHERE 的真值语义使三分区不完备，同属工具侧生成约束问题；待 GROUP_BY 4-vs-0 验证后定论

## 四、结论

- **1 个已钉死的真 MySQL bug**（三值逻辑违例，含最小复现，建议上报官方）
- **~60 个发现中：~10 个已定性为工具侧假阳性（非确定性）、~45 个候选/专项、3 个驱动级**
- oracle 的核心变形关系（TLP 三分区、CERT 单调性、EDC 等价、DQE 错误包含）在 v2.7.12 修复后的产出**以真发现与深语义问题为主**，工具噪声占比已大幅下降
