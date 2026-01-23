package org.flywaydbextended.core;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FlywayExtended {
    private static final Logger LOGGER = Logger.getLogger(FlywayExtended.class.getName());
    private final Flyway flyway;

    public FlywayExtended(Flyway flyway) {
        this.flyway = flyway;
    }

    public void rollback() {
        rollback(null);
    }

    public void rollback(String targetVersion) {
        Configuration config = flyway.getConfiguration();
        DataSource dataSource = config.getDataSource();
        String table = config.getTable();

        try (Connection connection = dataSource.getConnection()) {
            boolean metaDataAutoCommit = connection.getAutoCommit();
            try {
                if (metaDataAutoCommit) {
                    connection.setAutoCommit(false);
                }

                if (targetVersion == null) {
                    // Rollback one step (default)
                    rollbackTransaction(connection, config, table, null);
                } else {
                    // Rollback TO version
                    int targetRank = -1;
                    if (!"0".equals(targetVersion)) {
                        MigrationInfo targetInfo = findVersionToRollback(connection, table, targetVersion);
                        if (targetInfo == null) {
                            throw new RuntimeException("Target version not found or not successful: " + targetVersion);
                        }
                        targetRank = targetInfo.getRank();
                    }
                    // Loop while max rank > targetRank
                    while (true) {
                        MigrationInfo lastInfo = findVersionToRollback(connection, table, null);
                        if (lastInfo == null)
                            break;
                        if (lastInfo.getRank() <= targetRank)
                            break;
                        if ("BASELINE".equalsIgnoreCase(lastInfo.getType())
                                || "SCHEMA".equalsIgnoreCase(lastInfo.getType())) {
                            LOGGER.info("Reached Baseline/Schema marker. Stopping rollback.");
                            break;
                        }

                        rollbackTransaction(connection, config, table, null); // Rollback last
                    }
                }

                connection.commit();
            } catch (Exception e) {
                if (metaDataAutoCommit) {
                    try {
                        connection.rollback();
                    } catch (SQLException ex) {
                        LOGGER.warning("Failed to rollback transaction: " + ex.getMessage());
                    }
                }
                throw new RuntimeException("Rollback failed", e);
            } finally {
                if (metaDataAutoCommit) {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ex) {
                        LOGGER.warning("Failed to restore auto-commit: " + ex.getMessage());
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database connection failed", e);
        }
    }

    private void rollbackTransaction(Connection connection, Configuration config, String table, String targetVersion)
            throws SQLException, IOException {
        // Find version to rollback
        MigrationInfo migrationInfo = findVersionToRollback(connection, table, targetVersion);

        if (migrationInfo == null) {
            LOGGER.info("No successful version found to rollback.");
            return;
        }

        // Check if already rolled back
        if ("ROLLBACK".equalsIgnoreCase(migrationInfo.getType())) {
            LOGGER.info("Version " + migrationInfo.getVersion() + " is already rolled back.");
            return;
        }

        // Check if Baseline/Schema
        if ("BASELINE".equalsIgnoreCase(migrationInfo.getType())
                || "SCHEMA".equalsIgnoreCase(migrationInfo.getType())) {
            LOGGER.info("Skipping rollback for marker: " + migrationInfo.getType());
            return;
        }

        LOGGER.info(
                "Rolling back version: " + migrationInfo.getVersion() + " (Script: " + migrationInfo.getScript() + ")");

        String undoScriptName = resolveUndoScriptName(config, migrationInfo.getScript());

        // Use pattern: beforeRollback__U1__Initial_Schema.sql
        String beforeScriptName = "beforeRollback__" + undoScriptName;
        String afterScriptName = "afterRollback__" + undoScriptName;

        // 1. Execute Global & Version-specific beforeRollback
        executeCallback(connection, config, "beforeRollback.sql");
        executeScript(connection, config, beforeScriptName, false);

        // 2. Execute Undo Script
        executeScript(connection, config, undoScriptName, true);

        // 3. Execute Version-specific & Global afterRollback
        executeScript(connection, config, afterScriptName, false);
        executeCallback(connection, config, "afterRollback.sql");

        // 4. Update History
        updateHistory(connection, table, migrationInfo.getRank());

        LOGGER.info("Rollback complete for version: " + migrationInfo.getVersion());
    }

    private MigrationInfo findVersionToRollback(Connection connection, String table, String targetVersion)
            throws SQLException {
        String query;
        if (targetVersion != null) {
            query = "SELECT \"installed_rank\", \"version\", \"description\", \"type\", \"script\" FROM \"" + table
                    + "\" WHERE \"version\" = ? AND \"success\" = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, targetVersion);
                stmt.setBoolean(2, true);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next())
                        return map(rs);
                }
            }
        } else {
            // Last successful version
            query = "SELECT \"installed_rank\", \"version\", \"description\", \"type\", \"script\" FROM \"" + table
                    + "\" WHERE \"installed_rank\" = (SELECT MAX(\"installed_rank\") FROM \"" + table
                    + "\" WHERE \"success\" = ? AND \"type\" NOT IN ('ROLLBACK', 'UNDO'))";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setBoolean(1, true);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next())
                        return map(rs);
                }
            }
        }
        return null;
    }

    private MigrationInfo map(ResultSet rs) throws SQLException {
        return new MigrationInfo(
                rs.getInt("installed_rank"),
                rs.getString("version"),
                rs.getString("description"),
                rs.getString("type"),
                rs.getString("script"));
    }

    private void executeCallback(Connection connection, Configuration config, String scriptName)
            throws SQLException, IOException {
        executeScript(connection, config, scriptName, false);
    }

    // --- Undo Configuration ---
    private String undoSqlMigrationPrefix = "U";
    private String undoSqlMigrationSeparator = "__";
    private String undoSqlMigrationSuffix = ".sql";

    public void setUndoSqlMigrationPrefix(String undoSqlMigrationPrefix) {
        this.undoSqlMigrationPrefix = undoSqlMigrationPrefix;
    }

    public void setUndoSqlMigrationSeparator(String undoSqlMigrationSeparator) {
        this.undoSqlMigrationSeparator = undoSqlMigrationSeparator;
    }

    public void setUndoSqlMigrationSuffix(String undoSqlMigrationSuffix) {
        this.undoSqlMigrationSuffix = undoSqlMigrationSuffix;
    }

    // --- Inner Class: UndoSqlScriptExecutor ---
    public static class UndoSqlScriptExecutor {
        private static final Logger LOG = Logger.getLogger(UndoSqlScriptExecutor.class.getName());

        public void execute(Connection connection, String sql) throws SQLException {
            if (sql == null || sql.trim().isEmpty())
                return;
            // Execute statement and log result set if present
            try (PreparedStatement statement = connection.prepareStatement(sql.trim())) {
                boolean hasResultSet = statement.execute();
                if (hasResultSet) {
                    logResultSet(statement);
                }
            }
        }

        private void logResultSet(Statement statement) throws SQLException {
            try (ResultSet rs = statement.getResultSet()) {
                java.sql.ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                StringBuilder table = new StringBuilder();
                table.append("\n+");
                for (int i = 1; i <= columnCount; i++)
                    table.append("----------------------+");
                table.append("\n| ");
                for (int i = 1; i <= columnCount; i++)
                    table.append(String.format("%-20s | ", metaData.getColumnLabel(i)));
                table.append("\n+");
                for (int i = 1; i <= columnCount; i++)
                    table.append("----------------------+");

                while (rs.next()) {
                    table.append("\n| ");
                    for (int i = 1; i <= columnCount; i++) {
                        String val = rs.getString(i);
                        if (val == null)
                            val = "NULL";
                        if (val.length() > 20)
                            val = val.substring(0, 17) + "...";
                        table.append(String.format("%-20s | ", val));
                    }
                }
                table.append("\n+").append(new String(new char[columnCount * 23]).replace("\0", "-")).append("+");
                LOG.info(table.toString());
            }
        }
    }

    // --- Execution Logic ---

    private void executeScript(Connection connection, Configuration config, String scriptName, boolean required)
            throws IOException, SQLException {
        // Validate name structure against Separator (basic check)
        if (!scriptName.contains(undoSqlMigrationSeparator) && !scriptName.startsWith("before")
                && !scriptName.startsWith("after")) {
            // In a strict mode we might throw, but for flexible lookup we proceed.
        }

        String sql = findScriptContent(config, scriptName);
        if (sql == null) {
            if (required) {
                throw new RuntimeException("Script not found: " + scriptName);
            }
            return;
        }

        LOGGER.info("Executing script: " + scriptName);

        UndoSqlScriptExecutor executor = new UndoSqlScriptExecutor();

        try (BufferedReader reader = new BufferedReader(new StringReader(sql))) {
            StringBuilder currentStmt = new StringBuilder();
            boolean inPlSql = false;
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                if (trimmed.equals("/")) {
                    executor.execute(connection, currentStmt.toString());
                    currentStmt.setLength(0);
                    inPlSql = false;
                    continue;
                }

                if (!inPlSql
                        && (trimmed.toUpperCase().startsWith("DECLARE") || trimmed.toUpperCase().startsWith("BEGIN"))) {
                    inPlSql = true;
                }

                currentStmt.append(line).append("\n");

                if (!inPlSql && trimmed.endsWith(";")) {
                    String stmt = currentStmt.toString().trim();
                    if (stmt.endsWith(";"))
                        stmt = stmt.substring(0, stmt.length() - 1);
                    LOGGER.info("DEBUG SQL: [" + stmt + "]");
                    executor.execute(connection, stmt);
                    currentStmt.setLength(0);
                }
            }

            String remaining = currentStmt.toString().trim();
            if (!remaining.isEmpty()) {
                if (remaining.endsWith(";"))
                    remaining = remaining.substring(0, remaining.length() - 1);
                executor.execute(connection, remaining);
            }
        }
    }

    private String resolveUndoScriptName(Configuration config, String originalScriptName) {
        String originalPrefix = config.getSqlMigrationPrefix();
        String baseName = originalScriptName;
        if (originalScriptName.startsWith(originalPrefix)) {
            baseName = originalScriptName.substring(originalPrefix.length());
        }
        return undoSqlMigrationPrefix + baseName;
    }

    private String findScriptContent(Configuration config, String scriptName) throws IOException {
        for (Location location : config.getLocations()) {
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
        return null;
    }

    private String readInputStream(InputStream is) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        }
    }

    private void updateHistory(Connection connection, String table, int installedRank) throws SQLException {
        String query = "UPDATE \"" + table + "\" SET \"type\" = 'UNDO' WHERE \"installed_rank\" = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, installedRank);
            stmt.executeUpdate();
        }
    }

    private static class MigrationInfo {
        private final int rank;
        private final String version;
        private final String description;
        private final String type;
        private final String script;

        public MigrationInfo(int rank, String version, String description, String type, String script) {
            this.rank = rank;
            this.version = version;
            this.description = description;
            this.type = type;
            this.script = script;
        }

        public int getRank() {
            return rank;
        }

        public String getVersion() {
            return version;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public String getScript() {
            return script;
        }
    }
}
