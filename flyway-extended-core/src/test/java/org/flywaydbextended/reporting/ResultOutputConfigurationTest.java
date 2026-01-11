package org.flywaydbextended.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for ResultOutputConfiguration.
 */
@DisplayName("ResultOutputConfiguration Tests")
class ResultOutputConfigurationTest {

    private ResultOutputConfiguration config;

    @BeforeEach
    void setUp() {
        config = new ResultOutputConfiguration();
    }

    @Test
    @DisplayName("Should have correct default values")
    void testDefaultValues() {
        assertThat(config.isSaveResults()).isFalse();
        assertThat(config.getFormat()).isEqualTo("JSON");
        assertThat(config.getLocation()).isEqualTo("./flyway-results.json");
        assertThat(config.isIncludeRollback()).isTrue();
    }

    @Test
    @DisplayName("Should map environment variables correctly")
    void testEnvironmentVariableMapping() {
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_OUTPUT_SAVE_RESULTS"))
                .isEqualTo("saveResults");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_OUTPUT_FORMAT"))
                .isEqualTo("format");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_OUTPUT_LOCATION"))
                .isEqualTo("location");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("FLYWAY_OUTPUT_INCLUDE_ROLLBACK"))
                .isEqualTo("includeRollback");
        assertThat(config.getConfigurationParameterFromEnvironmentVariable("UNKNOWN_VAR"))
                .isNull();
    }

    @Test
    @DisplayName("Should extract parameters from configuration map")
    void testExtractParametersFromConfiguration() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.output.saveResults", "true");
        configMap.put("flyway.output.format", "XML");
        configMap.put("flyway.output.location", "/tmp/results.xml");
        configMap.put("flyway.output.includeRollback", "false");

        config.extractParametersFromConfiguration(configMap);

        assertThat(config.isSaveResults()).isTrue();
        assertThat(config.getFormat()).isEqualTo("XML");
        assertThat(config.getLocation()).isEqualTo("/tmp/results.xml");
        assertThat(config.isIncludeRollback()).isFalse();
    }

    @Test
    @DisplayName("Should handle partial configuration")
    void testPartialConfiguration() {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("flyway.output.saveResults", "true");
        // Other values not set

        config.extractParametersFromConfiguration(configMap);

        assertThat(config.isSaveResults()).isTrue();
        assertThat(config.getFormat()).isEqualTo("JSON"); // default
        assertThat(config.getLocation()).isEqualTo("./flyway-results.json"); // default
    }

    @Test
    @DisplayName("Should handle empty configuration")
    void testEmptyConfiguration() {
        Map<String, String> configMap = new HashMap<>();

        config.extractParametersFromConfiguration(configMap);

        // All should remain default
        assertThat(config.isSaveResults()).isFalse();
        assertThat(config.getFormat()).isEqualTo("JSON");
    }

    @Test
    @DisplayName("Should set and get saveResults")
    void testSetSaveResults() {
        config.setSaveResults(true);
        assertThat(config.isSaveResults()).isTrue();

        config.setSaveResults(false);
        assertThat(config.isSaveResults()).isFalse();
    }

    @Test
    @DisplayName("Should set and get format")
    void testSetFormat() {
        config.setFormat("CSV");
        assertThat(config.getFormat()).isEqualTo("CSV");
    }

    @Test
    @DisplayName("Should set and get location")
    void testSetLocation() {
        config.setLocation("/custom/path/results.json");
        assertThat(config.getLocation()).isEqualTo("/custom/path/results.json");
    }

    @Test
    @DisplayName("Should set and get includeRollback")
    void testSetIncludeRollback() {
        config.setIncludeRollback(false);
        assertThat(config.isIncludeRollback()).isFalse();

        config.setIncludeRollback(true);
        assertThat(config.isIncludeRollback()).isTrue();
    }
}
