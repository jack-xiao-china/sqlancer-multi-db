@echo off
cd /d "d:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main"
echo Step 1: Building SQLancer...
mvn clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo Build failed. Ensure Maven is installed and in PATH.
    exit /b 1
)
echo Step 2: Running QUERY_PARTITIONING test...
cd target
java -jar sqlancer-2.0.0.jar --host localhost --port 3306 --username root --password password --num-tries 9 --timeout-seconds 300 --num-threads 2 mysql --oracle QUERY_PARTITIONING
echo Exit code: %ERRORLEVEL%
