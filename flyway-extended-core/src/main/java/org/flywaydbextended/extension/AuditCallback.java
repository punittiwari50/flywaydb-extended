package org.flywaydbextended.extension;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.MigrationInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/**
 * Callback-based audit logger for Flyway migrations.
 * 
 * This replaces the StatementInterceptor approach which is not available
 * via SPI in Flyway Community Edition.
 * 
 * Use Cases:
 * 1. Log all migration events executed by Flyway
 * 2. Track migration execution details
 * 3. Audit database changes
 * 4. Debug migration issues
 * 5. Integration with external logging/monitoring systems
 * 
 * Usage:
 * 
 * <pre>
 * Flyway flyway = Flyway.configure()
 *         .dataSource(url, user, password)
 *         .callbacks(new AuditCallback())
 *         .load();
 * </pre>
 * 
 * Configuration via system properties:
 * - flyway.audit.enabled=true (default: true)
 * - flyway.audit.verbose=true (default: false)
 */
public class AuditCallback implements Callback {

    private final boolean enabled;
    private final boolean verbose;

    /**
     * Events this callback supports.
     */
    private static final Set<Event> SUPPORTED_EVENTS = EnumSet.of(
            Event.BEFORE_MIGRATE,
            Event.AFTER_MIGRATE,
            Event.AFTER_MIGRATE_ERROR,
            Event.BEFORE_EACH_MIGRATE,
            Event.AFTER_EACH_MIGRATE,
            Event.AFTER_EACH_MIGRATE_ERROR,
            Event.BEFORE_CLEAN,
            Event.AFTER_CLEAN,
            Event.BEFORE_UNDO,
            Event.AFTER_UNDO,
            Event.BEFORE_EACH_UNDO,
            Event.AFTER_EACH_UNDO,
            Event.BEFORE_REPAIR,
            Event.AFTER_REPAIR,
            Event.BEFORE_VALIDATE,
            Event.AFTER_VALIDATE,
            Event.BEFORE_BASELINE,
            Event.AFTER_BASELINE,
            Event.BEFORE_INFO,
            Event.AFTER_INFO);

    /**
     * Create an AuditCallback with default settings (enabled=true, verbose=false).
     */
    public AuditCallback() {
        this(Boolean.parseBoolean(System.getProperty("flyway.audit.enabled", "true")),
                Boolean.parseBoolean(System.getProperty("flyway.audit.verbose", "false")));
    }

    /**
     * Create an AuditCallback with explicit settings.
     */
    public AuditCallback(boolean enabled, boolean verbose) {
        this.enabled = enabled;
        this.verbose = verbose;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return enabled && SUPPORTED_EVENTS.contains(event);
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        // Log events should not affect transaction behavior
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        if (!enabled) {
            return;
        }

        String message = buildEventMessage(event, context);
        logEvent(event.name(), message);
    }

    @Override
    public String getCallbackName() {
        return "AuditCallback";
    }

    /**
     * Build a descriptive message for the event.
     */
    private String buildEventMessage(Event event, Context context) {
        StringBuilder sb = new StringBuilder();

        // Add migration info if available
        MigrationInfo migrationInfo = context.getMigrationInfo();
        if (migrationInfo != null) {
            sb.append("Migration: V").append(migrationInfo.getVersion());
            sb.append(" - ").append(migrationInfo.getDescription());
            sb.append(" [").append(migrationInfo.getType()).append("]");

            if (migrationInfo.getExecutionTime() != null) {
                sb.append(" (").append(migrationInfo.getExecutionTime()).append(" ms)");
            }
        }

        // Add context info for verbose mode
        if (verbose && context.getConnection() != null) {
            try {
                String catalog = context.getConnection().getCatalog();
                if (catalog != null) {
                    sb.append(" | Database: ").append(catalog);
                }
            } catch (Exception e) {
                // Ignore connection errors during logging
            }
        }

        return sb.length() > 0 ? sb.toString() : event.name();
    }

    /**
     * Log an event to the audit log.
     */
    protected void logEvent(String eventType, String message) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String icon = getEventIcon(eventType);
        String logEntry = String.format("[%s] %s [%s] %s",
                timestamp, icon, eventType, message);

        // Default: print to stdout
        System.out.println("AUDIT: " + logEntry);
    }

    /**
     * Get an icon for the event type.
     */
    private String getEventIcon(String eventType) {
        if (eventType.contains("ERROR")) {
            return "❌";
        } else if (eventType.startsWith("BEFORE")) {
            return "🔄";
        } else if (eventType.startsWith("AFTER")) {
            return "✅";
        }
        return "📋";
    }

    /**
     * Check if audit logging is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if verbose mode is enabled.
     */
    public boolean isVerbose() {
        return verbose;
    }
}
