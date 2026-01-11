# META-INF/services - Java Service Provider Interface (SPI) Explained

## What is META-INF/services?

The `META-INF/services` directory is part of Java's **Service Provider Interface (SPI)** mechanism. It allows libraries to discover and load implementations of interfaces **automatically at runtime** without hardcoding them.

---

## 🎯 Purpose

### Automatic Plugin Discovery

Instead of manually registering plugins:
```java
// ❌ Manual registration (hard-coded)
List<Plugin> plugins = new ArrayList<>();
plugins.add(new RollbackCommandExtension());
plugins.add(new AuditStatementInterceptor());
plugins.add(new ResultOutputCallback());
```

SPI allows automatic discovery:
```java
// ✅ Automatic discovery via SPI
ServiceLoader<Plugin> loader = ServiceLoader.load(Plugin.class);
List<Plugin> plugins = new ArrayList<>();
loader.forEach(plugins::add);
```

---

## 📂 File Structure

### Location
```
src/main/resources/
└── META-INF/
    └── services/
        ├── org.flywaydb.core.extensibility.Plugin
        ├── org.flywaydb.core.extensibility.CommandExtension
        ├── org.flywaydb.core.api.callback.Callback
        └── [other interface names]
```

### File Naming Convention

**Rule**: The filename MUST be the **fully qualified interface name**

Examples:
- `org.flywaydb.core.extensibility.Plugin`
- `org.flywaydb.core.extensibility.CommandExtension`
- `org.flywaydb.core.api.callback.Callback`
- `org.flywaydb.core.extensibility.ConfigurationExtension`

---

## 📝 File Content Format

### Basic Format

Each line contains the **fully qualified class name** of an implementation:

```
# File: org.flywaydb.core.extensibility.CommandExtension
com.example.flywayextended.command.RollbackCommandExtension
```

### Multiple Implementations

```
# File: org.flywaydb.core.api.callback.Callback
com.example.flywayextended.output.ResultOutputCallback
com.example.flywayextended.rollback.SchemaHistoryCallback
com.example.flywayextended.logging.SqlLoggingCallback
```

### With Comments

```
# File: org.flywaydb.core.extensibility.Plugin

# Rollback functionality
com.example.flywayextended.command.RollbackCommandExtension

# SQL auditing
com.example.flywayextended.interceptor.AuditStatementInterceptor

# Result output
com.example.flywayextended.output.ResultOutputCallback
```

---

## 🔧 How Flyway Uses SPI

### 1. Plugin Discovery

Flyway uses `ServiceLoader` to discover all plugins:

```java
// Flyway internal code
public class PluginRegister {
    public <T extends Plugin> List<T> getInstancesOf(Class<T> pluginClass) {
        ServiceLoader<T> loader = ServiceLoader.load(pluginClass);
        List<T> instances = new ArrayList<>();
        loader.forEach(instances::add);
        return instances;
    }
}
```

### 2. Extension Loading

When Flyway starts:
```java
// 1. Load all Plugin implementations
ServiceLoader<Plugin> plugins = ServiceLoader.load(Plugin.class);

// 2. Filter by specific type
List<CommandExtension> commands = plugins.stream()
    .filter(p -> p instanceof CommandExtension)
    .map(p -> (CommandExtension) p)
    .collect(Collectors.toList());

// 3. Use the extensions
for (CommandExtension cmd : commands) {
    if (cmd.handlesCommand("rollback")) {
        cmd.handle("rollback", config, flags);
    }
}
```

---

## 📋 Our Project's SPI Files

### File 1: CommandExtension

**Location**: `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.CommandExtension`

**Content**:
```
com.example.flywayextended.command.RollbackCommandExtension
```

**Purpose**: Registers our custom `rollback` command with Flyway

**Result**: Enables `flyway rollback` CLI command

---

### File 2: Plugin (Recommended)

**Location**: `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.Plugin`

**Content**:
```
# Command Extensions
com.example.flywayextended.command.RollbackCommandExtension

# Configuration Extensions
com.example.flywayextended.rollback.RollbackConfigurationExtension
com.example.flywayextended.output.ResultOutputConfiguration

# Callbacks
com.example.flywayextended.output.ResultOutputCallback

# Statement Interceptors
com.example.flywayextended.interceptor.AuditStatementInterceptor
```

