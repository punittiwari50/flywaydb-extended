package org.flywaydbextended.reporting;

import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for ResultOutputCallback.
 */
@DisplayName("ResultOutputCallback Tests")
class ResultOutputCallbackTest {

    private ResultOutputConfiguration config;
    private ResultOutputCallback callback;
    private ByteArrayOutputStream outputCapture;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        config = new ResultOutputConfiguration();
        callback = new ResultOutputCallback(config);

        // Capture stdout
        outputCapture = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputCapture));
    }

    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Should return correct callback name")
    void testGetCallbackName() {
        assertThat(callback.getCallbackName()).isEqualTo("ResultOutputCallback");
    }

    @Test
    @DisplayName("Should support after-operation events")
    void testSupportsAfterEvents() {
        assertThat(callback.supports(Event.AFTER_MIGRATE, null)).isTrue();
        assertThat(callback.supports(Event.AFTER_CLEAN, null)).isTrue();
        assertThat(callback.supports(Event.AFTER_INFO, null)).isTrue();
        assertThat(callback.supports(Event.AFTER_VALIDATE, null)).isTrue();
        assertThat(callback.supports(Event.AFTER_BASELINE, null)).isTrue();
        assertThat(callback.supports(Event.AFTER_REPAIR, null)).isTrue();
    }

    @Test
    @DisplayName("Should not support before-operation events")
    void testDoesNotSupportBeforeEvents() {
        assertThat(callback.supports(Event.BEFORE_MIGRATE, null)).isFalse();
        assertThat(callback.supports(Event.BEFORE_CLEAN, null)).isFalse();
        assertThat(callback.supports(Event.BEFORE_INFO, null)).isFalse();
    }

    @Test
    @DisplayName("Should not support each-migration events")
    void testDoesNotSupportEachMigrationEvents() {
        assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, null)).isFalse();
        assertThat(callback.supports(Event.AFTER_EACH_MIGRATE, null)).isFalse();
    }

    @Test
    @DisplayName("Should not handle in transaction")
    void testCannotHandleInTransaction() {
        assertThat(callback.canHandleInTransaction(Event.AFTER_MIGRATE, null)).isFalse();
    }

    @Test
    @DisplayName("Should not output when saveResults is false")
    void testNoOutputWhenSaveResultsDisabled() {
        config.setSaveResults(false);

        callback.handle(Event.AFTER_MIGRATE, null);

        String output = outputCapture.toString();
        assertThat(output).doesNotContain("Result output callback triggered");
    }

    @Test
    @DisplayName("Should output when saveResults is true")
    void testOutputWhenSaveResultsEnabled() {
        try {
            config.setSaveResults(true);

            callback.handle(Event.AFTER_MIGRATE, null);

            String output = outputCapture.toString();
            assertThat(output).contains("Result output callback triggered for: MIGRATE");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should identify operation types correctly")
    void testOperationTypeIdentification() {
        try {
            config.setSaveResults(true);

            callback.handle(Event.AFTER_MIGRATE, null);
            assertThat(outputCapture.toString()).contains("MIGRATE");
            outputCapture.reset();

            callback.handle(Event.AFTER_CLEAN, null);
            assertThat(outputCapture.toString()).contains("CLEAN");
            outputCapture.reset();

            callback.handle(Event.AFTER_VALIDATE, null);
            assertThat(outputCapture.toString()).contains("VALIDATE");
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should write result via public method")
    void testWriteResult() {
        try {
            config.setSaveResults(true);
            config.setLocation("./test-results.json");

            // Create a simple result object
            MigrateResult result = new MigrateResult(
                    "10.0.0",
                    "testdb",
                    "PUBLIC");

            // This should not throw
            callback.writeResult("MIGRATE", result);
        } finally {
            System.setOut(originalOut);
        }
    }
}
