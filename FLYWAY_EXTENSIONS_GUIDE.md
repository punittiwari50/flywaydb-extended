# Flyway Extension Points - Comprehensive Guide

This guide explains Flyway's extension mechanisms in simple terms with practical examples, focusing on implementing rollback functionality with audit tracking.

---

## 1. CherryPickConfiguration

### What It Does (Simple Words)
Lets you **pick and choose which migrations to run** instead of running all of them. Like selecting specific items from a menu instead of ordering everything.

### Use Case
- Run only specific migrations (e.g., V1, V5, V10) while skipping others
- Useful for testing individual migrations
- Helpful when you want to apply only certain fixes to production

### Example
```java
public class CustomCherryPickConfig implements CherryPickConfiguration {
    
    @Override
    public MigrationPattern[] getMigrationPatterns(Configuration config) {
        // Only run migrations V1, V2, and V5
        return new MigrationPattern[] {
            MigrationPattern.fromPattern("1"),
            MigrationPattern.fromPattern("2"),
            MigrationPattern.fromPattern("5")
        };
    }
    
    @Override
    public String getName() {
        return "CustomCherryPick";
    }
}
```

---

## 2. Plugin

### What It Does (Simple Words)
The **base interface for all Flyway extensions**. Think of it as a contract that says "I'm a Flyway add-on" and allows you to control when your extension is active.

### Use Case
- Enable/disable extensions based on license
- Set priority when multiple extensions compete
- Control which extensions run in which environments

---

## 3. MigrationResolver

### What It Does (Simple Words)
**Finds and loads migrations** from custom sources. By default, Flyway looks for `.sql` files, but you can teach it to read from anywhere: JSON files, databases, REST APIs, etc.

### Use Case
- Load migrations from a database table
- Read migrations from JSON/YAML/XML files
- Fetch migrations from a REST API
- Support custom naming conventions

---

## 4. SqlMigrationExecutorFactory

### What It Does (Simple Words)
**Creates the object that actually runs SQL migrations**. Lets you customize how SQL scripts are executed (e.g., add logging, modify SQL, handle errors differently).

---

## 5. ConfigurationExtension

### What It Does (Simple Words)
**Adds custom configuration options to Flyway**. Like adding new settings that your custom plugins can use.

---

## 6. Resource vs LoadableResource

### What It Does (Simple Words)
- **Resource**: Basic information about a file (path, filename) - like a file's address
- **LoadableResource**: Can actually read the file's contents - like opening and reading the file

---

## 7. ResourceTypeProvider

### What It Does (Simple Words)
**Tells Flyway what types of files to recognize** based on their prefix. For example, "V" = versioned migration, "R" = repeatable migration, "U" = undo migration.

---

## 8. OperationResult

### What It Does (Simple Words)
**Represents the result of a Flyway operation** (migrate, clean, info, etc.). Contains information about what happened.

### Use Case
- Return results from custom commands
- Log operation outcomes
- Display operation summaries

---

## 9. CommandExtension

### What It Does (Simple Words)
**Adds custom commands to Flyway**. Like adding your own buttons to Flyway's control panel - you can create commands like `flyway rollback`, `flyway backup`, etc.

### Use Case
- Add rollback command to Flyway CLI
- Create custom database operations
- Integrate with CI/CD pipelines
- Build reusable Flyway extensions

### Example
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
        // Execute rollback logic
        RollbackService service = new RollbackService(flyway, dataSource);
        return service.rollback(targetVersion, user, reason);
    }
}
```

**Usage:**
```bash
flyway rollback -rollback.targetVersion=1.0 -rollback.user=admin
```

---

For complete code examples and detailed implementation guide, see:
- `IMPLEMENTATION_PLAN.md` - Full implementation strategy
- `ENHANCED_CODE_EXAMPLES.md` - Working code examples
- `COMMAND_EXTENSION_GUIDE.md` - CommandExtension details
