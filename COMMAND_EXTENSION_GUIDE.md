# CommandExtension Guide

## What is CommandExtension?

`CommandExtension` is a Flyway extension point that allows you to **add custom commands** to Flyway, making them available via CLI, API, or Maven/Gradle plugins.

---

## 🎯 Purpose

### Add Custom Commands to Flyway

Instead of just using built-in commands like `migrate`, `clean`, `info`, you can create your own:
- `flyway rollback` - Roll back migrations
- `flyway backup` - Backup database before migration
- `flyway snapshot` - Create database snapshot
- `flyway custom-command` - Any custom operation

---

## 📋 Interface Definition

```java
public interface CommandExtension extends PluginMetadata {
    /**
     * Check if this extension handles the specified command
     */
    boolean handlesCommand(String command);
    
    /**
     * Check if this extension handles the specified parameter
     */
    boolean handlesParameter(String parameter);
    
    /**
     * Get command for a flag (optional)
     */
    default String getCommandForFlag(String flag) {
        return null;
    }
    
    /**
     * Handle the command execution
     */
    OperationResult handle(String command, Configuration config, 
                          List<String> flags) throws FlywayException;
}
```

---

## 💡 Use Cases

### 1. **Rollback Command** (Our Implementation)
Add custom rollback functionality to Flyway Community Edition

### 2. **Backup Command**
Create database backups before migrations

### 3. **Snapshot Command**
Create database snapshots for testing

### 4. **Audit Command**
Generate audit reports of migrations

### 5. **Custom Validation**
Add custom validation logic beyond Flyway's built-in validation

---

## 🔧 Our Implementation: RollbackCommandExtension

### Features

✅ **CLI Integration** - Use `flyway rollback` command  
✅ **Flexible Parameters** - Roll back by version or count  
✅ **User Tracking** - Track who performed rollback  
✅ **Reason Logging** - Document why rollback was performed  
✅ **OperationResult** - Returns standard Flyway result  

### File

[RollbackCommandExtension.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/command/RollbackCommandExtension.java)

---

## 📝 Usage

### Via Flyway CLI

```bash
# Roll back to specific version
flyway rollback -rollback.targetVersion=1.0 -rollback.user=admin -rollback.reason="Bug in v2.0"

# Roll back last 2 migrations
flyway rollback -rollback.count=2 -rollback.user=admin -rollback.reason="Testing"
```

### Via Java API

```java
// Configure Flyway
Flyway flyway = Flyway.configure()
    .dataSource(dataSource)
    .load();

// Execute rollback command
OperationResult result = flyway.execute("rollback", Arrays.asList(
    "-rollback.targetVersion=1.0",
    "-rollback.user=admin",
    "-rollback.reason=Bug fix"
));

// Check result
RollbackResult rollbackResult = (RollbackResult) result;
System.out.println("Success: " + rollbackResult.success);
System.out.println("Rolled back: " + rollbackResult.migrationsRolledBack);
```

### Via Maven

```xml
<plugin>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-maven-plugin</artifactId>
    <version>11.2.0</version>
    <configuration>
        <command>rollback</command>
        <rollback.targetVersion>1.0</rollback.targetVersion>
        <rollback.user>admin</rollback.user>
        <rollback.reason>Bug fix</rollback.reason>
    </configuration>
</plugin>
```

```bash
mvn flyway:rollback
```

---

## 🎨 Implementation Details

### 1. Command Handling

```java
@Override
public boolean handlesCommand(String command) {
    return "rollback".equalsIgnoreCase(command);
}
```

**What it does**: Tells Flyway this extension handles the "rollback" command

### 2. Parameter Handling

```java
@Override
public boolean handlesParameter(String parameter) {
    return parameter.startsWith("rollback.");
}
```

**What it does**: Tells Flyway this extension handles parameters like:
- `rollback.targetVersion`
- `rollback.count`
- `rollback.user`
- `rollback.reason`

