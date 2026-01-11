# Adding Columns to flyway_schema_history - Best Practices Guide

## Overview

This guide explains the **best practices** for adding custom columns to Flyway's `flyway_schema_history` table, including when to use `StatementInterceptor` and recommended approaches.

---

## ✅ Recommended Approach: One-Time Schema Migration

### Why This Approach?

1. **Explicit and Clear** - Easy to understand what's happening
2. **Tracked** - Uses metadata table to prevent re-execution
3. **Idempotent** - Safe to run multiple times
4. **Database-Agnostic** - Works with all databases
5. **Auditable** - Clear record of when enhancement was applied

### Implementation (Already in Project)

```java
public class SchemaHistoryEnhancer {
    private static final String ENHANCEMENT_ID = "add_rollback_audit_columns_v1";
    
    public void enhanceSchemaHistory(Connection conn, String user) throws SQLException {
        // Check if already applied
        if (metadataTable.isEnhancementApplied(conn, ENHANCEMENT_ID)) {
            return; // Skip if already done
        }
        
        // Add columns
        addColumnIfNotExists(conn, "rolled_back", "BOOLEAN DEFAULT FALSE");
        addColumnIfNotExists(conn, "rollback_date", "TIMESTAMP NULL");
        addColumnIfNotExists(conn, "rollback_user", "VARCHAR(100) NULL");
        addColumnIfNotExists(conn, "rollback_reason", "VARCHAR(500) NULL");
        
        // Record that enhancement was applied
        metadataTable.recordEnhancement(conn, ENHANCEMENT_ID, 
            "Added rollback audit columns", user);
    }
}
```

**Usage:**
```java
try (Connection conn = dataSource.getConnection()) {
    SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();
    enhancer.enhanceSchemaHistory(conn, "admin");
}
```

---

## 🔧 Alternative Approach: Flyway Callback

### When to Use
- Want automatic execution on first migration
- Don't want manual enhancement step
- Prefer Flyway-native approach

### Implementation

```java
public class SchemaHistoryCallback implements Callback {
    
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }
    
    @Override
    public void handle(Event event, Context context) {
        try (Connection conn = context.getConnection()) {
            SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();
            enhancer.enhanceSchemaHistory(conn, "flyway");
        } catch (Exception e) {
            // Log but don't fail migration
            System.err.println("Failed to enhance schema history: " + e.getMessage());
        }
    }
}
```

**Usage:**
```java
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .callbacks(new SchemaHistoryCallback())
    .load();
```

---

## 🚫 NOT Recommended: Direct ALTER TABLE in Migration

### Why Not?

```sql
-- ❌ DON'T DO THIS in a regular migration file
ALTER TABLE flyway_schema_history ADD COLUMN rolled_back BOOLEAN;
```

**Problems:**
1. Flyway tracks this as a migration
2. Can't easily skip if already applied
3. Breaks on re-baseline
4. Not idempotent
5. Fails if column exists

---

## 📊 Comparison of Approaches

| Approach | Pros | Cons | Recommended? |
|----------|------|------|--------------|
| **One-Time Schema Migration** | Explicit, tracked, idempotent | Requires manual call | ✅ **YES** |
| **Flyway Callback** | Automatic, Flyway-native | Less explicit | ✅ **YES** |
| **Regular Migration** | Simple | Not idempotent, tracked as migration | ❌ **NO** |
| **Manual ALTER TABLE** | Quick | No tracking, not repeatable | ❌ **NO** |

---

## 🎯 Best Practice: Metadata Table Pattern

### Create Metadata Table

```sql
CREATE TABLE IF NOT EXISTS flyway_rollback_metadata (
    enhancement_id VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL,
    applied_by VARCHAR(100),
    description VARCHAR(500)
);
```

### Track Enhancements

```java
public void recordEnhancement(Connection conn, String enhancementId, 
                             String description, String appliedBy) {
    String sql = "INSERT INTO flyway_rollback_metadata " +
                "(enhancement_id, applied_at, applied_by, description) " +
                "VALUES (?, ?, ?, ?)";
    
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, enhancementId);
        stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
        stmt.setString(3, appliedBy);
        stmt.setString(4, description);
        stmt.executeUpdate();
    }
}
```

### Check Before Applying

```java
public boolean isEnhancementApplied(Connection conn, String enhancementId) {
    String sql = "SELECT COUNT(*) FROM flyway_rollback_metadata " +
                "WHERE enhancement_id = ?";
    
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, enhancementId);
        try (ResultSet rs = stmt.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
```

---

## 🔍 Database-Specific Considerations

### PostgreSQL
```sql
ALTER TABLE flyway_schema_history 
ADD COLUMN IF NOT EXISTS rolled_back BOOLEAN DEFAULT FALSE;
```

### MySQL
```sql
ALTER TABLE flyway_schema_history 
ADD COLUMN rolled_back BOOLEAN DEFAULT FALSE;
-- Note: MySQL doesn't have IF NOT EXISTS for columns
-- Need to check INFORMATION_SCHEMA first
```

