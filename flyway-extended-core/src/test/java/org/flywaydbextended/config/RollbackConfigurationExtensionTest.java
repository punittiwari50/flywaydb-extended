package org.flywaydbextended.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for RollbackConfigurationExtension.
 */
@DisplayName("RollbackConfigurationExtension Tests")
class RollbackConfigurationExtensionTest {

    private RollbackConfigurationExtension config;

    @BeforeEach
    void setUp() {
        config = new RollbackConfigurationExtension();
    }

    @Test
    @DisplayName("Should have correct default values")
    void testDefaultValues() {
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getUser()).isEqualTo(System.getProperty("user.name"));
        assertThat(config.getAuditTable()).isEqualTo("flyway_schema_history");
    }

    @Test
    @DisplayName("Should map environment variables correctly")
    void testEnvironmentVariableMapping() {
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_ROLLBACK_ENABLED"))
                .isEqualTo("enabled");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_ROLLBACK_USER"))
                .isEqualTo("user");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_ROLLBACK_AUDIT_TABLE"))
                .isEqualTo("auditTable");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("UNKNOWN_VAR"))
                .isNull();
    }

    @Test
    @DisplayName("Should extract parameters from configuration map")
    void testExtractParametersFromConfiguration() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.rollback.enabled", "true");
        configMap.put("flyway.rollback.user", "testuser");
        configMap.put("flyway.rollback.auditTable", "custom_history");

        config.extractParametersFromConfiguration(configMap);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getUser()).isEqualTo("testuser");
        assertThat(config.getAuditTable()).isEqualTo("custom_history");
    }

    @Test
    @DisplayName("Should handle partial configuration")
    void testPartialConfiguration() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.rollback.enabled", "true");
        // user and auditTable not set

        config.extractParametersFromConfiguration(configMap);

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getUser()).isEqualTo(System.getProperty("user.name")); // default
        assertThat(config.getAuditTable()).isEqualTo("flyway_schema_history"); // default
    }

    @Test
    @DisplayName("Should handle empty configuration")
    void testEmptyConfiguration() {
        Map<String, String> configMap = new HashMap<>();

        config.extractParametersFromConfiguration(configMap);

        // All should remain default
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getAuditTable()).isEqualTo("flyway_schema_history");
    }

    @Test
    @DisplayName("Should set and get enabled")
    void testSetEnabled() {
        config.setEnabled(true);
        assertThat(config.isEnabled()).isTrue();

        config.setEnabled(false);
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should set and get user")
    void testSetUser() {
        config.setUser("newuser");
        assertThat(config.getUser()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("Should set and get auditTable")
    void testSetAuditTable() {
        config.setAuditTable("my_audit_table");
        assertThat(config.getAuditTable()).isEqualTo("my_audit_table");
    }
}
