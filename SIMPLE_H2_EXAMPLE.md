# Simple H2 Example - Quick Start Guide

This is a simple, self-contained example demonstrating all Flyway rollback features using H2 in-memory database.

## What This Example Shows

1. ✅ **H2 Database Setup** - In-memory database (no installation needed)
2. ✅ **Schema Enhancement** - One-time addition of audit columns
3. ✅ **Forward Migrations** - Running V1 and V2 migrations
4. ✅ **Rollback Operation** - Rolling back from V2 to V1
5. ✅ **Audit Tracking** - Viewing rollback history
6. ✅ **Result Output** - Saving results to JSON file

## How to Run

### Option 1: Using Maven

```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended

# Compile
mvnw.cmd clean compile

# Run the simple example
mvnw.cmd exec:java -Dexec.mainClass="com.example.flywayextended.examples.SimpleH2Example"
```

### Option 2: Using JAR

```bash
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended

# Build
mvnw.cmd clean package

# Run
java -cp target\flyway-extended-1.0-SNAPSHOT.jar com.example.flywayextended.examples.SimpleH2Example
```

## Expected Output

```
╔════════════════════════════════════════════════════════╗
║   Flyway Rollback Extension - Simple H2 Example       ║
╚════════════════════════════════════════════════════════╝

📝 Step 1: Configuring result output...
   ✓ Results will be saved to: ./flyway-results/h2-example-results.json

🗄️  Step 2: Setting up H2 database...
   ✓ H2 in-memory database created
   ✓ Connection: jdbc:h2:mem:testdb

🔧 Step 3: Enhancing schema history table...
Applying schema history enhancement...
Added column: rolled_back
Added column: rollback_date
Added column: rollback_user
Added column: rollback_reason
Schema history enhancement completed successfully.
   ✓ Added 4 audit columns to flyway_schema_history
     - rolled_back (BOOLEAN)
     - rollback_date (TIMESTAMP)
     - rollback_user (VARCHAR)
     - rollback_reason (VARCHAR)

⬆️  Step 4: Running forward migrations...
   ✓ Migrations executed: 2
   ✓ Target version: 2

📊 Step 5: Current database state:
   ✓ USERS table: 2 rows
   ✓ ORDERS table: 0 rows

📜 Step 6: Migration history:
   ✓ V1: create users table [SUCCESS]
   ✓ V2: create orders table [SUCCESS]

⬇️  Step 7: Performing rollback to version 1...
Rolling back version: 2

   📋 Rollback Results:
   ├─ Success: ✓ YES
   ├─ Migrations rolled back: 1
   ├─ Versions: [2]
   ├─ User: admin
   └─ Time: 2026-01-09T15:23:46

📊 Step 8: Database state after rollback:
   ✓ USERS table: 2 rows
   ✗ ORDERS table: does not exist

🔍 Step 9: Rollback audit history:
   ✓ V1 (create users table): ACTIVE
   🔄 V2 (create orders table):
      ├─ Status: ROLLED BACK
      ├─ Date: 2026-01-09 15:23:46
      ├─ User: admin
      └─ Reason: Demonstrating rollback functionality

📋 Step 10: Enhancement metadata:
   📌 add_rollback_audit_columns_v1
      ├─ Applied: 2026-01-09 15:23:46
      ├─ By: admin
      └─ Description: Added rollback audit columns to flyway_schema_history

╔════════════════════════════════════════════════════════╗
║                  ✓ Example Completed                   ║
╚════════════════════════════════════════════════════════╝

📁 Results saved to: ./flyway-results/h2-example-results.json
💾 Database: H2 in-memory (jdbc:h2:mem:testdb)

✨ All features demonstrated successfully!
```

## What Happens Step by Step

### Step 1: Configure Result Output
- Sets up JSON output to save operation results
- Results will be saved to `./flyway-results/h2-example-results.json`

