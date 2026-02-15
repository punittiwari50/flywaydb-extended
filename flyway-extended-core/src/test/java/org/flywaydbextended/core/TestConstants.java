package org.flywaydbextended.core;

/**
 * Constants for test infrastructure and database configuration.
 */
public class TestConstants {

    // H2 In-Memory Database Configuration
    // DB_CLOSE_DELAY=-1 keeps the database content as long as the VM is alive
    public static final String JDBC_URL = "jdbc:h2:mem:negative_test_db;DB_CLOSE_DELAY=-1";
    public static final String DB_USER = "sa";
    public static final String DB_PASSWORD = "";

    private TestConstants() {
        // Prevent instantiation
    }
}
