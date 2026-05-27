# EET工具扩展逻辑错误检测方法可行性评估报告

**报告日期**: 2026-05-09
**评估对象**: 基于EET (继承SQLsmith) 扩展其他Oracle方法
**评估范围**: EDC、SONAR、DQP、TLP、NoREC、WRITE_CHECK

---

## 1. EET架构分析

### 1.1 EET继承自SQLsmith的核心模块

| 模块 | 文件 | 功能 | 扩展价值 |
|------|------|------|----------|
| **Schema模块** | schema.hh/cc | 数据库schema信息（表、列、索引、函数） | ⭐⭐⭐⭐⭐ 基础设施完备 |
| **Grammar模块** | grammar.hh/cc | SQL语法生成（SELECT/JOIN/CTE/UNION） | ⭐⭐⭐⭐⭐ 复杂查询生成能力强 |
| **Value Expr模块** | value_expr/ | 表达式生成与变换（CASE/LIKE/子查询） | ⭐⭐⭐⭐⭐ EET核心优势 |
| **DUT模块** | dut.hh/cc + mysql.cc/postgres.cc | 测试设备接口（执行/备份/恢复） | ⭐⭐⭐⭐⭐ 关键基础设施 |
| **Rel Model模块** | relmodel.hh | 关系模型（表、列、类型） | ⭐⭐⭐⭐ 类型系统完整 |
| **General Process模块** | general_process.hh/cc | 测试流程控制 | ⭐⭐⭐⭐ 可扩展框架 |

### 1.2 EET已有基础设施

**数据库管理功能** (general_process.hh):
```cpp
// 数据库备份恢复
void dut_backup(dbms_info& d_info);           // 创建备份
void dut_reset(dbms_info& d_info);            // 重置数据库
void dut_reset_to_backup(dbms_info& d_info);  // 恢复到备份点

// 数据库内容获取
void dut_get_content(dbms_info& d_info, 
    map<string, vector<vector<string>>>& content);  // 获取所有表内容

// 内容比较
bool compare_content(map<string, vector<vector<string>>>&a_content, 
                     map<string, vector<vector<string>>>&b_content);

// 查询生成
int generate_database(dbms_info& d_info, int t_num);
void gen_stmts_for_one_txn(shared_ptr<schema> &db_schema, ...);
```

**MySQL/PostgreSQL实现** (mysql.cc/postgres.cc):
```cpp
void dut_mysql::backup(void) {
    string backup_name = "/tmp/" + test_db + "_bk.sql";
    string mysql_dump = "mysqldump ... > " + backup_name;
    system(mysql_dump.c_str());
}

void dut_mysql::reset_to_backup(void) {
    reset();
    string restore_cmd = "mysql ... < " + backup_name;
    system(restore_cmd.c_str());
}

void dut_mysql::get_content(vector<string>& tables_name, 
                            map<string, vector<vector<string>>>& content) {
    for (auto& table : tables_name) {
        string query = "SELECT * FROM " + table;
        // 执行查询，填充content
    }
}
```

### 1.3 EET架构优势

| 优势 | 说明 |
|------|------|
| **C++高性能** | 查询生成速度快，适合大规模测试 |
| **SQLsmith成熟框架** | SQL语法生成经过多年验证 |
| **模块化设计** | Schema、Grammar、DUT分离，易于扩展 |
| **类型系统完整** | sqltype、routine、op支持复杂类型推断 |
| **DBMS抽象层** | dut_base接口统一，多DBMS支持 |
| **备份恢复基础设施** | 已实现，可直接用于EDC |
| **内容获取基础设施** | 已实现，可直接用于EDC |

### 1.4 EET架构劣势

| 劣势 | 说明 |
|------|------|
| **单一Oracle** | 仅支持EET方法，缺少其他验证逻辑 |
| **缺少事务测试** | 无并发事务调度框架 |
| **缺少优化器控制** | 无Hints、Optimizer Variables接口 |
| **缺少WHERE分区逻辑** | 无TLP三元逻辑分区 |
| **缺少COUNT对比逻辑** | 无NoREC COUNT验证 |
| **C++扩展复杂度高** | 相比Java，新功能开发更难 |
| **缺少多线程测试** | 单进程执行，效率受限 |

---

## 2. 各Oracle扩展可行性分析

### 2.1 EDC (Equivalent Database Construction) Oracle

**EDC原理**:
```
1. 在原始数据库执行查询 Q，获取结果 R1
2. 构建等价数据库 DB_raw (只有数据，无约束/索引)
3. 在 DB_raw 执行相同查询 Q，获取结果 R2
4. 对比 R1 和 R2 - 若不同，则约束/索引处理有Bug
```

