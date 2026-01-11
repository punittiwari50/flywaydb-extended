# SQL Output Logging Guide

## Overview

This guide shows how to capture and log SQL output for **both existing Flyway commands** (migrate, clean, info, etc.) and **custom commands** (rollback).

---

## 🎯 Three Approaches to SQL Logging

### 1. **StatementInterceptor** (Most Comprehensive)
- ✅ Captures **all SQL statements**
- ✅ Works for **existing and custom commands**
- ✅ Lowest-level interception
- ⚠️ Uses internal Flyway API

### 2. **Callbacks** (Recommended for Most Cases)
- ✅ Captures **lifecycle events**
- ✅ Works for **existing Flyway commands**
- ✅ Stable public API
- ⚠️ No SQL-level detail

### 3. **Custom Logging in RollbackService** (For Custom Commands)
- ✅ Full control over **rollback logging**
- ✅ Can log SQL before/after execution
- ✅ Simple and direct
- ⚠️ Only works for custom commands

---

## 📊 Comparison Table

| Feature | StatementInterceptor | Callbacks | Custom Logging |
|---------|---------------------|-----------|----------------|
| **Existing Flyway Commands** | ✅ Yes | ✅ Yes | ❌ No |
| **Custom Commands (Rollback)** | ✅ Yes | ❌ No | ✅ Yes |
| **SQL Statement Detail** | ✅ Full | ❌ None | ✅ Full |
| **API Stability** | ⚠️ Internal | ✅ Public | ✅ Custom |
| **Ease of Use** | ⚠️ Complex | ✅ Easy | ✅ Easy |
| **Performance Impact** | ⚠️ Higher | ✅ Lower | ✅ Lower |

---

## 1️⃣ StatementInterceptor - Complete SQL Logging

### For ALL Commands (Existing + Custom)

**Implementation**: [AuditStatementInterceptor.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/interceptor/AuditStatementInterceptor.java)

### Enable Logging

```java
// Enable SQL auditing
System.setProperty("flyway.audit.enabled", "true");
System.setProperty("flyway.audit.logPath", "./flyway-sql-audit.log");
System.setProperty("flyway.audit.verbose", "true");

// Configure Flyway
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .load();

// Run ANY Flyway command - SQL will be logged
flyway.migrate();    // Logged
flyway.clean();      // Logged
flyway.info();       // Logged
flyway.validate();   // Logged

// Custom rollback - also logged
RollbackService service = new RollbackService(flyway, dataSource);
service.rollback("1.0", "admin", "reason");  // Logged
```

### Example Output (flyway-sql-audit.log)

```
[2026-01-09T15:50:03] [INIT] StatementInterceptor initialized for database: testdb
[2026-01-09T15:50:03] [SCHEMA_HISTORY_CREATE] Creating schema history table (baseline: false)
[2026-01-09T15:50:03] [SQL_STATEMENT] Executing SQL: CREATE TABLE IF NOT EXISTS flyway_schema_history...
[2026-01-09T15:50:03] [SCRIPT_MIGRATION] Executing migration script: V1__create_users_table.sql
[2026-01-09T15:50:03] [SQL_STATEMENT] Executing SQL: CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(100)...
[2026-01-09T15:50:03] [PREPARED_STATEMENT] SQL: INSERT INTO flyway_schema_history VALUES (?, ?, ?, ?) | Params: {1=1, 2=create users table}
[2026-01-09T15:50:04] [SQL_STATEMENT] Executing SQL: DROP TABLE IF EXISTS orders
[2026-01-09T15:50:04] [PREPARED_STATEMENT] SQL: UPDATE flyway_schema_history SET rolled_back = TRUE WHERE version = ? | Params: {1=2}
```

### What Gets Logged

✅ **Existing Flyway Commands:**
- `migrate` - All forward migration SQL
- `clean` - All DROP statements
- `repair` - Schema history updates
- `validate` - Validation queries
- `baseline` - Baseline creation

✅ **Custom Commands:**
- `rollback` - Undo migration SQL
- Schema history updates
- All custom SQL

---

## 2️⃣ Callbacks - Lifecycle Event Logging

### For Existing Flyway Commands Only

**Best for**: High-level operation tracking without SQL details

### Implementation

