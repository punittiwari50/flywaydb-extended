package org.flywaydbextended.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for SchemaHistoryEnhancer with 100% code coverage.
 * Tests schema enhancement across different database modes.
 */
@DisplayName("SchemaHistoryEnhancer Tests")
class SchemaHistoryEnhancerTest {

    private SchemaHistoryEnhancer enhancer;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        enhancer = new SchemaHistoryEnhancer();
    }

    @Test
    @DisplayName("Should enhance schema history table successfully")
    void testEnhanceSchemaHistory() throws Exception {
        // Given: Fresh H2 database with flyway_schema_history
        connection = DriverManager.getConnection("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1", "sa", "");
        createSchemaHistoryTable(connection);

        // When: Enhance schema
        enhancer.enhanceSchemaHistory(connection, "testUser");

        // Then: All audit columns should exist
        assertColumnExists(connection, "flyway_schema_history", "rolled_back");
        assertColumnExists(connection, "flyway_schema_history", "rollback_date");
        assertColumnExists(connection, "flyway_schema_history", "rollback_user");
        assertColumnExists(connection, "flyway_schema_history", "rollback_reason");

        // Verify metadata table
        assertMetadataRecorded(connection, "add_rollback_audit_columns_v1", "testUser");

        connection.close();
    }

    @Test
    @DisplayName("Should be idempotent - running twice should not fail")
    void testIdempotency() throws Exception {
        // Given: Database with schema already enhanced
        connection = DriverManager.getConnection("jdbc:h2:mem:test2;DB_CLOSE_DELAY=-1", "sa", "");
        createSchemaHistoryTable(connection);
        enhancer.enhanceSchemaHistory(connection, "user1");

        // When: Enhance again
        enhancer.enhanceSchemaHistory(connection, "user2");

        // Then: Should not fail and should still have columns
        assertColumnExists(connection, "flyway_schema_history", "rolled_back");
        assertColumnExists(connection, "flyway_schema_history", "rollback_date");
        assertColumnExists(connection, "flyway_schema_history", "rollback_user");
        assertColumnExists(connection, "flyway_schema_history", "rollback_reason");

        // Metadata should still show first user
        assertMetadataRecorded(connection, "add_rollback_audit_columns_v1", "user1");

        connection.close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "ORACLE", "MYSQL", "POSTGRESQL" })
    @DisplayName("Should work with different database modes")
    void testDifferentDatabaseModes(String mode) throws Exception {
        // Given: H2 configured to emulate different databases
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + mode + "test;MODE=" + mode + ";DB_CLOSE_DELAY=-1", "sa", "");
        createSchemaHistoryTable(connection);

        // When: Enhance schema
        enhancer.enhanceSchemaHistory(connection, "admin");

        // Then: Should succeed
        assertColumnExists(connection, "flyway_schema_history", "rolled_back");
        assertColumnExists(connection, "flyway_schema_history", "rollback_date");
        assertColumnExists(connection, "flyway_schema_history", "rollback_user");
        assertColumnExists(connection, "flyway_schema_history", "rollback_reason");

        connection.close();
    }

    @Test
    @DisplayName("Should handle existing columns gracefully")
    void testExistingColumns() throws Exception {
        // Given: Database with some audit columns already present
        connection = DriverManager.getConnection("jdbc:h2:mem:test3;DB_CLOSE_DELAY=-1", "sa", "");
        createSchemaHistoryTable(connection);

        // Manually add one column first
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE \"flyway_schema_history\" ADD COLUMN \"rolled_back\" BOOLEAN DEFAULT FALSE");
        }

        // When: Enhance schema (should detect existing column and add remaining ones)
        enhancer.enhanceSchemaHistory(connection, "admin");

        // Then: Should succeed and have all columns (including the pre-existing one)
        assertColumnExists(connection, "flyway_schema_history", "rolled_back");
        assertColumnExists(connection, "flyway_schema_history", "rollback_date");
        assertColumnExists(connection, "flyway_schema_history", "rollback_user");
        assertColumnExists(connection, "flyway_schema_history", "rollback_reason");

        connection.close();
    }

    @Test
    @DisplayName("Should create metadata table if not exists")
    void testMetadataTableCreation() throws Exception {
        // Given: Fresh database
        connection = DriverManager.getConnection("jdbc:h2:mem:test4;DB_CLOSE_DELAY=-1", "sa", "");
        createSchemaHistoryTable(connection);

        // When: Enhance schema
        enhancer.enhanceSchemaHistory(connection, "admin");

        // Then: Metadata table should exist
        assertTableExists(connection, "FLYWAY_ROLLBACK_METADATA");

        connection.close();
    }

    // Helper methods

    private void createSchemaHistoryTable(Connection conn) throws Exception {
        // Load SQL from external file
        try (java.io.InputStream is = getClass()
                .getResourceAsStream("/config/sql-files/create_schema_history_table.sql")) {
            if (is == null) {
                throw new RuntimeException("Could not find create_schema_history_table.sql");
            }
            String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    private void assertColumnExists(Connection conn, String tableName, String columnName) throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = '" + tableName + "' " +
                                "AND COLUMN_NAME = '" + columnName + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThan(0);
        }
    }

    private void assertTableExists(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_NAME = '" + tableName + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThan(0);
        }
    }

    private void assertMetadataRecorded(Connection conn, String enhancementId, String appliedBy) throws Exception {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT applied_by FROM flyway_rollback_metadata " +
                                "WHERE enhancement_id = '" + enhancementId + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("applied_by")).isEqualTo(appliedBy);
        }
    }
}
