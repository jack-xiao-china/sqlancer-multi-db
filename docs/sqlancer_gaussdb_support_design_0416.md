# SQLancer GaussDB 支持架构设计文档

**文档版本**: 2026-04-16 (v2)
**作者**: Architecture Design Team
**目标**: 扩展SQLancer以完整支持GaussDB集中式（PG兼容和A兼容两种模式）

---

## 一、概述与背景

### 1.1 GaussDB 简介

GaussDB是华为公司研发的OLTP数据库，具有以下技术特征：

| 特性 | 说明 |
|------|------|
| **内核基础** | PostgreSQL内核 |
| **主要兼容模式** | A兼容（Oracle）、PG兼容（PostgreSQL） |
| **架构形态** | 集中式与分布式版本，本设计针对集中式 |
| **语法参考** | https://support.huaweicloud.com/centralized-devg-v8-gaussdb/gaussdb-42-0332.html |

> **说明**：B兼容模式已废弃，不在本设计范围内；M兼容模式通过独立的`gaussdbm`模块实现，不在本次范围。

### 1.2 当前代码库状态

| 模块 | 位置 | 状态 | 说明 |
|------|------|------|------|
| `gaussdb` | `src/sqlancer/gaussdb/` | 基础框架 | **需重命名为gaussdb-pg**，本次设计目标（PG兼容） |
| `gaussdbm` | `src/sqlancer/gaussdbm/` | 已完整实现 | M兼容（MySQL风格），独立模块，不在本次范围 |
| `gaussdb-a` | 待新建 | 无 | A兼容模式（Oracle风格），本次设计目标 |

### 1.3 设计决策：独立模块架构

**决策结论**：采用方案A（分开为2个独立模块），原因如下：

| 决策因素 | 说明 |
|----------|------|
| **NULL语义差异** | PG模式：空字符串 ≠ NULL；A模式：空字符串 = NULL（Oracle语义）。这是根本差异，直接影响Constant类的所有比较逻辑 |
| **函数库不同** | PG使用COALESCE/CASE WHEN/::type；A使用NVL/DECODE/TO_CHAR/TO_DATE |
| **数据类型命名** | PG：int4/int8/varchar/text；A：NUMBER/VARCHAR2/CLOB/BLOB |
| **参考成功模式** | gaussdbm已独立实现M兼容，验证了"不同兼容模式独立模块"的有效性 |

**命名规范**：

| 兼容模式 | 模块目录 | 命令行名称 | 说明 |
|----------|---------|-----------|------|
| PG兼容 | `gaussdb-pg` | `gaussdb-pg` | 复用postgres模块代码，当前gaussdb需重命名 |
| A兼容 | `gaussdb-a` | `gaussdb-a` | 新建模块，参考Oracle语法 |
| M兼容 | `gaussdbm` | `gaussdb-m` | 已有独立实现，不在本次范围 |

### 1.4 设计目标

1. **gaussdb-pg模块**：完善PG兼容模式的全部Test Oracle
2. **gaussdb-a模块**：新建A兼容模式，实现全部Test Oracle
3. **复用现有代码**：PG模式复用postgres模块，A模式参考Oracle语法特性
4. **可扩展性**：便于后续添加新Oracle和GaussDB新特性

---

## 二、SQLancer 架构分析

### 2.1 核心架构层次

```
┌─────────────────────────────────────────────────────────────┐
│                      Main.java (入口)                        │
│                   MainOptions (全局参数)                      │
├─────────────────────────────────────────────────────────────┤
│              ProviderAdapter / SQLProviderAdapter            │
│         (数据库生成、测试执行、Oracle调度)                      │
├─────────────────────────────────────────────────────────────┤
│   GlobalState / SQLGlobalState                              │
│   (连接状态、Schema、随机数生成器、日志)                        │
├─────────────────────────────────────────────────────────────┤
│   Schema (AbstractSchema → 具体Schema)                      │
│   (表结构、列类型、索引信息)                                   │
├─────────────────────────────────────────────────────────────┤
│   AST Layer (Expression, Select, Join, etc.)                │
│   (抽象语法树节点定义)                                        │
├─────────────────────────────────────────────────────────────┤
│   Generator Layer (ExpressionGenerator, TableGenerator)     │
│   (SQL生成器)                                                │
├─────────────────────────────────────────────────────────────┤
│   Visitor Layer (ToStringVisitor)                           │
│   (AST到SQL字符串转换)                                        │
├─────────────────────────────────────────────────────────────┤
│   Oracle Layer (TLP, NoREC, PQS, CERT, DQP, DQE, EET)       │
│   (测试预言机实现)                                            │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 关键接口与类

| 层次 | 关键接口/类 | 作用 |
|------|------------|------|
| **Provider层** | `DatabaseProvider` | 定义DBMS适配器契约 |
| | `ProviderAdapter` | 提供通用测试流程 |
| | `SQLProviderAdapter` | SQL数据库专用适配器 |
| **State层** | `GlobalState<O,S,C>` | 测试会话状态容器 |
| | `SQLGlobalState<O,S>` | SQL数据库状态 |
| **Schema层** | `AbstractSchema` | Schema基类 |
| | `AbstractRelationalTable` | 关系表基类 |
| | `AbstractTableColumn` | 列定义基类 |
| **AST层** | `Expression` | 表达式接口 |
| | `Select<J,E,T,C>` | SELECT语句节点 |
| | `Join<E,T,C>` | JOIN节点 |
| **Generator层** | `UntypedExpressionGenerator` | 表达式生成器基类 |
| | `NoRECGenerator` | NoREC专用生成器接口 |
| | `TLPWhereGenerator` | TLP专用生成器接口 |
| **Oracle层** | `TestOracle` | Oracle接口 |
| | `OracleFactory` | Oracle工厂枚举 |

### 2.3 Oracle实现模式

```java
// Oracle工厂枚举模式（参考PostgresOracleFactory）
public enum GaussDBOracleFactory implements OracleFactory<GaussDBGlobalState> {
    NOREC {
        @Override
        public TestOracle<GaussDBGlobalState> create(GaussDBGlobalState state) throws Exception {
            GaussDBExpressionGenerator gen = new GaussDBExpressionGenerator(state);
            return new NoRECOracle<>(state, gen, GaussDBErrors.getExpressionErrors());
        }
    },
    // ... 其他Oracle
}
```

---

## 三、GaussDB PG兼容与A兼容模式对比分析

### 3.1 核心差异对比

| 维度 | PG兼容模式 | A兼容模式（Oracle） |
|------|-----------|-------------------|
| **NULL语义** | 空字符串 ≠ NULL（标准三值逻辑） | 空字符串 = NULL（Oracle语义） |
| **数据类型** | int4/int8, varchar, text, timestamp, boolean, json/jsonb, uuid, array | NUMBER, VARCHAR2, DATE, TIMESTAMP, CLOB, BLOB |
| **函数库** | COALESCE, CASE WHEN, array_agg, json函数, ::type转换 | NVL, DECODE, TO_DATE, TO_CHAR, LISTAGG |
| **类型转换** | `::type` 或 CAST(x AS type) | CAST(x AS type), TO_CHAR, TO_DATE |
| **布尔类型** | BOOLEAN类型存在 | 无BOOLEAN，用0/1或Y/N表示 |
| **特殊语法** | LIMIT/OFFSET, RETURNING | DUAL表, ROWNUM, (+) JOIN语法 |

### 3.2 NULL语义差异的影响（关键）

这是决定独立模块设计的核心因素：

```java
// PG语义：空字符串不等于NULL
''.sqlEquals(NULL) → FALSE  // 明确的比较结果

