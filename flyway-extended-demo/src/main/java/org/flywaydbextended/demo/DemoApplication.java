/*-
 * ========================LICENSE_START=================================
 * flyway-extended-demo
 * ========================================================================
 * Copyright (C) 2010 - 2026 Red Gate Software Ltd
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.flywaydbextended.demo;

import java.util.Arrays;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydbextended.core.command.RollbackCommandExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication implements org.springframework.boot.CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public org.flywaydb.core.Flyway flyway(javax.sql.DataSource dataSource) {
        return org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .load();
    }

    // Removed autowired Flyway field to avoid circular dependency
    // @org.springframework.beans.factory.annotation.Autowired
    // private org.flywaydb.core.Flyway flyway;

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource dataSource;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    private void manualClean() {
        System.out.println("Performing Surgical Clean (Dropping demo objects)...");
        try (java.sql.Connection conn = dataSource.getConnection();
                java.sql.Statement stmt = conn.createStatement()) {

            // Helper list of objects to drop
            String[] drops = {
                    "DROP VIEW TEST_TABLE_SUMMARY",
                    "DROP TABLE TEST_TABLE CASCADE CONSTRAINTS",
                    "DROP TABLE \"flyway_schema_history\" CASCADE CONSTRAINTS"
            };

            for (String sql : drops) {
                try {
                    stmt.execute(sql);
                    System.out.println("Executed: " + sql);
                } catch (java.sql.SQLException e) {
                    // Ignore "table or view does not exist" (ORA-00942)
                    if (e.getErrorCode() == 942) {
                        System.out.println("Object not found (already clean): " + sql);
                    } else {
                        System.err.println("Failed to execute: " + sql + " Error: " + e.getMessage());
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Database connection failed during manual clean: " + e.getMessage());
        }
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Flyway Extended Demo Application started!");
        Arrays.asList(args).forEach(System.out::println);
        boolean rollback = false;
        boolean clean = false;
        String targetVersion = null;

        for (String arg : args) {
            String lowerArg = arg.toLowerCase();
            if (lowerArg.contains("rollback")) {
                rollback = true;
            }
            if (lowerArg.contains("clean")) {
                clean = true;
            }
            if (arg.startsWith("version=")) {
                targetVersion = arg.substring("version=".length());
            }
        }

        String dbProfile = System.getProperty("spring.profiles.active", "postgres");

        String locations = "classpath:db/migration/common";
        if ("oracle".equals(dbProfile)) {
            locations += ",classpath:db/migration/oracle";
        } else if ("postgres".equals(dbProfile)) {
            locations += ",classpath:db/migration/postgres";
        }

        if (clean) {
            manualClean();
            System.out.println("Manual Clean complete.");
        }

        if (rollback) {
            System.out.println("Executing Rollback...");
            String undoPrefix = env.getProperty("flyway.undoPrefix", "U");
            String undoSeparator = env.getProperty("flyway.undoSeparator", "__");
            String undoSuffix = env.getProperty("flyway.undoSuffix", ".sql");

            System.out.println("Configuring FlywayExtended with:");
            System.out.println("  Prefix: " + undoPrefix);
            System.out.println("  Separator: " + undoSeparator);
            System.out.println("  Suffix: " + undoSuffix);

            FluentConfiguration fluentConfiguration = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations.split(","))
                    .baselineOnMigrate(true);

            if (targetVersion != null) {
                fluentConfiguration.target(targetVersion);
            }

            // Using RollbackCommandExtension directly as per functional tests
            RollbackCommandExtension extension = new RollbackCommandExtension();
            try {
                extension.handle(fluentConfiguration, java.util.Collections.emptyList());
                System.out.println("Rollback/Undo executed successfully.");
            } catch (org.flywaydb.core.api.FlywayException e) {
                System.err.println("Rollback failed: " + e.getMessage());
                throw e;
            }

        } else if (!clean) {
            // Default behavior: Migrate
            if (!rollback) {
                System.out.println("Executing Migration...");
                try {
                    org.flywaydb.core.Flyway flywayInstance = org.flywaydb.core.Flyway.configure()
                            .dataSource(dataSource)
                            .locations(locations.split(","))
                            .baselineOnMigrate(true)
                            .load();
                    flywayInstance.baseline();
                } catch (Exception e) {
                    // Check for specific Enum error caused by legacy ROLLBACK rows
                    if (e.toString().contains("No enum constant") && e.toString().contains("ROLLBACK")) {
                        System.err
                                .println("Detected corrupted schema history (legacy ROLLBACK type). Auto-cleaning...");
                        manualClean();
                        System.out.println("Clean complete. Retrying Migration...");
                        org.flywaydb.core.Flyway retryFlyway = org.flywaydb.core.Flyway.configure()
                                .dataSource(dataSource).load();
                        retryFlyway.migrate();
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    // @org.springframework.context.annotation.Bean
    public static org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer flywayConfigurationCustomizer(
            javax.sql.DataSource dataSource) {
        return configuration -> {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                String databaseProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();
                System.out.println("Detected Database: " + databaseProductName);
                // Simple mapping: 'postgresql' -> 'postgresql'
                // You might need more complex mapping for other DBs (e.g. 'oracle' from 'Oracle
                // Database...')
                String location = "classpath:db/migration/" + databaseProductName;
                configuration.locations(location);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Failed to determine database vendor", e);
            }
        };
    }

}