**EET扩展EDC可行性**: ⭐⭐⭐⭐⭐ (极高)

**EET已有基础设施**:
| 功能 | EET现状 | EDC需求 | 匹配度 |
|------|----------|---------|--------|
| **数据库备份** | dut_backup() ✅ | 需要完整备份 | ✅ 已有 |
| **数据库恢复** | dut_reset_to_backup() ✅ | 需要恢复到备份点 | ✅ 已有 |
| **获取表内容** | dut_get_content() ✅ | 需要获取所有表数据 | ✅ 已有 |
| **内容比较** | compare_content() ✅ | 需要对比数据库状态 | ✅ 已有 |
| **查询生成** | grammar.cc ✅ | 需要生成测试查询 | ✅ 已有 |

**需要新增的功能**:
| 功能 | 开发难度 | 说明 |
|------|----------|------|
| **创建原始数据库** | 中 | 需要创建无约束/索引的表 |
| **批量数据复制** | 低 | 基于已有get_content() |
| **EDC验证逻辑** | 中 | 新增EDCOracle类 |

**EDC扩展代码示意**:
```cpp
class EDCOracle {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // Step 1: 执行原始查询
        string query = generate_query(db_schema);
        vector<vector<string>> original_result;
        dut->test(query, &original_result);
        
        // Step 2: 创建原始数据库
        string raw_db_name = create_raw_database(db_schema, dut);
        
        // Step 3: 复制数据到原始数据库
        copy_data_to_raw_db(db_schema, dut, raw_db_name);
        
        // Step 4: 在原始数据库执行查询
        vector<vector<string>> raw_result;
        dut->test_in_db(query, raw_db_name, &raw_result);
        
        // Step 5: 对比结果
        if (!compare_results(original_result, raw_result)) {
            report_bug(query, original_result, raw_result);
        }
    }
};
```

### 2.2 SONAR (Select Optimization N-gram Analysis Runtime) Oracle

**SONAR原理**:
```
1. 生成优化查询: SELECT ... WHERE condition
2. 生成非优化查询: SELECT ... FROM (SELECT ..., condition IS TRUE AS flag FROM ...) WHERE flag=1
3. 对比结果 - 若不同，优化器有Bug
```

**EET扩展SONAR可行性**: ⭐⭐⭐⭐ (高)

**EET已有基础设施**:
| 功能 | EET现状 | SONAR需求 | 匹配度 |
|------|----------|-----------|--------|
| **查询生成** | grammar.cc ✅ | 需要生成SELECT | ✅ 已有 |
| **表达式变换** | value_expr ✅ | 需要条件提取 | ✅ 可扩展 |
| **结果比较** | compare_output() ✅ | 需要结果对比 | ✅ 已有 |
| **窗口函数生成** | window_function.hh ✅ | SONAR常用窗口函数 | ✅ 已有 |

**需要新增的功能**:
| 功能 | 开发难度 | 说明 |
|------|----------|------|
| **非优化查询生成** | 中 | 将WHERE条件提取到外层查询 |
| **flag字段生成** | 低 | condition IS TRUE AS flag |
| **外层过滤逻辑** | 低 | WHERE flag=1 |

**SONAR扩展代码示意**:
```cpp
class SONAROracle {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // Step 1: 生成优化查询
        shared_ptr<query_spec> optimized_query = generate_query(db_schema);
        MySQLExpression where_condition = optimized_query->search;  // 获取WHERE条件
        
        // Step 2: 构造非优化查询
        // 内层: SELECT ..., (condition IS TRUE) AS flag FROM ...
        shared_ptr<query_spec> inner_query = clone_query(optimized_query);
        inner_query->search = nullptr;  // 移除WHERE
        // 添加flag列: condition IS TRUE AS flag
        add_flag_column(inner_query, where_condition);
        
        // 外层: SELECT ... FROM (inner_query) WHERE flag=1
        shared_ptr<query_spec> unoptimized_query = wrap_with_outer_query(inner_query);
        
        // Step 3: 执行并对比
        vector<vector<string>> opt_result = execute(dut, optimized_query);
        vector<vector<string>> unopt_result = execute(dut, unoptimized_query);
        
        if (!compare_output(opt_result, unopt_result)) {
            report_bug(optimized_query, unoptimized_query);
        }
    }
};
```

### 2.3 DQP (Differential Query Plans) Oracle

