# StatementInterceptor Guide

## What is StatementInterceptor?

`StatementInterceptor` is a powerful Flyway extension point that allows you to **intercept and monitor all SQL statements** executed by Flyway during migrations.

---

## 🎯 Use Cases

### 1. **SQL Auditing**
- Log all SQL statements executed
- Track who executed what and when
- Compliance and security requirements

### 2. **Debugging**
- See exactly what SQL is being run
- Identify problematic statements
- Understand migration failures

### 3. **Performance Monitoring**
- Track execution times
- Identify slow queries
- Optimize migrations

### 4. **Custom Logging**
- Write to custom log files
- Send to monitoring systems
- Integrate with APM tools

### 5. **Statement Modification** (Advanced)
- Wrap connections with proxies
- Modify SQL before execution
- Add custom behavior

---

## 💡 Does It Add Value?

### ✅ YES, if you need:
- **Audit trail** of all database changes
- **Debugging** complex migration issues
- **Compliance** requirements (SOX, HIPAA, etc.)
- **Performance** analysis
- **Custom logging** beyond Flyway's built-in logging

### ❌ NO, if:
- Simple migrations with no audit requirements
- Flyway's built-in logging is sufficient
- No compliance or debugging needs
- Performance overhead is a concern

---

## 🔧 Our Implementation: AuditStatementInterceptor

### Features

✅ **Comprehensive Logging** - All SQL statements logged  
✅ **Timestamped Events** - Know exactly when things happened  
✅ **Migration Tracking** - Track which migrations executed  
✅ **Configurable** - Enable/disable via system properties  
✅ **File-Based Audit Log** - Persistent audit trail  
✅ **Verbose Mode** - Optional console output  

### What It Logs

1. **Schema History Events**
   - Table creation
   - Migration insertions
   - Delete failures

2. **SQL Execution**
   - SQL scripts
   - SQL statements
   - Prepared statements
   - Callable statements

3. **Migration Events**
   - Script migrations
   - Java migrations
   - Commands

---

## 📝 Usage

### Enable the Interceptor

**System Properties:**
```bash
-Dflyway.audit.enabled=true
-Dflyway.audit.logPath=./flyway-audit.log
-Dflyway.audit.verbose=true
```

**In Code:**
```java
System.setProperty("flyway.audit.enabled", "true");
System.setProperty("flyway.audit.logPath", "./flyway-audit.log");
System.setProperty("flyway.audit.verbose", "true");

// Note: StatementInterceptor requires Flyway internal API access
// This is an advanced feature and may not be directly configurable
// in all Flyway setups
```

---

## 📊 Example Audit Log Output

```
[2026-01-09T15:45:18] [INIT] StatementInterceptor initialized for database: testdb
[2026-01-09T15:45:18] [SCHEMA_HISTORY_CREATE] Creating schema history table (baseline: false)
[2026-01-09T15:45:18] [SQL_STATEMENT] Executing SQL: CREATE TABLE IF NOT EXISTS flyway_schema_history...
[2026-01-09T15:45:19] [SCRIPT_MIGRATION] Executing migration script: V1__create_users_table.sql
[2026-01-09T15:45:19] [SQL_STATEMENT] Executing SQL: CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(100)...
[2026-01-09T15:45:19] [SCHEMA_HISTORY_INSERT] Inserting migration: 1 - create users table
[2026-01-09T15:45:19] [PREPARED_STATEMENT] SQL: INSERT INTO flyway_schema_history VALUES (?, ?, ?, ?) | Params: {1=1, 2=create users table, ...}
[2026-01-09T15:45:20] [SCRIPT_MIGRATION] Executing migration script: V2__create_orders_table.sql
[2026-01-09T15:45:20] [SQL_STATEMENT] Executing SQL: CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT...
[2026-01-09T15:45:20] [SCHEMA_HISTORY_INSERT] Inserting migration: 2 - create orders table
[2026-01-09T15:45:21] [CLOSE] StatementInterceptor closing
```

---

## 🎨 Advanced Use Cases

### 1. Performance Monitoring

```java
public class PerformanceStatementInterceptor implements StatementInterceptor {
    private Map<String, Long> executionTimes = new HashMap<>();
    
    @Override
    public void sqlStatement(SqlStatement statement) {
        long start = System.currentTimeMillis();
        // Statement executes
        long duration = System.currentTimeMillis() - start;
        executionTimes.put(statement.getSql(), duration);
    }
}
```