// A模式（Oracle语义）：空字符串被视为NULL
''.sqlEquals(NULL) → NULL   // 空串本身是NULL，NULL=NULL返回NULL
```

此差异直接影响：
- **Constant类**：所有比较操作逻辑不同
- **PQS Oracle**：期望值计算逻辑不同
- **TLP Oracle**：TRUE/FALSE/NULL分区逻辑不同
- **EET Oracle**：等价变换规则需适配不同语义

### 3.3 PG兼容模式数据类型映射

| GaussDB类型 | PostgreSQL对应 | SQLancer支持状态 |
|------------|---------------|-----------------|
| INTEGER | int4 | ✓ 已支持 |
| BIGINT | int8 | ✓ 已支持 |
| SMALLINT | int2 | ✓ 已支持 |
| VARCHAR(n) | varchar | ✓ 已支持 |
| TEXT | text | ✓ 已支持 |
| CHAR(n) | bpchar | ✓ 已支持 |
| FLOAT/DOUBLE PRECISION | float8 | ✓ 已支持 |
| NUMERIC/DECIMAL | numeric | ✓ 已支持 |
| DATE | date | ✓ 已支持 |
| TIME | time | ✓ 已支持 |
| TIMESTAMP | timestamp | ✓ 已支持 |
| BOOLEAN | bool | ✓ 已支持 |
| BYTEA | bytea | 需扩展 |
| JSON/JSONB | json/jsonb | 需扩展 |
| UUID | uuid | 需扩展 |
| ARRAY | array | 需扩展 |

### 3.4 A兼容模式数据类型映射

| GaussDB A类型 | Oracle对应 | SQLancer需实现 |
|--------------|-----------|---------------|
| NUMBER(p,s) | NUMBER | IntConstant/DecimalConstant |
| VARCHAR2(n) | VARCHAR2 | StringConstant |
| CLOB | CLOB | StringConstant (长文本) |
| BLOB | BLOB | 需新实现 |
| DATE | DATE (含时间) | DateConstant |
| TIMESTAMP | TIMESTAMP | TimestampConstant |
| RAW | RAW | 需新实现 |

**A模式特殊类型处理**：
- 无BOOLEAN类型：使用NUMBER(1)或CHAR(1)（Y/N）模拟
- DATE类型含时间：与PG的DATE不同，A模式的DATE包含时分秒

---

## 四、架构设计方案

### 4.1 模块层次设计（双模块架构）

```
src/sqlancer/
├── gaussdb-pg/                  # GaussDB PG兼容模式 - 重命名现有gaussdb
│   ├── GaussDBPGProvider.java   # 数据库适配器
│   ├── GaussDBPGGlobalState.java # 全局状态
│   ├── GaussDBPGOptions.java    # 命令行选项
│   ├── GaussDBPGOracleFactory.java # Oracle工厂枚举
│   ├── GaussDBPGSchema.java     # Schema定义（PG类型）
│   ├── GaussDBPGErrors.java     # 预期错误列表
│   ├── GaussDBPGToStringVisitor.java # SQL字符串生成
│   ├── GaussDBPGExpectedValueVisitor.java # 值预期验证
│   ├── ast/
│   │   ├── GaussDBPGExpression.java
│   │   ├── GaussDBPGConstant.java      # PG语义：空串≠NULL
│   │   ├── GaussDBPGSelect.java
│   │   ├── GaussDBPGJoin.java
│   │   └── ... (其他AST节点，参考postgres模块)
│   ├── gen/
│   │   ├── GaussDBPGExpressionGenerator.java
│   │   ├── GaussDBPGTableGenerator.java
│   │   └── ... (其他生成器)
│   └── oracle/
│       ├── GaussDBPGFuzzer.java
│       ├── GaussDBPGPivotedQuerySynthesisOracle.java
│       ├── tlp/
│       ├── ext/
│       └── eet/
│
├── gaussdb-a/                   # GaussDB A兼容模式 - 新建模块
│   ├── GaussDBAProvider.java    # 数据库适配器
│   ├── GaussDBAGlobalState.java # 全局状态
│   ├── GaussDBAOptions.java     # 命令行选项
│   ├── GaussDBAOracleFactory.java # Oracle工厂枚举
│   ├── GaussDBASchema.java      # Schema定义（Oracle类型）
│   ├── GaussDBAErrors.java      # 预期错误列表
│   ├── GaussDBAToStringVisitor.java
│   ├── GaussDBAExpectedValueVisitor.java
│   ├── ast/
│   │   ├── GaussDBAExpression.java
│   │   ├── GaussDBAConstant.java         # Oracle语义：空串=NULL
│   │   ├── GaussDBASelect.java
│   │   ├── GaussDBAJoin.java             # 支持(+)外连接语法
│   │   ├── GaussDBADualTable.java        # DUAL表支持
│   │   └── ... (其他AST节点)
│   ├── gen/
│   │   ├── GaussDBAExpressionGenerator.java
│   │   ├── GaussDBATableGenerator.java
│   │   └── ... (其他生成器)
│   └── oracle/
│       ├── GaussDBAFuzzer.java
│       ├── GaussDBAPivotedQuerySynthesisOracle.java
│       ├── tlp/
│       ├── ext/
│       └── eet/
│
├── gaussdbm/                    # GaussDB M兼容（已有）
│   └── ... (保持现有结构)
│
└── common/                      # 共享组件
    ├── oracle/                  # 通用Oracle基类
    ├── gen/                     # 通用生成器接口
    └── ast/                     # 通用AST基类
```

### 4.2 类继承关系图

```
                    DatabaseProvider
                         │
            ┌────────────┴────────────┐
            │                         │
    ProviderAdapter            SQLProviderAdapter
            │                         │
    ┌───────┴───────────┬───────────┴───────┐
    │                   │                   │
 GaussDBPGProvider  GaussDBAProvider  PostgresProvider
    │                   │                   │
    └───┬───────────────┴───────────────────┘
        │
    GlobalState
        │
    SQLGlobalState
        │
    ├──── GaussDBPGGlobalState ── PostgresGlobalState (参考)
    │
    └──── GaussDBAGlobalState (新建)

    AbstractSchema
        │
    ├──── GaussDBPGSchema ── PostgresSchema (参考)
    │
    └──── GaussDBASchema (新建，Oracle类型)

    Expression<C>
        │
    ├──── GaussDBPGExpression
    │
    └──── GaussDBAExpression

    Constant (语义差异关键点)
        │
    ├──── GaussDBPGConstant     # 空字符串 ≠ NULL
    │         │
    │         ├─ IntConstant
    │         ├─ StringConstant  # '' ≠ NULL
    │         ├─ BooleanConstant # TRUE/FALSE
    │         └─ NullConstant
    │
    └──── GaussDBAConstant      # 空字符串 = NULL (Oracle语义)
              │
              ├─ NumberConstant
              ├─ Varchar2Constant # ''视为NULL
              ├─ IntAsBooleanConstant # 0/1模拟布尔
              └─ NullConstant

    UntypedExpressionGenerator
        │
    ├──── GaussDBPGExpressionGenerator ── PostgresExpressionGenerator (参考)
    │
    └──── GaussDBAExpressionGenerator (新建)
```
        │
        ├─ NoRECGenerator<...>
        ├─ TLPWhereGenerator<...>
        ├─ CERTGenerator<...>
        │
    GaussDBExpressionGenerator ── PostgresExpressionGenerator (参考)
```

> **备注**：gaussdbm模块为独立实现（M兼容），不在本次设计范围内。

### 4.3 核心类设计

#### 4.3.1 GaussDBPGProvider（PG兼容）

```java
@AutoService(sqlancer.DatabaseProvider.class)
public class GaussDBPGProvider 
    extends SQLProviderAdapter<GaussDBPGGlobalState, GaussDBPGOptions> {
    
    public GaussDBPGProvider() {
        super(GaussDBPGGlobalState.class, GaussDBPGOptions.class);
    }
    
    @Override
    public void generateDatabase(GaussDBPGGlobalState state) throws Exception;
    
    @Override
    public SQLConnection createDatabase(GaussDBPGGlobalState state) throws SQLException;
    
    @Override
    public String getDBMSName() { return "gaussdb-pg"; }
    
    @Override
    protected String getQueryPlan(String sql, GaussDBPGGlobalState state);
}
```

#### 4.3.2 GaussDBAProvider（A兼容）

```java
@AutoService(sqlancer.DatabaseProvider.class)
public class GaussDBAProvider 
    extends SQLProviderAdapter<GaussDBAGlobalState, GaussDBAOptions> {
    
    public GaussDBAProvider() {
        super(GaussDBAGlobalState.class, GaussDBAOptions.class);
    }
    
    @Override
    public void generateDatabase(GaussDBAGlobalState state) throws Exception;
    
    @Override
    public SQLConnection createDatabase(GaussDBAGlobalState state) throws SQLException;
    
    @Override
    public String getDBMSName() { return "gaussdb-a"; }
    
    // A模式特殊处理：Oracle语法适配
    @Override
    protected String getQueryPlan(String sql, GaussDBAGlobalState state);
}
```

#### 4.3.3 GaussDBPGGlobalState（PG兼容）

```java
public class GaussDBPGGlobalState 
    extends SQLGlobalState<GaussDBPGOptions, GaussDBPGSchema> {
    
    // PG兼容特定配置
    private boolean enableTimeTypes = false;
    private boolean enableJsonTypes = false;
    private boolean enableUuidTypes = false;
    private boolean enableArrayTypes = false;
    
    public boolean usesPQS() {
        return getDbmsSpecificOptions().getTestOracleFactory()
            .stream().anyMatch(o -> o == GaussDBPGOracleFactory.PQS);
    }
    
    @Override
    protected GaussDBPGSchema readSchema() throws Exception {
        return GaussDBPGSchema.fromConnection(getConnection(), getDatabaseName());
    }
}
```

#### 4.3.4 GaussDBAGlobalState（A兼容）

```java
public class GaussDBAGlobalState 
    extends SQLGlobalState<GaussDBAOptions, GaussDBASchema> {
    
    // A兼容特定配置
    private boolean enableClobBlob = false;
    
    public boolean usesPQS() {
        return getDbmsSpecificOptions().getTestOracleFactory()
            .stream().anyMatch(o -> o == GaussDBAOracleFactory.PQS);
    }
    
    @Override
    protected GaussDBASchema readSchema() throws Exception {
        return GaussDBASchema.fromConnection(getConnection(), getDatabaseName());
    }
}
```

#### 4.3.5 GaussDBPGConstant（PG语义：空串≠NULL）

```java
public abstract class GaussDBPGConstant implements GaussDBPGExpression {
    
    // PG语义：空字符串不等于NULL
    public static class StringConstant extends GaussDBPGConstant {
        private final String value;
        
        @Override
        public GaussDBPGConstant sqlEquals(GaussDBPGConstant right) {
            if (right.isNull()) {
                // 空字符串与NULL比较 → 返回NULL（三值逻辑）
                // 但空字符串本身不是NULL
                return createNullConstant();
            }
            if (right.isString()) {
                // 正常的字符串比较
                return createBooleanConstant(value.equals(right.asString()));
            }
            // ...
        }
        
        @Override
        public boolean isNull() {
            return false;  // 空字符串不是NULL
        }
    }
}
```

#### 4.3.6 GaussDBAConstant（Oracle语义：空串=NULL）

```java
public abstract class GaussDBAConstant implements GaussDBAExpression {
    
    // Oracle语义：空字符串被视为NULL
    public static class Varchar2Constant extends GaussDBAConstant {
        private final String value;
        
        @Override
        public GaussDBAConstant sqlEquals(GaussDBAConstant right) {
            // Oracle语义：空字符串 = NULL
            if (value.isEmpty() || right.isNull() || (right.isString() && right.asString().isEmpty())) {
                // NULL = NULL → 返回NULL（不是TRUE也不是FALSE）
                return createNullConstant();
            }
            if (right.isString()) {
                return createBooleanConstant(value.equals(right.asString()));
            }
            // ...
        }
        
        @Override
        public boolean isNull() {
            return value.isEmpty();  // 空字符串被视为NULL！
        }
    }
    
    // A模式无BOOLEAN类型，用NUMBER模拟
    public static class NumberAsBooleanConstant extends GaussDBAConstant {
        private final int value;  // 0 或 1
        
        @Override
        public String getTextRepresentation() {
            return String.valueOf(value);
        }
    }
}
```

#### 4.3.7 GaussDBPGExpressionGenerator（PG兼容）

```java
public class GaussDBPGExpressionGenerator 
    extends UntypedExpressionGenerator<GaussDBPGExpression, GaussDBPGColumn>
    implements NoRECGenerator<...>, TLPWhereGenerator<...>, CERTGenerator<...> {
    
    private final GaussDBPGGlobalState state;
    
    // 使用CASE WHEN构造非优化查询（PG语法）
    @Override
    public String generateUnoptimizedQueryString(GaussDBPGSelect select, GaussDBPGExpression where) {
        // PG: CASE WHEN condition THEN 1 ELSE 0 END
        GaussDBPGCaseWhen caseExpr = new GaussDBCaseWhen(where, 
            GaussDBPGConstant.createIntConstant(1),
            GaussDBPGConstant.createIntConstant(0));
        // ...
    }
}
```