**DQP原理**:
```
1. 生成查询 Q
2. 执行 Q 获取结果 R1
3. 使用不同Hints/Optimizer Variables执行 Q，获取结果 R2...Rn
4. 对比所有结果 - 若不同，优化器配置影响语义，有Bug
```

**EET扩展DQP可行性**: ⭐⭐⭐ (中等)

**EET已有基础设施**:
| 功能 | EET现状 | DQP需求 | 匹配度 |
|------|----------|---------|--------|
| **查询生成** | grammar.cc ✅ | 需要生成SELECT | ✅ 已有 |
| **结果比较** | compare_output() ✅ | 需要多次对比 | ✅ 已有 |
| **Hints支持** | ❌ 无 | 需要 MySQL Hints | ❌ 需新增 |
| **Optimizer Variables** | ❌ 无 | 需要 SET命令支持 | ❌ 需新增 |

**需要新增的功能**:
| 功能 | 开发难度 | 说明 |
|------|----------|------|
| **Hints生成** | 中 | STRAIGHT_JOIN, INDEX hints等 |
| **Optimizer Variables** | 高 | SET optimizer_switch='...' |
| **多次执行框架** | 低 | 循环执行+对比 |

**DQP扩展代码示意**:
```cpp
class DQPOracle {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // Step 1: 生成原始查询
        shared_ptr<query_spec> query = generate_query(db_schema);
        string original_sql = print_query(query);
        
        // Step 2: 执行原始查询
        vector<vector<string>> original_result = execute(dut, original_sql);
        
        // Step 3: 生成Hints列表
        vector<string> hints = generate_hints(query);  // 新增功能
        
        // Step 4: 测试每个Hint
        for (auto& hint : hints) {
            string hinted_sql = add_hint(original_sql, hint);
            vector<vector<string>> result = execute(dut, hinted_sql);
            if (!compare_output(original_result, result)) {
                report_bug(original_sql, hinted_sql, hint);
            }
        }
        
        // Step 5: 测试Optimizer Variables (新增功能)
        vector<string> optimizer_vars = get_optimizer_variables();
        for (auto& var : optimizer_vars) {
            dut->test("SET " + var);  // 设置变量
            vector<vector<string>> result = execute(dut, original_sql);
            if (!compare_output(original_result, result)) {
                report_bug(original_sql, var);
            }
            dut->reset();  // 恢复默认设置
        }
    }
};
```

### 2.4 TLP (Ternary Logic Partitioning) Oracle

**TLP原理**:
```
1. 原始查询: SELECT ... WHERE condition → 结果 R
2. 分区查询:
   - SELECT ... WHERE condition IS TRUE → R_true
   - SELECT ... WHERE condition IS FALSE → R_false
   - SELECT ... WHERE condition IS NULL → R_null
3. 验证: R = R_true ∪ R_false ∪ R_null
```

**EET扩展TLP可行性**: ⭐⭐⭐⭐ (高)

**EET已有基础设施**:
| 功能 | EET现状 | TLP需求 | 匹配度 |
|------|----------|---------|--------|
| **查询生成** | grammar.cc ✅ | 需要生成SELECT | ✅ 已有 |
| **WHERE条件提取** | query_spec->search ✅ | 需要获取WHERE | ✅ 已有 |
| **结果比较** | compare_output() ✅ | 需要结果合并验证 | ✅ 可扩展 |
| **IS TRUE/IS FALSE** | bool_expr支持 ✅ | 需要NULL判断表达式 | ✅ 已有 |

**需要新增的功能**:
| 功能 | 开发难度 | 说明 |
|------|----------|------|
| **分区查询生成** | 低 | 复制原始查询，修改WHERE |
| **结果合并验证** | 中 | R_true + R_false + R_null = R |

**TLP扩展代码示意**:
```cpp
class TLPOracle {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // Step 1: 生成原始查询
        shared_ptr<query_spec> original_query = generate_query(db_schema);
        MySQLExpression condition = original_query->search;
        
        // Step 2: 执行原始查询
        vector<vector<string>> original_result = execute(dut, original_query);
        
        // Step 3: 生成分区查询
        // IS TRUE
        shared_ptr<query_spec> true_query = clone_query(original_query);
        true_query->search = new is_true_expr(condition);
        vector<vector<string>> true_result = execute(dut, true_query);
        
        // IS FALSE
        shared_ptr<query_spec> false_query = clone_query(original_query);
        false_query->search = new is_false_expr(condition);
        vector<vector<string>> false_result = execute(dut, false_query);
        
        // IS NULL
        shared_ptr<query_spec> null_query = clone_query(original_query);
        null_query->search = new is_null_expr(condition);
        vector<vector<string>> null_result = execute(dut, null_query);
        
        // Step 4: 验证合并
        vector<vector<string>> merged_result = merge_results(true_result, false_result, null_result);
        if (!compare_output(original_result, merged_result)) {
            report_bug(condition, original_result, merged_result);
        }
    }
};
```

