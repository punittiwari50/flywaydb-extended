package org.flywaydbextended.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for RollbackMetadataTable.
 */
@DisplayName("RollbackMetadataTable Tests")
class RollbackMetadataTableTest {

    private RollbackMetadataTable metadataTable;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        metadataTable = new RollbackMetadataTable();
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:metadatatest;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            // Drop the table if exists
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS flyway_rollback_metadata");
            }
            connection.close();
        }
    }

    @Test
    @DisplayName("Should create metadata table if not exists")
    void testEnsureTableExists() throws SQLException {
        metadataTable.ensureTableExists(connection);

        // Verify table exists
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_NAME = 'FLYWAY_ROLLBACK_METADATA'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Should handle multiple calls to ensureTableExists")
    void testMultipleEnsureTableExistsCalls() throws SQLException {
        // Call multiple times - should not throw
        metadataTable.ensureTableExists(connection);
        metadataTable.ensureTableExists(connection);
        metadataTable.ensureTableExists(connection);

        // Table should still exist
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_NAME = 'FLYWAY_ROLLBACK_METADATA'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Should return false for non-applied enhancement")
    void testIsEnhancementAppliedWhenNotApplied() throws SQLException {
        boolean applied = metadataTable.isEnhancementApplied(connection, "test_enhancement_1");

        assertThat(applied).isFalse();
    }

    @Test
    @DisplayName("Should record and detect applied enhancement")
    void testRecordAndCheckEnhancement() throws SQLException {
        String enhancementId = "add_rollback_columns_v1";
        String description = "Added rollback columns to schema history";
        String appliedBy = "admin";

        // Initially not applied
        assertThat(metadataTable.isEnhancementApplied(connection, enhancementId)).isFalse();

        // Record enhancement
        metadataTable.recordEnhancement(connection, enhancementId, description, appliedBy);

        // Now should be applied
        assertThat(metadataTable.isEnhancementApplied(connection, enhancementId)).isTrue();
    }

    @Test
    @DisplayName("Should store enhancement details correctly")
    void testEnhancementDetailsStored() throws SQLException {
        String enhancementId = "test_enhancement";
        String description = "Test description";
        String appliedBy = "testuser";

        metadataTable.ensureTableExists(connection);
        metadataTable.recordEnhancement(connection, enhancementId, description, appliedBy);

        // Query and verify stored data
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT enhancement_id, applied_by, description, applied_at " +
                                "FROM flyway_rollback_metadata WHERE enhancement_id = 'test_enhancement'")) {

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("enhancement_id")).isEqualTo(enhancementId);
            assertThat(rs.getString("applied_by")).isEqualTo(appliedBy);
            assertThat(rs.getString("description")).isEqualTo(description);
            assertThat(rs.getTimestamp("applied_at")).isNotNull();
        }
    }

    @Test
    @DisplayName("Should handle multiple different enhancements")
    void testMultipleEnhancements() throws SQLException {
        metadataTable.ensureTableExists(connection);
        metadataTable.recordEnhancement(connection, "enhancement_1", "First", "user1");
        metadataTable.recordEnhancement(connection, "enhancement_2", "Second", "user2");

        assertThat(metadataTable.isEnhancementApplied(connection, "enhancement_1")).isTrue();
        assertThat(metadataTable.isEnhancementApplied(connection, "enhancement_2")).isTrue();
        assertThat(metadataTable.isEnhancementApplied(connection, "enhancement_3")).isFalse();
    }
}
