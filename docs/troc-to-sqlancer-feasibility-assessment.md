# 借鉴 Troc 补齐 SQLancer 事务检测能力 — 可行性评估报告（修订版）

> **初版日期**：2026-06-03
> **修订日期**：2026-06-03（重新评估）
> **覆盖范围**：MySQL、PostgreSQL、GaussDB-A、GaussDB-M（仅四款数据库）
> **参考文档**：
> - Troc vs SQLancer 事务 Oracle 异同点分析
> - 可移植到 SQLancer 事务 Oracle 的优化项评估

---

## 修订说明

初版评估将 **Fucci-MT Oracle** 定位为 P0 最高优先级突破口，修订版**推翻此判断**。

核心原因：SQLancer 已有两个**完全可用且覆盖度更高**的 Oracle 体系（WRITE_CHECK 全覆盖 + TX_INFER 覆盖 MySQL/GaussDB-M），Fucci 只是第三个实验性框架。Troc 的内存建模方法实际上是 TX_INFER 辅助表方法的**低配版**，不是升级版。

---

## 一、现有已完整实现 Oracle 的实际覆盖度

| Oracle | 方法 | MySQL | PG | GaussDB-M | GaussDB-A | 成熟度 |
|--------|------|:-----:|:--:|:---------:|:---------:|:------:|
| **WRITE_CHECK** | 同一事务组不同 schedule 执行对比（纯黑箱） | ✅ 完整 | ✅ 完整 | ✅ 完整 | ✅ 完整 | **生产可用** |
| **TX_INFER** | 辅助版本表 `_vt` 上用真实 SQL 模拟 MVCC | ✅ 660行 | ❌ 无 | ✅ 660行 | ❌ 无 | **生产可用** |
| **Fucci-MT** | 内存 MVCC 模拟（≈Troc 方法） | ⚠️ Stub | ⚠️ Stub | ⚠️ Stub | ⚠️ Stub | **空壳** |
| **Fucci-DT** | 差分测试（参考数据库对比） | ⚠️ Stub | ⚠️ Stub | ⚠️ Stub | ⚠️ Stub | **空壳** |
| **Fucci-CS** | 约束求解验证 | ⚠️ Stub | ⚠️ Stub | ⚠️ Stub | ⚠️ Stub | **空壳** |

---

## 二、Troc 方法 vs SQLancer TX_INFER 的本质对比

| 维度 | Troc（内存建模） | SQLancer TX_INFER（辅助表 SQL 执行） |
|------|----------------|-----------------------------------|
| MVCC 推断方式 | 纯 Java 内存推演 | 在真实 DB 上执行辅助 SQL |
| WHERE 子句处理 | 手工解析 WHERE 提取行 ID（**脆弱**） | DBMS 自身执行 WHERE，无需解析（**健壮**） |
| 准确性 | 受限于 SQL 语义解析能力 | 利用 DBMS 原生能力，无解析误差 |
| 适用范围 | InnoDB 语义 | InnoDB 语义（当前），可扩展 |
| 代码量 | ~150 行核心 | ~660 行核心 |
| 版本追踪 | Version 链 + View 快照 | `_vt` 辅助表 + rid/vid/deleted/txid |

**结论**：TX_INFER 的方法论**优于** Troc。Troc 的内存建模是 TX_INFER 的低配版，不是升级版。

---

## 三、初版评估的关键偏差修正

