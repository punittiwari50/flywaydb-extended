package org.flywaydbextended.demo;

import org.flywaydbextended.reporting.ResultOutputCallback;
import org.flywaydbextended.reporting.ResultOutputConfiguration;
import org.flywaydbextended.api.RollbackResult;
import org.flywaydbextended.core.RollbackService;
import org.flywaydbextended.infrastructure.SchemaHistoryEnhancer;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Simple H2 Database Example - Demonstrates Flyway Rollback Extension
 * 
 * This example shows:
 * 1. Setting up H2 in-memory database
 * 2. Running forward migrations
 * 3. Adding rollback audit columns (one-time)
 * 4. Performing rollback
 * 5. Saving results to JSON file
 * 6. Querying rollback history
 */
public class SimpleH2Example {

    public static void main(String[] args) {
        System.out.println("+========================================================+");
        System.out.println("|   Flyway Rollback Extension - Simple H2 Example       |");
        System.out.println("+========================================================+\n");

        try {
            // ============================================================
            // STEP 1: Configure Result Output (Optional)
            // ============================================================
            System.out.println("📝 Step 1: Configuring result output...");
            ResultOutputConfiguration outputConfig = new ResultOutputConfiguration();
            outputConfig.setSaveResults(true);
            outputConfig.setFormat("JSON");
            outputConfig.setLocation("./flyway-results/h2-example-results.json");

            ResultOutputCallback outputCallback = new ResultOutputCallback(outputConfig);
            System.out.println("   ✓ Results will be saved to: " + outputConfig.getLocation());
            System.out.println();

            // ============================================================
            // STEP 2: Configure Flyway with H2 Database
            // ============================================================
            System.out.println("🗄️  Step 2: Setting up H2 database...");
            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "")
                    .locations("classpath:db/migration")
                    .callbacks(outputCallback)
                    .load();

            System.out.println("   ✓ H2 in-memory database created");
            System.out.println("   ✓ Connection: jdbc:h2:mem:testdb");
            System.out.println();

            // ============================================================
            // STEP 3: Run Forward Migrations
            // ============================================================
            System.out.println("⬆️  Step 3: Running forward migrations...");
            var migrateResult = flyway.migrate();
            System.out.println("   ✓ Migrations executed: " + migrateResult.migrationsExecuted);
            System.out.println("   ✓ Target version: " + migrateResult.targetSchemaVersion);

            // Save migrate result
            outputCallback.writeResult("MIGRATE", migrateResult);
            System.out.println();

            // ============================================================
            // STEP 4: Enhance Schema History (One-Time Operation)
            // ============================================================
            System.out.println("🔧 Step 4: Enhancing schema history table...");
            try (Connection conn = flyway.getConfiguration().getDataSource().getConnection()) {
                SchemaHistoryEnhancer enhancer = new SchemaHistoryEnhancer();
                enhancer.enhanceSchemaHistory(conn, "admin");
            }
            System.out.println("   ✓ Added 4 audit columns to flyway_schema_history");
            System.out.println("     - rolled_back (BOOLEAN)");
            System.out.println("     - rollback_date (TIMESTAMP)");
            System.out.println("     - rollback_user (VARCHAR)");
            System.out.println("     - rollback_reason (VARCHAR)");
            System.out.println();

            // ============================================================
            // STEP 5: Display Current Database State
            // ============================================================
            System.out.println("📊 Step 5: Current database state:");
            displayDatabaseState(flyway);
            System.out.println();

            // ============================================================
            // STEP 6: Display Migration History
            // ============================================================
            System.out.println("📜 Step 6: Migration history:");
            displayMigrationHistory(flyway);
            System.out.println();

            // ============================================================
            // STEP 7: Perform Rollback
            // ============================================================
            System.out.println("⬇️  Step 7: Performing rollback to version 1...");
            RollbackService rollbackService = new RollbackService(
                    flyway,
                    flyway.getConfiguration().getDataSource());

            RollbackResult rollbackResult = rollbackService.rollback(
                    "1", // Target version
                    "admin", // User
                    "Demonstrating rollback functionality" // Reason
            );

            // Display rollback results
            System.out.println("\n   📋 Rollback Results:");
            System.out.println("   ├─ Success: " + (rollbackResult.success ? "✓ YES" : "✗ NO"));
            System.out.println("   ├─ Migrations rolled back: " + rollbackResult.migrationsRolledBack);
            System.out.println("   ├─ Versions: " + rollbackResult.rolledBackVersions);
            System.out.println("   ├─ User: " + rollbackResult.rollbackUser);
            System.out.println("   └─ Time: " + rollbackResult.rollbackTime);

            if (rollbackResult.errorMessage != null) {
                System.out.println("   ⚠️  Error: " + rollbackResult.errorMessage);
            }

            // Save rollback result
            outputCallback.writeResult("ROLLBACK", rollbackResult);
            System.out.println();

            // ============================================================
            // STEP 8: Display Updated Database State
            // ============================================================
            System.out.println("📊 Step 8: Database state after rollback:");
            displayDatabaseState(flyway);
            System.out.println();

            // ============================================================
            // STEP 9: Query Rollback Audit History
            // ============================================================
            System.out.println("🔍 Step 9: Rollback audit history:");
            displayRollbackAudit(flyway);
            System.out.println();

            // ============================================================
            // STEP 10: Display Metadata Table
            // ============================================================
            System.out.println("📋 Step 10: Enhancement metadata:");
            displayMetadata(flyway);
            System.out.println();

            // ============================================================
            // Summary
            // ============================================================
            System.out.println("==========================================================");
            System.out.println("                  [COMPLETED] Example Finished            ");
            System.out.println("==========================================================");
            System.out.println("\nResults saved to: " + outputConfig.getLocation());
            System.out.println("Database: H2 in-memory (jdbc:h2:mem:testdb)");
            System.out.println("\nAll features demonstrated successfully!");

        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Display current database state (tables and row counts).
     */
    private static void displayDatabaseState(Flyway flyway) {
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                Statement stmt = conn.createStatement()) {

            // Check if users table exists
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) as cnt FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_NAME = 'USERS'")) {
                rs.next();
                boolean usersExists = rs.getInt("cnt") > 0;

                if (usersExists) {
                    ResultSet userCount = stmt.executeQuery("SELECT COUNT(*) as cnt FROM users");
                    userCount.next();
                    System.out.println("   ✓ USERS table: " + userCount.getInt("cnt") + " rows");
                } else {
                    System.out.println("   ✗ USERS table: does not exist");
                }
            }