#### 4.3.8 GaussDBAExpressionGenerator（A兼容）

```java
public class GaussDBAExpressionGenerator 
    extends UntypedExpressionGenerator<GaussDBAExpression, GaussDBAColumn>
    implements NoRECGenerator<...>, TLPWhereGenerator<...>, CERTGenerator<...> {
    
    private final GaussDBAGlobalState state;
    
    // A模式无CASE WHEN，使用DECODE（Oracle语法）
    @Override
    public String generateUnoptimizedQueryString(GaussDBASelect select, GaussDBAExpression where) {
        // Oracle: DECODE(condition, TRUE, 1, 0)
        // 或使用CASE（GaussDB A模式支持CASE）
        // ...
    }
    
    // A模式特殊函数
    public GaussDBAExpression generateNVL(GaussDBAExpression expr, GaussDBAExpression replacement);
    public GaussDBAExpression generateDECODE(GaussDBAExpression expr, GaussDBAConstant search, GaussDBAConstant result);
}
```

---

## 五、Test Oracle 实现方案

### 5.1 Oracle支持矩阵（双模块）

| Oracle | gaussdb-pg状态 | gaussdb-a状态 | 实现复杂度 | 说明 |
|--------|---------------|---------------|------------|------|
| **TLP_WHERE** | 需实现 | 需实现 | 低 | 复用common.TLPWhereOracle |
| **HAVING** | 需实现 | 需实现 | 低 | 继承TLPBase |
| **AGGREGATE** | 需实现 | 需实现 | 低 | 继承TLPBase |
| **DISTINCT** | 需实现 | 需实现 | 低 | 继承TLPBase |
| **GROUP_BY** | 需实现 | 需实现 | 低 | 继承TLPBase |
| **NOREC** | 需实现 | 需实现 | 低 | 复用common.NoRECOracle |
| **PQS** | 需实现 | 需实现 | 中 | 需row value提取，NULL语义差异 |
| **CERT** | 需实现 | 需实现 | 中 | EXPLAIN解析定制 |
| **DQP** | 需实现 | 需实现 | 中 | 执行计划控制 |
| **DQE** | 需实现 | 需实现 | 中 | SELECT/UPDATE/DELETE一致性 |
| **EET** | 需实现 | 需实现 | 高 | 表达式等价变换，NULL语义差异影响大 |
| **CODDTEST** | 需实现 | 需实现 | 中 | 常量驱动等价变换 |
| **FUZZER** | 需实现 | 需实现 | 低 | 随机SQL执行 |
| **QUERY_PARTITIONING** | 需实现 | 需实现 | 低 | 组合Oracle |

> **参考**：gaussdbm模块已实现上述全部Oracle，可作为参考；postgres模块是gaussdb-pg的主要参考。

### 5.2 TLP Oracle实现（双模块差异）

TLP Oracle核心原理：
```
原始查询: SELECT * FROM t WHERE predicate
分区查询:
  Q1: SELECT * FROM t WHERE predicate
  Q2: SELECT * FROM t WHERE NOT predicate
  Q3: SELECT * FROM t WHERE predicate IS NULL
  
验证: Q1 ∪ Q2 ∪ Q3 应等于 SELECT * FROM t（无WHERE）
```

**PG模式实现**：
```java
// GaussDBPGTLPBase.java
public class GaussDBPGTLPBase {
    protected GaussDBPGGlobalState state;
    protected GaussDBPGExpressionGenerator gen;
    
    // PG语义：空串不参与IS NULL判断
    protected GaussDBPGExpression generateIsNullPredicate(GaussDBPGExpression expr) {
        return new GaussDBPGUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
    }
}
```

**A模式实现（关键差异）**：
```java
// GaussDBATLPBase.java
public class GaussDBATLPBase {
    protected GaussDBAGlobalState state;
    protected GaussDBAExpressionGenerator gen;
    
    // Oracle语义：空串被视为NULL，IS NULL判断需特殊处理
    protected GaussDBAExpression generateIsNullPredicate(GaussDBAExpression expr) {
        // 对于VARCHAR2类型，空串=NULL，IS NULL会匹配空串
        // 需在生成器中正确处理
        return new GaussDBAUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
    }
    
    // A模式NULL三值逻辑验证需额外注意
    protected void verifyNullSemantics() {
        // Oracle: '' = NULL, NULL=NULL → NULL (不是TRUE也不是FALSE)
    }
}
```

### 5.3 NoREC Oracle实现（双模块差异）

NoREC核心原理：
```
优化查询: SELECT COUNT(*) FROM t WHERE predicate (被优化器处理)
非优化查询: SELECT SUM(CASE WHEN predicate THEN 1 ELSE 0 END) FROM t (逐行计算)

验证: 两结果应相等
```

**PG模式实现**：
```java
// GaussDBPGExpressionGenerator.java
@Override
public String generateUnoptimizedQueryString(GaussDBPGSelect select, GaussDBPGExpression where) {
    // PG语法：CASE WHEN ... THEN ... ELSE ... END
    GaussDBPGCaseWhen caseExpr = new GaussDBPGCaseWhen(
        where,
        GaussDBPGConstant.createIntConstant(1),
        GaussDBPGConstant.createIntConstant(0)
    );
    select.setFetchColumns(List.of(caseExpr));
    return "SELECT SUM(ref0) FROM (" + select.asString() + ") AS res";
}
```

**A模式实现**：
```java
// GaussDBAExpressionGenerator.java
@Override
public String generateUnoptimizedQueryString(GaussDBASelect select, GaussDBAExpression where) {
    // A模式可使用CASE WHEN或DECODE
    // GaussDB A模式支持CASE语法，也可用DECODE
    GaussDBACaseWhen caseExpr = new GaussDBACaseWhen(
        where,
        GaussDBAConstant.createNumberConstant(1),
        GaussDBAConstant.createNumberConstant(0)
    );
    select.setFetchColumns(List.of(caseExpr));
    return "SELECT SUM(ref0) FROM (" + select.asString() + ") AS res";
}

// 或使用DECODE版本
public String generateUnoptimizedQueryStringWithDECODE(GaussDBASelect select, GaussDBAExpression where) {
    // Oracle风格：DECODE(condition, TRUE, 1, 0)
    // 注意：DECODE不适用于布尔条件，需转换
}
```

### 5.4 PQS Oracle实现（NULL语义关键差异）

PQS核心原理：
```
1. 随机选取"轴心行"(pivot row)
2. 构造保证选中该行的查询
3. 执行查询验证轴心行是否在结果中
```

**PG模式实现要点**：
- `GaussDBPGSchema.GaussDBPGRowValue` 提取
- `GaussDBPGExpectedValueVisitor` 预期值验证
- 空字符串列值不影响NULL判断
- 参考 `PostgresPivotedQuerySynthesisOracle`

**A模式实现要点（关键差异）**：
- `GaussDBASchema.GaussDBARowValue` 提取
- **空字符串列值被视为NULL**，影响轴心行选择和查询构造
- 需特殊处理：避免选择空字符串值作为轴心行的关键条件
- 预期值验证需考虑Oracle NULL语义

### 5.5 CERT Oracle实现

CERT核心原理：
```
原始查询: SELECT * FROM t WHERE predicate
派生查询: SELECT * FROM t WHERE predicate AND stricter_condition

验证: EXPLAIN显示派生查询估计行数 ≤ 原查询估计行数
```

**PG模式EXPLAIN解析**：
```java
public class GaussDBPGCERTExplainParser {
    // PG兼容模式EXPLAIN输出解析（类似PostgreSQL）
    public static CheckedFunction<SQLancerResultSet, Optional<Long>> rowCountParser() {
        return (rs) -> {
            String content = rs.getString(1);
            // 解析 "rows=xxx" 或 "A-Rows: xxx"
        };
    }
}
```

**A模式EXPLAIN解析**：
```java
public class GaussDBACERTExplainParser {
    // A模式EXPLAIN输出可能有不同格式
    public static CheckedFunction<SQLancerResultSet, Optional<Long>> rowCountParser() {
        return (rs) -> {
            String content = rs.getString(1);
            // 解析A模式特有的EXPLAIN格式
        };
    }
}
```

### 5.6 DQP Oracle实现

DQP核心原理：
```
同一查询在不同执行计划下结果应一致
通过配置参数控制执行计划选择
```

**实现要点**：
- PG模式：`set enable_seqscan = off` 等参数（与PG类似）
- A模式：需确认A模式的执行计划控制参数

### 5.7 DQE Oracle实现

DQE核心原理：
```
同一predicate下：
- SELECT返回的行数
- UPDATE修改的行数  
- DELETE删除的行数
应保持一致
```

**实现要点**：
- PG模式：实现 `GaussDBPGUpdateGenerator`、`GaussDBPGDeleteGenerator`
- A模式：实现 `GaussDBAUpdateGenerator`、`GaussDBADeleteGenerator`
- 参考 gaussdbm 模块实现

### 5.8 EET Oracle实现（NULL语义影响最大）

EET核心原理：
```
原始表达式: E
等价变换: E' (语义保持)
验证: SELECT E FROM t 与 SELECT E' FROM t 结果multiset相等
```

**七条变换规则**：

| Rule | 变换 | 原理 |
|------|------|------|
| R1 | `B → false_expr OR B` | `false_expr`恒假 |
| R2 | `B → true_expr AND B` | `true_expr`恒真 |
| R3 | `E → CASE WHEN false_expr THEN R ELSE E END` | WHEN永假 |
| R4 | `E → CASE WHEN true_expr THEN E ELSE R END` | WHEN永真 |
| R5 | `E → CASE WHEN rb THEN copy(E) ELSE E END` | 随机分支+拷贝 |
| R6 | `E → CASE WHEN rb THEN E ELSE copy(E) END` | 对称 |
| R7 | `E → E` | 保守不变换 |

**PG模式EET实现**：
- 使用标准 `CASE WHEN ... THEN ... ELSE ... END` 语法
- NULL三值逻辑：`NULL OR x → NULL`，需正确处理

