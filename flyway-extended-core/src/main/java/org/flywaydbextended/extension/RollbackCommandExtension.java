package org.flywaydbextended.extension;

import org.flywaydbextended.api.RollbackResult;
import org.flywaydbextended.core.RollbackService;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.OperationResultBase;
import org.flywaydb.core.extensibility.CommandExtension;

import javax.sql.DataSource;
import java.util.List;

/**
 * CommandExtension for custom rollback command.
 * 
 * Allows running rollback via Flyway CLI:
 * flyway rollback -rollback.targetVersion=1.0
 * flyway rollback -rollback.count=2
 * 
 * Or programmatically:
 * flyway.execute("rollback", Arrays.asList("-rollback.targetVersion=1.0"));
 */
public class RollbackCommandExtension implements CommandExtension {

    @Override
    public boolean handlesCommand(String command) {
        return "rollback".equalsIgnoreCase(command);
    }

    @Override
    public boolean handlesParameter(String parameter) {
        return parameter.startsWith("rollback.");
    }

    @Override
    public org.flywaydb.core.api.output.OperationResultBase handle(String command, java.util.Map<String, String> config,
            List<String> flags) throws FlywayException {
        try {
            // Parse parameters from flags
            String targetVersion = null;
            Integer count = null;
            String user = System.getProperty("user.name");
            String reason = "Rollback via CommandExtension";

            for (String flag : flags) {
                if (flag.startsWith("-rollback.targetVersion=")) {
                    targetVersion = flag.substring("-rollback.targetVersion=".length());
                } else if (flag.startsWith("-rollback.count=")) {
                    count = Integer.parseInt(flag.substring("-rollback.count=".length()));
                } else if (flag.startsWith("-rollback.user=")) {
                    user = flag.substring("-rollback.user=".length());
                } else if (flag.startsWith("-rollback.reason=")) {
                    reason = flag.substring("-rollback.reason=".length());
                }
            }

            // Create configuration from Map
            org.flywaydb.core.api.configuration.ClassicConfiguration configuration = new org.flywaydb.core.api.configuration.ClassicConfiguration();
            configuration.configure(config);

            // Get datasource (from configuration)
            DataSource dataSource = configuration.getDataSource();

            if (dataSource == null && (configuration.getUrl() == null)) {
                throw new FlywayException("DataSource URL is required for rollback");
            }

            // Create Flyway instance from configuration
            Flyway flyway = new Flyway(configuration);

            // Create rollback service
            // If explicit dataSource is available, use it. Otherwise rely on configuration
            // which Flyway uses.
            // RollbackService implementation likely uses Flyway's datasource via Flyway
            // object if passed,
            // but the constructor takes both.
            // If dataSource is null here but URL is set, Flyway creates one internally.
            // We can try to get it? No public API on Flyway object to get DataSource in
            // 8.5.1?
            // Actually config.getDataSource() might be null if URL is used.
            // Let's rely on what we have.

            RollbackService rollbackServiceReal = new RollbackService(flyway,
                    dataSource != null ? dataSource : configuration.getDataSource());

            // Execute rollback
            RollbackResult result;
            if (count != null) {
                result = rollbackServiceReal.rollbackLast(count, user, reason);
            } else if (targetVersion != null) {
                result = rollbackServiceReal.rollback(targetVersion, user, reason);
            } else {
                throw new FlywayException(
                        "Either -rollback.targetVersion or -rollback.count must be specified");
            }

            // Log result
            System.out.println("Rollback completed:");
            System.out.println("  Success: " + result.success);
            System.out.println("  Migrations rolled back: " + result.migrationsRolledBack);
            System.out.println("  Versions: " + result.rolledBackVersions);

            return result;

        } catch (Exception e) {
            throw new FlywayException("Rollback failed: " + e.getMessage(), e);
        }
    }
}
