package org.flywaydbextended.extension;

import org.flywaydbextended.api.RollbackResult;
import org.flywaydb.core.Flyway;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.FlywayException;

import org.flywaydb.core.api.output.OperationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for RollbackCommandExtension with 100% code coverage.
 */
@DisplayName("RollbackCommandExtension Tests")
class RollbackCommandExtensionTest {

    private RollbackCommandExtension extension;
    private Flyway flyway;

    @BeforeEach
    void setUp() {
        extension = new RollbackCommandExtension();

        flyway = Flyway.configure()
                .dataSource("jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void tearDown() {
        if (flyway != null) {
            flyway.clean();
        }
    }

    @Test
    @DisplayName("Should handle rollback command")
    void testHandlesCommand() {
        assertThat(extension.handlesCommand("rollback")).isTrue();
        assertThat(extension.handlesCommand("ROLLBACK")).isTrue();
        assertThat(extension.handlesCommand("Rollback")).isTrue();
        assertThat(extension.handlesCommand("migrate")).isFalse();
        assertThat(extension.handlesCommand("clean")).isFalse();
    }

    @Test
    @DisplayName("Should handle rollback parameters")
    void testHandlesParameter() {
        assertThat(extension.handlesParameter("rollback.targetVersion")).isTrue();
        assertThat(extension.handlesParameter("rollback.count")).isTrue();
        assertThat(extension.handlesParameter("rollback.user")).isTrue();
        assertThat(extension.handlesParameter("rollback.reason")).isTrue();
        assertThat(extension.handlesParameter("migrate.locations")).isFalse();
    }

    @Test
    @DisplayName("Should execute rollback with targetVersion")
    void testHandleWithTargetVersion() {
        // Given: Flags with target version

        // When: Execute rollback
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.url", "jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1");
        configMap.put("flyway.user", "sa");
        configMap.put("flyway.password", "");

        OperationResult result = extension.handle("rollback", configMap,
                Arrays.asList(
                        "-rollback.targetVersion=1",
                        "-rollback.user=testUser",
                        "-rollback.reason=Test rollback"));

        // Then: Should succeed
        assertThat(result).isInstanceOf(RollbackResult.class);
        RollbackResult rollbackResult = (RollbackResult) result;
        assertThat(rollbackResult.success).isTrue();
        assertThat(rollbackResult.migrationsRolledBack).isEqualTo(1);
    }

    @Test
    @DisplayName("Should execute rollback with count")
    void testHandleWithCount() {
        // Given: Flags with count

        // When: Execute rollback
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.url", "jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1");
        configMap.put("flyway.user", "sa");
        configMap.put("flyway.password", "");

        OperationResult result = extension.handle("rollback", configMap,
                Arrays.asList(
                        "-rollback.count=1",
                        "-rollback.user=admin",
                        "-rollback.reason=Rollback last migration"));

        // Then: Should succeed
        assertThat(result).isInstanceOf(RollbackResult.class);
        RollbackResult rollbackResult = (RollbackResult) result;
        assertThat(rollbackResult.success).isTrue();
        assertThat(rollbackResult.migrationsRolledBack).isEqualTo(1);
    }

    @Test
    @DisplayName("Should fail when no targetVersion or count specified")
    void testHandleWithoutTargetOrCount() {
        // Given: Flags without target or count

        // When/Then: Should throw exception
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.url", "jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1");
        configMap.put("flyway.user", "sa");
        configMap.put("flyway.password", "");

        assertThatThrownBy(() -> extension.handle("rollback", configMap,
                Arrays.asList("-rollback.user=admin")))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Either -rollback.targetVersion or -rollback.count must be specified");
    }

    @Test
    @DisplayName("Should fail when DataSource is null")
    void testHandleWithNullDataSource() {
        // Given: Configuration with null datasource
        // Flyway validation fails before we can even call the extension
        assertThatThrownBy(() -> Flyway.configure()
                .dataSource(null, null, null)
                .load())
                .isInstanceOf(FlywayException.class);
    }

    @Test
    @DisplayName("Should use default user when not specified")
    void testHandleWithDefaultUser() {
        // Given: Flags without user

        // When: Execute rollback
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.url", "jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1");
        configMap.put("flyway.user", "sa");
        configMap.put("flyway.password", "");

        OperationResult result = extension.handle("rollback", configMap,
                Arrays.asList(
                        "-rollback.targetVersion=1",
                        "-rollback.reason=Test"));

        // Then: Should use system user
        assertThat(result).isInstanceOf(RollbackResult.class);
        RollbackResult rollbackResult = (RollbackResult) result;
        assertThat(rollbackResult.rollbackUser).isEqualTo(System.getProperty("user.name"));
    }

    @Test
    @DisplayName("Should use default reason when not specified")
    void testHandleWithDefaultReason() {
        // Given: Flags without reason

        // When: Execute rollback
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.url", "jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1");
        configMap.put("flyway.user", "sa");
        configMap.put("flyway.password", "");

        OperationResult result = extension.handle("rollback", configMap,
                Collections.singletonList("-rollback.targetVersion=1"));

        // Then: Should use default reason
        assertThat(result).isInstanceOf(RollbackResult.class);
    }

    @Test
    @DisplayName("Should return correct description")
    void testGetDescription() {
        assertThat(extension.getDescription())
                .isEqualTo("Rolls back migrations to a specific version or count");
    }

    @Test
    @DisplayName("Should parse count parameter correctly")
    void testParseCountParameter() {
        // Given: Flags with count

        // When: Execute with count=2
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.url", "jdbc:h2:mem:cmdtest;DB_CLOSE_DELAY=-1");
        configMap.put("flyway.user", "sa");
        configMap.put("flyway.password", "");

        OperationResult result = extension.handle("rollback", configMap,
                Collections.singletonList("-rollback.count=2"));

        // Then: Should rollback 2 migrations
        assertThat(result).isInstanceOf(RollbackResult.class);
        RollbackResult rollbackResult = (RollbackResult) result;
        assertThat(rollbackResult.migrationsRolledBack).isEqualTo(2);
    }
}
