# Local JDBC Drivers

This directory contains local JDBC drivers for database connections.

## GaussDB Driver

### opengauss-jdbc-6.0.2-og.jar
- **Source**: openGauss official JDBC driver
- **Driver class**: `org.opengauss.Driver`
- **JDBC URL format**: `jdbc:opengauss://host:port/database`
- **Features**: Supports SM3/sha256 authentication for all GaussDB variants (A/M/PG)

## Usage

The driver is automatically included in the classpath when building with Maven.
The jar will be copied to `target/lib/` during the package phase.

## Portability

This project uses local driver jars for maximum portability:

1. **Copy entire project** to new environment - includes lib/ directory
2. **Build**: `mvn clean package -DskipTests`
3. **Run**: `java -cp "target/sqlancer-2.0.0.jar:target/lib/*" sqlancer.Main ...`

No Maven central repository download required for GaussDB drivers.

## Directory Structure

```
project-root/
├── lib/
│   ├── opengauss-jdbc-6.0.2-og.jar  <- GaussDB driver (REQUIRED)
│   └── README.md
├── pom.xml
├── src/
└── target/
    ├── sqlancer-2.0.0.jar
    └── lib/
        └── opengauss-jdbc-6.0.2-og.jar  <- copied during build
```

## Quick Start for New Environment

```bash
# 1. Copy entire project directory
scp -r sqlancer-main/ user@new-server:/path/to/

# 2. Build (no network required for GaussDB driver)
cd /path/to/sqlancer-main
mvn clean package -DskipTests

# 3. Test GaussDB-A
java -cp "target/sqlancer-2.0.0.jar:target/lib/*" sqlancer.Main \
  --username tpcc --password "xxx" --host xxx --port 8000 \
  gaussdb-a --oracle AGGREGATE

# 4. Test GaussDB-M
java -cp "target/sqlancer-2.0.0.jar:target/lib/*" sqlancer.Main \
  --username xxx --password "xxx" --host xxx --port xxx \
  gaussdb-m --oracle AGGREGATE
```