### 2.5 NoREC (Non-Optimizing Reference Engine) Oracle

**NoREC原理**:
```
1. 优化查询: SELECT COUNT(*) FROM t WHERE condition → count1
2. 非优化查询: SELECT SUM(CASE WHEN condition THEN 1 ELSE 0 END) FROM t → count2
3. 对比: count1 == count2
```

**EET扩展NoREC可行性**: ⭐⭐⭐⭐ (高)

**EET已有基础设施**:
| 功能 | EET现状 | NoREC需求 | 匹配度 |
|------|----------|-----------|--------|
| **COUNT生成** | aggregates支持 ✅ | COUNT(*) | ✅ 已有 |
| **CASE WHEN生成** | case_expr.hh ✅ | CASE WHEN condition THEN 1 ELSE 0 | ✅ 已有 |
| **SUM生成** | aggregates支持 ✅ | SUM(...) | ✅ 已有 |
| **结果比较** | compare_output() ✅ | 数值对比 | ✅ 已有 |

**需要新增的功能**:
| 功能 | 开发难度 | 说明 |
|------|----------|------|
| **非优化查询生成** | 低 | CASE WHEN包装 |
| **数值结果提取** | 低 | 提取COUNT/SUM结果 |

**NoREC扩展代码示意**:
```cpp
class NoRECOracle {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // Step 1: 生成优化查询
        // SELECT COUNT(*) FROM t WHERE condition
        shared_ptr<query_spec> optimized_query = generate_count_query(db_schema);
        MySQLExpression condition = optimized_query->search;
        
        // Step 2: 执行优化查询
        vector<vector<string>> opt_result = execute(dut, optimized_query);
        int count1 = extract_count(opt_result);
        
        // Step 3: 生成非优化查询
        // SELECT SUM(CASE WHEN condition THEN 1 ELSE 0 END) FROM t
        shared_ptr<query_spec> unoptimized_query = generate_sum_case_query(db_schema, condition);
        
        // Step 4: 执行非优化查询
        vector<vector<string>> unopt_result = execute(dut, unoptimized_query);
        int count2 = extract_sum(unopt_result);
        
        // Step 5: 对比
        if (count1 != count2) {
            report_bug(condition, count1, count2);
        }
    }
};
```

### 2.6 WRITE_CHECK (Transaction Testing) Oracle

**WRITE_CHECK原理**:
```
1. 生成多个并发事务 (INSERT/UPDATE/DELETE)
2. 生成随机事务调度 (语句交错顺序)
3. 执行调度，获取最终数据库状态
4. 重放调度，对比数据库状态
5. 若状态不同，事务隔离级别有Bug
```

**EET扩展WRITE_CHECK可行性**: ⭐⭐ (低)

**EET已有基础设施**:
| 功能 | EET现状 | WRITE_CHECK需求 | 匹配度 |
|------|----------|-----------------|--------|
| **数据库备份恢复** | dut_backup/reset ✅ | 需要重置到初始状态 | ✅ 已有 |
| **内容获取** | dut_get_content() ✅ | 需要对比最终状态 | ✅ 已有 |
| **事务语句生成** | ❌ 无 | 需要 BEGIN/COMMIT/ROLLBACK | ❌ 需新增 |
| **并发调度框架** | ❌ 无 | 需要多线程/多进程执行 | ❌ 需新增 |
| **DML语句生成** | ❌ 无 | 需要 INSERT/UPDATE/DELETE | ❌ 需新增 |

**需要新增的功能**:
| 功能 | 开发难度 | 说明 |
|------|----------|------|
| **事务语句生成** | 高 | BEGIN/COMMIT/ROLLBACK生成 |
| **DML语句生成** | 高 | INSERT/UPDATE/DELETE生成 |
| **并发调度框架** | 极高 | 多进程执行+语句交错 |
| **隔离级别设置** | 中 | SET TRANSACTION ISOLATION LEVEL |
| **连接池管理** | 高 | 多连接并发执行 |

