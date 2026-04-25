# GaussDB-A Debug Guide

## 步骤一：编译

```bash
mvn clean package -DskipTests
```

## 步骤二：压测调试

```bash
cd target
java -jar sqlancer-2.0.0.jar --host localhost --port 8000 --username root --password password --database-prefix tb --num-tries 1 --timeout-seconds 100 --num-threads 2 gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING
```

**注意**: 需要先创建A兼容数据库：
```sql
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';
```