            // Check if orders table exists
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) as cnt FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_NAME = 'ORDERS'")) {
                rs.next();
                boolean ordersExists = rs.getInt("cnt") > 0;

                if (ordersExists) {
                    ResultSet orderCount = stmt.executeQuery("SELECT COUNT(*) as cnt FROM orders");
                    orderCount.next();
                    System.out.println("   ✓ ORDERS table: " + orderCount.getInt("cnt") + " rows");
                } else {
                    System.out.println("   ✗ ORDERS table: does not exist");
                }
            }

        } catch (Exception e) {
            System.err.println("   ⚠️  Error checking database state: " + e.getMessage());
        }
    }

    /**
     * Display migration history from Flyway.
     */
    private static void displayMigrationHistory(Flyway flyway) {
        var info = flyway.info();
        for (var migration : info.all()) {
            String status = migration.getState().name();
            String icon = status.equals("SUCCESS") ? "✓" : status.equals("PENDING") ? "⏳" : "?";

            System.out.println(String.format("   %s V%s: %s [%s]",
                    icon,
                    migration.getVersion(),
                    migration.getDescription(),
                    status));
        }
    }

    /**
     * Display rollback audit information from schema history.
     */
    private static void displayRollbackAudit(Flyway flyway) {
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT \"version\", \"description\", \"rolled_back\", \"rollback_date\", " +
                                "\"rollback_user\", \"rollback_reason\" " +
                                "FROM \"flyway_schema_history\" " +
                                "WHERE \"type\" = 'SQL' " +
                                "ORDER BY \"installed_rank\"")) {

            while (rs.next()) {
                String version = rs.getString("version");
                String desc = rs.getString("description");
                boolean rolledBack = rs.getBoolean("rolled_back");
                String rollbackDate = rs.getString("rollback_date");
                String rollbackUser = rs.getString("rollback_user");
                String rollbackReason = rs.getString("rollback_reason");

                if (rolledBack) {
                    System.out.println(String.format("   🔄 V%s (%s):", version, desc));
                    System.out.println("      ├─ Status: ROLLED BACK");
                    System.out.println("      ├─ Date: " + rollbackDate);
                    System.out.println("      ├─ User: " + rollbackUser);
                    System.out.println("      └─ Reason: " + rollbackReason);
                } else {
                    System.out.println(String.format("   ✓ V%s (%s): ACTIVE", version, desc));
                }
            }

        } catch (Exception e) {
            System.err.println("   ⚠️  Error querying audit history: " + e.getMessage());
        }
    }

    /**
     * Display enhancement metadata.
     */
    private static void displayMetadata(Flyway flyway) {
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT ENHANCEMENT_ID, APPLIED_AT, APPLIED_BY, DESCRIPTION " +
                                "FROM FLYWAY_ROLLBACK_METADATA " +
                                "ORDER BY APPLIED_AT")) {

            while (rs.next()) {
                String id = rs.getString("enhancement_id");
                String appliedAt = rs.getString("applied_at");
                String appliedBy = rs.getString("applied_by");
                String description = rs.getString("description");

                System.out.println("   📌 " + id);
                System.out.println("      ├─ Applied: " + appliedAt);
                System.out.println("      ├─ By: " + appliedBy);
                System.out.println("      └─ Description: " + description);
            }

        } catch (Exception e) {
            System.err.println("   ⚠️  Error querying metadata: " + e.getMessage());
        }
    }
}