**WRITE_CHECK扩展代码示意**:
```cpp
class WriteCheckOracle {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // Step 1: 生成多个事务
        vector<vector<string>> transactions = generate_transactions(db_schema);  // 新增
        
        // Step 2: 生成随机调度
        vector<string> schedule = generate_schedule(transactions);  // 新增
        
        // Step 3: 执行调度
        dut_backup(d_info);  // 备份初始状态
        execute_schedule(dut, schedule);  // 新增：并发执行
        map<string, vector<vector<string>>> final_state;
        dut_get_content(d_info, final_state);  // 获取最终状态
        
        // Step 4: 重放调度
        dut_reset_to_backup(d_info);  // 恢复初始状态
        execute_schedule(dut, schedule);  // 再次执行
        map<string, vector<vector<string>>> replay_state;
        dut_get_content(d_info, replay_state);
        
        // Step 5: 对比
        if (!compare_content(final_state, replay_state)) {
            report_bug(schedule, final_state, replay_state);
        }
    }
};
```

---

## 3. 综合可行性评分

### 3.1 各Oracle扩展难度评分

| Oracle | 基础设施匹配 | 新增功能难度 | 开发工作量 | 综合可行性 |
|--------|------------|--------------|------------|------------|
| **EDC** | ⭐⭐⭐⭐⭐ | ⭐⭐ | 2-3周 | ⭐⭐⭐⭐⭐ 极高 |
| **SONAR** | ⭐⭐⭐⭐ | ⭐⭐⭐ | 3-4周 | ⭐⭐⭐⭐ 高 |
| **TLP** | ⭐⭐⭐⭐ | ⭐⭐ | 2-3周 | ⭐⭐⭐⭐ 高 |
| **NoREC** | ⭐⭐⭐⭐ | ⭐⭐ | 2-3周 | ⭐⭐⭐⭐ 高 |
| **DQP** | ⭐⭐ | ⭐⭐⭐⭐ | 4-5周 | ⭐⭐⭐ 中等 |
| **WRITE_CHECK** | ⭐ | ⭐⭐⭐⭐⭐ | 6-8周 | ⭐⭐ 低 |

### 3.2 扩展ROI (投资回报率) 评估

| Oracle | 开发投入 | Bug检测潜力 | 已验证Bug数 | ROI评分 |
|--------|----------|-------------|-------------|---------|
| **EDC** | 2-3周 | 高 (约束/索引Bug) | SQLancer已发现多个 | ⭐⭐⭐⭐⭐ |
| **SONAR** | 3-4周 | 高 (优化器Bug) | SQLancer SONAR有效 | ⭐⭐⭐⭐ |
| **TLP** | 2-3周 | 高 (WHERE逻辑Bug) | SQLancer TLP系列有效 | ⭐⭐⭐⭐⭐ |
| **NoREC** | 2-3周 | 高 (优化器Bug) | SQLancer NoREC有效 | ⭐⭐⭐⭐ |
| **DQP** | 4-5周 | 中 (优化器配置Bug) | SQLancer DQP有效 | ⭐⭐⭐ |
| **WRITE_CHECK** | 6-8周 | 高 (隔离级别Bug) | GaussDB-M已发现Bug | ⭐⭐⭐ |

---

## 4. 扩展方案

### 方案一：轻量级扩展方案 (推荐)

**目标**: 在EET框架基础上，快速扩展EDC + SONAR + TLP三个高ROI Oracle

**实施策略**:
1. **优先扩展EDC**: 利用已有backup/reset/get_content基础设施
2. **扩展SONAR**: 利用已有表达式变换框架
3. **扩展TLP**: 利用已有WHERE条件提取能力

**架构设计**:
```cpp
// 新增Oracle基类
class OracleBase {
public:
    virtual void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) = 0;
    virtual string get_name() = 0;
};

// Oracle工厂
class OracleFactory {
public:
    static shared_ptr<OracleBase> create(string oracle_name) {
        if (oracle_name == "EET") return make_shared<EETOracle>();
        if (oracle_name == "EDC") return make_shared<EDCOracle>();
        if (oracle_name == "SONAR") return make_shared<SONAROracle>();
        if (oracle_name == "TLP") return make_shared<TLPOracle>();
        // ...
    }
};

// 主测试流程
void run_test(dbms_info& d_info, vector<string> oracle_names) {
    shared_ptr<schema> db_schema = get_schema(d_info);
    shared_ptr<dut_base> dut = dut_setup(d_info);
    
    for (auto& oracle_name : oracle_names) {
        shared_ptr<OracleBase> oracle = OracleFactory::create(oracle_name);
        oracle->check(db_schema, dut);
    }
}
```

