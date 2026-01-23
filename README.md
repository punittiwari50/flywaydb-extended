# Flyway Rollback Extension

A comprehensive rollback solution for Flyway Community Edition with audit tracking and result output persistence.

## Features

✅ **Custom Rollback Functionality** - Roll back migrations using undo scripts (U-prefixed files)
✅ **Audit Tracking** - Updates `flyway_schema_history` entries with `ROLLBACK` type
✅ **Configurable** - All settings via standard Flyway configuration  

## Quick Start

### 1. Build the Project

```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended
mvnw.cmd clean package
```

### 2. Run the Example

```bash
java -jar target\flyway-extended-1.0-SNAPSHOT.jar
```

## 🚀 Simple H2 Example (Recommended for First-Time Users)

### Run with H2 (Default/In-Memory)
```bash
mvnw.cmd -pl flyway-extended-demo spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1 --spring.datasource.username=sa --spring.datasource.password= --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
```

### Run with PostgreSQL Profile
```bash
mvnw.cmd -pl flyway-extended-demo spring-boot:run -Dspring-boot.run.profiles=postgresql
```

### Run with Oracle Profile
```bash
mvnw.cmd -pl flyway-extended-demo spring-boot:run -Dspring-boot.run.profiles=oracle
```

### Performing Rollback via CLI
The demo application now supports rollback via command-line arguments:

```bash
# Rollback last version (automatically after migration)
mvnw.cmd -pl flyway-extended-demo spring-boot:run -Dspring-boot.run.profiles=postgresql -Dspring-boot.run.arguments="--rollback"

# Rollback to specific version
mvnw.cmd -pl flyway-extended-demo spring-boot:run -Dspring-boot.run.profiles=postgresql -Dspring-boot.run.arguments="--rollback version=1.0"
```

**What it demonstrates:**
- ✅ H2 in-memory database (no setup required)

- ✅ Schema enhancement (one-time audit column addition)
- ✅ Forward migrations (V1, V2)
- ✅ Rollback operation (V2 → V1)
- ✅ Audit tracking (who, when, why)
- ✅ Result output (JSON file)

**See**: [SIMPLE_H2_EXAMPLE.md](SIMPLE_H2_EXAMPLE.md) for detailed guide and expected output.

---

## Project Structure

```
flyway-extended/
├── src/main/java/org/flywaydbextended/core/
│   └── FlywayExtended.java                  # Extended Flyway class with rollback logic
├── src/test/resources/db/migration/
│   ├── V1__Init.sql                         # Forward migration
│   ├── U1__Init.sql                         # Undo migration
│   ├── ...                                  # Other migrations
│   ├── beforeRollback.sql                   # Pre-rollback callback
│   └── afterRollback.sql                    # Post-rollback callback
├── flyway-extended-demo/                    # Demo application
├── README.md                                # This file
└── ...
```

## Components

### Core
- **FlywayExtended**: Wrapper around standard `Flyway` instance providing extended capabilities like `rollback()`.

### key Features
- **Smart Rollback**: Automatically detects the last successful version to rollback.
- **Undo Scripts**: Looks for `U<version>__<description>.sql` matching the deployed version.
- **Callbacks**: Supports `beforeRollback.sql` and `afterRollback.sql` hooks.
- **State Management**: Updates `flyway_schema_history` to reflect `ROLLBACK` state.


## Usage

### Basic Rollback

```java
import org.flywaydb.core.Flyway;
import org.flywaydbextended.core.FlywayExtended;

// 1. Configure standard Flyway
Flyway flyway = Flyway.configure()
    .dataSource("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "")
    .locations("classpath:db/migration")
    .load();

// 2. Initialize FlywayExtended
FlywayExtended flywayExtended = new FlywayExtended(flyway);

// 3. Perform Rollback
// Rollback the last successful version (e.g. V2 -> V1)
flywayExtended.rollback();

// OR Rollback to a specific target version (rolling back ONLY that version logic)
// Note: This command rolls back the targeted version, executing its U script.
flywayExtended.rollback("1.2");
```

### Callbacks
You can include `beforeRollback.sql` and `afterRollback.sql` in your migration locations. These will be executed before and after the rollback script respectively.


### Configuration

The Rollback command leverages standard Flyway configuration.

```properties
# standard flyway configuration
flyway.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
flyway.user=sa
flyway.password=
flyway.locations=classpath:db/migration
```

**Note**: The rollback command uses the same data source and locations as the configured Flyway instance.

## Database Configuration Examples

### PostgreSQL
```properties
flyway.url=jdbc:postgresql://localhost:5432/postgres
flyway.user=postgres
flyway.password=postgres
flyway.locations=classpath:db/migration/postgresql
```

### Oracle
```properties
flyway.url=jdbc:oracle:thin:@localhost:1521:XE
flyway.user=system
flyway.password=mysecretpassword
flyway.locations=classpath:db/migration/oracle
```


## Migration File Naming

- **Forward migrations**: `V{version}__{description}.sql`
  - Example: `V1__create_users_table.sql`
  
- **Undo migrations**: `U{version}__{description}.sql` (Same description as V script)
  - Example: `U1__create_users_table.sql`
  - Note: The description part matches the migration description stored in history.


## Database Schema

After enhancement, `flyway_schema_history` table includes:

| Column | Type | Description |
|--------|------|-------------|
| rolled_back | BOOLEAN | Whether migration was rolled back |
| rollback_date | TIMESTAMP | When rollback occurred |
| rollback_user | VARCHAR(100) | Who performed rollback |
| rollback_reason | VARCHAR(500) | Why rollback was performed |

## Documentation

- **[FLYWAY_EXTENSIONS_GUIDE.md](FLYWAY_EXTENSIONS_GUIDE.md)** - Explains all 8 Flyway extension points
- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** - Detailed implementation plan and architecture
- **[SIMPLE_H2_EXAMPLE.md](SIMPLE_H2_EXAMPLE.md)** - Simple H2 database example (recommended start)
- **[SQL_OUTPUT_LOGGING_GUIDE.md](SQL_OUTPUT_LOGGING_GUIDE.md)** - ⭐ **How to log SQL for existing & custom commands**
- **[COMMAND_EXTENSION_GUIDE.md](COMMAND_EXTENSION_GUIDE.md)** - ⭐ **Add custom commands to Flyway CLI**
- **[SPI_SERVICES_GUIDE.md](SPI_SERVICES_GUIDE.md)** - ⭐ **META-INF/services explained**
- **[SCHEMA_COLUMN_GUIDE.md](SCHEMA_COLUMN_GUIDE.md)** - Best practices for adding columns to flyway_schema_history
- **[STATEMENT_INTERCEPTOR_GUIDE.md](STATEMENT_INTERCEPTOR_GUIDE.md)** - StatementInterceptor usage and value

## Important Notes

> **⚠️ Flyway Community Edition**: This is a custom implementation. Flyway Community Edition does not include built-in rollback.

> **⚠️ Manual Undo Scripts**: You must create undo scripts manually for each migration. Incorrect undo scripts can cause data loss.

> **⚠️ Schema Changes**: Adding audit columns to `flyway_schema_history` is a one-way change.

## Requirements

- Java 17 or higher
- Flyway 11.2.0 or higher
- Maven (for building)

## License

This is an example implementation for educational purposes.

## Author

Created as part of Flyway extension demonstration.
