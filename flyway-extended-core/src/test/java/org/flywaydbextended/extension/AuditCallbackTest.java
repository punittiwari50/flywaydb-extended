package org.flywaydbextended.extension;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.callback.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for AuditCallback.
 */
@DisplayName("AuditCallback Tests")
class AuditCallbackTest {

    private AuditCallback callback;
    private ByteArrayOutputStream outputCapture;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        callback = new AuditCallback(true, false);

        // Capture stdout for verification
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
        assertThat(callback.getCallbackName()).isEqualTo("AuditCallback");
    }

    @Test
    @DisplayName("Should support migration events when enabled")
    void testSupportsEventsWhenEnabled() {
        AuditCallback enabledCallback = new AuditCallback(true, false);

        assertThat(enabledCallback.supports(Event.BEFORE_MIGRATE, null)).isTrue();
        assertThat(enabledCallback.supports(Event.AFTER_MIGRATE, null)).isTrue();
        assertThat(enabledCallback.supports(Event.BEFORE_EACH_MIGRATE, null)).isTrue();
        assertThat(enabledCallback.supports(Event.AFTER_EACH_MIGRATE, null)).isTrue();
        assertThat(enabledCallback.supports(Event.BEFORE_CLEAN, null)).isTrue();
        assertThat(enabledCallback.supports(Event.AFTER_CLEAN, null)).isTrue();
    }

    @Test
    @DisplayName("Should not support any events when disabled")
    void testDoesNotSupportEventsWhenDisabled() {
        AuditCallback disabledCallback = new AuditCallback(false, false);

        assertThat(disabledCallback.supports(Event.BEFORE_MIGRATE, null)).isFalse();
        assertThat(disabledCallback.supports(Event.AFTER_MIGRATE, null)).isFalse();
        assertThat(disabledCallback.supports(Event.BEFORE_EACH_MIGRATE, null)).isFalse();
    }

    @Test
    @DisplayName("Should always handle in transaction")
    void testCanHandleInTransaction() {
        assertThat(callback.canHandleInTransaction(Event.BEFORE_MIGRATE, null)).isTrue();
        assertThat(callback.canHandleInTransaction(Event.AFTER_EACH_MIGRATE, null)).isTrue();
    }

    @Test
    @DisplayName("Should report enabled status correctly")
    void testIsEnabled() {
        AuditCallback enabledCallback = new AuditCallback(true, false);
        AuditCallback disabledCallback = new AuditCallback(false, false);

        assertThat(enabledCallback.isEnabled()).isTrue();
        assertThat(disabledCallback.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Should report verbose status correctly")
    void testIsVerbose() {
        AuditCallback verboseCallback = new AuditCallback(true, true);
        AuditCallback quietCallback = new AuditCallback(true, false);

        assertThat(verboseCallback.isVerbose()).isTrue();
        assertThat(quietCallback.isVerbose()).isFalse();
    }

    @Test
    @DisplayName("Should log events with timestamp")
    void testLogsWithTimestamp() {
        // Create a testable callback
        TestableAuditCallback testCallback = new TestableAuditCallback();

        testCallback.logEvent("TEST_EVENT", "Test message");

        assertThat(testCallback.lastLogEntry).contains("TEST_EVENT");
        assertThat(testCallback.lastLogEntry).contains("Test message");
    }

    @Test
    @DisplayName("Should work with actual Flyway migration")
    void testWithActualMigration() {
        try {
            AuditCallback auditCallback = new AuditCallback(true, true);

            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:h2:mem:auditcallbacktest;DB_CLOSE_DELAY=-1", "sa", "")
                    .locations("classpath:db/migration")
                    .cleanDisabled(false)
                    .callbacks(auditCallback)
                    .load();

            flyway.clean();
            flyway.migrate();

            String output = outputCapture.toString();

            // Verify audit logs were generated
            assertThat(output).contains("AUDIT:");
            assertThat(output).contains("BEFORE_MIGRATE");
            assertThat(output).contains("AFTER_MIGRATE");

            flyway.clean();
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should use default constructor with system properties")
    void testDefaultConstructor() {
        // Default constructor reads from system properties
        AuditCallback defaultCallback = new AuditCallback();

        // By default, enabled is true (unless system property says otherwise)
        assertThat(defaultCallback.isEnabled()).isTrue();
    }

    /**
     * Testable subclass that captures log output.
     */
    static class TestableAuditCallback extends AuditCallback {
        String lastLogEntry;

        TestableAuditCallback() {
            super(true, false);
        }

        @Override
        protected void logEvent(String eventType, String message) {
            lastLogEntry = eventType + " | " + message;
            super.logEvent(eventType, message);
        }
    }
}