**文件结构**:
```
EET-main/
├── oracle/               # 新增目录
│   ├── oracle_base.hh
│   ├── oracle_base.cc
│   ├── oracle_factory.hh
│   ├── edc_oracle.hh
│   ├── edc_oracle.cc      # EDC实现
│   ├── sonar_oracle.hh
│   ├── sonar_oracle.cc    # SONAR实现
│   ├── tlp_oracle.hh
│   ├── tlp_oracle.cc      # TLP实现
│   ├── norec_oracle.hh    # NoREC实现 (可选)
│   └── dqp_oracle.hh      # DQP实现 (可选)
├── value_expr/            # 已有，可复用
├── grammar.hh/cc          # 已有，可复用
├── dut.hh/cc              # 已有，可复用
└── general_process.hh/cc  # 已有，可复用
```

**开发时间表**:
| 周次 | 任务 | 产出 |
|------|------|------|
| Week 1 | OracleBase架构 + EDC实现 | EDCOracle可用 |
| Week 2 | SONAR实现 | SONAROracle可用 |
| Week 3 | TLP实现 | TLPOracle可用 |
| Week 4 | 集成测试 + Bug验证 | 3个Oracle全部可用 |

**优势**:
- 快速见效 (4周完成3个Oracle)
- 高ROI (EDC/TLP bug检测能力强)
- 低风险 (复用已有基础设施)
- 架构清晰 (Oracle工厂模式)

**劣势**:
- Oracle数量有限 (仅3-4个)
- WRITE_CHECK/DQP等复杂Oracle不实现
- 可能遗漏部分Bug类型

---

### 方案二：全面扩展方案

**目标**: 在EET框架基础上，扩展全部6种Oracle (EDC + SONAR + DQP + TLP + NoREC + WRITE_CHECK)

**实施策略**:
1. **Phase 1 (Week 1-4)**: EDC + SONAR + TLP + NoREC (轻量级Oracle)
2. **Phase 2 (Week 5-7)**: DQP (中等复杂度Oracle)
3. **Phase 3 (Week 8-12)**: WRITE_CHECK (高复杂度Oracle)

**架构设计**:
```cpp
// 多Oracle并行测试框架
class MultiOracleRunner {
public:
    void run(dbms_info& d_info, vector<string> oracle_names) {
        // 并行执行多个Oracle (多进程)
        vector<pid_t> processes;
        for (auto& oracle_name : oracle_names) {
            pid_t pid = fork();
            if (pid == 0) {
                // 子进程执行Oracle
                shared_ptr<OracleBase> oracle = OracleFactory::create(oracle_name);
                run_oracle_in_process(d_info, oracle);
                exit(0);
            }
            processes.push_back(pid);
        }
        
        // 等待所有进程完成
        for (auto& pid : processes) {
            waitpid(pid, NULL, 0);
        }
    }
};

// DQP扩展: Hints + Optimizer Variables
class HintGenerator {
public:
    vector<string> generate_hints(shared_ptr<query_spec>& query) {
        vector<string> hints;
        hints.push_back("STRAIGHT_JOIN");
        hints.push_back("INDEX(t0 idx1)");
        hints.push_back("NO_INDEX(t1)");
        // ...
        return hints;
    }
};

class OptimizerVarManager {
public:
    vector<string> get_optimizer_vars(string dbms_name) {
        if (dbms_name == "mysql") {
            return {"optimizer_switch='index_merge=off'",
                    "optimizer_switch='block_nested_loop=off'",
                    // ...
            };
        }
        // PostgreSQL, SQLite等
    }
};

// WRITE_CHECK扩展: 事务调度框架
class TransactionScheduler {
public:
    vector<string> generate_schedule(vector<vector<string>>& transactions) {
        // 随机交错事务语句
        vector<string> schedule;
        int num_tx = transactions.size();
        while (true) {
            int tx_id = random_int(0, num_tx);
            int stmt_id = get_next_stmt_id(transactions[tx_id]);
            if (stmt_id >= transactions[tx_id].size()) continue;
            schedule.push_back(format("tx%d-stmt%d", tx_id, stmt_id));
            if (all_transactions_complete(transactions)) break;
        }
        return schedule;
    }
};

class ConcurrentExecutor {
public:
    void execute_schedule(shared_ptr<dut_base>& dut, vector<string>& schedule) {
        // 多连接并发执行
        vector<shared_ptr<dut_base>> connections = create_connections(dut, num_tx);
        for (auto& stmt_tag : schedule) {
            int tx_id = parse_tx_id(stmt_tag);
            int stmt_id = parse_stmt_id(stmt_tag);
            string stmt = transactions[tx_id][stmt_id];
            connections[tx_id]->test(stmt);
        }
    }
};
```