**A模式EET实现（关键差异）**：
- 支持CASE语法，也支持DECODE
- **NULL语义差异影响false_expr和true_expr构造**
- 空字符串在Oracle语义下是NULL，影响等价判断
- 需重新验证七条规则在Oracle语义下的正确性

---

## 六、错误处理与预期错误

### 6.1 GaussDBPGErrors（PG兼容）

```java
public final class GaussDBPGErrors {
    
    private static List<String> expressionErrorStrings() {
        List<String> errors = new ArrayList<>();
        errors.add("syntax error");
        errors.add("invalid input syntax");
        errors.add("division by zero");
        errors.add("value out of range");
        errors.add("integer out of range");
        errors.add("numeric field overflow");
        errors.add("invalid value for date");
        errors.add("data exception - string data right truncation");
        return errors;
    }
    
    public static List<String> getGroupingErrors() {
        List<String> errors = new ArrayList<>();
        errors.add("must appear in the GROUP BY clause");
        errors.add("nonaggregated column");
        return errors;
    }
    
    public static List<String> getInsertUpdateErrors() {
        List<String> errors = new ArrayList<>();
        errors.add("violates not-null constraint");
        errors.add("violates unique constraint");
        errors.add("duplicate key value");
        errors.add("null value in column");
        errors.add("value too long for type");
        return errors;
    }
}
```

### 6.2 GaussDBAErrors（A兼容）

```java
public final class GaussDBAErrors {
    
    private static List<String> expressionErrorStrings() {
        List<String> errors = new ArrayList<>();
        errors.add("syntax error");
        errors.add("invalid number");       // Oracle风格错误
        errors.add("division by zero");
        errors.add("value too large");
        errors.add("ORA-01722");             // Oracle错误码风格
        errors.add("invalid month format");
        errors.add("invalid date format");
        return errors;
    }
    
    // A模式特定错误
    public static List<String> getOracleStyleErrors() {
        List<String> errors = new ArrayList<>();
        errors.add("ORA-");                  // Oracle错误码前缀
        errors.add("PLS-");                  // PL/SQL错误
        return errors;
    }
}
```

---

## 七、JDBC连接与配置

### 7.1 PG模式连接配置

```java
// GaussDBPGProvider.java
@Override
public SQLConnection createDatabase(GaussDBPGGlobalState state) throws SQLException {
    // PG兼容模式使用PostgreSQL JDBC驱动
    String jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";
    
    Properties props = new Properties();
    props.setProperty("user", options.getUserName());
    props.setProperty("password", options.getPassword());
    
    Connection con = DriverManager.getConnection(jdbcUrl, props);
    
    try (Statement s = con.createStatement()) {
        s.execute("DROP SCHEMA IF EXISTS " + dbName + " CASCADE");
        s.execute("CREATE SCHEMA " + dbName);
        s.execute("SET search_path TO " + dbName);
    }
    
    return new SQLConnection(con);
}
```

### 7.2 A模式连接配置

```java
// GaussDBAProvider.java
@Override
public SQLConnection createDatabase(GaussDBAGlobalState state) throws SQLException {
    // A兼容模式可能需华为驱动或特殊配置
    String jdbcUrl = options.getConnectionURL();
    if (jdbcUrl == null) {
        jdbcUrl = "jdbc:postgresql://localhost:5432/postgres";  // 兼容PG驱动
    }
    
    Properties props = new Properties();
    props.setProperty("user", options.getUserName());
    props.setProperty("password", options.getPassword());
    
    // A模式特定属性（如需）
    // props.setProperty("compatibilityMode", "A");
    
    Connection con = DriverManager.getConnection(jdbcUrl, props);
    
    try (Statement s = con.createStatement()) {
        s.execute("DROP SCHEMA IF EXISTS " + dbName + " CASCADE");
        s.execute("CREATE SCHEMA " + dbName);
        s.execute("SET search_path TO " + dbName);
    }
    
    return new SQLConnection(con);
}
```

### 7.3 命令行选项

**PG模式**：
```java
public class GaussDBPGOptions implements DBMSSpecificOptions<GaussDBPGOracleFactory> {
    
    @Parameter(names = "--enable-time-types")
    public boolean enableTimeTypes = false;
    
    @Parameter(names = "--enable-json-types")
    public boolean enableJsonTypes = false;
    
    @Parameter(names = "--enable-uuid-types")
    public boolean enableUuidTypes = false;
    
    @Parameter(names = "--enable-array-types")
    public boolean enableArrayTypes = false;
    
    @Parameter(names = "--oracle")
    public List<GaussDBPGOracleFactory> oracleFactory = 
        Collections.singletonList(GaussDBPGOracleFactory.QUERY_PARTITIONING);
}
```

**A模式**：
```java
public class GaussDBAOptions implements DBMSSpecificOptions<GaussDBAOracleFactory> {
    
    @Parameter(names = "--enable-clob-blob")
    public boolean enableClobBlob = false;
    
    @Parameter(names = "--oracle")
    public List<GaussDBAOracleFactory> oracleFactory = 
        Collections.singletonList(GaussDBAOracleFactory.QUERY_PARTITIONING);
}
```

---

## 八、实施路线图（双模块并行开发）

### 8.1 Phase 1: 基础框架与重命名（Week 1-2）

| 任务 | 模块 | 预估工作量 | 输出 |
|------|------|-----------|------|
| gaussdb重命名为gaussdb-pg | PG | 1天 | 目录重构、类重命名 |
| GaussDBPGProvider实现 | PG | 2天 | Provider类 |
| GaussDBPGGlobalState实现 | PG | 1天 | GlobalState类 |
| GaussDBPGSchema实现 | PG | 2天 | Schema类+PG数据类型 |
| GaussDBPGErrors实现 | PG | 1天 | PG错误列表 |
| GaussDBAProvider实现 | A | 2天 | 新Provider类 |
| GaussDBAGlobalState实现 | A | 1天 | GlobalState类 |
| GaussDBASchema实现 | A | 2天 | Schema类+Oracle数据类型 |
| GaussDBAErrors实现 | A | 1天 | Oracle错误列表 |
| JDBC连接测试（双模式） | 双 | 2天 | 连接验证 |

### 8.2 Phase 2: AST与生成器（Week 3-4）

| 任务 | 模块 | 预估工作量 | 输出 |
|------|------|-----------|------|
| PG AST节点定义 | PG | 3天 | Expression/Select/Join等（参考postgres） |
| PG Constant实现（空串≠NULL） | PG | 2天 | GaussDBPGConstant |
| PG ToStringVisitor | PG | 2天 | SQL生成 |
| PG ExpressionGenerator | PG | 3天 | 表达式生成器 |
| A AST节点定义 | A | 4天 | Expression/Select/Join等（新建） |
| A Constant实现（空串=NULL） | A | 3天 | GaussDBAConstant（关键差异） |
| A ToStringVisitor | A | 2天 | Oracle风格SQL生成 |
| A ExpressionGenerator | A | 3天 | Oracle函数适配 |
| TableGenerator（双模式） | 双 | 2天 | 建表生成器 |

### 8.3 Phase 3: 基础Oracle（Week 5-6）

| 任务 | 模块 | 预估工作量 | 输出 |
|------|------|-----------|------|
| PG TLP_WHERE | PG | 1天 | 复用common |
| PG NOREC | PG | 1天 | 复用common |
| PG TLP系列（HAVING/AGG/DISTINCT/GROUP_BY） | PG | 2天 | 继承TLPBase |
| PG FUZZER | PG | 1天 | 随机执行 |
| PG OracleFactory | PG | 1天 | 工厂类 |
| A TLP_WHERE | A | 2天 | NULL语义适配 |
| A NOREC | A | 2天 | DECODE适配 |
| A TLP系列 | A | 3天 | NULL语义适配 |
| A FUZZER | A | 1天 | 随机执行 |
| A OracleFactory | A | 1天 | 工厂类 |

### 8.4 Phase 4: 高级Oracle（Week 7-8）

| 任务 | 模块 | 预估工作量 | 输出 |
|------|------|-----------|------|
| PG PQS | PG | 2天 | 轴心行验证（参考postgres） |
| PG CERT | PG | 2天 | EXPLAIN解析 |
| PG DQP | PG | 2天 | 执行计划控制 |
| PG DQE | PG | 3天 | UPDATE/DELETE生成器 |
| PG CODDTEST | PG | 2天 | 常量驱动变换 |
| A PQS | A | 3天 | 轴心行验证（NULL语义关键） |
| A CERT | A | 2天 | EXPLAIN解析（可能不同格式） |
| A DQP | A | 2天 | 执行计划控制（确认参数） |
| A DQE | A | 3天 | UPDATE/DELETE生成器 |
| A CODDTEST | A | 2天 | 常量驱动变换 |

### 8.5 Phase 5: EET Oracle（Week 9-10）

| 任务 | 模块 | 预估工作量 | 输出 |
|------|------|-----------|------|
| PG EET框架 | PG | 2天 | 基础结构 |
| PG EET Transformer | PG | 3天 | 七条规则（参考postgres/mysql） |
| PG EET Query Generator | PG | 1天 | 查询生成 |
| PG EET Component Reducer | PG | 2天 | 缩减支持 |
| A EET框架 | A | 2天 | 基础结构 |
| A EET Transformer | A | 4天 | 七条规则+Oracle NULL语义适配（重点） |
| A EET Query Generator | A | 1天 | 查询生成 |
| A EET Component Reducer | A | 2天 | 缩减支持 |

### 8.6 Phase 6: 测试与优化（Week 11-12）

| 任务 | 模块 | 预估工作量 | 输出 |
|------|------|-----------|------|
| PG Oracle集成测试 | PG | 2天 | PG模式验证 |
| A Oracle集成测试 | A | 2天 | A模式验证（重点NULL语义） |
| 错误处理优化 | 双 | 1天 | 错误过滤 |
| 性能调优 | 双 | 1天 | 执行效率 |
| 文档完善 | 双 | 2天 | 用户文档（双模式说明） |

---

## 九、风险评估与应对策略

### 9.1 技术风险

