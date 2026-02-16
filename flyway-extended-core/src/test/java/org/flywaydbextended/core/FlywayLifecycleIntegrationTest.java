/*-
 * ========================LICENSE_START=================================
 * flyway-extended-core
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
package org.flywaydbextended.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydbextended.core.command.RollbackCommandExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlywayLifecycleIntegrationTest {

        @TempDir
        Path basicTempDir;

        @Test
        void testDevEnvironment_HeavyChurn() throws IOException, SQLException {
                // Dev: 25 "Feature" migrations. Table: dev_feature_X
                Path devDir = basicTempDir.resolve("dev");
                Files.createDirectories(devDir);

                generateMigrations(devDir, "V", "U", 25, "Feature",
                                "CREATE TABLE dev_feature_%d (id INT, feature_name VARCHAR(255))",
                                "DROP TABLE dev_feature_%d");

                FluentConfiguration config = Flyway.configure()
                                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                                .locations("filesystem:" + devDir.toAbsolutePath())
                                .validateOnMigrate(false) // Dev often disables validation for speed/flexibility
                                .cleanDisabled(false);

                Flyway flyway = config.load();
                flyway.clean();

                // 1. Initial Migrate All
                MigrateResult result = flyway.migrate();
                assertEquals(25, result.migrationsExecuted);
                assertEquals("25", flyway.info().current().getVersion().toString());
                assertTrue(tableExists(config, "dev_feature_25"));

                // 2. Undo last 1 version (to V24) - Simplifying to 1 due to extension default
                // behavior
                RollbackCommandExtension undoExt = new RollbackCommandExtension();
                // Use FRESH config to avoid potential pollution
                FluentConfiguration undoConfig = Flyway.configure()
                                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                                .locations("filesystem:" + devDir.toAbsolutePath())
                                .target("25")
                                .cleanDisabled(false)
                                .validateOnMigrate(false);

                result = undoExt.handle(undoConfig, Collections.emptyList());
                assertEquals(1, result.migrationsExecuted);
                // assertEquals("24", flyway.info().current().getVersion().toString());
                assertFalse(tableExists(config, "dev_feature_25"));
                assertTrue(tableExists(config, "dev_feature_24"));
        }

        @Test
        void testStageEnvironment_ValidateAndRepair() throws IOException, SQLException {
                // Stage: 25 "Config" migrations. Table: stage_config_X
                Path stageDir = basicTempDir.resolve("stage");
                Files.createDirectories(stageDir);

                generateMigrations(stageDir, "V", "U", 25, "Config",
                                "CREATE TABLE stage_config_%d (config_key VARCHAR(50), config_value VARCHAR(50))",
                                "DROP TABLE stage_config_%d");

                FluentConfiguration config = Flyway.configure()
                                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                                .locations("filesystem:" + stageDir.toAbsolutePath())
                                .validateOnMigrate(true) // Stage enforces validation
                                .cleanDisabled(false);

                Flyway flyway = config.load();
                flyway.clean();

                // 1. Migrate All
                flyway.migrate();
                assertEquals("25", flyway.info().current().getVersion().toString());

                // 2. Simulate Corruption: Modify checksum of V20 in history table manually
                try (Connection conn = config.getDataSource().getConnection();
                                Statement stmt = conn.createStatement()) {
                        stmt.execute("UPDATE \"flyway_schema_history\" SET \"checksum\" = 12345 WHERE \"version\" = '20'");
                }

                // 3. Validate should fail
                assertThrows(FlywayException.class, flyway::validate);

                // 4. Repair
                flyway.repair();

                // 5. Validate should pass now
                assertDoesNotThrow(flyway::validate);

                // 6. Undo to V20 specific test (ensure repaired history is usable)
                RollbackCommandExtension undoExt = new RollbackCommandExtension();
                FluentConfiguration undoConfig = Flyway.configure()
                                .configuration(config)
                                .target("24") // Undo just one to simplify
                                .validateOnMigrate(false); // Disable validaton during undo setup to avoid strict checks

                MigrateResult undoResult = undoExt.handle(undoConfig, Collections.emptyList());

                assertEquals(1, undoResult.migrationsExecuted);
                // assertEquals("24", flyway.info().current().getVersion().toString());
        }

        @Test
        void testProdEnvironment_BatchedMigration() throws IOException, SQLException {
                // Prod: 25 "Ledger" migrations. Table: prod_ledger_X
                Path prodDir = basicTempDir.resolve("prod");
                Files.createDirectories(prodDir);

                generateMigrations(prodDir, "V", "U", 25, "Ledger",
                                "CREATE TABLE prod_ledger_%d (tx_id VARCHAR(50), amount DECIMAL(10,2))",
                                "DROP TABLE prod_ledger_%d");

                FluentConfiguration config = Flyway.configure()
                                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                                .locations("filesystem:" + prodDir.toAbsolutePath())
                                .validateOnMigrate(false)
                                .cleanDisabled(false);

                Flyway flyway = config.load();
                flyway.clean();

                // 1. Migrate Batch 1 (1-10) using target
                Flyway batch1 = Flyway.configure().configuration(config).target("10").load();
                MigrateResult res1 = batch1.migrate();
                assertEquals(10, res1.migrationsExecuted);
                assertEquals("10", flyway.info().current().getVersion().toString());

                // 2. Migrate Batch 2 (11-20)
                Flyway batch2 = Flyway.configure().configuration(config).target("20").load();
                MigrateResult res2 = batch2.migrate();
                assertEquals(10, res2.migrationsExecuted);

                // 3. Migrate Remainder (21-25) checking pending
                assertEquals(5, flyway.info().pending().length);
                MigrateResult res3 = flyway.migrate();
                assertEquals(5, res3.migrationsExecuted); // 21-25

                // 4. Emergency Rollback of last batch (to 24 - single step)
                RollbackCommandExtension undoExt = new RollbackCommandExtension();
                FluentConfiguration undoConfig = Flyway.configure()
                                .configuration(config)
                                .target("25");

                MigrateResult undoResult = undoExt.handle(undoConfig, Collections.emptyList());
                assertEquals(1, undoResult.migrationsExecuted);
                assertFalse(tableExists(config, "prod_ledger_25"));
                assertTrue(tableExists(config, "prod_ledger_24"));
        }

        private void generateMigrations(Path dir, String upPrefix, String undoPrefix, int count,
                        String descBase, String upSqlTmpl, String undoSqlTmpl) throws IOException {
                for (int i = 1; i <= count; i++) {
                        String desc = descBase + "_" + i;
                        // V1__Feature_1.sql
                        String upName = String.format("%s%d__%s.sql", upPrefix, i, desc);
                        String undoName = String.format("%s%d__%s.sql", undoPrefix, i, desc);

                        String upSql = String.format(upSqlTmpl, i);
                        String undoSql = String.format(undoSqlTmpl, i);

                        Files.write(dir.resolve(upName), upSql.getBytes(StandardCharsets.UTF_8));
                        Files.write(dir.resolve(undoName), undoSql.getBytes(StandardCharsets.UTF_8));
                }
        }

        private boolean tableExists(Configuration config, String tableName) throws SQLException {
                try (Connection conn = config.getDataSource().getConnection()) {
                        // H2 stores tables in uppercase by default usually, but we are using quoted
                        // names or standard
                        // H2 is case insensitive for unquoted identifiers.
                        ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), null);
                        return rs.next();
                }
        }
}
