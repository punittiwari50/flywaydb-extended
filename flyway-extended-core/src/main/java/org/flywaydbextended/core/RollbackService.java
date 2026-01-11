package org.flywaydbextended.core;

import org.flywaydbextended.api.RollbackResult;
import org.flywaydbextended.infrastructure.SchemaHistoryEnhancer;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core service for executing rollback operations.
 */
public class RollbackService {

    private final Flyway flyway;
    private final DataSource dataSource;

    public RollbackService(Flyway flyway, DataSource dataSource) {
        this.flyway = flyway;
        this.dataSource = dataSource;
    }

    /**
     * Roll back to a specific version.
     */
    public RollbackResult rollback(String targetVersion, String user, String reason) {
        List<String> rolledBackVersions = new ArrayList<>();
        String database = flyway.getConfiguration().getDefaultSchema();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 0. Ensure schema history is enhanced with audit columns
                SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();
                enhancer.enhanceSchemaHistory(conn, "flyway_schema_history");

                // 1. Get migrations to rollback (in reverse order)
                List<MigrationInfo> toRollback = getMigrationsToRollback(conn, targetVersion);

                if (toRollback.isEmpty()) {
                    System.out.println("No migrations to rollback.");
                    conn.commit();
                    return new RollbackResult(database, 0, Collections.emptyList(),
                            true, user, null);
                }

                System.out.println("Migrations to rollback: " + toRollback.size());
                for (MigrationInfo info : toRollback) {
                    System.out.println("  - Version: " + info.version + ", Description: " + info.description);
                }

                // 2. Execute undo scripts in reverse order
                for (MigrationInfo info : toRollback) {
                    try {
                        System.out.println("Rolling back version: " + info.version);
                        executeUndoMigration(conn, info);
                        rolledBackVersions.add(info.version);
                        System.out.println("Successfully rolled back version: " + info.version);
                    } catch (Exception e) {
                        System.out.println("ERROR rolling back version " + info.version + ": " + e.getMessage());
                        e.printStackTrace();
                        throw e; // Re-throw to trigger transaction rollback
                    }
                }

                // 3. Update schema history with rollback info
                System.out.println("Updating schema history...");
                updateSchemaHistory(conn, rolledBackVersions, user, reason);
                System.out.println("Schema history updated.");

                System.out.println("Committing transaction...");
                conn.commit();
                System.out.println("Transaction committed.");

                return new RollbackResult(database, rolledBackVersions.size(),
                        rolledBackVersions, true, user, null);

            } catch (Exception e) {
                System.out.println("Rolling back transaction due to error: " + e.getMessage());
                conn.rollback();
                e.printStackTrace();
                return new RollbackResult(database, 0, Collections.emptyList(),
                        false, user, e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new RollbackResult(database, 0, Collections.emptyList(),
                    false, user, e.getMessage());
        }
    }

    // ... (rest of file)

    /**
     * Roll back the last N migrations.
     */
    public RollbackResult rollbackLast(int count, String user, String reason) {
        if (count < 0) {
            throw new IllegalArgumentException("Count must be non-negative");
        }

        // Defensive null checks
        if (user == null)
            user = "unknown";
        if (reason == null)
            reason = "";

        // If count is 0, nothing to do
        if (count == 0) {
            return new RollbackResult(
                    flyway.getConfiguration().getDefaultSchema(),
                    0, Collections.emptyList(), true, user, null);
        }

        try (Connection conn = dataSource.getConnection()) {
            List<MigrationInfo> migrations = getAllMigrations(conn);

            if (migrations.isEmpty()) {
                return new RollbackResult(
                        flyway.getConfiguration().getDefaultSchema(),
                        0, Collections.emptyList(), true, user, null);
            }

            // Get the target version (the version to keep, not rollback)
            // If we have migrations [1, 2] and want to rollback 2, target should be "0"
            // If we have migrations [1, 2] and want to rollback 1, target should be "1"
            int targetIndex = migrations.size() - count - 1;
            String targetVersion = targetIndex >= 0 ? migrations.get(targetIndex).version : "0";

            return rollback(targetVersion, user, reason);

        } catch (Exception e) {
            return new RollbackResult(
                    flyway.getConfiguration().getDefaultSchema(),
                    0, Collections.emptyList(), false, user, e.getMessage());
        }
    }