```java
public class SqlLoggingCallback implements Callback {
    
    private final PrintWriter logWriter;
    
    public SqlLoggingCallback(String logPath) throws IOException {
        this.logWriter = new PrintWriter(new FileWriter(logPath, true), true);
    }
    
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE ||
               event == Event.AFTER_MIGRATE ||
               event == Event.BEFORE_EACH_MIGRATE ||
               event == Event.AFTER_EACH_MIGRATE ||
               event == Event.BEFORE_CLEAN ||
               event == Event.AFTER_CLEAN;
    }
    
    @Override
    public void handle(Event event, Context context) {
        String timestamp = LocalDateTime.now().toString();
        String message = String.format("[%s] Event: %s", timestamp, event);
        
        logWriter.println(message);
        
        // For BEFORE_EACH_MIGRATE, log migration info
        if (event == Event.BEFORE_EACH_MIGRATE) {
            MigrationInfo info = context.getMigrationInfo();
            logWriter.println("  Migration: " + info.getVersion() + 
                            " - " + info.getDescription());
            logWriter.println("  Script: " + info.getScript());
        }
    }
}
```

### Usage

```java
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .callbacks(new SqlLoggingCallback("./flyway-events.log"))
    .load();

flyway.migrate();  // Events logged
flyway.clean();    // Events logged
```

### Example Output (flyway-events.log)

```
[2026-01-09T15:50:03] Event: BEFORE_MIGRATE
[2026-01-09T15:50:03] Event: BEFORE_EACH_MIGRATE
  Migration: 1 - create users table
  Script: V1__create_users_table.sql
[2026-01-09T15:50:03] Event: AFTER_EACH_MIGRATE
[2026-01-09T15:50:04] Event: BEFORE_EACH_MIGRATE
  Migration: 2 - create orders table
  Script: V2__create_orders_table.sql
[2026-01-09T15:50:04] Event: AFTER_EACH_MIGRATE
[2026-01-09T15:50:04] Event: AFTER_MIGRATE
```

### Limitations

❌ **Does NOT log:**
- Actual SQL statements
- Custom rollback commands
- SQL-level details

✅ **Does log:**
- Migration lifecycle events
- Migration metadata
- Operation timing

---

## 3️⃣ Custom Logging in RollbackService

### For Custom Rollback Command

**Best for**: Detailed rollback SQL logging with full control

### Enhanced RollbackService with Logging

```java
public class RollbackService {
    
    private final Flyway flyway;
    private final DataSource dataSource;
    private final PrintWriter sqlLog;
    
    public RollbackService(Flyway flyway, DataSource dataSource, String logPath) 
            throws IOException {
        this.flyway = flyway;
        this.dataSource = dataSource;
        this.sqlLog = new PrintWriter(new FileWriter(logPath, true), true);
    }
    
    public RollbackResult rollback(String targetVersion, String user, String reason) {
        logSql("=== ROLLBACK STARTED ===");
        logSql("Target Version: " + targetVersion);
        logSql("User: " + user);
        logSql("Reason: " + reason);
        logSql("Timestamp: " + LocalDateTime.now());
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            // Get migrations to rollback
            List<MigrationInfo> toRollback = getMigrationsToRollback(conn, targetVersion);
            logSql("Migrations to rollback: " + toRollback.size());
            
            for (MigrationInfo info : toRollback) {
                logSql("\n--- Rolling back version: " + info.version + " ---");
                
                // Load and log undo SQL
                String undoSql = loadUndoSql(info.version);
                logSql("Undo SQL:");
                logSql(undoSql);
                
                // Execute undo SQL
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(undoSql);
                    logSql("✓ Executed successfully");
                }
            }
            
            // Update schema history
            logSql("\n--- Updating schema history ---");
            String updateSql = "UPDATE flyway_schema_history " +
                              "SET rolled_back = TRUE, " +
                              "    rollback_date = ?, " +
                              "    rollback_user = ?, " +
                              "    rollback_reason = ? " +
                              "WHERE version = ?";
            logSql("Update SQL: " + updateSql);
            
            updateSchemaHistory(conn, rolledBackVersions, user, reason);
            
            conn.commit();
            logSql("\n=== ROLLBACK COMPLETED SUCCESSFULLY ===\n");
            
            return new RollbackResult(/* ... */);
            
        } catch (Exception e) {
            logSql("ERROR: " + e.getMessage());
            logSql("=== ROLLBACK FAILED ===\n");
            throw e;
        }
    }
    
    private void logSql(String message) {
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        sqlLog.println("[" + timestamp + "] " + message);
    }
}
```