### Step 2: Setup H2 Database
- Creates H2 in-memory database
- No installation or configuration needed
- Connection: `jdbc:h2:mem:testdb`

### Step 3: Enhance Schema History
- **One-time operation** (tracked in metadata table)
- Adds 4 audit columns to `flyway_schema_history`:
  - `rolled_back` - Boolean flag
  - `rollback_date` - When rollback occurred
  - `rollback_user` - Who performed rollback
  - `rollback_reason` - Why rollback was performed

### Step 4: Run Forward Migrations
- Executes V1: Creates `users` table with 2 sample users
- Executes V2: Creates `orders` table with foreign key to users

### Step 5: Display Database State
- Shows which tables exist
- Shows row counts for each table

### Step 6: Display Migration History
- Lists all migrations and their status
- Shows version, description, and state

### Step 7: Perform Rollback
- Rolls back from version 2 to version 1
- Executes undo migration U2 (drops orders table)
- Updates schema history with rollback information

### Step 8: Display Updated Database State
- Shows database state after rollback
- `users` table still exists
- `orders` table has been removed

### Step 9: Query Rollback Audit
- Shows which migrations are active
- Shows which migrations were rolled back
- Displays rollback date, user, and reason

### Step 10: Display Enhancement Metadata
- Shows which schema enhancements have been applied
- Proves that enhancement only runs once

## Database Schema

### After Enhancement

**flyway_schema_history** table includes:

| Column | Type | Description |
|--------|------|-------------|
| version | VARCHAR | Migration version |
| description | VARCHAR | Migration description |
| success | BOOLEAN | Migration success |
| **rolled_back** | **BOOLEAN** | **Rollback flag** |
| **rollback_date** | **TIMESTAMP** | **When rolled back** |
| **rollback_user** | **VARCHAR** | **Who rolled back** |
| **rollback_reason** | **VARCHAR** | **Why rolled back** |

**flyway_rollback_metadata** table:

| Column | Type | Description |
|--------|------|-------------|
| enhancement_id | VARCHAR | Enhancement identifier |
| applied_at | TIMESTAMP | When applied |
| applied_by | VARCHAR | Who applied |
| description | VARCHAR | What was done |

## Output Files

### flyway-results/h2-example-results.json

```json
{
  "timestamp": "2026-01-09T15:23:46",
  "operation": "MIGRATE",
  "result": "MigrateResult{...}"
}
{
  "timestamp": "2026-01-09T15:23:46",
  "operation": "ROLLBACK",
  "result": "RollbackResult{database='PUBLIC', migrationsRolledBack=1, versions=[2], success=true, user='admin', time=2026-01-09T15:23:46}"
}
```

## Key Features Demonstrated

✅ **H2 In-Memory Database** - No setup required  
✅ **One-Time Schema Migration** - Audit columns added only once  
✅ **Rollback Functionality** - Roll back to specific version  
✅ **Audit Tracking** - Complete rollback history  
✅ **Result Output** - JSON file with operation results  
✅ **Visual Output** - Clear, formatted console output  

## Troubleshooting

### Maven not found
Use `mvnw.cmd` (Maven Wrapper) instead of `mvn`:
```bash
mvnw.cmd clean compile
```

### Class not found
Make sure to compile first:
```bash
mvnw.cmd clean compile
mvnw.cmd exec:java -Dexec.mainClass="com.example.flywayextended.examples.SimpleH2Example"
```

### Database already exists
H2 in-memory database is recreated each time, so this shouldn't happen.

## Next Steps

- Review the source code in `SimpleH2Example.java`
- Check the generated JSON file in `flyway-results/`
- Try modifying the migrations
- Experiment with different rollback scenarios

## Summary

This example provides a **complete, working demonstration** of all Flyway rollback features using H2 database with:
- ✅ Zero configuration
- ✅ No external dependencies
- ✅ Clear visual output
- ✅ All features demonstrated
- ✅ Easy to understand and modify
