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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydbextended.core.command.RollbackCommandExtension;
import org.junit.jupiter.api.Test;

class RollbackCommandExtensionTest {

    @Test
    void handlesCommand_shouldReturnTrueForUndo() {
        RollbackCommandExtension extension = new RollbackCommandExtension();
        assertTrue(extension.handlesCommand("undo"));
    }

    @Test
    void handlesCommand_shouldReturnFalseForOtherCommands() {
        RollbackCommandExtension extension = new RollbackCommandExtension();
        assertFalse(extension.handlesCommand("migrate"));
        assertFalse(extension.handlesCommand("info"));
    }

    @Test
    void handlesParameter_shouldReturnFalse() {
        RollbackCommandExtension extension = new RollbackCommandExtension();
        assertFalse(extension.handlesParameter("-undo.target"));
        assertFalse(extension.handlesParameter("any"));
    }

    @Test
    void handle_shouldExecuteCommand(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws java.io.IOException {
        // Setup Migration
        java.nio.file.Path migration = tempDir.resolve("V1__Init.sql");
        java.nio.file.Files.write(migration,
                "CREATE TABLE t1 (id INT);".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.nio.file.Path undoMigration = tempDir.resolve("U1__Init.sql");
        java.nio.file.Files.write(undoMigration, "DROP TABLE t1;".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.nio.file.Path migration2 = tempDir.resolve("V2__Add.sql");
        java.nio.file.Files.write(migration2,
                "CREATE TABLE t2 (id INT);".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.nio.file.Path undoMigration2 = tempDir.resolve("U2__Add.sql");
        java.nio.file.Files.write(undoMigration2, "DROP TABLE t2;".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Setup Flyway and Migrate
        Flyway flyway = Flyway.configure()
                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                .locations("filesystem:" + tempDir.toAbsolutePath())
                .validateOnMigrate(false)
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();

        // Test Extension
        RollbackCommandExtension extension = new RollbackCommandExtension();
        List<String> flags = Collections.emptyList();

        // Use a configuration derived from the one with valid datasource/locations
        // And specify target "1" to undo the migration V2
        Configuration testConfig = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .target("1");

        MigrateResult result = extension.handle(testConfig, flags);
        assertNotNull(result);
        assertTrue(result.migrationsExecuted > 0);
    }
}