| 优化项 | 初版评级 | 修订评级 | 修正理由 |
|--------|---------|---------|---------|
| **PG/GaussDB-A TX_INFER 补齐** | P2（被遗漏） | **P0 最高** | 这是最大的能力空白，初版未识别 |
| **Fucci-MT Oracle** | P0 最高 | ⚠️ 搁置 | 与 TX_INFER 功能重叠，内存建模不如辅助表 |
| **FucciLockAnalyzer** | P0 高 | ⭐ 搁置 | 跨 DBMS 锁差异大，判断标准不明确 |
| **Range Conflict 检测** | P1 高 | ⭐ 搁置 | TX_INFER 已覆盖幻读检测，仅 InnoDB 适用 |
| **穷举 Schedule** | P2 高 | P2 维持 | 增量改进，非核心缺口 |
| **Undecided 过滤** | P1 中 | **P0 高** | 一处改动全 Oracle 受益，投入产出比最高 |
| **Fucci-DT / CS** | 搁置 | ❌ 搁置 | 价值存疑，实现复杂 |

---

## 四、各优化项重新评估

### A. PG/GaussDB-A TX_INFER 补齐 — ⭐⭐⭐ 最高优先级（初版遗漏）

**现状**：MySQL 和 GaussDB-M 各有 ~660 行完整实现的 TX_INFER，PG/GaussDB-A 完全没有 MVCC 推断能力。

**可行性**：✅ 高

| 维度 | 分析 |
|------|------|
| 移植路径 | 从 `MySQLTxInfer.java` 移植到 `PostgresTxInfer.java`，修改 SQL 方言 |
| SQL 方言差异 | PG 无 `LIMIT 1` UPDATE（用 `ctid` 子查询）；版本表创建语法不同 |
| MVCC 语义差异 | PG 快照在 BEGIN 建立（非第一条 SELECT），需调整 `createSnapshot()` 调用时机 |
| GaussDB-A | 复用 PG 实现，语法几乎相同 |

**优势**：
- 直接填补最大覆盖空白——PG/GaussDB-A 完全没有 MVCC 推断能力
- 复用已验证的 TX_INFER 架构，不需要验证新方法
- 辅助表方法比内存建模更健壮（无需解析 WHERE）

**劣势**：
- 代码量大（PG ~500 行 + GaussDB-A ~500 行）
- PG 的 MVCC 语义（SSI、predicate lock）需在 analyzeStmt 中增加 PG 特定分支

**工作量**：~1000 行新增

---

### B. Undecided Case 过滤 — ⭐⭐ 投入产出比最高

**现状**：`TxBase.compareAllResults()` 对所有差异一律报 Bug，不区分「确定 Bug」和「无法判定」。

**可行性**：✅ 高

| 维度 | 分析 |
|------|------|
| 受益范围 | WRITE_CHECK + TX_INFER + Fucci 全部 Oracle 共用 TxBase |
| Troc 逻辑 | 死锁→undecided；abort→视 shouldNotAbort() 判断 |
| 误报减少 | 预估减少 30-50% 误报 |

**优势**：
- 一处改动，所有 Oracle 受益
- 直接降低误报率
- Troc 已验证此逻辑有效

**劣势**：
- shouldNotAbort() 判断标准需 DBMS 特定化
- 过滤过松可能漏报真实 Bug

**工作量**：~80-120 行

---

### C. Fucci Adapter 语句生成修复 — ⭐⭐ 为未来铺路

**现状**：所有4个 Adapter 的 `generateStatements()` 返回硬编码的固定 SQL。这不是 fuzzing，是占位代码。

**可行性**：✅ 高

**优势**：
- 修复后 Fucci 框架从不可用变为可测试
- 即使不补全 MT/DT/CS，至少有随机语句输入可用于后续开发

**劣势**：
- FucciTransaction 类型转换需要处理
- 随机 SQL 需要携带行 ID 信息供 MVCC 模拟使用

**工作量**：~100-150 行

---

### D. 穷举 Schedule — ⭐⭐ 增量改进

**可行性**：✅ 高

**为什么收益有限**：
- SQLancer Fucci 固定2事务，穷举空间本就小
- WRITE_CHECK 已经在跑随机 schedule，穷举只是增量覆盖
- 边际收益低：随机采样足够多 schedule 后，穷举额外发现的 Bug 很少

**工作量**：~60-80 行

---

### E. FucciLockAnalyzer 补全 — ⭐ 搁置（初版高估）

