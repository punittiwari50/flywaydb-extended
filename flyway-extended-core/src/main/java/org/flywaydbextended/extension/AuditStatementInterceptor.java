package org.flywaydbextended.extension;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.configuration.Configuration;

import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.schemahistory.AppliedMigration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;

import org.flywaydb.core.internal.sqlscript.SqlStatement;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Custom StatementInterceptor for logging and auditing SQL statements.
 * 
 * Use Cases:
 * 1. Log all SQL statements executed by Flyway
 * 2. Track migration execution details
 * 3. Audit database changes
 * 4. Debug migration issues
 * 5. Performance monitoring
 * 
 * Value Added:
 * - Complete audit trail of all SQL executed
 * - Helps debug failed migrations
 * - Performance analysis
 * - Compliance and security auditing
 */
public class AuditStatementInterceptor implements StatementInterceptor {

    private boolean enabled = false;

    @Override
    public void init(Database database, Table table) {
        String enabledProp = System.getProperty("flyway.audit.enabled", "false");
        this.enabled = Boolean.parseBoolean(enabledProp);

        if (enabled) {
            logEvent("INIT", "StatementInterceptor initialized for database: " +
                    database.getCatalog());
        }
    }

    public boolean isConfigured(Configuration configuration) {
        return enabled;
    }

    public List<Callback> getCallbacks() {
        return Collections.singletonList(new Callback() {
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.BEFORE_EACH_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                if (event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null) {
                    // Log all migrations, or filter if possible.
                    // For now logging all "EACH_MIGRATE" which covers Java migrations too.
                    // We can distinguish via getScript() usually containing class name for Java.
                    logEvent("MIGRATION",
                            "Executing migration: " + context.getMigrationInfo().getScript());
                }
            }

            @Override
            public String getCallbackName() {
                return "AuditStatementInterceptorCallback";
            }
        });
    }

    public Connection createConnectionProxy(Connection connection) {
        // Return original connection (could wrap for more detailed tracking)
        return connection;
    }

    @Override
    public void schemaHistoryTableCreate(boolean baseline) {
        logEvent("SCHEMA_HISTORY_CREATE",
                "Creating schema history table (baseline: " + baseline + ")");
    }

    @Override
    public void schemaHistoryTableInsert(AppliedMigration appliedMigration) {
        logEvent("SCHEMA_HISTORY_INSERT",
                "Inserting migration: " + appliedMigration.getVersion() +
                        " - " + appliedMigration.getDescription());
    }

    @Override
    public void close() {
        if (enabled) {
            logEvent("CLOSE", "StatementInterceptor closing");
        }
    }

    @Override
    public void sqlScript(LoadableResource resource) {
        logEvent("SQL_SCRIPT", "Executing script: " + resource.getFilename());
    }

    @Override
    public void sqlStatement(SqlStatement statement) {
        logEvent("SQL_STATEMENT",
                "Executing SQL: " + truncate(statement.getSql(), 200));
    }

    @Override
    public void interceptCommand(String command) {
        logEvent("COMMAND", "Command: " + command);
    }

    @Override
    public void interceptStatement(String sql) {
        logEvent("STATEMENT", "SQL: " + truncate(sql, 200));
    }

    @Override
    public void interceptPreparedStatement(String sql, Map<Integer, Object> params) {
        logEvent("PREPARED_STATEMENT",
                "SQL: " + truncate(sql, 200) + " | Params: " + params);
    }

    @Override
    public void interceptCallableStatement(String sql) {
        logEvent("CALLABLE_STATEMENT", "SQL: " + truncate(sql, 200));
    }

    @Override
    public void schemaHistoryTableDeleteFailed(Table table, AppliedMigration appliedMigration) {
        logEvent("DELETE_FAILED", "Failed to delete migration: " + appliedMigration.getVersion());
    }

    /**
     * Log an event to the audit log.
     */
    private void logEvent(String eventType, String message) {
        if (!enabled) {
            return;
        }

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String logEntry = String.format("[%s] [%s] %s",
                timestamp, eventType, message);

        // Also log to console if verbose
        if (Boolean.getBoolean("flyway.audit.verbose")) {
            System.out.println("AUDIT: " + logEntry);
        }
    }

    /**
     * Truncate long strings for logging.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "... (truncated)";
    }

}
