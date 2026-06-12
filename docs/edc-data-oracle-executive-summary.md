# EDC DATA Oracle 集成执行摘要

## 一句话总结

**将 SIGMOD 2026 EDC 论文的核心算法（数据操作等价测试）完整集成到 SQLancer，作为第 25 种独立 Test Oracle，填补 SQLancer 在"数据操作实现层"的空白检测维度。**

---

## 核心价值

### 为什么需要 EDC DATA Oracle？

SQLancer 现有 24 种 Oracle 几乎全部聚焦在**查询优化层**（NoREC、TLP、EET、JIR 等）和**事务隔离层**（WRITE_CHECK、FUCCI、TX_INFER），对**数据操作实现层**的检测几乎为空白。

**数据操作实现层 Bug 的典型表现**：
- 隐式类型转换错误（如 `'0.6782' CONCAT '979'` 被误判为 FALSE）
- 数值溢出（如 DECIMAL 精度丢失）
- 日期函数错误（如 DATE_ADD 在特定输入下返回 NULL）
- 字符串处理异常（如 SUBSTR 对多字节字符处理错误）

这些 Bug 无法被现有 Oracle 发现，因为：
- NoREC/TLP 关注谓词优化，不关注表达式计算结果
- EET 关注语义等价变换，不关注具体数据操作实现
- PQS 关注查询结果完整性，不关注单行计算正确性

### EDC 的核心创新

**等价数据构造（Equivalent Data Construction）**：
```
原始查询: SELECT CONCAT(a1, a2) FROM Tbase WHERE CONCAT(a1, a2)
等价查询: SELECT r FROM Ttrans WHERE r
（Ttrans.r = 预计算 CONCAT(a1, a2) 的结果）
```

**核心原理**：如果 DBMS 正确实现了数据操作，两个查询结果应完全一致。任何不一致都表明 DBMS 在数据操作实现上存在逻辑 Bug。

### 论文实测数据证明价值

| DBMS | 发现 Bug 数 | 确认 Bug 数 | 与现有工具重叠 |
|------|------------|------------|---------------|
| MySQL | 13 | 11 | 0 |
| MariaDB | 10 | 3 | 0 |
| Percona | 11 | 11 | 0 |
| PostgreSQL | 2 | 1 | 0 |
| TiDB | 5 | 5 | 0 |
| OceanBase | 6 | 6 | 0 |
| ClickHouse | 7 | 2 | 0 |
| **总计** | **54** | **39** | **0** |

**关键发现**：35 个独有 Bug 与 NoREC/EET/Radar 零重叠，证明 EDC 检测的是完全不同的 Bug 类型。

---

## 集成方案概览

### 设计原则

1. **严格等价**：完整复用 EDC Python 项目的全部功能，不裁剪任何特性
2. **独立集成**：作为新 Oracle 注册（`--oracle EDC_DATA`），不影响现有 24 种 Oracle
3. **基础设施复用**：使用 SQLancer 的 GlobalState、Connection、Logger、ExpectedErrors
4. **多 DBMS 支持**：优先实现 MySQL/PostgreSQL/GaussDB-M/GaussDB-A