| 风险 | 影响模块 | 影响 | 应对策略 |
|------|---------|------|---------|
| GaussDB PG语法差异 | PG | 低 | 直接复用postgres模块代码 |
| GaussDB A语法差异 | A | 中 | 参考Oracle特性，定制实现 |
| NULL语义差异（空串=NULL） | A | 高 | Constant类独立实现，重点测试 |
| EXPLAIN输出格式差异 | 双 | 中 | PG复用postgres解析，A定制解析 |
| JDBC驱动兼容性 | 双 | 低 | PG用PG驱动，A确认驱动支持 |
| A模式函数适配 | A | 中 | NVL/DECODE/TO_CHAR等函数实现 |
| 分布式版本差异 | 双 | 低 | 集中式版本优先 |

### 9.2 实施风险

| 风险 | 影响 | 应对策略 |
|------|------|---------|
| 双模块并行开发复杂度 | 高 | PG优先（复用postgres），A后续 |
| A模式NULL语义测试不充分 | 高 | 专项NULL语义测试计划 |
| 工作量估计偏差 | 中 | 模块化实施，增量交付 |
| GaussDB环境获取 | 中 | 云服务或本地测试环境 |

---

## 十、测试验证方案

### 10.1 单元测试

**PG模式**：
```java
// GaussDBPGConstantTest.java
@Test
public void testStringConstantNotEqualsNull() {
    // PG语义：空字符串不等于NULL
    GaussDBPGConstant empty = GaussDBPGConstant.createStringConstant("");
    assertFalse(empty.isNull());  // 空串不是NULL
}

// GaussDBPGExpressionGeneratorTest.java
@Test
public void testExpressionGeneration() {
    // 验证PG语法表达式生成
}
```

**A模式**：
```java
// GaussDBAConstantTest.java（关键测试）
@Test
public void testEmptyStringEqualsNull() {
    // Oracle语义：空字符串等于NULL
    GaussDBAConstant empty = GaussDBAConstant.createVarchar2Constant("");
    assertTrue(empty.isNull());  // 串被视为NULL！
}

@Test
public void testNullEqualsNull() {
    // NULL = NULL 在Oracle语义下返回NULL（不是TRUE）
    GaussDBAConstant null1 = GaussDBAConstant.createNullConstant();
    GaussDBAConstant null2 = GaussDBAConstant.createNullConstant();
    GaussDBAConstant result = null1.sqlEquals(null2);
    assertTrue(result.isNull());  // NULL=NULL → NULL
}
```

### 10.2 Oracle测试命令

**PG模式测试**：
```bash
# PG模式基础测试
java -jar sqlancer.jar gaussdb-pg --oracle TLP_WHERE --num-tries 10

# PG模式组合测试
java -jar sqlancer.jar gaussdb-pg --oracle QUERY_PARTITIONING --timeout-seconds 300

# PG模式PQS测试
java -jar sqlancer.jar gaussdb-pg --oracle PQS --num-tries 5

# PG模式EET测试
java -jar sqlancer.jar gaussdb-pg --oracle EET --num-tries 10

# PG模式高级类型测试
java -jar sqlancer.jar gaussdb-pg --oracle NOREC --enable-time-types=true
```

**A模式测试**：
```bash
# A模式基础测试
java -jar sqlancer.jar gaussdb-a --oracle TLP_WHERE --num-tries 10

# A模式组合测试
java -jar sqlancer.jar gaussdb-a --oracle QUERY_PARTITIONING --timeout-seconds 300

# A模式NULL语义专项测试（关键）
java -jar sqlancer.jar gaussdb-a --oracle PQS --num-tries 5

# A模式EET测试（NULL语义影响最大）
java -jar sqlancer.jar gaussdb-a --oracle EET --num-tries 10
```

### 10.3 验证标准

| Oracle | PG模式验证标准 | A模式验证标准 |
|--------|---------------|---------------|
| TLP系列 | 分区结果与原查询一致（空串≠NULL不影响） | 分区结果与原查询一致（空串=NULL需特殊处理） |
| NoREC | CASE WHEN计数一致 | CASE/DECODE计数一致 |
| PQS | 轴心行在结果中存在 | 轴心行验证需避开空串列值 |
| CERT | 基数估计约束满足 | 基数估计约束满足 |
| DQP | 不同计划结果一致 | 不同计划结果一致 |
| DQE | SELECT/UPDATE/DELETE计数一致 | SELECT/UPDATE/DELETE计数一致 |
| EET | 变换前后multiset相等 | 变换前后multiset相等（NULL语义关键） |

---

## 十一、GaussDB语法覆盖规范

本章节详细定义GaussDB PG兼容模式和A兼容模式的语法覆盖范围，确保测试完整性。

### 12.1 数据类型完整覆盖

#### 12.1.1 PG兼容模式数据类型

| 类型分类 | 具体类型 | SQLancer状态 | 生成器处理 | 说明 |
|---------|---------|-------------|-----------|------|
| **整数类型** | SMALLINT, INTEGER, BIGINT | ✓ 需实现 | IntConstant | 2/4/8字节整数 |
| **浮点类型** | FLOAT, REAL, DOUBLE PRECISION | ✓ 需实现 | FloatConstant/DoubleConstant | 单精度/双精度浮点 |
| **定点类型** | NUMERIC(p,s), DECIMAL(p,s) | ✓ 需实现 | DecimalConstant | 精确数值 |
| **字符类型** | CHAR(n), VARCHAR(n), TEXT | ✓ 需实现 | StringConstant | 定长/变长字符串 |
| **布尔类型** | BOOLEAN | ✓ 需实现 | BooleanConstant | TRUE/FALSE/NULL |
| **日期时间** | DATE, TIME, TIMESTAMP, TIMESTAMPTZ, INTERVAL | ⚠ 需扩展 | DateConstant等 | 需开关控制 |
| **二进制** | BYTEA | ⚠ 需扩展 | ByteaConstant | 二进制数据 |
| **JSON类型** | JSON, JSONB | ⚠ 需扩展 | JsonConstant | JSON数据 |
| **UUID类型** | UUID | ⚠ 需扩展 | UUIDConstant | UUID标识符 |
| **数组类型** | INT[], TEXT[], UUID[]等 | ⚠ 需扩展 | ArrayConstant | 数组数据 |
| **枚举类型** | ENUM | ⚠ 需扩展 | EnumConstant | 用户定义枚举 |
| **网络地址** | INET, CIDR | ⚠ 需扩展 | InetConstant | IP地址 |
| **位串类型** | BIT(n), BIT VARYING(n) | ⚠ 需扩展 | BitConstant | 位串 |
| **货币类型** | MONEY | ⚠ 需扩展 | MoneyConstant | 货币金额 |

**优先级建议**：
- P0（必须）：INT, VARCHAR, BOOLEAN, DECIMAL
- P1（重要）：FLOAT, DOUBLE, DATE, TIMESTAMP, TEXT
- P2（可选）：JSON, UUID, ARRAY, BYTEA

#### 12.1.2 A兼容模式数据类型（Oracle风格）

| 类型分类 | 具体类型 | SQLancer状态 | 生成器处理 | 说明 |
|---------|---------|-------------|-----------|------|
| **数值类型** | NUMBER(p,s), INTEGER | ✓ 需实现 | NumberConstant | Oracle数值 |
| **字符类型** | VARCHAR2(n), NVARCHAR2(n), CHAR(n) | ✓ 需实现 | Varchar2Constant | Oracle字符串 |
| **大对象** | CLOB, NCLOB, BLOB | ⚠ 需扩展 | ClobConstant等 | 大文本/二进制 |
| **日期时间** | DATE（含时间）, TIMESTAMP, TIMESTAMP WITH TIME ZONE | ✓ 需实现 | DateConstant | Oracle DATE含时间 |
| **二进制** | RAW(n), LONG RAW | ⚠ 需扩展 | RawConstant | 原始二进制 |
| **布尔模拟** | NUMBER(1)或CHAR(1) | ✓ 需实现 | NumberAsBooleanConstant | A模式无BOOLEAN |
| **ROWID** | ROWID, UROWID | ⚠ 需扩展 | RowidConstant | 行标识符 |
| **XML类型** | XMLTYPE | ⚠ 需扩展 | XmlConstant | XML数据 |

**A模式特殊处理**：
- BOOLEAN类型不存在，需用NUMBER(1)或CHAR(1)模拟
- DATE类型默认包含时间（与PG不同）
- 空字符串被视为NULL（关键语义差异）

### 12.2 DDL语法覆盖

#### 12.2.1 CREATE TABLE语法

**PG模式**：
```sql
CREATE [TEMPORARY | UNLOGGED] TABLE [IF NOT EXISTS] table_name (
    column_name data_type [column_constraint ...],
    [, ...]
    [, table_constraint ...]
)
[INHERITS (parent_table [, ...])]
[PARTITION BY {RANGE | LIST | HASH} (partition_key)]
[WITH (storage_parameter = value [, ...])]
[ON COMMIT {PRESERVE ROWS | DELETE ROWS | DROP}]
[TABLESPACE tablespace_name]
```

**列约束**：
| 约束 | PG支持 | A支持 | 生成器实现 |
|------|--------|--------|-----------|
| NOT NULL / NULL | ✓ | ✓ | ✓ 需实现 |
| DEFAULT | ✓ | ✓ | ✓ 需实现 |
| UNIQUE | ✓ | ✓ | ✓ 需实现 |
| PRIMARY KEY | ✓ | ✓ | ✓ 需实现 |
| CHECK (expr) | ✓ | ✓ | ✓ 需实现 |
| REFERENCES (FK) | ✓ | ✓ | ⚠ 需扩展 |
| GENERATED ALWAYS AS (expr) STORED | ✓ | ⚠ | ✓ PG需实现 |
| GENERATED {ALWAYS | BY DEFAULT} AS IDENTITY | ✓ | ⚠ | ✓ PG需实现 |

**表约束**：
| 约束 | PG支持 | A支持 | 生成器实现 |
|------|--------|--------|-----------|
| PRIMARY KEY (cols) | ✓ | ✓ | ✓ 需实现 |
| UNIQUE (cols) | ✓ | ✓ | ✓ 需实现 |
| CHECK (expr) | ✓ | ✓ | ✓ 需实现 |
| FOREIGN KEY (cols) REFERENCES | ✓ | ✓ | ⚠ 需扩展 |
| EXCLUDE USING gist (...) | ✓ | - | ⚠ PG扩展 |

