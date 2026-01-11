package org.flywaydbextended.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Manages the flyway_rollback_metadata table for tracking one-time schema
 * enhancements.
 */
public class RollbackMetadataTable {

    private static final String TABLE_NAME = "flyway_rollback_metadata";

    /**
     * Ensure the metadata table exists.
     */
    public void ensureTableExists(Connection conn) throws SQLException {
        String createTableSql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "  enhancement_id VARCHAR(100) PRIMARY KEY," +
                "  applied_at TIMESTAMP NOT NULL," +
                "  applied_by VARCHAR(100)," +
                "  description VARCHAR(500)" +
                ")";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
        }
    }

    /**
     * Check if an enhancement has already been applied.
     */
    public boolean isEnhancementApplied(Connection conn, String enhancementId)
            throws SQLException {
        ensureTableExists(conn);

        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME +
                " WHERE enhancement_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, enhancementId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Record that an enhancement has been applied.
     */
    public void recordEnhancement(Connection conn, String enhancementId,
            String description, String appliedBy)
            throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME +
                " (enhancement_id, applied_at, applied_by, description) " +
                "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, enhancementId);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(3, appliedBy);
            stmt.setString(4, description);
            stmt.executeUpdate();
        }
    }
}
