package org.flywaydbextended.infrastructure;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One-time migration to add rollback audit columns to flyway_schema_history.
 * Uses metadata table to ensure it only runs once.
 */
public class SchemaHistoryEnhancer {

    private static final String ENHANCEMENT_ID = "add_rollback_audit_columns_v1";
    private static final String SCHEMA_HISTORY_TABLE = "flyway_schema_history";

    private final RollbackMetadataTable metadataTable;

    public SchemaHistoryEnhancer() {
        this.metadataTable = new RollbackMetadataTable();
    }

    /**
     * Add rollback audit columns to flyway_schema_history (one-time operation).
     */
    public void enhanceSchemaHistory(Connection conn, String user) throws SQLException {
        // Check if already applied
        if (metadataTable.isEnhancementApplied(conn, ENHANCEMENT_ID)) {
            System.out.println("Schema history enhancement already applied, skipping.");
            return;
        }

        System.out.println("Applying schema history enhancement...");

        // Add columns if they don't exist
        addColumnIfNotExists(conn, "rolled_back", "BOOLEAN DEFAULT FALSE");
        addColumnIfNotExists(conn, "rollback_date", "TIMESTAMP NULL");
        addColumnIfNotExists(conn, "rollback_user", "VARCHAR(100) NULL");
        addColumnIfNotExists(conn, "rollback_reason", "VARCHAR(500) NULL");

        // Record that enhancement was applied
        metadataTable.recordEnhancement(
                conn,
                ENHANCEMENT_ID,
                "Added rollback audit columns to flyway_schema_history",
                user);

        System.out.println("Schema history enhancement completed successfully.");
    }

    /**
     * Add a column to flyway_schema_history if it doesn't already exist.
     */
    private void addColumnIfNotExists(Connection conn, String columnName, String columnDef)
            throws SQLException {

        if (columnExists(conn, SCHEMA_HISTORY_TABLE, columnName)) {
            System.out.println("Column " + columnName + " already exists, skipping.");
            return;
        }

        String sql = "ALTER TABLE \"" + SCHEMA_HISTORY_TABLE +
                "\" ADD COLUMN \"" + columnName + "\" " + columnDef;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Added column: " + columnName);
        }
    }

    /**
     * Check if a column exists in a table.
     */
    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metadata = conn.getMetaData();

        // Try lowercase first (for quoted identifiers like "rolled_back")
        try (ResultSet rs = metadata.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                System.out.println("Checking column: " + columnName + " in table: " + tableName + " - exists: true");
                return true;
            }
        }

        // Try uppercase (for unquoted identifiers that H2 converts to uppercase)
        String tableNameUpper = tableName.toUpperCase();
        String columnNameUpper = columnName.toUpperCase();

        try (ResultSet rs = metadata.getColumns(null, null, tableNameUpper, columnNameUpper)) {
            boolean exists = rs.next();
            System.out.println("Checking column: " + columnName + " in table: " + tableName + " - exists: " + exists);
            return exists;
        }
    }
}