**文件结构**:
```
EET-main/
├── oracle/               # Oracle目录
│   ├── oracle_base.hh/cc
│   ├── oracle_factory.hh/cc
│   ├── edc_oracle.hh/cc
│   ├── sonar_oracle.hh/cc
│   ├── tlp_oracle.hh/cc
│   ├── norec_oracle.hh/cc
│   ├── dqp_oracle.hh/cc
│   ├── writecheck_oracle.hh/cc
│   └── multi_oracle_runner.hh/cc
├── hints/                 # 新增: Hints生成
│   ├── hint_generator.hh/cc
│   └── optimizer_vars.hh/cc
├── transaction/           # 新增: 事务测试
│   ├── transaction_generator.hh/cc
│   ├── transaction_scheduler.hh/cc
│   ├── concurrent_executor.hh/cc
│   └── isolation_level.hh/cc
├── dml/                   # 新增: DML语句生成
│   ├── insert_generator.hh/cc
│   ├── update_generator.hh/cc
│   └── delete_generator.hh/cc
└── ...
```

**开发时间表**:
| 周次 | Phase | 任务 | 产出 |
|------|-------|------|------|
| Week 1-4 | Phase 1 | EDC + SONAR + TLP + NoREC | 4个轻量级Oracle可用 |
| Week 5-7 | Phase 2 | DQP + Hints + Optimizer | DQP可用 |
| Week 8-10 | Phase 3a | Transaction框架 + DML生成 | 事务基础设施可用 |
| Week 11-12 | Phase 3b | WRITE_CHECK实现 | WRITE_CHECK可用 |

**优势**:
- Oracle覆盖全面 (6种)
- Bug检测能力最强
- 可组合测试 (多Oracle并行)
- 架构完整 (Hints + Transaction)

**劣势**:
- 开发周期长 (12周)
- C++复杂度高 (并发/多进程)
- 风险高 (WRITE_CHECK复杂)
- 维护成本高

---

### 方案三：混合方案 (推荐折衷)

**目标**: 基于EET扩展核心Oracle，复杂Oracle借鉴SQLancer Java实现

**实施策略**:
1. **EET扩展**: EDC + SONAR + TLP + NoREC (C++实现，快速见效)
2. **SQLancer集成**: WRITE_CHECK (Java实现，复用SQLancer代码)
3. **DQP可选**: 根据需求决定C++或Java实现

**架构设计**:
```
[ EET C++ Core ]
├── EDC Oracle      (C++实现，复用backup/reset)
├── SONAR Oracle    (C++实现，复用表达式变换)
├── TLP Oracle      (C++实现，复用WHERE提取)
├── NoREC Oracle    (C++实现，复用CASE WHEN)
└── DQP Oracle      (C++或Java实现)

[ SQLancer Java Extension ]
├── WRITE_CHECK Oracle  (Java实现，复用SQLancer事务框架)
└── JDBC Bridge         (C++/Java互操作)
```

**跨语言桥接方案**:
```cpp
// C++端调用Java WRITE_CHECK
class WriteCheckBridge {
public:
    void check(shared_ptr<schema>& db_schema, shared_ptr<dut_base>& dut) {
        // 通过JNI调用Java SQLancer
        JNIEnv* jenv = get_jni_env();
        jclass writecheck_class = jenv->FindClass("sqlancer/mysql/oracle/transaction/MySQLWriteCheckOracle");
        jmethodID check_method = jenv->GetMethodID(writecheck_class, "check", "()V");
        jenv->CallVoidMethod(writecheck_obj, check_method);
    }
};
```

**或者采用进程间通信**:
```cpp
// C++端启动Java进程执行WRITE_CHECK
class WriteCheckBridge {
public:
    void check(dbms_info& d_info) {
        string cmd = "java -jar sqlancer.jar mysql --oracle=WRITE_CHECK";
        cmd += " --host=" + d_info.host_addr;
        cmd += " --port=" + to_string(d_info.test_port);
        cmd += " --database=" + d_info.test_db;
        
        system(cmd.c_str());
        // 解析Java输出的Bug报告
    }
};
```

