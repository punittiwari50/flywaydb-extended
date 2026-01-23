package org.flywaydbextended.core;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class FlywayExtendedTest {
    private Flyway flyway;
    private FlywayExtended flywayExtended;

    @BeforeEach
    void setUp() {
        // Use in-memory DB. DB remains open due to DB_CLOSE_DELAY=-1
        flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate(); // Migrates V1 to V5
        flywayExtended = new FlywayExtended(flyway);

        System.out.println("Setup Complete. Current Version: " + flyway.info().current().getVersion());
    }

    @Test
    void testSequentialRollbacks_5_Iterations() throws Exception {
        System.out.println("=== Starting 5 Iteration Rollback Test ===");

        // --- Iteration 1: Rollback V5 (Insert Logs) ---
        assertTrue(tableExists("LOGS"));
        assertEquals(1, countRows("LOGS"));

        System.out.println("1. Rolling back V5...");
        flywayExtended.rollback();

        // Validation
        assertEquals(0, countRows("LOGS"));
        assertEquals("UNDO", getVersionType("5"));
        assertEquals(2, countRows("EVENTS")); // before + after
        System.out.println("   V5 Rollback Validated.");

        // --- Iteration 2: Rollback V4 (Create Logs) ---
        System.out.println("2. Rolling back V4...");
        flywayExtended.rollback();

        // Validation
        assertFalse(tableExists("LOGS"));
        assertEquals("UNDO", getVersionType("4"));
        assertEquals(4, countRows("EVENTS"));
        System.out.println("   V4 Rollback Validated.");

        // --- Iteration 3: Rollback V3 (Add Details) ---
        assertTrue(columnExists("TEST_DATA", "DETAILS"));

        System.out.println("3. Rolling back V3...");
        flywayExtended.rollback();

        // Validation
        assertFalse(columnExists("TEST_DATA", "DETAILS"));
        assertEquals("UNDO", getVersionType("3"));
        assertEquals(6, countRows("EVENTS"));
        System.out.println("   V3 Rollback Validated.");

        // --- Iteration 4: Rollback V2 (Insert Data) ---
        assertEquals(2, countRows("TEST_DATA"));

        System.out.println("4. Rolling back V2...");
        flywayExtended.rollback();

        // Validation
        assertEquals(0, countRows("TEST_DATA"));
        assertEquals("UNDO", getVersionType("2"));
        assertEquals(8, countRows("EVENTS"));
        System.out.println("   V2 Rollback Validated.");

        // --- Iteration 5: Rollback V1 (Create Table) ---
        assertTrue(tableExists("TEST_DATA"));

        System.out.println("5. Rolling back V1...");
        flywayExtended.rollback();

        // Validation
        assertFalse(tableExists("TEST_DATA"));
        assertEquals("UNDO", getVersionType("1"));
        assertEquals(10, countRows("EVENTS"));
        System.out.println("   V1 Rollback Validated.");

        System.out.println("=== 5 Iteration Test Passed ===");
    }

    // --- Helpers ---

    private int countRows(String table) throws SQLException {
        try (Connection con = flyway.getConfiguration().getDataSource().getConnection();
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private String getVersionType(String version) throws SQLException {
        try (Connection con = flyway.getConfiguration().getDataSource().getConnection();
                Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT \"type\" FROM \"flyway_schema_history\" WHERE \"version\" = '" + version + "'")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection con = flyway.getConfiguration().getDataSource().getConnection();
                ResultSet rs = con.getMetaData().getTables(null, null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection con = flyway.getConfiguration().getDataSource().getConnection();
                ResultSet rs = con.getMetaData().getColumns(null, null, tableName.toUpperCase(),
                        columnName.toUpperCase())) {
            return rs.next();
        }
    }
}