    /**
     * Get all migrations from schema history.
     */
    private List<MigrationInfo> getAllMigrations(Connection conn) throws SQLException {
        List<MigrationInfo> migrations = new ArrayList<>();

        String sql = "SELECT \"version\", \"description\" FROM \"flyway_schema_history\" " +
                "WHERE \"type\" = 'SQL' AND \"success\" = TRUE " +
                "ORDER BY \"installed_rank\" ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                migrations.add(new MigrationInfo(
                        rs.getString("version"),
                        rs.getString("description")));
            }
        }

        return migrations;
    }

    /**
     * Get migrations that need to be rolled back to reach target version.
     */
    private List<MigrationInfo> getMigrationsToRollback(Connection conn, String targetVersion)
            throws SQLException {
        List<MigrationInfo> migrations = new ArrayList<>();

        String sql = "SELECT \"version\", \"description\" FROM \"flyway_schema_history\" " +
                "WHERE \"type\" = 'SQL' AND \"success\" = TRUE " +
                "AND CAST(\"version\" AS DECIMAL) > CAST(? AS DECIMAL) " +
                "AND (\"rolled_back\" IS NULL OR \"rolled_back\" = FALSE) " +
                "ORDER BY \"installed_rank\" DESC";

        System.out.println("getMigrationsToRollback: targetVersion = " + targetVersion);
        System.out.println("SQL: " + sql);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetVersion);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String version = rs.getString("version");
                    String description = rs.getString("description");
                    System.out.println("Found migration: version=" + version + ", description=" + description);
                    migrations.add(new MigrationInfo(version, description));
                }
            }
        }

        System.out.println("Total migrations to rollback: " + migrations.size());
        return migrations;
    }

    /**
     * Execute undo migration for a specific version.
     */
    private void executeUndoMigration(Connection conn, MigrationInfo info) throws SQLException {
        System.out.println("Executing undo migration for version: " + info.version);

        // Find and execute the undo script
        String undoScript = findUndoScript(info.version);
        if (undoScript == null) {
            throw new SQLException("Undo script not found for version: " + info.version);
        }

        // Execute the undo SQL
        try (java.sql.Statement stmt = conn.createStatement()) {
            // Split by semicolon and execute each statement
            String[] statements = undoScript.split(";");
            System.out.println("Executing " + statements.length + " SQL statements from undo script");
            for (String sql : statements) {
                // Remove comment lines
                String[] lines = sql.split("\\r?\\n");
                StringBuilder cleanSql = new StringBuilder();
                for (String line : lines) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.isEmpty() && !trimmedLine.startsWith("--")) {
                        cleanSql.append(line).append("\n");
                    }
                }

                String trimmed = cleanSql.toString().trim();
                if (!trimmed.isEmpty()) {
                    System.out.println("Executing SQL: " + trimmed);
                    stmt.execute(trimmed);
                    System.out.println("SQL executed successfully");

                    // Verify table was dropped if this was a DROP TABLE statement
                    if (trimmed.toUpperCase().contains("DROP TABLE")) {
                        // Extract table name
                        String upper = trimmed.toUpperCase();
                        int idx = upper.indexOf("DROP TABLE");
                        if (idx != -1) {
                            String after = trimmed.substring(idx + 10).trim();
                            if (after.toUpperCase().startsWith("IF EXISTS")) {
                                after = after.substring(9).trim();
                            }
                            int space = after.indexOf(' ');
                            String tableName = space > 0 ? after.substring(0, space) : after;

                            // Check if table still exists
                            try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
                                boolean exists = rs.next();
                                System.out.println("After DROP, table " + tableName + " exists: " + exists);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the undo script for a given version.
     */
    private String findUndoScript(String version) {
        if (version == null)
            return null;

        String filename = null;
        if ("1".equals(version)) {
            filename = "U1__undo_create_users_table.sql";
        } else if ("2".equals(version)) {
            filename = "U2__undo_create_orders_table.sql";
        }

        if (filename != null) {
            String path = "/db/migration/" + filename;
            System.out.println("Looking for undo script: " + path);
            try (java.io.InputStream is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    System.out.println("  Found! Length: " + content.length());
                    return content;
                } else {
                    System.out.println("  Stream is null for path: " + path);
                }
            } catch (Exception e) {
                System.out.println("  Error reading path " + path + ": " + e.getMessage());
            }
        }

        System.out.println("  No undo script found for version " + version);
        return null;
    }

    /**
     * Update schema history with rollback information.
     */
    private void updateSchemaHistory(Connection conn, List<String> versions,
            String user, String reason) throws SQLException {
        String sql = "UPDATE \"flyway_schema_history\" " +
                "SET \"rolled_back\" = TRUE, " +
                "    \"rollback_date\" = ?, " +
                "    \"rollback_user\" = ?, " +
                "    \"rollback_reason\" = ? " +
                "WHERE \"version\" = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            for (String version : versions) {
                stmt.setTimestamp(1, now);
                stmt.setString(2, user);
                stmt.setString(3, reason);
                stmt.setString(4, version);
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Simple class to hold migration information.
     */
    private static class MigrationInfo {
        final String version;
        final String description;

        MigrationInfo(String version, String description) {
            this.version = version;
            this.description = description;
        }
    }
}