### 3. Command Execution

```java
@Override
public OperationResult handle(String command, Configuration config, 
                              List<String> flags) {
    // 1. Parse flags
    String targetVersion = extractFlag(flags, "-rollback.targetVersion=");
    Integer count = extractIntFlag(flags, "-rollback.count=");
    String user = extractFlag(flags, "-rollback.user=");
    String reason = extractFlag(flags, "-rollback.reason=");
    
    // 2. Create service
    RollbackService service = new RollbackService(flyway, dataSource);
    
    // 3. Execute rollback
    RollbackResult result;
    if (count != null) {
        result = service.rollbackLast(count, user, reason);
    } else {
        result = service.rollback(targetVersion, user, reason);
    }
    
    // 4. Return result
    return result;
}
```

---

## 🔄 How Flyway Discovers CommandExtensions

### 1. Service Provider Interface (SPI)

Create file: `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.CommandExtension`

```
com.example.flywayextended.command.RollbackCommandExtension
```

### 2. Flyway Auto-Discovery

Flyway automatically discovers and loads all `CommandExtension` implementations on the classpath.

### 3. Plugin Registration

```java
// Flyway internally does:
List<CommandExtension> extensions = 
    configuration.getPluginRegister()
        .getInstancesOf(CommandExtension.class);

// Find extension that handles command
CommandExtension extension = extensions.stream()
    .filter(ext -> ext.handlesCommand("rollback"))
    .findFirst()
    .orElseThrow();

// Execute command
OperationResult result = extension.handle("rollback", config, flags);
```

---

## 📊 Comparison with Other Approaches

| Approach | CLI Support | API Support | Complexity | Flexibility |
|----------|-------------|-------------|------------|-------------|
| **CommandExtension** | ✅ Yes | ✅ Yes | ⚠️ Medium | ✅ High |
| **Direct Service Call** | ❌ No | ✅ Yes | ✅ Low | ⚠️ Medium |
| **Callback** | ❌ No | ⚠️ Limited | ✅ Low | ⚠️ Low |

---

## 🎯 Benefits of CommandExtension

### 1. **CLI Integration**
```bash
# Works just like built-in commands
flyway rollback -rollback.targetVersion=1.0
flyway migrate
flyway info
```

### 2. **Consistent Interface**
```java
// Same API for all commands
flyway.execute("migrate");
flyway.execute("rollback", flags);
flyway.execute("custom-command", flags);
```

### 3. **Maven/Gradle Integration**
```xml
<configuration>
    <command>rollback</command>
</configuration>
```

### 4. **Standard Result Handling**
```java
OperationResult result = flyway.execute("rollback", flags);
// Result can be logged, saved, processed like any Flyway result
```

---

## 🚀 Complete Example

### Step 1: Create CommandExtension

```java
public class RollbackCommandExtension implements CommandExtension {
    @Override
    public boolean handlesCommand(String command) {
        return "rollback".equalsIgnoreCase(command);
    }
    
    @Override
    public boolean handlesParameter(String parameter) {
        return parameter.startsWith("rollback.");
    }
    
    @Override
    public OperationResult handle(String command, Configuration config, 
                                  List<String> flags) {
        // Implementation
        RollbackService service = new RollbackService(flyway, dataSource);
        return service.rollback(targetVersion, user, reason);
    }
}
```

### Step 2: Register Extension

Create `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.CommandExtension`:

```
com.example.flywayextended.command.RollbackCommandExtension
```

### Step 3: Use Command

**CLI:**
```bash
flyway rollback -rollback.targetVersion=1.0
```

**Java:**
```java
Flyway flyway = Flyway.configure().dataSource(dataSource).load();
flyway.execute("rollback", Arrays.asList("-rollback.targetVersion=1.0"));
```

**Maven:**
```bash
mvn flyway:rollback -Dflyway.rollback.targetVersion=1.0
```

---

## 📋 Parameters Reference

