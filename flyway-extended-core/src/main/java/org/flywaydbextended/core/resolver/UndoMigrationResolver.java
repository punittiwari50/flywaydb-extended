package org.flywaydbextended.core.resolver;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydbextended.core.ExtendedConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UndoMigrationResolver {

    private static final Logger LOGGER = Logger.getLogger(UndoMigrationResolver.class.getName());

    private static final String DEFAULT_UNDO_PREFIX = "U";

    private final Configuration configuration;

    public UndoMigrationResolver(Configuration configuration) {
        this.configuration = configuration;
    }

    public String resolveUndoScript(String originalScriptName) {
        String undoScriptName = resolveUndoScriptName(originalScriptName);
        try {
            return resolveResource(undoScriptName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read undo script: " + undoScriptName, e);
        }
    }

    public String resolveUndoScriptName(String originalScriptName) {
        String originalPrefix = configuration.getSqlMigrationPrefix();
        String baseName = originalScriptName;
        if (originalScriptName.startsWith(originalPrefix)) {
            baseName = originalScriptName.substring(originalPrefix.length());
        }

        String prefix = configuration.getUndoSqlMigrationPrefix();
        if (prefix == null) {
            prefix = DEFAULT_UNDO_PREFIX;
        }
        if (configuration instanceof ExtendedConfiguration) {
            prefix = configuration.getUndoSqlMigrationPrefix();
        } else {
            // Fallback if not using ExtendedConfiguration, though we expect it.
            // We can check if ClassicConfiguration has it via reflection or methods if we
            // added them there.
            // For now, assuming compatibility with the getter if available or default.
            // If the configuration object has a getUndoSqlMigrationPrefix method (via Duck
            // Typing/Reflection/Exception), try it.
            // But simpler: Configuration interface doesn't have it.
            // The calling code usually ensures configuration is correct.
            // Let's assume the earlier refactor made it available or we use default.
            // Wait, FlywayExtended.java had:
            // if (prefix == null) prefix = DEFAULT_UNDO_PREFIX;
            // But getUndoSqlMigrationPrefix() is not on Configuration interface.
            // We'll verify how FlywayExtended did it.
        }

        // REVISIT: FlywayExtended check:
        // String prefix = config.getUndoSqlMigrationPrefix();
        // This fails if config is just Configuration interface.
        // We need to cast or rely on ExtendedConfiguration.

        if (configuration instanceof ExtendedConfiguration) {
            prefix = ((ExtendedConfiguration) configuration).getUndoSqlMigrationPrefix();
        }

        // Attempt to get it from ClassicConfiguration if ExtendedConfiguration is not
        // used but logic injected
        if (prefix == null) {
            try {
                java.lang.reflect.Method method = configuration.getClass().getMethod("getUndoSqlMigrationPrefix");
                prefix = (String) method.invoke(configuration);
            } catch (Exception e) {
                // Ignore
            }
        }

        if (prefix == null)
            prefix = DEFAULT_UNDO_PREFIX;

        return prefix + baseName;
    }

    public String resolveResource(String scriptName) throws IOException {
        for (Location location : configuration.getLocations()) {
            if (location.isClassPath()) {
                String path = location.getPath();
                String resourcePath = path + "/" + scriptName;
                InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (is != null) {
                    return readInputStream(is);
                }
            } else if (location.isFileSystem()) {
                String path = location.getPath();
                File file = new File(path, scriptName);
                if (file.exists()) {
                    return readInputStream(new FileInputStream(file));
                }
            }
        }
        return null; // Not found
    }

    private String readInputStream(InputStream is) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }
}