### Usage

```java
// Create service with SQL logging
RollbackService service = new RollbackService(
    flyway, 
    dataSource, 
    "./rollback-sql.log"
);

// Perform rollback - all SQL logged
RollbackResult result = service.rollback("1.0", "admin", "Bug fix");
```

### Example Output (rollback-sql.log)

```
[2026-01-09T15:50:03] === ROLLBACK STARTED ===
[2026-01-09T15:50:03] Target Version: 1.0
[2026-01-09T15:50:03] User: admin
[2026-01-09T15:50:03] Reason: Bug fix
[2026-01-09T15:50:03] Timestamp: 2026-01-09T15:50:03
[2026-01-09T15:50:03] Migrations to rollback: 1

[2026-01-09T15:50:03] --- Rolling back version: 2 ---
[2026-01-09T15:50:03] Undo SQL:
[2026-01-09T15:50:03] DROP TABLE IF EXISTS orders;
[2026-01-09T15:50:03] ✓ Executed successfully

[2026-01-09T15:50:03] --- Updating schema history ---
[2026-01-09T15:50:03] Update SQL: UPDATE flyway_schema_history SET rolled_back = TRUE, rollback_date = ?, rollback_user = ?, rollback_reason = ? WHERE version = ?
[2026-01-09T15:50:03] 
[2026-01-09T15:50:03] === ROLLBACK COMPLETED SUCCESSFULLY ===
```

---

## 🎨 Combined Approach - Best of All Worlds

### Complete SQL Logging Solution

```java
public class CompleteSqlLoggingExample {
    
    public static void main(String[] args) throws Exception {
        
        // 1. Enable StatementInterceptor for ALL SQL
        System.setProperty("flyway.audit.enabled", "true");
        System.setProperty("flyway.audit.logPath", "./all-sql.log");
        
        // 2. Configure result output
        ResultOutputConfiguration outputConfig = new ResultOutputConfiguration();
        outputConfig.setSaveResults(true);
        outputConfig.setFormat("JSON");
        outputConfig.setLocation("./operation-results.json");
        
        // 3. Configure Flyway with callbacks
        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:testdb", "sa", "")
            .callbacks(
                new SqlLoggingCallback("./lifecycle-events.log"),
                new ResultOutputCallback(outputConfig)
            )
            .load();
        
        // 4. Create rollback service with SQL logging
        RollbackService rollbackService = new RollbackService(
            flyway,
            flyway.getConfiguration().getDataSource(),
            "./rollback-sql.log"
        );
        
        // 5. Run operations - everything is logged!
        
        // Existing Flyway commands
        flyway.migrate();    // Logged in: all-sql.log, lifecycle-events.log, operation-results.json
        flyway.info();       // Logged in: all-sql.log, lifecycle-events.log, operation-results.json
        
        // Custom rollback command
        rollbackService.rollback("1.0", "admin", "Testing");  
        // Logged in: all-sql.log, rollback-sql.log, operation-results.json
    }
}
```

### Output Files Created

```
project/
├── all-sql.log                  # ALL SQL from StatementInterceptor
├── lifecycle-events.log         # Flyway lifecycle events from Callback
├── rollback-sql.log            # Detailed rollback SQL from RollbackService
└── operation-results.json      # Operation results (migrate, rollback, etc.)
```

---

## 📋 Logging Levels Comparison

### Level 1: Operation Results Only
```java
// Just capture what happened, not how
ResultOutputCallback callback = new ResultOutputCallback(config);
```
**Output**: Operation success/failure, counts, versions
**Use for**: High-level reporting

### Level 2: Lifecycle Events
```java
// Capture when things happened
SqlLoggingCallback callback = new SqlLoggingCallback(logPath);
```
**Output**: Before/after events, migration metadata
**Use for**: Operation timing, flow tracking

### Level 3: SQL Statements
```java
// Capture actual SQL executed
System.setProperty("flyway.audit.enabled", "true");
// OR
RollbackService service = new RollbackService(flyway, ds, logPath);
```
**Output**: Every SQL statement executed
**Use for**: Debugging, auditing, compliance

---

## 🎯 Recommendations by Use Case

### Use Case 1: Production Monitoring
**Goal**: Track what operations ran and when