**为什么前次高估**：
- InnoDB 9种锁 vs PG 只有 SHARE/EXCLUSIVE + SSI predicate lock
- Troc 锁分析只覆盖 InnoDB，不能移植到 PG
- 核心困难不在建模，在判断标准——什么情况下「多锁」是 Bug

---

### F. Range Conflict 检测 — ⭐ 搁置（初版高估）

**为什么前次高估**：
- TX_INFER 的 `_vt` 辅助表**已经能检测幻读**——通过追踪 deleted 和 txid
- Troc 的快照前后对比法是 TX_INFER 的替代方案，不是增强方案
- 仅对 PG/GaussDB-A 无意义

---

### G. Fucci-MT Oracle — ⚠️ 搁置（初版严重高估）

**为什么前次严重高估**：
- Fucci-MT 和 TX_INFER 做同一件事——推断 MVCC 可见性并对比实际执行
- TX_INFER 用真实 SQL 执行辅助表（更健壮），Fucci-MT 用内存建模（更脆弱）
- 补全 Fucci-MT 后，它不会比 TX_INFER 更好，只是多了一个冗余 Oracle
- 唯一优势（不需创建辅助表，速度快）在 Bug 检测准确性面前微不足道

---

### H. Fucci-DT / Fucci-CS — ❌ 搁置

- **DT**：需要参考数据库实例，运维成本高。WRITE_CHECK 已通过 schedule 变分实现差分测试核心思路
- **CS**：约束求解器从零实现复杂度过高，收益不确定

---

## 五、四款数据库特殊考量

### MySQL & GaussDB-M（InnoDB 家族）

| 特性 | 现状 | 需要做的事 |
|------|------|-----------|
| WRITE_CHECK | ✅ 完整 | Undecided 过滤 |
| TX_INFER | ✅ 完整 | 无需改动 |
| 幻读检测 | ✅ TX_INFER 已覆盖 | 无需额外 Range Conflict 检测 |
| 死锁处理 | ⚠️ 无过滤 | Undecided 过滤（死锁是标准行为） |

### PostgreSQL & GaussDB-A（PG 家族）

| 特性 | 现状 | 需要做的事 |
|------|------|-----------|
| WRITE_CHECK | ✅ 完整 | Undecided 过滤 |
| TX_INFER | ❌ **完全缺失** | **最高优先级补齐** |
| MVCC 快照点 | BEGIN | TX_INFER 中需正确建模 |
| SSI (SERIALIZABLE) | ⚠️ 无特殊处理 | TX_INFER 中需增加 SSI 分支 |

---

## 六、推荐实施路线图

### 方案 A：以 TX_INFER 为主线（推荐）

| 阶段 | 优化项 | 预估工作量 | 依赖 |
|------|--------|-----------|------|
| **阶段1** | Undecided Case 过滤 | ~80-120 行 | 无（快速收益） |
| **阶段2** | PostgreSQL TX_INFER 补齐 | ~500 行 | 无（填补最大空白） |
| **阶段3** | GaussDB-A TX_INFER 补齐 | ~500 行 | 阶段2（复用 PG 代码） |
| **阶段4** | Fucci Adapter 语句生成修复 | ~100-150 行 | 无（为未来铺路） |
| **阶段5** | 穷举 Schedule | ~60-80 行 | 无（增量改进） |

### 方案 B：以 Fucci 为主线（初版推荐，已推翻）

| 问题 | 说明 |
|------|------|
| 与 TX_INFER 重叠 | Fucci-MT 与 TX_INFER 做同一件事，投入产出比低 |
| 方法论劣势 | 内存建模不如辅助表健壮 |
| 跨 DBMS 复杂 | 锁分析需 InnoDB vs PG 两套实现 |
| 覆盖空白未填 | PG/GaussDB-A 的 MVCC 推断能力仍未补齐 |

---

## 七、关键风险矩阵（修订版）

