# Flyway Extension Examples

This document outlines how to extend Flyway functionality using three main methods.

## 1. Wrapper Application (Custom Commands)
**When to use:**
- You need completely new functionality (e.g., `rollback` in CE, `snapshot`, `sync`).
- You need to combine multiple Flyway commands or enforce specific arguments.

**Code Example:**
See [FlywayWrapper.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/FlywayWrapper.java). This wraps `Flyway` and adds a custom `doRollback` method.

## 2. Callbacks (Lifecycle Hooks)
**When to use:**
- Logging or auditing events (start/finish).
- Sending notifications (Slack, Email).
- Pre-checks (e.g., specific DB state) or post-checks.

**Optionality:**
- Optional. Use only if you need side effects.

**Independent Code Example:**
```java
package com.example.flywayextended;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

public class NotificationCallback implements Callback {
    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE || event == Event.AFTER_MIGRATE;
    }
    @Override
    public boolean canHandleInTransaction(Event event, Context context) { return true; }
    @Override
    public void handle(Event event, Context context) {
        if (event == Event.BEFORE_MIGRATE) {
            System.out.println(">> Notification: Starting...");
        } else if (event == Event.AFTER_MIGRATE) {
            System.out.println(">> Notification: Finished!");
        }
    }
    @Override
    public String getCallbackName() { return "NotificationCallback"; }
}
```

## 3. Java Migrations (Plugins/Resolvers)
**When to use:**
- SQL is insufficient (complex logic, blobs, external APIs).
- Dynamic data insertion.

**Optionality:**
- Optional. Most migrations should be SQL.

**Independent Code Example:**
```java
package com.example.flywayextended;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.MigrationVersion;

public class V3__Dynamic_Java_Migration implements JavaMigration {
    @Override
    public MigrationVersion getVersion() { return MigrationVersion.fromVersion("3"); }
    @Override
    public String getDescription() { return "Dynamic Migration"; }
    @Override
    public Integer getChecksum() { return 123456; }
    @Override
    public boolean canExecuteInTransaction() { return true; }
    @Override
    public void migrate(Context context) throws Exception {
        System.out.println(">> Executing Java Logic");
        try (var stmt = context.getConnection().createStatement()) {
            stmt.execute("INSERT INTO test_table (id, name) VALUES (3, 'Java Data')");
        }
    }
}
```

## 4. Custom MigrationResolver (Advanced Plugin)
**When to use:**
- Reading migrations from custom formats (JSON, YAML, XML).
- Loading migrations from external sources (REST API, database).
- Supporting custom naming conventions beyond V/R/U prefixes.

**Optionality:**
- Optional. Only needed for non-standard migration sources.

### Architecture Overview
A `MigrationResolver` is responsible for discovering and resolving migrations into `ResolvedMigration` objects that Flyway can execute. The resolver lifecycle:

1. **Discovery**: Scan configured locations for migration sources
2. **Parsing**: Extract version, description, and content from each source
3. **Resolution**: Create `ResolvedMigration` objects with executors
4. **Registration**: Return collection to Flyway for execution

### Key Interfaces

#### MigrationResolver
```java
public interface MigrationResolver extends Plugin {
    // Main method: return all discovered migrations
    Collection<ResolvedMigration> resolveMigrations(Context context);
    
    // Optional: prefix for this resolver (e.g., "V", "R", "U")
    default String getPrefix(Configuration configuration) { return null; }
    
    // Optional: default migration type
    default MigrationType getDefaultMigrationType() { return null; }
}
```

#### ResolvedMigration
Each resolved migration must provide:
- `MigrationVersion getVersion()` - Version number (null for repeatable)
- `String getDescription()` - Human-readable description
- `String getScript()` - Script name/path
- `Integer getChecksum()` - Optional checksum for change detection
- `MigrationType getType()` - Migration type (SQL, JDBC, etc.)
- `String getPhysicalLocation()` - File path for error reporting
- `MigrationExecutor getExecutor()` - Executor to run the migration

#### MigrationExecutor
The executor performs the actual migration:
```java
public interface MigrationExecutor {
    void execute(Context context) throws SQLException;
    boolean canExecuteInTransaction();
    boolean shouldExecute();
}
```

### Implementation Example

