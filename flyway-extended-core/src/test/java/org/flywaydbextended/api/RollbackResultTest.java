package org.flywaydbextended.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for RollbackResult.
 */
@DisplayName("RollbackResult Tests")
class RollbackResultTest {

    @Test
    @DisplayName("Should create successful rollback result")
    void testSuccessfulRollbackResult() {
        List<String> versions = Arrays.asList("2", "1");

        RollbackResult result = new RollbackResult(
                "testdb",
                2,
                versions,
                true,
                "admin",
                null);

        assertThat(result.database).isEqualTo("testdb");
        assertThat(result.migrationsRolledBack).isEqualTo(2);
        assertThat(result.rolledBackVersions).containsExactly("2", "1");
        assertThat(result.success).isTrue();
        assertThat(result.rollbackUser).isEqualTo("admin");
        assertThat(result.rollbackTime).isNotNull();
        assertThat(result.errorMessage).isNull();
    }

    @Test
    @DisplayName("Should create failed rollback result")
    void testFailedRollbackResult() {
        RollbackResult result = new RollbackResult(
                "testdb",
                0,
                Collections.emptyList(),
                false,
                "admin",
                "Migration failed: table not found");

        assertThat(result.success).isFalse();
        assertThat(result.errorMessage).isEqualTo("Migration failed: table not found");
        assertThat(result.migrationsRolledBack).isEqualTo(0);
        assertThat(result.rolledBackVersions).isEmpty();
    }

    @Test
    @DisplayName("Should generate proper toString")
    void testToString() {
        List<String> versions = Arrays.asList("2");

        RollbackResult result = new RollbackResult(
                "mydb",
                1,
                versions,
                true,
                "testuser",
                null);

        String str = result.toString();

        assertThat(str).contains("mydb");
        assertThat(str).contains("migrationsRolledBack=1");
        assertThat(str).contains("[2]");
        assertThat(str).contains("success=true");
        assertThat(str).contains("testuser");
    }

    @Test
    @DisplayName("Should handle empty versions list")
    void testEmptyVersionsList() {
        RollbackResult result = new RollbackResult(
                "db",
                0,
                Collections.emptyList(),
                true,
                "user",
                null);

        assertThat(result.rolledBackVersions).isEmpty();
        assertThat(result.migrationsRolledBack).isEqualTo(0);
    }

    @Test
    @DisplayName("Should set rollback time automatically")
    void testRollbackTimeIsSet() {
        RollbackResult result = new RollbackResult(
                "db",
                1,
                Collections.singletonList("1"),
                true,
                "user",
                null);

        assertThat(result.rollbackTime).isNotNull();
        // Time should be recent (within last minute)
        assertThat(result.rollbackTime)
                .isAfter(java.time.LocalDateTime.now().minusMinutes(1));
    }
}