### Rollback Command Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `rollback.targetVersion` | String | Yes* | Version to roll back to | `1.0` |
| `rollback.count` | Integer | Yes* | Number of migrations to roll back | `2` |
| `rollback.user` | String | No | User performing rollback | `admin` |
| `rollback.reason` | String | No | Reason for rollback | `Bug fix` |

*Either `targetVersion` or `count` must be specified

---

## ⚠️ Important Considerations

### 1. **API Stability**
- `CommandExtension` is marked as "under development"
- May change in future Flyway versions
- Test thoroughly when upgrading

### 2. **Error Handling**
```java
@Override
public OperationResult handle(String command, Configuration config, 
                              List<String> flags) throws FlywayException {
    try {
        // Your logic
    } catch (Exception e) {
        throw new FlywayException("Command failed: " + e.getMessage(), e);
    }
}
```

### 3. **Parameter Validation**
```java
if (targetVersion == null && count == null) {
    throw new FlywayException(
        "Either -rollback.targetVersion or -rollback.count must be specified"
    );
}
```

### 4. **Result Object**
```java
// Must return OperationResult
public class RollbackResult implements OperationResult {
    public final boolean success;
    public final int migrationsRolledBack;
    // ...
}
```

---

## 🎨 Advanced Examples

### Example 1: Backup Command

```java
public class BackupCommandExtension implements CommandExtension {
    @Override
    public boolean handlesCommand(String command) {
        return "backup".equalsIgnoreCase(command);
    }
    
    @Override
    public OperationResult handle(String command, Configuration config, 
                                  List<String> flags) {
        String backupPath = extractFlag(flags, "-backup.path=");
        // Create database backup
        createBackup(config.getDataSource(), backupPath);
        return new BackupResult(backupPath, true);
    }
}
```

**Usage:**
```bash
flyway backup -backup.path=./backups/db-backup.sql
```

### Example 2: Snapshot Command

```java
public class SnapshotCommandExtension implements CommandExtension {
    @Override
    public boolean handlesCommand(String command) {
        return "snapshot".equalsIgnoreCase(command);
    }
    
    @Override
    public OperationResult handle(String command, Configuration config, 
                                  List<String> flags) {
        String snapshotName = extractFlag(flags, "-snapshot.name=");
        // Create database snapshot
        createSnapshot(config.getDataSource(), snapshotName);
        return new SnapshotResult(snapshotName, true);
    }
}
```

**Usage:**
```bash
flyway snapshot -snapshot.name=before-migration
```

---

## 📚 Related Files

- [RollbackCommandExtension.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/command/RollbackCommandExtension.java) - Our implementation
- [RollbackService.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackService.java) - Rollback logic
- [RollbackResult.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/rollback/RollbackResult.java) - Result object

---

## 🎯 Summary

### ✅ CommandExtension Enables:
1. **Custom CLI commands** - Add new commands to Flyway CLI
2. **Parameter handling** - Define custom parameters
3. **Standard integration** - Works with CLI, API, Maven, Gradle
4. **Consistent results** - Returns `OperationResult` like built-in commands

### ✅ Our Implementation:
1. **Rollback command** - `flyway rollback`
2. **Flexible parameters** - Version or count based
3. **Audit tracking** - User and reason logging
4. **Production ready** - Error handling, validation

### ✅ Use When:
- You want CLI integration for custom commands
- You need Maven/Gradle plugin support
- You want consistent interface with Flyway commands
- You're building reusable Flyway extensions

---

## 🚀 Quick Start

```bash
# 1. Build project
cd c:\DEV\SOURCES\SOURCE_DIR\flyway-extended
mvnw.cmd clean package

# 2. Add to Flyway classpath
# Copy JAR to Flyway's lib directory or add to classpath

# 3. Use rollback command
flyway rollback -rollback.targetVersion=1.0 -rollback.user=admin
```

**Result**: Custom rollback command integrated into Flyway!
