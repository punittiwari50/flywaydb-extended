package org.flywaydbextended.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydbextended.core.command.RollbackCommandExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Positive test case for Undo operation triggering
 * VersionedScopedFlywayCallback.
 * Uses H2 in-memory database and avoiding mocking.
 */
public class UndoPositiveTest {

    @TempDir
    Path tempDir;

    private FluentConfiguration baseConfig;
    private static final List<String> executedCallbacks = new ArrayList<>();

    @BeforeEach
    void setUp() throws SQLException {
        executedCallbacks.clear();

        baseConfig = Flyway.configure()
                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                .locations("classpath:migration/positive/scripts")
                .callbacks(new org.flywaydbextended.core.api.callback.VersionedScopedFlywayCallback())
                .placeholders(Collections.singletonMap("FLYWAY_VERSIONSCOPED_LOCATION",
                        "dummy"))
                .cleanDisabled(false)
                .validateOnMigrate(true);

        Flyway flyway = baseConfig.load();
        flyway.clean();

        // Register ALIAS for callback verification
        try (Connection conn = baseConfig.getDataSource().getConnection()) {
            try {
                conn.createStatement().execute("DROP ALIAS IF EXISTS RECORD_CALLBACK");
            } catch (SQLException ignored) {
            }
            conn.createStatement().execute(
                    "CREATE ALIAS RECORD_CALLBACK FOR \"org.flywaydbextended.core.UndoPositiveTest.recordCallback\"");
        }
    }

    public static void recordCallback(String name) {
        executedCallbacks.add(name);
    }

    @Test
    void testPositiveUndoCallbackExecution() {
        // 1. Initial Migrate (V1)
        Flyway flyway = baseConfig.load();
        flyway.migrate();

        assertEquals("1", flyway.info().current().getVersion().toString());

        // 2. Undo V1 (Target "1")
        // This should trigger beforeUndo__U1__Callback.sql
        RollbackCommandExtension undoExt = new RollbackCommandExtension();
        FluentConfiguration undoConfig = Flyway.configure()
                .configuration(baseConfig)
                .target("1")
                .validateOnMigrate(false);

        MigrateResult result = undoExt.handle(undoConfig, Collections.emptyList());

        assertEquals(1, result.migrationsExecuted);

        // 3. Verify callback execution
        // We expect "beforeUndo:U1" to be in the list
        assertTrue(executedCallbacks.contains("beforeUndo:U1"),
                "Callback 'beforeUndo__U1__Callback.sql' should have been executed. Actual: " + executedCallbacks);
    }
}