**开发时间表**:
| 周次 | 任务 | 产出 |
|------|------|------|
| Week 1-3 | EDC + SONAR + TLP + NoREC (C++) | 4个Oracle可用 |
| Week 4 | DQP (可选) | DQP可用 |
| Week 5-6 | WRITE_CHECK桥接 (复用SQLancer) | WRITE_CHECK可用 |
| Week 7 | 集成测试 | 全部Oracle可用 |

**优势**:
- 开发周期适中 (7周)
- 复用现有代码 (SQLancer WRITE_CHECK)
- Oracle覆盖全面 (5-6种)
- 降低复杂度 (Java处理复杂事务)

**劣势**:
- 跨语言桥接复杂
- 需要维护两个项目
- 部署依赖增加 (Java环境)

---

## 5. 推荐方案

### 5.1 方案对比总结

| 方案 | Oracle数量 | 开发周期 | 复杂度 | Bug检测能力 | 推荐场景 |
|------|-----------|----------|--------|-------------|----------|
| **方案一 (轻量级)** | 3-4个 | 4周 | 低 | 高 (EDC/TLP核心) | 快速验证、原型开发 |
| **方案二 (全面)** | 6个 | 12周 | 极高 | 最高 | 全面测试、长期项目 |
| **方案三 (混合)** | 5-6个 | 7周 | 中 | 高 | 生产环境、折衷选择 |

### 5.2 推荐决策矩阵

| 需求 | 推荐方案 | 说明 |
|------|----------|------|
| **快速扩展** | 方案一 | 4周见效，优先EDC/TLP |
| **全面覆盖** | 方案二 | 12周完成全部Oracle |
| **生产部署** | 方案三 | 7周，复用成熟代码 |
| **资源有限** | 方案一 | 开发成本低 |
| **长期投入** | 方案二 | 架构完整，可扩展 |
| **风险规避** | 方案三 | 降低WRITE_CHECK复杂度 |

---

## 6. 风险评估

### 6.1 技术风险

| 风险项 | 影响 | 概率 | 缓解措施 |
|--------|------|------|----------|
| **C++并发Bug** | WRITE_CHECK执行失败 | 高 | 方案三用Java替代 |
| **跨语言桥接失败** | Oracle无法调用 | 中 | IPC通信替代JNI |
| **Schema不匹配** | 查询生成失败 | 低 | 兼容性测试 |
| **性能瓶颈** | 多Oracle执行慢 | 中 | 多进程并行 |

### 6.2 维护风险

| 风险项 | 影响 | 概率 | 缓解措施 |
|--------|------|------|----------|
| **EET上游更新** | 新功能不兼容 | 中 | 版本锁定 |
| **SQLancer上游更新** | WRITE_CHECK不兼容 | 中 | 定期同步 |
| **DBMS版本更新** | SQL语法变化 | 低 | 错误处理增强 |

---

## 7. 结论

### 7.1 最终推荐

**推荐方案**: **方案三 (混合方案)**

**理由**:
1. **开发周期适中** (7周)，快速见效
2. **Oracle覆盖全面** (5-6种)，Bug检测能力强
3. **降低复杂度**，复用SQLancer WRITE_CHECK
4. **风险可控**，避免C++并发复杂性

### 7.2 实施建议

1. **Phase 1 (Week 1-3)**: 优先实现 EDC + SONAR + TLP + NoREC
   - 这些Oracle基础设施完备，开发风险低
   - ROI高，Bug检测能力强

2. **Phase 2 (Week 4-5)**: DQP实现 (可选)
   - 根据需求决定C++或Java实现
   - 可跳过，优先Phase 3

3. **Phase 3 (Week 5-6)**: WRITE_CHECK桥接
   - 复用SQLancer Java实现
   - 通过进程间通信调用

4. **Phase 4 (Week 7)**: 集成测试
   - 多Oracle组合测试
   - Bug验证报告

### 7.3 预期产出

**Bug检测能力提升**:
- EDC: 约束/索引处理Bug (已有SQLancer验证案例)
- SONAR: 优化器语义Bug (窗口函数场景)
- TLP: WHERE逻辑Bug (NULL处理)
- NoREC: 优化器COUNT Bug
- WRITE_CHECK: 隔离级别Bug (GaussDB-M已发现)

**覆盖范围对比**:
| Oracle | EET扩展前 | EET扩展后 |
|--------|----------|----------|
| EET | ✅ | ✅ |
| EDC | ❌ | ✅ |
| SONAR | ❌ | ✅ |
| TLP | ❌ | ✅ |
| NoREC | ❌ | ✅ |
| DQP | ❌ | ✅ (可选) |
| WRITE_CHECK | ❌ | ✅ |

---

**报告结束**