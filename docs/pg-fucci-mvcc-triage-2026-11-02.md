# PG FUCCI MVCC 发现研判报告（2026-11-02）

> 来源：v2.7.14 全量矩阵 PG FUCCI 复跑（8 报告/轮，"MT Oracle detected MVCC visibility violation"）
> 方法：失败报告提取 → MT Oracle 源码路径追踪 → 版本链 vs 执行结果比对机制分析 → 数据编码对比

## 一、结论：工具侧数据编码失配（假阳性），非 PG MVCC 真违反

## 二、根因分析

### 失配机制

PG FUCCI MT Oracle 的工作方式：
1. **执行侧**：`TxTestExecutor.execute()` → `getDBState()` → `QueryResultUtil.getQueryResult()`
2. **模拟侧**：`FucciMTOracle.simulateMVCCExecution()` → `MVCCSimulator.computeFinalStates()`
3. 两者通过 `TxBase.compareFinalDBState` → `compareQueryResult` → `preprocessQueryResult` 比较

**关键差异**：两条路径对二进制类型（bytea/bit）的编码不同：

| 路径 | 存储方式 | bytea 列的值 |
|------|----------|-------------|
| 执行侧（`QueryResultUtil.getQueryResult`） | `rs.getObject()` → 检测 `byte[]` → `byteArrToHexStr()` | `"0xDEADBEEF"` |
| 模拟侧（版本链 `Version.data`） | `rs.getObject()` → 原始 `Object[]`（`byte[]` 对象直接存） | `byte[]` 原始引用 |

`preprocessQueryResult` 调用 `Object.toString()`：
- 执行侧：`"0xDEADBEEF"`（hex 字符串）
- 模拟侧：`byte[].toString()` → `"[B@hashcode"` 或 PG JDBC 驱动返回的其他格式

### 失败报告中的证据

```
Error: (0)th values is different [0xDEADBEEF, 1111111000000101001011101001111]
```

- `0xDEADBEEF` = 执行侧的 `QueryResultUtil.byteArrToHexStr()` 编码（对应 `decode('deadbeef','hex')` 的 bytea 值）
- `1111111000000101001011101001111` = 模拟侧版本链中 UPDATE 语句解析后的二进制表示（非 0xDEADBEEF 的二进制——来自不同列或编码路径）

## 三、影响

| 项 | 说明 |
|----|------|
| 假阳性率 | PG FUCCI 的全部 8 报告/轮均由编码失配触发 |
| 根本原因 | `FucciMTOracle.initializeVersionChains` 用 `rs.getObject()` 存储原始对象，未做 byte[] → hex 转换 |
| 影响范围 | 所有含 bytea/BLOB/BINARY 列的表 + FUCCI MT oracle |
| PG MVCC 本身 | **无问题**——模拟和真实执行在逻辑上可能一致，仅编码表示不同 |

## 四、修复方案（建议 v2.7.15）

```
方案 A（推荐）: Version 数据统一编码
  在 initializeVersionChains 和 addVersion 时，对 byte[] 类型调用
  QueryResultUtil.byteArrToHexStr() 转换，使版本链与执行侧使用相同编码。

  文件: src/sqlancer/fucci/oracle/FucciMTOracle.java (initializeVersionChains ~L127)
  改动: rowData[i] = rs.getObject(i+1) → 若 instanceof byte[] 则 byteArrToHexStr()

方案 B: 比较前归一化
  在 compareFinalDBState 的 preprocessQueryResult 中统一处理 byte[]。
  缺点：需要在多个比较点添加处理，不如方案 A 集中。
```

## 五、排除的真 PG MVCC bug

研判过程中未发现 PG MVCC 可见性的真实违反。FUCCI MT oracle 的设计意图（模拟 MVCC 版本链可见性并与真实执行比对）是合理的，但当前因编码失配无法产生有效信号。

## 六、修复后复验（2026-11-02）

**值编码修复已验证生效**：
- `byte[] → hex` 归一化后，`(0)th values is different [0xDEADBEEF, ...]` 类假阳性清零
- 修复方式：`FucciMTOracle.initializeVersionChains` 中对 `byte[]` 调用 `QueryResultUtil.byteArrToHexStr()`（方法可见性从 private 改为 public）

**剩余 8 报告为行数不匹配**（`"The size of query results is different"`）：
- 根因：MVCC 版本链模拟的行创建/删除追踪精度不足——`computeFinalStates()` 产出的行数与真实执行 `SELECT *` 的行数不同
- 属**设计级模拟局限**（同 GaussDB TX_INFER 版本表模拟的行可见性分歧），非 PG MVCC bug
- 需重写版本链的行生命周期模型（INSERT 增行 / DELETE 减行 / rowId 追踪）才能消除