### H2
```sql
ALTER TABLE flyway_schema_history 
ADD COLUMN rolled_back BOOLEAN DEFAULT FALSE;
-- H2 will error if column exists, so check first
```

### SQL Server
```sql
IF NOT EXISTS (
    SELECT * FROM sys.columns 
    WHERE object_id = OBJECT_ID('flyway_schema_history') 
    AND name = 'rolled_back'
)
BEGIN
    ALTER TABLE flyway_schema_history 
    ADD rolled_back BIT DEFAULT 0;
END
```

---

## 💡 Our Implementation (Database-Agnostic)

```java
private void addColumnIfNotExists(Connection conn, String columnName, 
                                  String columnDef) throws SQLException {
    // Check if column exists using DatabaseMetaData
    if (columnExists(conn, "flyway_schema_history", columnName)) {
        System.out.println("Column " + columnName + " already exists, skipping.");
        return;
    }
    
    // Add column
    String sql = "ALTER TABLE flyway_schema_history " +
                "ADD COLUMN " + columnName + " " + columnDef;
    
    try (Statement stmt = conn.createStatement()) {
        stmt.execute(sql);
        System.out.println("Added column: " + columnName);
    }
}

private boolean columnExists(Connection conn, String tableName, 
                            String columnName) throws SQLException {
    DatabaseMetaData metadata = conn.getMetaData();
    try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName)) {
        return rs.next();
    }
}
```

**Advantages:**
- ✅ Works with all databases
- ✅ Uses JDBC metadata (standard)
- ✅ Idempotent
- ✅ Clear error messages

---

## 📝 Step-by-Step Guide

### Step 1: Create Metadata Table Manager

```java
public class RollbackMetadataTable {
    public void ensureTableExists(Connection conn) throws SQLException {
        // Create metadata table if not exists
    }
    
    public boolean isEnhancementApplied(Connection conn, String id) {
        // Check if enhancement was applied
    }
    
    public void recordEnhancement(Connection conn, String id, 
                                 String description, String user) {
        // Record enhancement
    }
}
```

### Step 2: Create Schema Enhancer

```java
public class SchemaHistoryEnhancer {
    private final RollbackMetadataTable metadataTable;
    
    public void enhanceSchemaHistory(Connection conn, String user) {
        // Check if already applied
        if (metadataTable.isEnhancementApplied(conn, ENHANCEMENT_ID)) {
            return;
        }
        
        // Add columns
        addColumnIfNotExists(conn, "rolled_back", "BOOLEAN DEFAULT FALSE");
        // ... add other columns
        
        // Record enhancement
        metadataTable.recordEnhancement(conn, ENHANCEMENT_ID, 
            "Added rollback audit columns", user);
    }
}
```

### Step 3: Call Enhancement

**Option A: Manual (Recommended)**
```java
try (Connection conn = dataSource.getConnection()) {
    SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();
    enhancer.enhanceSchemaHistory(conn, "admin");
}
```

**Option B: Automatic via Callback**
```java
Flyway flyway = Flyway.configure()
    .callbacks(new SchemaHistoryCallback())
    .load();
```

---

## ⚠️ Important Considerations

### 1. Timing
- **Before first migration**: Columns available immediately
- **After migrations exist**: Need to handle existing rows

### 2. Default Values
- Always provide sensible defaults
- Use NULL for optional columns
- Use FALSE for boolean flags

### 3. Data Type Compatibility
- Use standard SQL types
- Consider database-specific limitations
- Test on all target databases

### 4. Rollback Strategy
- **Can't easily rollback** schema changes
- Document the change
- Test thoroughly before production

### 5. Existing Data
```sql
-- Set default values for existing rows
UPDATE flyway_schema_history 
SET rolled_back = FALSE 
WHERE rolled_back IS NULL;
```

---

## 🎯 Summary

### ✅ DO:
1. Use metadata table to track enhancements
2. Check column existence before adding
3. Provide default values
4. Make it idempotent
5. Document the changes
6. Test on all target databases

### ❌ DON'T:
1. Add columns in regular migrations
2. Assume column doesn't exist
3. Forget default values
4. Make it database-specific
5. Skip error handling
6. Ignore existing data

---

## 📚 Related Files

- [SchemaHistoryEnhancer.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/SchemaHistoryEnhancer.java) - Our implementation
- [RollbackMetadataTable.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackMetadataTable.java) - Metadata tracker
- [SimpleH2Example.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/examples/SimpleH2Example.java) - Working example

---

## 🚀 Quick Reference

```java
// 1. Create enhancer
SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();

// 2. Enhance schema (one-time)
try (Connection conn = dataSource.getConnection()) {
    enhancer.enhanceSchemaHistory(conn, "admin");
}

// 3. Verify
SELECT * FROM flyway_rollback_metadata;
SELECT * FROM flyway_schema_history;
```

**Result**: Columns added once, tracked in metadata, safe to run multiple times!
