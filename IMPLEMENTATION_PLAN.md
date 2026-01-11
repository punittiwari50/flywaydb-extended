# Flyway Rollback Extension - Implementation Plan

## Goal

Implement a comprehensive rollback solution for Flyway that includes:
1. Custom rollback functionality using undo migrations
2. Output result capture for rollback operations
3. Enhanced schema history table with rollback audit columns
4. **ResultSet output persistence** as configurable option
5. **One-time schema migration** to add audit columns safely

## User Review Required

> [!IMPORTANT]
> **Database Schema Changes**: This implementation will modify the `flyway_schema_history` table by adding 4 new columns for rollback auditing. This is a **non-reversible change** to your database schema.

> [!WARNING]
> **Flyway Community Edition Limitation**: Flyway Community Edition does not include built-in rollback functionality. This implementation provides a custom solution using undo migrations (U-prefixed files) that must be manually created and maintained alongside forward migrations.

> [!CAUTION]
> **Manual Undo Script Creation**: For each versioned migration (V1, V2, etc.), you must manually create a corresponding undo migration (U1, U2, etc.) with the exact reverse operations. Incorrect undo scripts can cause data loss or corruption.

## Proposed Changes

### Component 1: Core Rollback Infrastructure

#### [NEW] [RollbackResult.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackResult.java)
- Implements `OperationResult` interface
- Captures rollback operation metadata:
  - Database name
  - Number of migrations rolled back
  - List of rolled back version numbers
  - Success/failure status
  - User who performed rollback
  - Timestamp of rollback operation

#### [NEW] [RollbackConfigurationExtension.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackConfigurationExtension.java)
- Implements `ConfigurationExtension` interface
- Provides custom configuration parameters:
  - `flyway.rollback.enabled` - Enable/disable rollback functionality
  - `flyway.rollback.user` - Default user for rollback operations
  - `flyway.rollback.auditTable` - Custom audit table name (default: flyway_schema_history)

#### [NEW] [UndoMigrationResolver.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/UndoMigrationResolver.java)
- Implements `MigrationResolver` interface
- Scans for undo migration files with "U" prefix (e.g., `U1__undo_create_users.sql`)
- Creates `ResolvedMigration` objects for each undo script
- Provides checksum validation for undo scripts

---

### Component 2: Rollback Execution Service

#### [NEW] [RollbackService.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackService.java)
- Core service for executing rollback operations
- Key methods:
  - `rollback(String targetVersion, String user, String reason)` - Roll back to specific version
  - `rollbackLast(int count, String user, String reason)` - Roll back last N migrations
  - `getMigrationsToRollback(Connection conn, String targetVersion)` - Query migrations to undo
  - `executeUndoMigration(Connection conn, MigrationInfo info)` - Execute single undo script
  - `updateSchemaHistory(Connection conn, List<String> versions, String user, String reason)` - Update audit columns

#### [NEW] [RollbackCallback.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackCallback.java)
- Implements Flyway `Callback` interface
- Hooks into Flyway lifecycle events
- Checks for rollback triggers (system properties or configuration)
- Invokes `RollbackService` when needed

---

### Component 3: Schema History Enhancement

#### [NEW] [SchemaHistoryEnhancer.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/SchemaHistoryEnhancer.java)
- **One-time migration** to add rollback audit columns to `flyway_schema_history` table
- Columns to add:
  ```sql
  ALTER TABLE flyway_schema_history 
  ADD COLUMN IF NOT EXISTS rolled_back BOOLEAN DEFAULT FALSE;
  
  ALTER TABLE flyway_schema_history 
  ADD COLUMN IF NOT EXISTS rollback_date TIMESTAMP NULL;
  
  ALTER TABLE flyway_schema_history 
  ADD COLUMN IF NOT EXISTS rollback_user VARCHAR(100) NULL;
  
  ALTER TABLE flyway_schema_history 
  ADD COLUMN IF NOT EXISTS rollback_reason VARCHAR(500) NULL;
  ```
- **Idempotent implementation**:
  - Checks if columns exist before attempting to add them
  - Uses database-specific syntax (IF NOT EXISTS for PostgreSQL, conditional for others)
  - Tracks migration in separate metadata table `flyway_rollback_metadata`
- Runs automatically on first use via `afterMigrate` callback
- Never runs twice (tracked via metadata table)

---

### Component 4: Example Usage and Documentation

#### [NEW] [RollbackExample.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/examples/RollbackExample.java)
- Complete working example demonstrating:
  - Flyway configuration with rollback extensions
  - Running forward migrations
  - Performing rollback to specific version
  - Capturing and displaying rollback results
  - Querying rollback audit history

#### [NEW] [db/migration/V1__create_users_table.sql](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/resources/db/migration/V1__create_users_table.sql)
- Example forward migration

#### [NEW] [db/migration/U1__undo_create_users_table.sql](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/resources/db/migration/U1__undo_create_users_table.sql)
- Example undo migration (reverse of V1)

#### [MODIFY] [EXTENSIONS.md](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/EXTENSIONS.md)
- Add comprehensive rollback documentation section
- Include usage examples
- Document configuration options
- Explain undo migration file naming conventions

#### [MODIFY] [pom.xml](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/pom.xml)
- Ensure all necessary Flyway dependencies are included
- Add any additional dependencies for rollback functionality

---

### Component 5: ResultSet Output Persistence

