package org.flywaydbextended.reporting;

import org.flywaydb.core.extensibility.ConfigurationExtension;

/**
 * Configuration extension for saving Flyway operation results to files.
 */
public class ResultOutputConfiguration implements ConfigurationExtension {

    private boolean saveResults = false;
    private String format = "JSON";
    private String location = "./flyway-results.json";
    private boolean includeRollback = true;

    public String getConfigurationParameterFromEnvironmentVariable(String environmentVariable) {
        switch (environmentVariable) {
            case "FLYWAY_OUTPUT_SAVE_RESULTS":
                return "saveResults";
            case "FLYWAY_OUTPUT_FORMAT":
                return "format";
            case "FLYWAY_OUTPUT_LOCATION":
                return "location";
            case "FLYWAY_OUTPUT_INCLUDE_ROLLBACK":
                return "includeRollback";
            default:
                return null;
        }
    }

    public void extractParametersFromConfiguration(java.util.Map<String, String> configuration) {
        String saveResultsStr = configuration.get("flyway.output.saveResults");
        if (saveResultsStr != null) {
            this.saveResults = Boolean.parseBoolean(saveResultsStr);
        }

        String formatStr = configuration.get("flyway.output.format");
        if (formatStr != null) {
            this.format = formatStr;
        }

        String locationStr = configuration.get("flyway.output.location");
        if (locationStr != null) {
            this.location = locationStr;
        }

        String includeRollbackStr = configuration.get("flyway.output.includeRollback");
        if (includeRollbackStr != null) {
            this.includeRollback = Boolean.parseBoolean(includeRollbackStr);
        }
    }

    // Getters and Setters
    public boolean isSaveResults() {
        return saveResults;
    }

    public void setSaveResults(boolean saveResults) {
        this.saveResults = saveResults;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isIncludeRollback() {
        return includeRollback;
    }

    public void setIncludeRollback(boolean includeRollback) {
        this.includeRollback = includeRollback;
    }
}
