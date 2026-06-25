# Local JDBC Drivers

This directory contains local JDBC drivers for database connections.

## GaussDB Driver

### opengauss-jdbc-6.0.2-og.jar
- **Source**: openGauss official JDBC driver
- **Driver class**: `org.opengauss.Driver`
- **JDBC URL format**: `jdbc:opengauss://host:port/database`
- **Features**: Supports SM3/sha256 authentication for all GaussDB variants (A/M/PG)

## Important: How to Run

**Maven `system` scope dependencies require explicit classpath specification.**

### Linux/macOS
```bash
# Build
mvn clean package -DskipTests

# Run (use -cp, NOT -jar)
java -cp "target/sqlancer-2.7.9.jar:target/lib/*" sqlancer.Main gaussdb-m ...
```

### Windows
```bash
# Build
mvn clean package -DskipTests

# Run (use -cp, NOT -jar)
java -cp "target/sqlancer-2.7.9.jar;target/lib/*" sqlancer.Main gaussdb-m ...
```

### Why NOT use `-jar`?
The `java -jar` command ignores external classpath. System scope dependencies
are not bundled into the jar, so you must use `-cp` to include `target/lib/*`.

## Portability

This project uses local driver jars for maximum portability:

1. **Copy entire project** to new environment - includes lib/ directory
2. **Build**: `mvn clean package -DskipTests`
3. **Run**: Use `-cp` with `target/lib/*` as shown above

## Directory Structure

```
project-root/
├── lib/
│   ├── opengauss-jdbc-6.0.2-og.jar  <- Source driver (REQUIRED)
│   └── README.md
├── pom.xml
├── src/
└── target/
    ├── sqlancer-2.7.9.jar           <- Main application
    └── lib/
        └── opengauss-jdbc-6.0.2-og.jar  <- Copied during build
```