**A模式CREATE TABLE语法**：
```sql
CREATE TABLE table_name (
    column_name data_type [DEFAULT expr] [NOT NULL]
    [, ...]
    [, CONSTRAINT constraint_name ...]
)
-- Oracle风格的约束语法
-- 支持 ENABLE/DISABLE 约束状态
```

#### 12.2.2 ALTER TABLE语法

| 操作 | PG语法 | A语法 | 生成器实现 |
|------|--------|--------|-----------|
| ADD COLUMN | ADD COLUMN col type | ADD (col type) | ✓ 需实现 |
| DROP COLUMN | DROP COLUMN col [CASCADE] | DROP COLUMN col | ✓ 需实现 |
| ALTER COLUMN | ALTER COLUMN col TYPE type | MODIFY col type | ✓ 需实现 |
| ADD CONSTRAINT | ADD CONSTRAINT name ... | ADD CONSTRAINT name ... | ⚠ 需扩展 |
| DROP CONSTRAINT | DROP CONSTRAINT name | DROP CONSTRAINT name | ⚠ 需扩展 |
| SET NOT NULL | ALTER COLUMN col SET NOT NULL | MODIFY col NOT NULL | ✓ 需实现 |
| DROP NOT NULL | ALTER COLUMN col DROP NOT NULL | MODIFY col NULL | ✓ 需实现 |
| SET DEFAULT | ALTER COLUMN col SET DEFAULT | MODIFY col DEFAULT expr | ✓ 需实现 |
| RENAME COLUMN | RENAME COLUMN col TO new | RENAME COLUMN col TO new | ✓ 需实现 |
| RENAME TABLE | RENAME TO new_name | RENAME TO new_name | ✓ 需实现 |

#### 12.2.3 CREATE INDEX语法

**PG模式**：
```sql
CREATE [UNIQUE] INDEX [CONCURRENTLY] [IF NOT EXISTS] index_name
ON [ONLY] table_name [USING method]
(
    column_name | (expression) [opclass] [ASC | DESC] [NULLS {FIRST | LAST}]
    [, ...]
)
[INCLUDE (column_name [, ...])]
[WHERE predicate]
```

| 索引类型 | PG支持 | A支持 | 说明 |
|---------|--------|--------|------|
| B-tree | ✓ | ✓ | 默认索引类型 |
| Hash | ✓ | ⚠ | 哈希索引 |
| GiST | ✓ | - | PG特有 |
| GIN | ✓ | - | PG特有 |
| 表达式索引 | ✓ | ✓ | 索引表达式 |
| 部分索引(WHERE) | ✓ | ⚠ | 条件索引 |
| INCLUDE列 | ✓ | - | PG特有 |

**A模式索引**：
```sql
CREATE [UNIQUE] INDEX index_name ON table_name (column_name [, ...])
-- Oracle风格可能不支持部分索引和表达式索引
```

#### 12.2.4 CREATE VIEW语法

**PG模式**：
```sql
CREATE [OR REPLACE] [TEMP | TEMPORARY] [RECURSIVE] VIEW view_name
[(column_name [, ...])]
AS query
[WITH [CASCADED | LOCAL] CHECK OPTION]

CREATE MATERIALIZED VIEW view_name
[(column_name [, ...])]
AS query
[WITH (storage_parameter)]
```

**A模式**：
```sql
CREATE [OR REPLACE] [FORCE] VIEW view_name
AS subquery
[WITH CHECK OPTION]
-- Oracle支持 FORCE VIEW 强制创建无效视图
```

#### 12.2.5 CREATE SEQUENCE语法

**PG模式**：
```sql
CREATE [TEMPORARY] SEQUENCE [IF NOT EXISTS] seq_name
[AS smallint | integer | bigint]
[INCREMENT [BY] increment]
[MINVALUE minvalue | NO MINVALUE]
[MAXVALUE maxvalue | NO MAXVALUE]
[START [WITH] start]
[CACHE cache]
[[NO] CYCLE]
[OWNED BY {table.column | NONE}]
```

**A模式**：
```sql
CREATE SEQUENCE seq_name
[INCREMENT BY increment]
[START WITH start]
[MAXVALUE maxvalue | NOMAXVALUE]
[MINVALUE minvalue | NOMINVALUE]
[CYCLE | NOCYCLE]
[CACHE cache | NOCACHE]
[ORDER | NOORDER]
```

#### 12.2.6 其他DDL对象

| 对象 | PG支持 | A支持 | 生成器实现 | 说明 |
|------|--------|--------|-----------|------|
| TRUNCATE TABLE | ✓ | ✓ | ✓ 需实现 | 清空表 |
| DROP TABLE | ✓ | ✓ | ✓ 需实现 | 删除表 |
| DROP INDEX | ✓ | ✓ | ✓ 需实现 | 删除索引 |
| DROP VIEW | ✓ | ✓ | ✓ 需实现 | 删除视图 |
| DROP SEQUENCE | ✓ | ✓ | ✓ 需实现 | 删除序列 |
| COMMENT ON | ✓ | ⚠ | ⚠ PG实现 | 注释 |
| ANALYZE | ✓ | ⚠ | ⚠ PG实现 | 统计信息 |
| REINDEX | ✓ | - | ⚠ PG实现 | 重建索引 |
| CLUSTER | ✓ | - | ⚠ PG实现 | 物理聚类 |
| VACUUM | ✓ | - | ⚠ PG实现 | 清理 |
| CREATE TABLESPACE | ✓ | ⚠ | ⚠ PG实现 | 表空间 |
| CREATE TRIGGER | ✓ | ✓ | ⚠ 需扩展 | 触发器 |
| CREATE FUNCTION | ✓ | ✓ | ⚠ 需扩展 | 函数 |
| CREATE PROCEDURE | ⚠ | ✓ | ⚠ 需扩展 | 存储过程 |
| CREATE PACKAGE | - | ✓ | ⚠ A扩展 | Oracle包 |
| CREATE SYNONYM | - | ✓ | ⚠ A扩展 | 同义词 |

### 12.3 DML语法覆盖

#### 12.3.1 SELECT语法

**核心SELECT结构**：
```sql
SELECT [ALL | DISTINCT] select_list
FROM from_item [, ...]
[WHERE where_condition]
[GROUP BY grouping_element [, ...]]
[HAVING having_condition]
[ORDER BY order_expression [ASC | DESC] [NULLS {FIRST | LAST}] [, ...]]
[LIMIT {count | ALL}]
[OFFSET start]
[FOR {UPDATE | NO KEY UPDATE | SHARE | NO KEY SHARE} [OF table_name [, ...]] [NOWAIT | SKIP LOCKED]]
```

**from_item类型**：
| 来源类型 | PG支持 | A支持 | AST节点 | 说明 |
|---------|--------|--------|---------|------|
| 表名 | ✓ | ✓ | TableReference | 直接引用 |
| 别名 | ✓ | ✓ | Alias | AS alias |
| 子查询 | ✓ | ✓ | DerivedTable | (SELECT...) AS alias |
| JOIN | ✓ | ✓ | JoinNode | 多表连接 |
| CTE (WITH) | ✓ | ✓ | CteDefinition | WITH子句 |
| LATERAL | ✓ | ⚠ | LateralReference | PG特有 |
| DUAL表 | - | ✓ | DualTableReference | Oracle虚拟表 |

**JOIN类型**：
| 连接类型 | PG语法 | A语法 | AST实现 |
|---------|--------|--------|---------|
| INNER JOIN | JOIN / INNER JOIN | JOIN / INNER JOIN | ✓ |
| LEFT OUTER JOIN | LEFT JOIN / LEFT OUTER JOIN | LEFT JOIN / LEFT OUTER JOIN | ✓ |
| RIGHT OUTER JOIN | RIGHT JOIN / RIGHT OUTER JOIN | RIGHT JOIN / RIGHT OUTER JOIN | ✓ |
| FULL OUTER JOIN | FULL JOIN / FULL OUTER JOIN | FULL OUTER JOIN | ✓ |
| CROSS JOIN | CROSS JOIN | CROSS JOIN | ✓ |
| NATURAL JOIN | NATURAL JOIN | NATURAL JOIN | ✓ |
| SELF JOIN | self join | self join | ✓ |
| (+) 外连接 | - | t1.col = t2.col(+) | ⚠ A特有 |

**窗口函数**：
```sql
function_name ([args]) OVER (
    [PARTITION BY partition_expr [, ...]]
    [ORDER BY order_expr [ASC | DESC] [NULLS {FIRST | LAST}] [, ...]]
    [frame_clause]
)
```

| 窗口函数 | PG支持 | A支持 | 生成器 |
|---------|--------|--------|--------|
| ROW_NUMBER() | ✓ | ✓ | ⚠ |
| RANK() | ✓ | ✓ | ⚠ |
| DENSE_RANK() | ✓ | ✓ | ⚠ |
| LEAD() / LAG() | ✓ | ✓ | ⚠ |
| FIRST_VALUE() / LAST_VALUE() | ✓ | ✓ | ⚠ |

#### 12.3.2 INSERT语法

**PG模式**：
```sql
INSERT INTO table_name [(column_name [, ...])]
[OVERRIDING {SYSTEM | USER} VALUE]
{DEFAULT VALUES | VALUES (value [, ...]) [, ...] | query}
[ON CONFLICT [conflict_target] conflict_action]
[RETURNING * | output_expression [[AS] output_name] [, ...]]
```

**关键特性**：
| 特性 | PG支持 | A支持 | 生成器 |
|------|--------|--------|--------|
| VALUES列表 | ✓ | ✓ | ✓ |
| 子查询INSERT | ✓ | ✓ | ⚠ |
| DEFAULT VALUES | ✓ | ⚠ | ⚠ |
| ON CONFLICT (UPSERT) | ✓ | ⚠ | ⚠ PG扩展 |
| RETURNING | ✓ | ⚠ | ⚠ PG扩展 |
| 多行INSERT | ✓ | ✓ | ✓ |

**A模式INSERT**：
```sql
INSERT INTO table_name [(columns)] VALUES (values [, ...])
-- Oracle支持 INSERT ALL 多表插入
-- 支持 MERGE INTO 合并插入
```

#### 12.3.3 UPDATE语法

