package org.flywaydbextended.reporting;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

/**
 * Callback that intercepts Flyway operations and saves results to configured
 * output.
 */
public class ResultOutputCallback implements Callback {

    private final ResultOutputConfiguration config;
    private final ResultOutputWriter writer;

    public ResultOutputCallback(ResultOutputConfiguration config) {
        this.config = config;
        this.writer = new ResultOutputWriter(config);
    }

    @Override
    public boolean supports(Event event, Context context) {
        // Intercept after-operation events
        return event == Event.AFTER_MIGRATE ||
                event == Event.AFTER_CLEAN ||
                event == Event.AFTER_INFO ||
                event == Event.AFTER_VALIDATE ||
                event == Event.AFTER_BASELINE ||
                event == Event.AFTER_REPAIR;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false; // Run outside transaction
    }

    @Override
    public void handle(Event event, Context context) {
        if (!config.isSaveResults()) {
            return;
        }

        try {
            // Get operation type from event
            String operationType = getOperationType(event);

            System.out.println("Result output callback triggered for: " + operationType);

            // Note: In a real implementation, you'd capture the actual OperationResult
            // This would require extending Flyway internals or using a shared state
            // mechanism

        } catch (Exception e) {
            System.err.println("Error in result output callback: " + e.getMessage());
        }
    }

    private String getOperationType(Event event) {
        switch (event) {
            case AFTER_MIGRATE:
                return "MIGRATE";
            case AFTER_CLEAN:
                return "CLEAN";
            case AFTER_INFO:
                return "INFO";
            case AFTER_VALIDATE:
                return "VALIDATE";
            case AFTER_BASELINE:
                return "BASELINE";
            case AFTER_REPAIR:
                return "REPAIR";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Public method to manually write a result.
     * Can be called from external code after operations.
     */
    public void writeResult(String operationType, org.flywaydb.core.api.output.OperationResult result) {
        writer.writeResult(operationType, result);
    }

    @Override
    public String getCallbackName() {
        return "ResultOutputCallback";
    }
}