**Solution**:
```java
✅ ResultOutputCallback (operation results)
✅ SqlLoggingCallback (lifecycle events)
❌ StatementInterceptor (too verbose)
```

### Use Case 2: Debugging Failed Migrations
**Goal**: See exactly what SQL failed

**Solution**:
```java
✅ StatementInterceptor (all SQL)
✅ Custom RollbackService logging (rollback SQL)
⚠️ Enable verbose mode
```

### Use Case 3: Compliance/Auditing
**Goal**: Complete audit trail of all database changes

**Solution**:
```java
✅ StatementInterceptor (all SQL)
✅ ResultOutputCallback (operation results)
✅ Custom RollbackService logging (rollback details)
```

### Use Case 4: Performance Analysis
**Goal**: Identify slow SQL statements

**Solution**:
```java
✅ StatementInterceptor with timing
✅ Custom performance tracking
```

---

## 🚀 Quick Start Examples

### Example 1: Log Existing Flyway Commands

```java
// Enable StatementInterceptor
System.setProperty("flyway.audit.enabled", "true");
System.setProperty("flyway.audit.logPath", "./flyway-sql.log");

Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .load();

flyway.migrate();  // All SQL logged to flyway-sql.log
```

### Example 2: Log Custom Rollback Command

```java
// Create service with logging
RollbackService service = new RollbackService(
    flyway, dataSource, "./rollback.log"
);

// Rollback with SQL logging
service.rollback("1.0", "admin", "reason");  // SQL logged to rollback.log
```

### Example 3: Log Everything

```java
// Enable all logging
System.setProperty("flyway.audit.enabled", "true");

ResultOutputConfiguration outputConfig = new ResultOutputConfiguration();
outputConfig.setSaveResults(true);

Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .callbacks(
        new SqlLoggingCallback("./events.log"),
        new ResultOutputCallback(outputConfig)
    )
    .load();

RollbackService service = new RollbackService(
    flyway, dataSource, "./rollback.log"
);

// Everything is logged!
flyway.migrate();
service.rollback("1.0", "admin", "reason");
```

---

## 📊 Summary Table

| Logging Method | Existing Commands | Custom Commands | SQL Detail | Complexity |
|----------------|-------------------|-----------------|------------|------------|
| **StatementInterceptor** | ✅ Yes | ✅ Yes | ✅ Full | ⚠️ High |
| **Callbacks** | ✅ Yes | ❌ No | ❌ None | ✅ Low |
| **Custom Logging** | ❌ No | ✅ Yes | ✅ Full | ✅ Low |
| **ResultOutputCallback** | ✅ Yes | ✅ Yes* | ❌ None | ✅ Low |

*ResultOutputCallback works for custom commands if you manually call `writeResult()`

---

## 💡 Best Practices

### 1. **Choose the Right Level**
- Don't log more than you need
- Consider performance impact
- Think about log file size

### 2. **Rotate Log Files**
- Implement log rotation
- Archive old logs
- Monitor disk space

### 3. **Secure Sensitive Data**
- SQL may contain passwords, PII
- Sanitize before logging
- Restrict log file access

### 4. **Performance Considerations**
- Async logging for production
- Buffer writes
- Consider sampling for high-volume

### 5. **Testing**
- Test logging in development
- Verify log format
- Check log file permissions

---

## 📚 Related Files

- [AuditStatementInterceptor.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/interceptor/AuditStatementInterceptor.java) - StatementInterceptor implementation
- [ResultOutputCallback.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/output/ResultOutputCallback.java) - Callback implementation
- [RollbackService.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackService.java) - Custom command logging
- [STATEMENT_INTERCEPTOR_GUIDE.md](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/STATEMENT_INTERCEPTOR_GUIDE.md) - Detailed interceptor guide

---

## 🎯 Final Recommendation

**For most projects:**
```java
// 1. Use ResultOutputCallback for operation results
ResultOutputCallback resultCallback = new ResultOutputCallback(config);

// 2. Add custom logging to RollbackService
RollbackService service = new RollbackService(flyway, ds, "./rollback.log");

// 3. Enable StatementInterceptor only when debugging
System.setProperty("flyway.audit.enabled", "false");  // Default off
```

**For compliance/auditing:**
```java
// Enable everything
System.setProperty("flyway.audit.enabled", "true");
// + ResultOutputCallback
// + Custom RollbackService logging
```

This gives you **flexibility** without **performance overhead** in normal operation!