**PG模式**：
```sql
UPDATE [ONLY] table_name [[AS] alias]
SET {column_name = {expression | DEFAULT} |
     (column_name [, ...]) = ({expression | DEFAULT} [, ...])} [, ...]
[FROM from_item [, ...]]
[WHERE where_condition | WHERE CURRENT OF cursor_name]
[RETURNING * | output_expression [[AS] output_name] [, ...]]
```

**关键特性**：
| 特性 | PG支持 | A支持 | 生成器 |
|------|--------|--------|--------|
| SET col = expr | ✓ | ✓ | ✓ |
| SET (cols) = (exprs) | ✓ | ⚠ | ⚠ |
| FROM子句 | ✓ | ⚠ | ⚠ |
| RETURNING | ✓ | ⚠ | ⚠ PG扩展 |
| WHERE CURRENT OF | ✓ | ⚠ | ⚠ PG扩展 |

#### 12.3.4 DELETE语法

**PG模式**：
```sql
DELETE FROM [ONLY] table_name [[AS] alias]
[USING from_item [, ...]]
[WHERE where_condition | WHERE CURRENT OF cursor_name]
[RETURNING * | output_expression [[AS] output_name] [, ...]]
```

**关键特性**：
| 特性 | PG支持 | A支持 | 生成器 |
|------|--------|--------|--------|
| WHERE条件 | ✓ | ✓ | ✓ |
| USING子句 | ✓ | ⚠ | ⚠ |
| RETURNING | ✓ | ⚠ | ⚠ PG扩展 |

#### 12.3.5 MERGE语法（A模式重点）

**A模式MERGE（Oracle风格）**：
```sql
MERGE INTO target_table USING source_table
ON (merge_condition)
WHEN MATCHED THEN
    UPDATE SET column = value [, ...]
    [DELETE where_clause]
WHEN NOT MATCHED THEN
    INSERT [(columns)] VALUES (values)
```

| 特性 | PG支持 | A支持 | 生成器 |
|------|--------|--------|--------|
| MERGE INTO | ⚠ (PG15+) | ✓ | ⚠ A重点 |

### 12.4 函数覆盖

#### 12.4.1 PG模式函数

**字符串函数**：
| 函数 | PG语法 | 返回类型 | PQS支持 | 说明 |
|------|--------|---------|---------|------|
| LENGTH | LENGTH(str) | INT | ✓ | 字符串长度 |
| LOWER | LOWER(str) | TEXT | ✓ | 小写转换 |
| UPPER | UPPER(str) | TEXT | ✓ | 大写转换 |
| SUBSTRING | SUBSTRING(str FROM start FOR len) | TEXT | ⚠ | 子串 |
| CONCAT | CONCAT(str1, str2, ...) | TEXT | ✓ | 连接 |
| CONCAT_WS | CONCAT_WS(sep, str, ...) | TEXT | ⚠ | 分隔连接 |
| TRIM | TRIM([LEADING|TRAILING|BOTH] chars FROM str) | TEXT | ⚠ | 剪裁 |
| REPLACE | REPLACE(str, from, to) | TEXT | ⚠ | 替换 |
| POSITION | POSITION(substr IN str) | INT | ⚠ | 位置 |
| LPAD/RPAD | LPAD(str, len, pad) | TEXT | ⚠ | 填充 |
| LEFT/RIGHT | LEFT(str, n) | TEXT | ⚠ | 取左/右 |
| REVERSE | REVERSE(str) | TEXT | ⚠ | 反转 |

**数值函数**：
| 函数 | PG语法 | 返回类型 | PQS支持 | 说明 |
|------|--------|---------|---------|------|
| ABS | ABS(n) | 数值 | ✓ | 绝对值 |
| CEIL/FLOOR | CEIL(n), FLOOR(n) | 数值 | ✓ | 向上/向下取整 |
| ROUND | ROUND(n, decimals) | 数值 | ✓ | 四舍五入 |
| TRUNC | TRUNC(n, decimals) | 数值 | ⚠ | 截断 |
| MOD | MOD(a, b) | 数值 | ✓ | 取模 |
| POWER | POWER(a, b) | 数值 | ⚠ | 幂运算 |
| SQRT | SQRT(n) | 数值 | ⚠ | 平方根 |
| SIGN | SIGN(n) | INT | ⚠ | 符号 |

**日期函数**：
| 函数 | PG语法 | 返回类型 | PQS支持 | 说明 |
|------|--------|---------|---------|------|
| NOW | NOW() | TIMESTAMP | ⚠ | 当前时间 |
| CURRENT_DATE | CURRENT_DATE | DATE | ⚠ | 当前日期 |
| CURRENT_TIME | CURRENT_TIME | TIME | ⚠ | 当前时间 |
| AGE | AGE(timestamp1, timestamp2) | INTERVAL | ⚠ | 时间差 |
| EXTRACT | EXTRACT(field FROM timestamp) | 数值 | ⚠ | 提取字段 |
| DATE_TRUNC | DATE_TRUNC(field, timestamp) | TIMESTAMP | ⚠ | 截断日期 |

**类型转换函数**：
| 函数 | PG语法 | 说明 |
|------|--------|------|
| CAST | CAST(expr AS type) | 标准转换 |
| ::type | expr::type | PG特有转换 |
| TO_DATE | TO_DATE(str, format) | 字符串转日期 |

**聚合函数**：
| 函数 | PG语法 | PQS支持 | 说明 |
|------|--------|---------|------|
| COUNT | COUNT(*) / COUNT(expr) | ✓ | 计数 |
| SUM | SUM(expr) | ✓ | 求和 |
| AVG | AVG(expr) | ✓ | 平均值 |
| MIN | MIN(expr) | ✓ | 最小值 |
| MAX | MAX(expr) | ✓ | 最大值 |
| STRING_AGG | STRING_AGG(expr, delimiter) | ⚠ | 字符串聚合 |
| ARRAY_AGG | ARRAY_AGG(expr) | ⚠ | 数组聚合 |

**NULL处理函数**：
| 函数 | PG语法 | A语法对应 | 说明 |
|------|--------|----------|------|
| COALESCE | COALESCE(expr1, expr2, ...) | COALESCE | 返回第一个非NULL |
| NULLIF | NULLIF(expr1, expr2) | NULLIF | 相等返回NULL |

#### 12.4.2 A模式函数（Oracle风格）

**字符串函数**：
| 函数 | A语法 | PG对应 | PQS支持 | 说明 |
|------|--------|--------|---------|------|
| LENGTH | LENGTH(str) | LENGTH | ✓ | 长度 |
| LENGTHB | LENGTHB(str) | - | ⚠ | 字节长度 |
| INSTR | INSTR(str, substr, start, nth) | POSITION扩展 | ⚠ | 查找位置 |
| SUBSTR | SUBSTR(str, start, len) | SUBSTRING | ⚠ | 子串 |
| CONCAT | CONCAT(str1, str2) | CONCAT | ✓ | 连接（仅两参数） |
| || (拼接) | str1 || str2 | CONCAT | ✓ | Oracle拼接 |
| LOWER/UPPER | LOWER(str)/UPPER(str) | LOWER/UPPER | ✓ | 大小写转换 |
| TRIM | TRIM([LEADING|TRAILING] chars FROM str) | TRIM | ⚠ | 剪裁 |
| LTRIM/RTRIM | LTRIM(str, chars) | TRIM LEADING/TRAILING | ⚠ | 左/右剪裁 |
| REPLACE | REPLACE(str, search, replace) | REPLACE | ⚠ | 替换 |
| LPAD/RPAD | LPAD(str, len, pad) | LPAD/RPAD | ⚠ | 填充 |
| INITCAP | INITCAP(str) | - | ⚠ | 首字母大写 |

**数值函数**：
| 函数 | A语法 | PG对应 | PQS支持 | 说明 |
|------|--------|--------|---------|------|
| ABS | ABS(n) | ABS | ✓ | 绝对值 |
| CEIL/FLOOR | CEIL(n)/FLOOR(n) | CEIL/FLOOR | ✓ | 取整 |
| ROUND | ROUND(n, decimals) | ROUND | ✓ | 四舍五入 |
| TRUNC | TRUNC(n, decimals) | TRUNC | ⚠ | 截断 |
| MOD | MOD(a, b) | MOD | ✓ | 取模 |
| POWER | POWER(a, b) | POWER | ⚠ | 幂运算 |
| SQRT | SQRT(n) | SQRT | ⚠ | 平方根 |
| SIGN | SIGN(n) | SIGN | ⚠ | 符号 |
| REMAINDER | REMAINDER(a, b) | - | ⚠ | Oracle余数 |

**日期函数（Oracle风格）**：
| 函数 | A语法 | PG对应 | PQS支持 | 说明 |
|------|--------|--------|---------|------|
| SYSDATE | SYSDATE | NOW() | ⚠ | 当前日期时间 |
| CURRENT_DATE | CURRENT_DATE | CURRENT_DATE | ⚠ | 当前日期 |
| ADD_MONTHS | ADD_MONTHS(date, n) | date + INTERVAL | ⚠ | 加月份 |
| MONTHS_BETWEEN | MONTHS_BETWEEN(d1, d2) | AGE扩展 | ⚠ | 月份数 |
| LAST_DAY | LAST_DAY(date) | - | ⚠ | 月末日期 |
| NEXT_DAY | NEXT_DAY(date, weekday) | - | ⚠ | 下个工作日 |
| EXTRACT | EXTRACT(field FROM date) | EXTRACT | ⚠ | 提取字段 |
| TO_DATE | TO_DATE(str, format) | TO_DATE扩展 | ⚠ | 字符串转日期 |
| TO_CHAR | TO_CHAR(expr, format) | - | ⚠ | 转字符串 |
| TO_TIMESTAMP | TO_TIMESTAMP(str, format) | TO_TIMESTAMP | ⚠ | 转时间戳 |

**NULL处理函数（关键差异）**：
| 函数 | A语法 | PG对应 | PQS支持 | 说明 |
|------|--------|--------|---------|------|
| NVL | NVL(expr1, expr2) | COALESCE(expr1, expr2) | ✓ | Oracle NULL替代 |
| NVL2 | NVL2(expr1, expr2, expr3) | CASE WHEN expr1 IS NOT NULL... | ⚠ | 三参数NULL处理 |
| COALESCE | COALESCE(expr1, ...) | COALESCE | ✓ | 多参数NULL替代 |
| NULLIF | NULLIF(expr1, expr2) | NULLIF | ⚠ | 相等返回NULL |
| LNNVL | LNNVL(condition) | - | ⚠ | Oracle条件否定 |