**Independent Code Example:**
```java
package com.example.flywayextended;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.resolver.ResolvedMigrationImpl;
import org.flywaydb.core.api.CoreMigrationType;
import java.util.ArrayList;
import java.util.Collection;

public class JsonMigrationResolver implements MigrationResolver {
    @Override
    public Collection<ResolvedMigration> resolveMigrations(Context context) {
        Collection<ResolvedMigration> migrations = new ArrayList<>();
        
        // Access Flyway's resource provider to scan for files
        var resources = context.resourceProvider.getResources("", new String[]{".json"});
        
        for (var resource : resources) {
            String filename = resource.getFilename();
            
            // Follow Flyway naming convention: V{version}__{description}.json
            if (!filename.startsWith("V") || !filename.contains("__")) continue;
            
            try {
                // Parse version and description from filename
                String versionPart = filename.substring(1, filename.indexOf("__"));
                String descPart = filename.substring(filename.indexOf("__") + 2, filename.lastIndexOf(".json"));
                MigrationVersion version = MigrationVersion.fromVersion(versionPart);
                String description = descPart.replace("_", " ");
                
                // Read and parse JSON to extract SQL
                String sql = extractSqlFromJson(resource);
                
                // Create executor that will run the SQL
                var executor = new JsonMigrationExecutor(sql);
                
                // Create ResolvedMigration with all required metadata
                migrations.add(new ResolvedMigrationImpl(
                    version,                          // Version
                    description,                      // Description
                    resource.getRelativePath(),       // Script path
                    null,                             // Checksum (optional)
                    null,                             // Equivalent checksum
                    CoreMigrationType.SQL,            // Type
                    resource.getAbsolutePathOnDisk(), // Physical location
                    executor                          // Executor
                ));
            } catch (Exception e) {
                System.err.println("Failed to parse: " + filename);
            }
        }
        return migrations;
    }
    
    @Override
    public String getPrefix(org.flywaydb.core.api.configuration.Configuration config) {
        return "V"; // Same prefix as standard SQL migrations
    }
}
```

**Executor Implementation:**
```java
package com.example.flywayextended;
import org.flywaydb.core.api.executor.Context;
import org.flywaydb.core.api.executor.MigrationExecutor;
import java.sql.SQLException;

public class JsonMigrationExecutor implements MigrationExecutor {
    private final String sql;
    
    public JsonMigrationExecutor(String sql) {
        this.sql = sql;
    }
    
    @Override
    public void execute(Context context) throws SQLException {
        try (var statement = context.getConnection().createStatement()) {
            statement.execute(sql);
        }
    }
    
    @Override
    public boolean canExecuteInTransaction() {
        return true; // Most migrations should run in transactions
    }
    
    @Override
    public boolean shouldExecute() {
        return true; // Always execute unless conditional logic needed
    }
}
```

**Example JSON Migration File** (`V4__Add_JSON_Migration_Table.json`):
```json
{
  "version": "4",
  "description": "Add JSON Migration Table",
  "sql": "CREATE TABLE json_migration_test (id INT PRIMARY KEY, data VARCHAR(255))"
}
```

### Best Practices

1. **Naming Conventions**: Follow Flyway's standard naming (V/R prefix) for consistency
2. **Checksums**: Implement checksum calculation to detect changes to applied migrations
3. **Error Handling**: Gracefully handle malformed migration files
4. **Resource Cleanup**: Ensure streams and resources are properly closed
5. **Transaction Safety**: Set `canExecuteInTransaction()` appropriately based on SQL content
6. **Logging**: Provide clear error messages for debugging

### Context Object
The `Context` parameter provides access to:
- `configuration` - Flyway configuration settings
- `resourceProvider` - Scans classpath/filesystem for resources
- `sqlScriptFactory` - Creates SQL script objects (for SQL-based migrations)
- `sqlScriptExecutorFactory` - Creates SQL executors
- `statementInterceptor` - Intercepts SQL statements for logging/modification



## How to Integrate
To use these:
1.  **Callbacks**: Register via `flyway.configure().callbacks(new NotificationCallback())`.
2.  **Java Migrations**: Register via `flyway.configure().javaMigrations(new V3__Dynamic_Java_Migration())` OR ensure they are in the classpath and scanning is enabled.
3.  **Custom Resolvers**: Register via `flyway.configure().resolvers(new JsonMigrationResolver())` OR use Java's ServiceLoader mechanism by creating `META-INF/services/org.flywaydb.core.api.resolver.MigrationResolver` file.