**Purpose**: Registers ALL our plugins with Flyway

**Why?**: `Plugin` is the base interface for all Flyway extensions, so registering here covers everything

---

## 🎨 Complete Example

### Step 1: Create Implementation

```java
package com.example.flywayextended.command;

import org.flywaydb.core.extensibility.CommandExtension;

public class RollbackCommandExtension implements CommandExtension {
    @Override
    public boolean handlesCommand(String command) {
        return "rollback".equalsIgnoreCase(command);
    }
    
    @Override
    public OperationResult handle(String command, Configuration config, 
                                  List<String> flags) {
        // Implementation
    }
}
```

### Step 2: Create SPI File

**File**: `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.CommandExtension`

**Content**:
```
com.example.flywayextended.command.RollbackCommandExtension
```

### Step 3: Build JAR

```bash
mvn clean package
```

**Result**: JAR contains:
```
flyway-extended-1.0-SNAPSHOT.jar
├── com/example/flywayextended/command/
│   └── RollbackCommandExtension.class
└── META-INF/
    └── services/
        └── org.flywaydb.core.extensibility.CommandExtension
```

### Step 4: Use Extension

```bash
# Add JAR to Flyway classpath
flyway -classpath=flyway-extended-1.0-SNAPSHOT.jar rollback -rollback.targetVersion=1.0

# Or copy to Flyway's lib directory
cp flyway-extended-1.0-SNAPSHOT.jar $FLYWAY_HOME/lib/

# Then use directly
flyway rollback -rollback.targetVersion=1.0
```

---

## 🔍 How to Debug SPI Loading

### Check if File Exists in JAR

```bash
jar -tf flyway-extended-1.0-SNAPSHOT.jar | grep META-INF/services
```

**Expected output**:
```
META-INF/services/org.flywaydb.core.extensibility.CommandExtension
META-INF/services/org.flywaydb.core.extensibility.Plugin
```

### Check File Content in JAR

```bash
jar -xf flyway-extended-1.0-SNAPSHOT.jar META-INF/services/org.flywaydb.core.extensibility.CommandExtension
cat META-INF/services/org.flywaydb.core.extensibility.CommandExtension
```

### Enable Java SPI Debugging

```bash
java -Djava.util.logging.config.file=logging.properties -jar app.jar
```

**logging.properties**:
```properties
java.util.ServiceLoader.level=FINE
```

---

## ⚠️ Common Mistakes

### 1. Wrong Filename

❌ **Wrong**:
```
META-INF/services/CommandExtension
META-INF/services/RollbackCommandExtension
```

✅ **Correct**:
```
META-INF/services/org.flywaydb.core.extensibility.CommandExtension
```

### 2. Wrong Package in Content

❌ **Wrong**:
```
# File content
RollbackCommandExtension
command.RollbackCommandExtension
```

✅ **Correct**:
```
# File content
com.example.flywayextended.command.RollbackCommandExtension
```

### 3. Missing File in JAR

**Problem**: File exists in `src/main/resources` but not in JAR

**Solution**: Check Maven resource configuration:
```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <includes>
                <include>**/*</include>
            </includes>
        </resource>
    </resources>
</build>
```

### 4. Class Not Found

**Problem**: SPI file references class that doesn't exist

**Error**:
```
java.util.ServiceConfigurationError: Provider com.example.NonExistentClass not found
```

**Solution**: Ensure class is compiled and in JAR

---

## 📊 Comparison: SPI vs Manual Registration

| Aspect | SPI (META-INF/services) | Manual Registration |
|--------|-------------------------|---------------------|
| **Discovery** | Automatic | Manual |
| **Coupling** | Loose | Tight |
| **Extensibility** | High | Low |
| **Configuration** | File-based | Code-based |
| **Modularity** | Excellent | Poor |
| **JAR Distribution** | Easy | Complex |

---

## 🎯 Benefits of SPI

### 1. **Loose Coupling**
```java
// No need to import implementation classes
// Flyway doesn't need to know about RollbackCommandExtension
ServiceLoader<CommandExtension> loader = ServiceLoader.load(CommandExtension.class);
```

### 2. **Plugin Architecture**
```
# Just drop JAR in lib directory
$FLYWAY_HOME/lib/
├── flyway-core.jar
├── flyway-extended.jar  # Contains our extensions
└── other-plugin.jar     # Other plugins
```