| 风险 | 严重度 | 概率 | 缓解措施 |
|------|--------|------|---------|
| PG TX_INFER 中 MVCC 语义建模不准确 | 高 | 中 | 参考 PG 官方文档 + 已有 WRITE_CHECK 交叉验证 |
| PG SQL 方言差异导致辅助表操作失败 | 中 | 中 | 先在 PG 上手动测试辅助表 SQL 模板 |
| GaussDB-M/A 行为与 MySQL/PG 不一致 | 中 | 中 | 在 GaussDB 实例上实测验证 |
| Undecided 过滤过松漏报真实 Bug | 中 | 低 | 日志记录所有 undecided case + 定期人工审查 |
| 穷举 Schedule 组合爆炸 | 低 | 低 | HYBRID 模式 + 阈值自动降级 |

---

## 八、不建议实施的项（修订版）

| 项 | 原因 |
|----|------|
| **Fucci-MT Oracle 补全** | 与 TX_INFER 功能重叠，方法论更弱 |
| **Fucci-DT Oracle** | 运维成本高，WRITE_CHECK 已覆盖差分测试核心 |
| **Fucci-CS Oracle** | 约束求解从零实现过于复杂 |
| **FucciLockAnalyzer 补全** | 跨 DBMS 锁差异大，判断标准不明确 |
| **Range Conflict 检测** | TX_INFER 已覆盖幻读检测，仅 InnoDB 适用 |
| **TiDB 支持** | 不在4款 DBMS 覆盖范围内 |
| **FucciCompositeOracle 重组** | 依赖的三个子 Oracle 均为 Stub，无实际意义 |

---

## 九、结论

**推荐方案 A（以 TX_INFER 为主线）**，核心理由：

1. **TX_INFER 是 SQLancer 独有的优势**——Troc 没有辅助表方法，只有内存建模。TX_INFER 是 SQLancer 应重点投资的方向
2. **最大覆盖空白是 PG/GaussDB-A**——不是 Fucci 框架的空壳
3. **Undecided 过滤是投入产出比最高的改进**——一处改动全 Oracle 受益

**初版方案的核心问题**：被 Fucci 框架的完整骨架误导，以为补全空壳是首要任务。实际上，已完整运行的 WRITE_CHECK + TX_INFER 才是 SQLancer 事务检测的核心能力，应围绕它们加强。

---

## 附录：关键代码现状

### TX_INFER 核心流程（MySQLTxInfer.java）

```
inferOracle()
  ├── 为每张表添加 rid 列 + 初始化 _vt 辅助版本表
  ├── 遍历 schedule 中每条语句：
  │     analyzeStmt()
  │       ├── BEGIN → createSnapshot(curTx)
  │       ├── COMMIT → committedTxs.add(curTx)
  │       ├── ROLLBACK → 从 _vt 删除该事务创建的版本
  │       ├── SELECT → 基于 _vt 计算 MVCC 可见行集合
  │       ├── INSERT → 向 _vt 插入新版本
  │       ├── UPDATE → 标记旧版本 deleted，插入新版本
  │       └── DELETE → 标记版本 deleted
  ├── 计算最终状态（_vt 中未删除的最新版本）
  └── 返回推断结果用于对比
```

### WRITE_CHECK 核心流程（MySQLWriteCheckOracle.java）

```
check()
  ├── 生成事务列表（1-5个事务）
  ├── 生成 schedule 列表
  ├── 对每个 schedule：
  │     ├── 在真实 DB 上执行 schedule → testResult
  │     ├── 重新生成 DB
  │     ├── 生成 oracle schedule（序列化顺序）→ oracleResult
  │     ├── 重新生成 DB
  │     ├── 生成 no-commit-rollback schedule → noCommitResult
  │     ├── SERIALIZABLE → compareAllResults()
  │     └── 其他级别 → compareWriteTxResults()
  └── 差异即 Bug
```
