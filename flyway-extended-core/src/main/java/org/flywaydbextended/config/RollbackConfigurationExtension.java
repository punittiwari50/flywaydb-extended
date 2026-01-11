package org.flywaydbextended.config;

import org.flywaydb.core.extensibility.ConfigurationExtension;

/**
 * Configuration extension for rollback functionality.
 * Provides custom configuration parameters for rollback operations.
 */
public class RollbackConfigurationExtension implements ConfigurationExtension {

    private boolean enabled = false;
    private String user = System.getProperty("user.name");
    private String auditTable = "flyway_schema_history";

    public String getConfigurationParameterFromEnvironmentVariable(String environmentVariable) {
        switch (environmentVariable) {
            case "FLYWAY_ROLLBACK_ENABLED":
                return "enabled";
            case "FLYWAY_ROLLBACK_USER":
                return "user";
            case "FLYWAY_ROLLBACK_AUDIT_TABLE":
                return "auditTable";
            default:
                return null;
        }
    }

    public void extractParametersFromConfiguration(java.util.Map<String, String> configuration) {
        String enabledStr = configuration.get("flyway.rollback.enabled");
        if (enabledStr != null) {
            this.enabled = Boolean.parseBoolean(enabledStr);
        }

        String userStr = configuration.get("flyway.rollback.user");
        if (userStr != null) {
            this.user = userStr;
        }

        String auditTableStr = configuration.get("flyway.rollback.auditTable");
        if (auditTableStr != null) {
            this.auditTable = auditTableStr;
        }
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getAuditTable() {
        return auditTable;
    }

    public void setAuditTable(String auditTable) {
        this.auditTable = auditTable;
    }
}