### 3. **No Code Changes**
```bash
# Add new functionality without modifying Flyway code
# Just add JAR to classpath
```

### 4. **Multiple Implementations**
```
# File: org.flywaydb.core.api.callback.Callback
com.example.plugin1.Callback1
com.example.plugin2.Callback2
com.example.plugin3.Callback3
```

---

## 🚀 Our Project's SPI Setup

### Files We Need to Create

#### 1. CommandExtension Registration

**File**: `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.CommandExtension`

```
com.example.flywayextended.command.RollbackCommandExtension
```

#### 2. Plugin Registration (Comprehensive)

**File**: `src/main/resources/META-INF/services/org.flywaydb.core.extensibility.Plugin`

```
# Command Extensions
com.example.flywayextended.command.RollbackCommandExtension

# Configuration Extensions  
com.example.flywayextended.rollback.RollbackConfigurationExtension
com.example.flywayextended.output.ResultOutputConfiguration

# Callbacks
com.example.flywayextended.output.ResultOutputCallback

# Statement Interceptors
com.example.flywayextended.interceptor.AuditStatementInterceptor
```

---

## 📝 Best Practices

### 1. **Use Comments**
```
# Rollback functionality
com.example.flywayextended.command.RollbackCommandExtension

# SQL auditing
com.example.flywayextended.interceptor.AuditStatementInterceptor
```

### 2. **One Implementation Per Line**
```
# ✅ Good
com.example.Plugin1
com.example.Plugin2

# ❌ Bad
com.example.Plugin1, com.example.Plugin2
```

### 3. **Fully Qualified Names**
```
# ✅ Good
com.example.flywayextended.command.RollbackCommandExtension

# ❌ Bad
RollbackCommandExtension
command.RollbackCommandExtension
```

### 4. **Verify in JAR**
```bash
# Always verify after building
jar -tf target/flyway-extended-1.0-SNAPSHOT.jar | grep META-INF/services
```

---

## 🔧 Maven Configuration

### Ensure Resources are Included

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <filtering>false</filtering>
        </resource>
    </resources>
</build>
```

### Maven Shade Plugin (for Uber JAR)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <configuration>
        <transformers>
            <!-- Merge SPI files from dependencies -->
            <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
        </transformers>
    </configuration>
</plugin>
```

---

## 🎓 Summary

### What is META-INF/services?
- Java's **Service Provider Interface (SPI)** mechanism
- Enables **automatic plugin discovery**
- **File-based** configuration

### How it Works?
1. Create file named after **interface** (fully qualified)
2. List **implementation classes** (fully qualified) in file
3. Java's `ServiceLoader` **automatically discovers** implementations
4. Flyway **loads and uses** the plugins

### Why Use It?
- ✅ **Loose coupling** - No hardcoded dependencies
- ✅ **Plugin architecture** - Easy to extend
- ✅ **No code changes** - Just add JAR
- ✅ **Standard Java** - Well-supported mechanism

### Our Usage
- `CommandExtension` - Adds `rollback` command
- `Plugin` - Registers all our extensions
- Enables Flyway to **automatically discover** our rollback functionality

---

## 📚 Related Files

- [RollbackCommandExtension.java](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/java/com/example/flywayextended/command/RollbackCommandExtension.java) - Implementation
- [org.flywaydb.core.extensibility.CommandExtension](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/src/main/resources/META-INF/services/org.flywaydb.core.extensibility.CommandExtension) - SPI file
- [COMMAND_EXTENSION_GUIDE.md](file:///c:/DEV/SOURCES/SOURCE_DIR/flyway-extended/COMMAND_EXTENSION_GUIDE.md) - CommandExtension guide

---

## 🚀 Quick Reference

```bash
# 1. Create SPI file
mkdir -p src/main/resources/META-INF/services
echo "com.example.MyPlugin" > src/main/resources/META-INF/services/org.flywaydb.core.extensibility.Plugin

# 2. Build JAR
mvn clean package

# 3. Verify
jar -tf target/my-plugin.jar | grep META-INF/services

# 4. Use
flyway -classpath=my-plugin.jar custom-command
```

**Result**: Your plugin is automatically discovered and loaded by Flyway!