### 2. Custom Logging Integration

```java
public class LoggingStatementInterceptor implements StatementInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(...);
    
    @Override
    public void interceptStatement(String sql) {
        logger.info("Executing SQL: {}", sql);
    }
}
```

### 3. Statement Modification (Advanced)

```java
public class ModifyingStatementInterceptor implements StatementInterceptor {
    @Override
    public Connection createConnectionProxy(Connection connection) {
        return new ConnectionProxy(connection) {
            @Override
            public Statement createStatement() {
                // Wrap statement to modify SQL
                return new StatementWrapper(super.createStatement());
            }
        };
    }
}
```

---

## ⚠️ Important Considerations

### 1. Performance Impact
- Logging adds overhead
- File I/O can be slow
- Consider async logging for production

### 2. Log File Size
- Can grow large quickly
- Implement log rotation
- Consider log aggregation tools

### 3. Sensitive Data
- SQL may contain sensitive data
- Sanitize before logging
- Secure log files appropriately

### 4. Internal API
- `StatementInterceptor` is an internal Flyway API
- May change between versions
- Test thoroughly when upgrading Flyway

---

## 🔄 Integration with Our Rollback Extension

### Combined Usage

```java
// Enable audit logging
System.setProperty("flyway.audit.enabled", "true");

// Configure Flyway with all extensions
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .callbacks(
        new ResultOutputCallback(outputConfig),
        new SchemaHistoryCallback()
    )
    .load();

// Run migrations (all SQL will be logged)
flyway.migrate();

// Perform rollback (rollback SQL will be logged)
RollbackService service = new RollbackService(flyway, dataSource);
service.rollback("1.0", "admin", "Testing");
```

**Result**: Complete audit trail of:
- Forward migrations
- Rollback operations
- Schema history updates
- All SQL statements

---

## 📋 Comparison: StatementInterceptor vs Other Approaches

| Feature | StatementInterceptor | Callback | Custom Logging |
|---------|---------------------|----------|----------------|
| SQL-level interception | ✅ Yes | ❌ No | ❌ No |
| Statement modification | ✅ Yes | ❌ No | ❌ No |
| Performance tracking | ✅ Yes | ⚠️ Limited | ❌ No |
| Ease of use | ⚠️ Complex | ✅ Easy | ✅ Easy |
| Flyway version stability | ⚠️ Internal API | ✅ Stable | ✅ Stable |

---

## 🎯 Recommendation

### Use StatementInterceptor if:
1. ✅ You need **complete SQL audit trail**
2. ✅ You have **compliance requirements**
3. ✅ You need to **debug complex migrations**
4. ✅ You want **performance monitoring**
5. ✅ You're comfortable with **internal APIs**

### Use Callbacks instead if:
1. ✅ You only need **lifecycle events**
2. ✅ You want **stable API**
3. ✅ You need **simple logging**
4. ✅ You don't need **SQL-level detail**

### Use Custom Logging if:
1. ✅ You only need **high-level tracking**
2. ✅ You want **simplest solution**
3. ✅ You don't need **SQL details**

---

## 📚 Related Files

- [AuditStatementInterceptor.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/interceptor/AuditStatementInterceptor.java) - Our implementation
- [ResultOutputCallback.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/output/ResultOutputCallback.java) - Callback alternative
- [SCHEMA_COLUMN_GUIDE.md](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/SCHEMA_COLUMN_GUIDE.md) - Schema modification guide

---

## 🚀 Quick Start

```java
// 1. Enable auditing
System.setProperty("flyway.audit.enabled", "true");
System.setProperty("flyway.audit.logPath", "./flyway-audit.log");

// 2. Configure Flyway (interceptor auto-discovered if on classpath)
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .load();

// 3. Run migrations
flyway.migrate();

// 4. Check audit log
cat ./flyway-audit.log
```

---

## 💡 Summary

**StatementInterceptor adds significant value for:**
- 🔍 Auditing and compliance
- 🐛 Debugging complex issues
- 📊 Performance monitoring
- 🔧 Advanced customization

**But consider simpler alternatives if:**
- 📝 Basic logging is sufficient
- ⚡ Performance is critical
- 🛡️ API stability is important

**Our implementation provides:**
- ✅ Complete SQL audit trail
- ✅ Timestamped events
- ✅ Configurable logging
- ✅ Production-ready code
