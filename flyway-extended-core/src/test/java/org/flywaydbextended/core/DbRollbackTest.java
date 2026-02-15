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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydbextended.core.command.RollbackCommandExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbRollbackTest {

    @TempDir
    Path tempDir;

    private FluentConfiguration baseConfig;

    @BeforeEach
    void setUp() {
        baseConfig = Flyway.configure()
                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                .locations("filesystem:" + tempDir.toAbsolutePath().toString())
                .validateOnMigrate(false)
                .cleanDisabled(false);

        Flyway flyway = baseConfig.load();
        flyway.clean();
    }

    @Test
    void undo_successful_single_migration() throws IOException, SQLException {
        createMigration("V", 1, "Init", "CREATE TABLE t1 (id INT);");
        createMigration("V", 2, "Add", "CREATE TABLE t2 (id INT);");
        createMigration("U", 2, "Add", "DROP TABLE t2;");

        Flyway flyway = baseConfig.load();
        flyway.migrate();
        assertTrue(tableExists("t2"));

        RollbackCommandExtension extension = new RollbackCommandExtension();
        FluentConfiguration undoConfig = Flyway.configure()
                .configuration(baseConfig)
                .target("1"); // Undo V2, go back to V1

        MigrateResult result = extension.handle(undoConfig, Collections.emptyList());
        assertEquals(1, result.migrationsExecuted);
        assertFalse(tableExists("t2"));
        assertTrue(tableExists("t1"));
    }

    @Test
    void undo_no_op_if_nothing_to_undo() throws IOException, SQLException {
        createMigration("V", 1, "Init", "CREATE TABLE t1 (id INT);");
        createMigration("U", 1, "Init", "DROP TABLE t1;");

        Flyway flyway = baseConfig.load();
        flyway.migrate();

        // Target is current version (1), so nothing to undo
        RollbackCommandExtension extension = new RollbackCommandExtension();
        FluentConfiguration undoConfig = Flyway.configure()
                .configuration(baseConfig)
                .target("1");

        MigrateResult result = extension.handle(undoConfig, Collections.emptyList());
        assertEquals(0, result.migrationsExecuted);
        assertTrue(tableExists("t1"));
    }

    @Test
    void undo_target_validation_failure_not_in_history() throws IOException {
        createMigration("V", 1, "Init", "CREATE TABLE t1 (id INT);");
        createMigration("U", 1, "Init", "DROP TABLE t1;");

        Flyway flyway = baseConfig.load();
        flyway.migrate();

        RollbackCommandExtension extension = new RollbackCommandExtension();
        FluentConfiguration undoConfig = Flyway.configure()
                .configuration(baseConfig)
                .target("5"); // 5 is not applied

        assertThrows(FlywayException.class, () -> extension.handle(undoConfig, Collections.emptyList()));
    }

    @Test
    void undo_target_validation_failure_undo_script_missing() throws IOException, SQLException {
        createMigration("V", 1, "Init", "CREATE TABLE t1 (id INT);");
        createMigration("V", 2, "Add", "CREATE TABLE t2 (id INT);");
        // No U2 script

        Flyway flyway = baseConfig.load();
        flyway.migrate();

        RollbackCommandExtension extension = new RollbackCommandExtension();
        FluentConfiguration undoConfig = Flyway.configure()
                .configuration(baseConfig)
                .target("1");

        // Should NOT throw exception, but log warning and skip
        assertDoesNotThrow(() -> extension.handle(undoConfig, Collections.emptyList()));

        // V2 should still exist because U2 was missing
        assertTrue(tableExists("t2"));
    }

    @Test
    void undo_skipping_execution() throws IOException, SQLException {
        createMigration("V", 1, "Init", "CREATE TABLE t1 (id INT);");
        createMigration("V", 2, "Add", "CREATE TABLE t2 (id INT);");
        createMigration("U", 2, "Add", "DROP TABLE t2;");

        Flyway flyway = baseConfig.load();
        flyway.migrate();

        RollbackCommandExtension extension = new RollbackCommandExtension();
        FluentConfiguration undoConfig = Flyway.configure()
                .configuration(baseConfig)
                .target("1")
                .skipExecutingMigrations(true); // Skip actual execution

        extension.handle(undoConfig, Collections.emptyList());

        // Flyway 10+ behaviors on skip execution might differ in reporting,
        // but it should definitely NOT drop the table.
        assertTrue(tableExists("t2"));
    }

    private void createMigration(String prefix, int version, String desc, String content) throws IOException {
        String filename = prefix + version + "__" + desc + ".sql";
        Path file = tempDir.resolve(filename);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (Connection conn = baseConfig.getDataSource().getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), null);
            return rs.next();
        }
    }
}
