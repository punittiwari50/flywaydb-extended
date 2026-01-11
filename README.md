# Flyway Rollback Extension

A comprehensive rollback solution for Flyway Community Edition with audit tracking and result output persistence.

## Features

✅ **Custom Rollback Functionality** - Roll back migrations using undo scripts (U-prefixed files)  
✅ **Audit Tracking** - Track rollback history in `flyway_schema_history` table  
✅ **Result Output Persistence** - Save operation results to JSON/XML/CSV files  
✅ **One-Time Schema Migration** - Safely add audit columns without duplication  
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

The easiest way to see all features in action:

```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended
mvnw.cmd clean compile
mvnw.cmd exec:java -Dexec.mainClass="com.example.flywayextended.examples.SimpleH2Example"
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
├── src/main/java/com/example/flywayextended/
│   ├── rollback/
│   │   ├── RollbackResult.java              # Result object for rollback operations
│   │   ├── RollbackConfigurationExtension.java  # Configuration extension
│   │   ├── RollbackMetadataTable.java       # Metadata table manager
│   │   ├── SchemaHistoryEnhancer.java       # One-time schema enhancement
│   │   └── RollbackService.java             # Core rollback service
│   ├── output/
│   │   ├── ResultOutputConfiguration.java   # Output configuration
│   │   ├── ResultOutputWriter.java          # Multi-format output writer
│   │   └── ResultOutputCallback.java        # Flyway callback for output
│   └── examples/
│       └── RollbackExample.java             # Complete working example
├── src/main/resources/db/migration/
│   ├── V1__create_users_table.sql           # Forward migration
│   ├── V2__create_orders_table.sql          # Forward migration
│   ├── U1__undo_create_users_table.sql      # Undo migration
│   └── U2__undo_create_orders_table.sql     # Undo migration
├── FLYWAY_EXTENSIONS_GUIDE.md               # Extension points guide
├── IMPLEMENTATION_PLAN.md                   # Detailed implementation plan
├── ENHANCED_CODE_EXAMPLES.md                # Complete code examples
└── README.md                                # This file
```

## Components

### Component 1: Core Rollback Infrastructure
- `RollbackResult` - Captures rollback operation metadata
- `RollbackConfigurationExtension` - Custom configuration parameters
- `RollbackMetadataTable` - Tracks one-time schema enhancements

### Component 2: Rollback Execution Service
- `RollbackService` - Core service for executing rollbacks
  - `rollback(targetVersion, user, reason)` - Roll back to specific version
  - `rollbackLast(count, user, reason)` - Roll back last N migrations

### Component 3: Schema History Enhancement
- `SchemaHistoryEnhancer` - Adds 4 audit columns to `flyway_schema_history`:
  - `rolled_back` (BOOLEAN) - Was this migration rolled back?
  - `rollback_date` (TIMESTAMP) - When was it rolled back?
  - `rollback_user` (VARCHAR) - Who performed the rollback?
  - `rollback_reason` (VARCHAR) - Why was it rolled back?

### Component 4: ResultSet Output Persistence
- `ResultOutputConfiguration` - Configure output saving
- `ResultOutputWriter` - Write results to JSON/XML/CSV
- `ResultOutputCallback` - Intercept Flyway operations

## Usage

### Basic Rollback

```java
// Configure Flyway
Flyway flyway = Flyway.configure()
    .dataSource("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "")
    .locations("classpath:db/migration")
    .load();

// Enhance schema history (one-time)
try (Connection conn = dataSource.getConnection()) {
    SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();
    enhancer.enhanceSchemaHistory(conn, "admin");
}

// Perform rollback
RollbackService rollbackService = new RollbackService(flyway, dataSource);
RollbackResult result = rollbackService.rollback(
    "1.0",           // Target version
    "admin",         // User
    "Bug in v2.0"    // Reason
);

// Check result
System.out.println("Success: " + result.success);
System.out.println("Rolled back: " + result.migrationsRolledBack + " migrations");
```

### Configuration

Create `flyway.conf`:

```properties
# Database connection
flyway.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
flyway.user=sa
flyway.password=

# Migration locations
flyway.locations=classpath:db/migration

# Result output configuration
flyway.output.saveResults=true
flyway.output.format=JSON
flyway.output.location=./flyway-results/operations.json
flyway.output.includeRollback=true

# Rollback configuration
flyway.rollback.enabled=true
flyway.rollback.user=admin
```

## Migration File Naming

- **Forward migrations**: `V{version}__{description}.sql`
  - Example: `V1__create_users_table.sql`
  
- **Undo migrations**: `U{version}__undo_{description}.sql`
  - Example: `U1__undo_create_users_table.sql`

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
- **[ENHANCED_CODE_EXAMPLES.md](ENHANCED_CODE_EXAMPLES.md)** - Complete code examples with explanations
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