#### [NEW] [ResultOutputConfiguration.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/output/ResultOutputConfiguration.java)
- Extends `ConfigurationExtension` to add output persistence settings
- Configuration parameters:
  - `flyway.output.saveResults` (boolean) - Enable/disable result saving
  - `flyway.output.format` (string) - Output format: JSON, XML, CSV (default: JSON)
  - `flyway.output.location` (string) - File path or directory for output
  - `flyway.output.includeRollback` (boolean) - Include rollback results (default: true)

#### [NEW] [ResultOutputWriter.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/output/ResultOutputWriter.java)
- Writes `OperationResult` objects to configured output location
- Supports multiple formats:
  - **JSON**: Structured JSON with full result details
  - **XML**: XML format for enterprise integration
  - **CSV**: Tabular format for reporting
- Automatically appends to existing files or creates new ones
- Includes timestamp and operation type in output

#### [NEW] [ResultOutputCallback.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/output/ResultOutputCallback.java)
- Implements `Callback` interface
- Intercepts Flyway operations (migrate, clean, info, validate, rollback)
- Captures `OperationResult` from each operation
- Writes results to configured location if enabled
- Handles errors gracefully (logs but doesn't fail operation)

**Example Configuration**:
```properties
# Enable result output saving
flyway.output.saveResults=true
flyway.output.format=JSON
flyway.output.location=./flyway-results/results.json
flyway.output.includeRollback=true
```

**Example Output (JSON)**:
```json
{
  "timestamp": "2026-01-09T15:03:26",
  "operation": "ROLLBACK",
  "database": "mydb",
  "result": {
    "migrationsRolledBack": 2,
    "rolledBackVersions": ["3", "2"],
    "success": true,
    "rollbackUser": "admin",
    "rollbackTime": "2026-01-09T15:03:26"
  }
}
```

---

### Component 6: One-Time Schema Migration Tracker

#### [NEW] [RollbackMetadataTable.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackMetadataTable.java)
- Creates and manages `flyway_rollback_metadata` table
- Tracks which schema enhancements have been applied
- Table structure:
  ```sql
  CREATE TABLE IF NOT EXISTS flyway_rollback_metadata (
    enhancement_id VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL,
    applied_by VARCHAR(100),
    description VARCHAR(500)
  );
  ```
- Used by `SchemaHistoryEnhancer` to ensure one-time execution

**Enhancement Tracking Flow**:
1. Check if `flyway_rollback_metadata` table exists (create if not)
2. Check if enhancement ID exists in metadata table
3. If not exists, apply schema changes
4. Record enhancement in metadata table
5. Never run again

---

## Verification Plan

### Automated Tests

#### Unit Tests

**Test File**: Create `src/test/java/com/example/flywayextended/rollback/RollbackServiceTest.java`

Tests to implement:
1. `testRollbackToVersion()` - Verify rollback to specific version
2. `testRollbackLast()` - Verify rolling back last N migrations
3. `testSchemaHistoryUpdate()` - Verify audit columns are updated correctly
4. `testRollbackResult()` - Verify result object contains correct data
5. `testUndoMigrationResolver()` - Verify undo migrations are discovered correctly

**Run command**:
```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended
mvn test -Dtest=RollbackServiceTest
```

#### Integration Tests

**Test File**: Create `src/test/java/com/example/flywayextended/rollback/RollbackIntegrationTest.java`

Tests to implement:
1. `testFullMigrationAndRollback()` - Run migrations then rollback
2. `testSchemaHistoryEnhancement()` - Verify columns are added to schema history
3. `testMultipleRollbacks()` - Test rolling back multiple times
4. `testRollbackWithMissingUndoScript()` - Test error handling

**Run command**:
```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended
mvn test -Dtest=RollbackIntegrationTest
```

### Manual Verification

#### Step 1: Build the Project
```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended
mvn clean package
```

#### Step 2: Run the Example
```bash
java -cp target/flyway-extended-1.0-SNAPSHOT.jar com.example.flywayextended.examples.RollbackExample
```

**Expected Output**:
```
Running migrations...
Migrations applied: 3
Current version: 3.0

Performing rollback to version 1.0...
Rollback completed: 2 migrations rolled back
Rolled back versions: [3, 2]
Rollback user: admin
Rollback time: 2026-01-09T14:50:00

Querying schema history...
Version 1: rolled_back=false
Version 2: rolled_back=true, rollback_date=2026-01-09 14:50:00, rollback_user=admin
Version 3: rolled_back=true, rollback_date=2026-01-09 14:50:00, rollback_user=admin
```

#### Step 3: Verify Database Schema
Connect to the test database and verify the schema history table:

```sql
-- Check that audit columns exist
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'flyway_schema_history' 
AND column_name IN ('rolled_back', 'rollback_date', 'rollback_user', 'rollback_reason');

-- Check rollback audit data
SELECT version, description, rolled_back, rollback_date, rollback_user, rollback_reason
FROM flyway_schema_history
ORDER BY installed_rank;
```

**Expected Results**:
- 4 audit columns should exist
- Rolled back migrations should have `rolled_back=true`
- Rollback date, user, and reason should be populated

---

## Summary

This implementation provides a complete rollback solution for Flyway Community Edition by:

1. **Rollback Functionality**: Custom `MigrationResolver` finds undo scripts (U-prefixed files), and `RollbackService` executes them in reverse order
2. **Output Results**: `RollbackResult` class captures all rollback operation details
3. **Audit Tracking**: Enhanced `flyway_schema_history` table tracks who rolled back what and when

The solution is fully extensible and follows Flyway's plugin architecture, making it easy to integrate into existing projects.