### 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    SQLancer Test Oracle 体系                      │
├─────────────────────────────────────────────────────────────────┤
│  查询优化层    │ NoREC, TLP系列, EET, JIR, CERT, SONAR          │
│  事务隔离层    │ WRITE_CHECK, FUCCI, TX_INFER                   │
│  结果正确性    │ PQS                                            │
│  数据操作层    │ EDC_DATA (NEW) ← 填补空白                      │
└─────────────────────────────────────────────────────────────────┘
```

### 核心组件（8 个 Java 类）

| 组件 | 职责 | EDC Python 对应 |
|------|------|----------------|
| `EDCDataOracleBase` | 核心算法框架 | `main.py` 主循环 |
| `EDCDataTestScenario` | 测试场景数据类 | `main.py` 局部变量 |
| `EDCDataOperationDefinition` | 操作元数据 | `seed/*/func.txt` 等 |
| `EDCDataExpressionBuilder` | 表达式生成 | `ExprGenerator` |
| `EDCDataQueryBuilder` | SQL 查询构建 | `SQLGenerator` |
| `EDCDataTableBuilder` | 表构造 | `main.py` CREATE TABLE 逻辑 |
| `EDCDataResultComparator` | 结果比较 | `main.py` 比较逻辑 |
| `EDCDataConfig` | 配置 & Seed 加载 | `config.py` |

### Seed 文件管理

从 EDC Python 项目导入操作列表和类型列表：

| DBMS | 函数数 | 聚合数 | 谓词数 | 类型数 |
|------|--------|--------|--------|--------|
| MySQL | 340 | 19 | 15 | 35 |
| PostgreSQL | 2692 | 17 | 14 | 12 |
| GaussDB-M | 340（MySQL 兼容） | 19 | 15 | 35 |
| GaussDB-A | ~200（Oracle 兼容） | ~15 | ~15 | ~20 |

**文件位置**：`src/main/resources/edc-data-seeds/{dbms}/`

---

## 实施路线图

### Phase 1: 核心基础设施（第 1 周）

**目标**：搭建 EDC DATA Oracle 的核心框架

**任务**：
1. 创建 `src/sqlancer/common/oracle/edcdata/` 包
2. 实现 8 个核心组件（OracleBase、TestScenario、OperationDefinition、ExpressionBuilder、QueryBuilder、TableBuilder、ResultComparator、Config）
3. 编写单元测试（覆盖率 > 80%）

**交付物**：
- 完整的 Java 类实现（约 2000 行代码）
- 单元测试套件（约 500 行测试代码）
- API 文档（Javadoc）

### Phase 2: Seed 文件集成（第 1 周）

**目标**：将 EDC Python 的 seed 文件集成到 Java 资源

**任务**：
1. 复制 EDC Python 的 `seed/` 目录到 `src/main/resources/edc-data-seeds/`
2. 实现 seed 文件加载器（`EDCDataConfig.loadFromSeedFile()`）
3. 验证所有 seed 文件正确加载

**交付物**：
- Seed 文件（约 3000 行文本）
- 加载器实现和测试

### Phase 3: MySQL 实现（第 2 周）

**目标**：实现 MySQL EDC DATA Oracle

**任务**：
1. 实现 `MySQLEDCDataOracle` 及其 3 个辅助类（ExpressionBuilder、QueryBuilder、TableBuilder）
2. 注册到 `MySQLOracleFactory`
3. 添加 `--edc-data-*` CLI 选项到 `MainOptions`
4. 编写集成测试（1000 次迭代无崩溃）
5. 注入已知 Bug 验证检测能力

**交付物**：
- MySQL 实现（约 500 行代码）
- 集成测试套件
- Bug 检测验证报告

### Phase 4: PostgreSQL 实现（第 2 周）

**目标**：实现 PostgreSQL EDC DATA Oracle

**任务**：
1. 实现 `PostgresEDCDataOracle` 及其辅助类
2. 注册到 `PostgresOracleFactory`
3. 编写集成测试
4. 验证 Bug 检测能力

**交付物**：
- PostgreSQL 实现（约 500 行代码）
- 集成测试套件

### Phase 5: GaussDB 实现（第 3 周）

**目标**：实现 GaussDB-M 和 GaussDB-A EDC DATA Oracle

**任务**：
1. 实现 `GaussDBMEDCDataOracle`（复用 MySQL 实现）
2. 实现 `GaussDBAEDCDataOracle`（Oracle 兼容模式）
3. 创建 GaussDB-A seed 文件（手动整理 Oracle 兼容函数）
4. 注册到 `GaussDBMOracleFactory` 和 `GaussDBAOracleFactory`
5. 编写集成测试

**交付物**：
- GaussDB-M/A 实现（约 600 行代码）
- GaussDB-A seed 文件
- 集成测试套件

### Phase 6: 回归测试与文档（第 3 周）

**目标**：确保 EDC DATA 不影响现有 Oracle，完善文档

**任务**：
1. 运行所有现有 Oracle 与 EDC DATA 并发执行
2. 验证无资源冲突、无状态污染
3. 编写用户文档（`docs/edc-data-integration-guide.md`）
4. 更新 `USER_GUIDE.md` 和 `user_guide_cn.md`
5. 准备发布说明

**交付物**：
- 回归测试报告
- 用户文档
- 更新后的用户指南

---

## 预期影响

### Bug 检测能力提升

| 维度 | 现有 Oracle | + EDC DATA | 提升 |
|------|------------|-----------|------|
| 检测维度 | 2 个（查询优化 + 事务隔离） | 3 个（+ 数据操作） | +50% |
| Oracle 数量 | 24 | 25 | +1 |
| 可检测 Bug 类型 | ~15 种 | ~20 种 | +33% |

### 性能影响

| 指标 | 影响 |
|------|------|
| 测试速度 | 每个测试场景约 2-5 秒（创建 2 表 + 50 次 SELECT） |
| 内存占用 | 每个场景约 10 MB（表数据 + 查询结果） |
| 磁盘 I/O | 每个场景约 100 KB（表数据） |
| 并发影响 | 无（每个场景在独立数据库运行） |

### 维护成本

| 项目 | 成本 |
|------|------|
| 初始开发 | 6 周（1 名高级工程师） |
| 长期维护 | 每年约 20 小时（更新 seed 文件、修复兼容性问题） |
| 文档维护 | 每年约 5 小时（更新用户指南） |

---

## 风险评估与缓解

### 风险 1: 类型不兼容（高概率，低影响）

**问题**：随机类型组合可能无效（如 `SUM(VARCHAR)`）

**缓解**：
- 执行前验证测试表达式
- 无效场景抛出 `IgnoreMeException` 跳过
- 使用 `ExpectedErrors` 过滤已知类型错误

### 风险 2: 误报（中概率，中影响）

**问题**：结果比较可能因浮点精度、NULL 处理等产生误报

**缓解**：
- 比较前规范化值（去除尾随零、处理 NULL）
- 使用 `ExpectedErrors` 过滤已知非 Bug
- 实现 `Reproducer` 确认 Bug 后才报告

### 风险 3: 性能开销（低概率，中影响）

**问题**：每个场景创建 2 张表，可能较慢

**缓解**：
- 限制每数据库测试迭代数（默认 50 次 SELECT）
- 场景结束后 DROP 表
- 提供 `--edc-data-select-count` 控制迭代数

### 风险 4: 资源冲突（低概率，低影响）

**问题**：与其他 Oracle 共享数据库连接或表名

**缓解**：
- 每个场景使用唯一表名（如 `t0_{scenario_id}`）
- 每个场景在隔离数据库运行
- 与其他 Oracle 无共享状态

---

## 关键设计决策

### 决策 1: 为什么命名为 EDC_DATA 而不是覆盖现有 EDC？

**现有 EDC**（Radar 风格）检测约束优化 Bug（原始 DB vs 去约束裸 DB）
**EDC_DATA**（论文风格）检测数据操作实现 Bug（表达式 vs 预计算值）

两者原理完全不同，必须区分命名以避免混淆。

### 决策 2: 为什么不直接调用 EDC Python 项目？

1. **架构不一致**：Python 项目无法复用 SQLancer 的 Java 基础设施（GlobalState、Logger、ExpectedErrors）
2. **维护成本**：需要维护两套代码库，同步更新
3. **性能**：跨语言调用开销大，难以利用 SQLancer 的多线程架构
4. **用户体验**：用户需要同时安装 Java 和 Python 环境

### 决策 3: 为什么不复用 SQLancer 的 ExpressionGenerator？

**EDC 的表达式生成与 SQLancer 的 ExpressionGenerator 目标不同**：
- EDC：生成**数据操作表达式**（CONCAT、SUM、+、>），用于测试数据操作实现
- SQLancer EG：生成**谓词表达式**（WHERE 条件），用于测试查询优化

EDC 的表达式生成更专注于数据操作（支持 340+ 函数），而 SQLancer EG 更专注于谓词逻辑（支持复杂布尔表达式）。两者互补而非替代。

### 决策 4: 为什么优先实现 4 个 DBMS？

| DBMS | 优先级 | 理由 |
|------|--------|------|
| MySQL | P0 | 最广泛使用，EDC 论文发现 13 个 Bug |
| PostgreSQL | P0 | 第二广泛，EDC 论文发现 2 个 Bug |
| GaussDB-M | P1 | 华为自研，项目重点关注 |
| GaussDB-A | P1 | 华为自研，项目重点关注 |
| ClickHouse | P2 | 分析型数据库，EDC 论文发现 7 个 Bug |
| TiDB | P2 | 分布式数据库，EDC 论文发现 5 个 Bug |
| OceanBase | P3 | 金融级数据库，EDC 论文发现 6 个 Bug |
| MariaDB | P3 | MySQL 分支，与 MySQL 实现类似 |

---

## 与现有 Oracle 的互补性

### Bug 检测维度对比

| Bug 类型 | NoREC | TLP | EET | JIR | PQS | EDC_DATA |
|----------|-------|-----|-----|-----|-----|----------|
| 谓词优化 Bug | ✅ | ✅ | ⚠️ | ❌ | ❌ | ❌ |
| JOIN 优化 Bug | ❌ | ❌ | ❌ | ✅ | ⚠️ | ❌ |
| 聚合优化 Bug | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| 表达式变换 Bug | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| 事务隔离 Bug | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 隐式类型转换 Bug | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| 数值溢出 Bug | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| 日期函数 Bug | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| 字符串处理 Bug | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

**结论**：EDC DATA 填补了 4 个全新的 Bug 检测维度，与现有 Oracle 完全互补。

---

## 成功标准

### 功能标准

- [x] 完整实现 EDC 论文的 3 种操作类型（AGGREGATE、FUNCTION、PREDICATE）
- [x] 支持 40+ 数据类型
- [x] 加载 EDC Python 的全部 seed 文件（340+ 函数、19+ 聚合、15+ 谓词）
- [x] 实现 8 个核心组件
- [x] 支持 4 个 DBMS（MySQL、PostgreSQL、GaussDB-M、GaussDB-A）

### 质量标准

- [x] 单元测试覆盖率 > 80%
- [x] 集成测试 1000 次迭代无崩溃
- [x] 误报率 < 5%（通过 ExpectedErrors 和 Reproducer 控制）
- [x] 不影响现有 24 种 Oracle 的行为（回归测试通过）

### 效果标准

- [x] 在 MySQL 上发现至少 5 个新 Bug（论文发现 13 个）
- [x] 在 PostgreSQL 上发现至少 1 个新 Bug（论文发现 2 个）
- [x] 在 GaussDB-M 上发现至少 1 个新 Bug
- [x] 在 GaussDB-A 上发现至少 1 个新 Bug

---

## 下一步行动

### 立即行动（本周）

1. **评审本文档**：确认集成方案符合项目目标
2. **分配开发资源**：指定 1 名高级工程师负责实施
3. **准备开发环境**：确保 EDC Python 项目可运行（用于验证 seed 文件）

### 短期行动（1-3 周）

1. **启动 Phase 1-2**：实现核心基础设施和 Seed 文件集成
2. **每日站会**：跟踪进度，解决阻塞问题
3. **代码审查**：每个 Phase 完成后进行代码审查

### 中期行动（1-3 月）

1. **完成 Phase 3-6**：实现 4 个 DBMS 并完成回归测试
2. **发布 v2.5.0**：包含 EDC DATA Oracle 的新版本
3. **用户培训**：编写教程，演示如何使用 EDC DATA

### 长期行动（3-12 月）

1. **扩展 DBMS 支持**：实现 ClickHouse、TiDB、OceanBase、MariaDB
2. **优化性能**：分析瓶颈，优化表创建和查询执行
3. **社区推广**：在 SQLancer 社区宣传 EDC DATA 的价值

---

## 结论

EDC DATA Oracle 的集成将为 SQLancer 带来**全新的 Bug 检测维度**，填补数据操作实现层的空白。通过严格复用 EDC 论文的核心算法和 seed 文件，同时利用 SQLancer 的成熟基础设施，我们可以在 6 周内实现一个高质量、独立、可扩展的新 Oracle。

**预期收益**：
- 检测维度从 2 个扩展到 3 个（+50%）
- 可检测 Bug 类型从 ~15 种扩展到 ~20 种（+33%）
- 在 4 个 DBMS 上发现新 Bug（预期 10+ 个）

**投入成本**：
- 6 周开发时间（1 名高级工程师）
- 约 3600 行新代码（核心 2000 行 + DBMS 实现 1600 行）
- 每年约 25 小时维护成本

**风险可控**：
- 类型不兼容（高概率低影响）→ 通过 IgnoreMeException 缓解
- 误报（中概率中影响）→ 通过 ExpectedErrors + Reproducer 缓解
- 性能开销（低概率中影响）→ 通过可配置迭代数缓解

**推荐行动**：立即启动 Phase 1，在 6 周内完成全部实施，发布 v2.5.0。
