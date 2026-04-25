步骤一：编译
set PATH=D:\tools\dev\apache-maven-3.9.13-bin\apache-maven-3.9.13\bin;%PATH%
mvn -version
cd D:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main\target>
mvn clean package -DskipTests
步骤二：压测调试
cd target
D:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main\target>java -jar sqlancer-2.0.0.jar --host 192.168.95.195 --port 8000 --username tpcc --password Taurus@123 --database-prefix tb --num-tries 1 --timeout-seconds 100 --num-threads 2 gaussdb-a  --oracle  "test_oracle" 