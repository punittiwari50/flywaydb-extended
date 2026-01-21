package org.flywaydbextended.demo;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

@Component
public class VerificationCallback implements Callback {

    private static final Logger LOG = Logger.getLogger(VerificationCallback.class.getName());

    @Override
    public boolean supports(Event event, Context context) {
        // Run this callback after the Migrate command completes
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        LOG.info("JAVA CALLBACK: Executing AFTER_MIGRATE verification...");

        try (Statement statement = context.getConnection().createStatement()) {
            String dbProductName = context.getConnection().getMetaData().getDatabaseProductName();
            String query;
            String schemaInfo;

            if (dbProductName.toLowerCase().contains("oracle")) {
                query = "SELECT table_name FROM user_tables ORDER BY table_name";
                schemaInfo = "User Tables";
            } else {
                // Postgres and others
                query = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name";
                schemaInfo = "Public Schema Tables";
            }

            LOG.info("--- " + schemaInfo + " (" + dbProductName + ") ---");
            LOG.info("Executing Query: " + query);
            try (ResultSet resultSet = statement.executeQuery(query)) {
                int tableCount = 0;
                while (resultSet.next()) {
                    String tableName = resultSet.getString(1);
                    // Filter out Flyway's own table to reduce noise, or keep it? Keeping it is
                    // fine.
                    LOG.info("Found Table: " + tableName);
                    tableCount++;
                }

                if (tableCount > 0) {
                    LOG.info("VERIFICATION SUCCESS: Found " + tableCount + " tables in the database.");
                } else {
                    LOG.warning("VERIFICATION ALERT: No tables found!");
                }
                LOG.info("-------------------------------------------");
            }
        } catch (SQLException e) {
            LOG.severe("Error during verification callback: " + e.getMessage());
            throw new RuntimeException("Verification Error", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "JavaVerificationCallback";
    }
}