**条件函数**：
| 函数 | A语法 | PG对应 | PQS支持 | 说明 |
|------|--------|--------|---------|------|
| DECODE | DECODE(expr, search, result, ..., default) | CASE WHEN | ⚠ | Oracle条件函数 |
| CASE | CASE WHEN ... THEN ... END | CASE WHEN | ✓ | 条件表达式 |
| NULLIF | NULLIF(expr1, expr2) | NULLIF | ⚠ | 条件NULL |

**DECODE详解（A模式关键）**：
```sql
DECODE(expr, search1, result1, search2, result2, ..., default)
-- 相当于 CASE expr WHEN search1 THEN result1 WHEN search2 THEN result2 ELSE default END
-- 注意：DECODE的比较使用NULL=NULL返回NULL（不是TRUE）
```

**聚合函数**：
| 函数 | A语法 | PG对应 | PQS支持 | 说明 |
|------|--------|--------|---------|------|
| COUNT | COUNT(*) / COUNT(expr) | COUNT | ✓ | 计数 |
| SUM | SUM(expr) | SUM | ✓ | 求和 |
| AVG | AVG(expr) | AVG | ✓ | 平均值 |
| MIN/MAX | MIN(expr)/MAX(expr) | MIN/MAX | ✓ | 最小/最大值 |
| LISTAGG | LISTAGG(expr, delimiter) WITHIN GROUP (ORDER BY ...) | STRING_AGG | ⚠ | Oracle字符串聚合 |

### 12.5 表达式覆盖

#### 12.5.1 比较表达式

| 操作 | PG语法 | A语法 | AST节点 |
|------|--------|--------|---------|
| 等于 | = | = | BinaryComparison(EQ) |
| 不等于 | <> 或 != | <> 或 != | BinaryComparison(NE) |
| 小于 | < | < | BinaryComparison(LT) |
| 大于 | > | > | BinaryComparison(GT) |
| 小于等于 | <= | <= | BinaryComparison(LE) |
| 大于等于 | >= | >= | BinaryComparison(GE) |
| BETWEEN | BETWEEN a AND b | BETWEEN a AND b | BetweenOperation |
| NOT BETWEEN | NOT BETWEEN a AND b | NOT BETWEEN a AND b | BetweenOperation(negated) |
| IS NULL | IS NULL | IS NULL | UnaryPostfix(IS_NULL) |
| IS NOT NULL | IS NOT NULL | IS NOT NULL | UnaryPostfix(IS_NOT_NULL) |
| IS TRUE/FALSE | IS TRUE/FALSE | - | PG特有 |
| IN | IN (values) | IN (values) | InOperation |
| NOT IN | NOT IN (values) | NOT IN (values) | InOperation(negated) |

#### 12.5.2 逻辑表达式

| 操作 | PG语法 | A语法 | AST节点 |
|------|--------|--------|---------|
| AND | AND | AND | BinaryLogical(AND) |
| OR | OR | OR | BinaryLogical(OR) |
| NOT | NOT | NOT | UnaryPrefix(NOT) |

#### 12.5.3 算术表达式

| 操作 | PG语法 | A语法 | AST节点 |
|------|--------|--------|---------|
| 加 | + | + | BinaryArithmetic(ADD) |
| 减 | - | - | BinaryArithmetic(SUB) |
| 乘 | * | * | BinaryArithmetic(MUL) |
| 除 | / | / | BinaryArithmetic(DIV) |
| 取模 | % 或 MOD() | MOD() | BinaryArithmetic(MOD) |

#### 12.5.4 特殊表达式

| 表达式 | PG支持 | A支持 | AST节点 | 说明 |
|---------|--------|--------|---------|------|
| CASE WHEN | ✓ | ✓ | CaseWhen | 条件表达式 |
| COALESCE | ✓ | ✓ | FunctionCall | NULL替代 |
| CAST | ✓ | ✓ | CastOperation | 类型转换 |
| ::type | ✓ | - | CastOperation | PG特有 |
| EXISTS | ✓ | ✓ | Exists | 存在性检查 |
| ANY/ALL | ✓ | ⚠ | AnyAll | 量词比较 |
| LIKE | ✓ | ✓ | LikeOperation | 模式匹配 |
| SIMILAR TO | ✓ | - | SimilarTo | PG正则扩展 |
| POSIX正则 | ~, ~*, !~, !~* | - | POSIXRegex | PG正则 |

### 12.6 事务与锁语句

| 语句 | PG支持 | A支持 | 生成器 | 说明 |
|------|--------|--------|--------|------|
| BEGIN/START TRANSACTION | ✓ | ✓ | ⚠ | 开始事务 |
| COMMIT | ✓ | ✓ | ⚠ | 提交 |
| ROLLBACK | ✓ | ✓ | ⚠ | 回滚 |
| SAVEPOINT | ✓ | ✓ | ⚠ | 保存点 |
| SET TRANSACTION | ✓ | ⚠ | ⚠ | 设置隔离级别 |
| LOCK TABLE | ✓ | ✓ | ⚠ | 锁表 |
| SELECT FOR UPDATE | ✓ | ✓ | ⚠ | 锁定查询行 |

### 12.7 SET语句

| 语句 | PG支持 | A支持 | 生成器 | 说明 |
|------|--------|--------|--------|------|
| SET enable_seqscan = off | ✓ | ⚠ | ⚠ DQP使用 | 执行计划控制 |
| SET enable_indexscan = off | ✓ | ⚠ | ⚠ | 执行计划控制 |
| SET random_page_cost | ✓ | ⚠ | ⚠ | 成本参数 |
| SET search_path | ✓ | ⚠ | ✓ | Schema搜索路径 |

### 12.8 语法覆盖优先级矩阵

**P0（必须实现）**：
- 数据类型：INT, VARCHAR, BOOLEAN, DECIMAL
- DDL：CREATE TABLE, DROP TABLE, CREATE INDEX
- DML：SELECT, INSERT, UPDATE, DELETE
- 函数：COALESCE/NVL, CASE WHEN, ABS, LENGTH
- 表达式：比较、逻辑、算术

**P1（重要扩展）**：
- 数据类型：FLOAT, DOUBLE, DATE, TIMESTAMP, TEXT
- DDL：ALTER TABLE, CREATE VIEW, CREATE SEQUENCE
- DML：JOIN(多种), LIMIT/OFFSET, GROUP BY, HAVING
- 函数：LOWER, UPPER, ROUND, SUBSTRING
- 表达式：BETWEEN, IN, LIKE

**P2（可选扩展）**：
- 数据类型：JSON, UUID, ARRAY, BYTEA
- DDL：TRIGGER, PROCEDURE, PACKAGE(A)
- DML：MERGE(A), 窗口函数
- 函数：窗口函数, TO_DATE, TO_CHAR(A)
- 表达式：EXISTS, POSIX正则(PG)

---

## 十二、附录

### 11.1 参考文档

- SQLancer GitHub: https://github.com/sqlancer/sqlancer
- GaussDB开发指南: https://support.huaweicloud.com/centralized-devg-v8-gaussdb/
- PostgreSQL文档: https://www.postgresql.org/docs/
- Oracle SQL参考: https://docs.oracle.com/en/database/oracle/
- EET论文: OSDI 2024 - Jiang et al.
- TLP论文: OOPSLA 2020 - Rigger et al.
- NoREC论文: ESEC/FSE 2020 - Rigger et al.

### 11.2 现有模块对比

| 特性 | gaussdb(现有→重命名) | gaussdb-pg(目标) | gaussdb-a(目标) | postgres(参考) |
|------|---------------------|------------------|-----------------|----------------|
| Provider | ✓基础 | ✓完整 | ✓新建 | ✓完整 |
| GlobalState | ✓基础 | ✓完整 | ✓新建 | ✓完整 |
| Schema | ✓基础 | ✓完整(PG类型) | ✓新建(Oracle类型) | ✓完整 |
| AST层 | ✓部分 | ✓完整 | ✓新建 | ✓完整 |
| Constant | ✓部分 | ✓完整(空串≠NULL) | ✓新建(空串=NULL) | ✓完整 |
| ExpressionGenerator | ✓部分 | ✓完整 | ✓新建 | ✓完整 |
| TLP WHERE | - | 需实现 | 需实现 | ✓ |
| NoREC | - | 需实现 | 需实现 | ✓ |
| PQS | - | 需实现 | 需实现 | ✓ |
| CERT | - | 需实现 | 需实现 | ✓ |
| DQP | - | 需实现 | 需实现 | ✓ |
| DQE | - | 需实现 | 需实现 | ✓ |
| EET | - | 需实现 | 需实现 | ✓ |
| CODDTEST | - | 需实现 | 需实现 | ✓ |
| FUZZER | - | 需实现 | 需实现 | ✓ |

### 11.3 关键代码路径

| 文件 | 路径 | 作用 |
|------|------|------|
| DatabaseProvider | `src/sqlancer/DatabaseProvider.java` | Provider接口 |
| GlobalState | `src/sqlancer/GlobalState.java` | 状态基类 |
| TLPWhereOracle | `src/sqlancer/common/oracle/TLPWhereOracle.java` | TLP通用实现 |
| NoRECOracle | `src/sqlancer/common/oracle/NoRECOracle.java` | NoREC通用实现 |
| PostgresProvider | `src/sqlancer/postgres/PostgresProvider.java` | PG参考实现 |
| PostgresConstant | `src/sqlancer/postgres/ast/PostgresConstant.java` | PG Constant参考 |
| PostgresExpressionGenerator | `src/sqlancer/postgres/gen/PostgresExpressionGenerator.java` | PG表达式生成参考 |
| PostgresOracleFactory | `src/sqlancer/postgres/PostgresOracleFactory.java` | PG Oracle工厂参考 |
| gaussdb(现有) | `src/sqlancer/gaussdb/` | 需重命名为gaussdb-pg |
| gaussdbm | `src/sqlancer/gaussdbm/` | M兼容参考实现 |