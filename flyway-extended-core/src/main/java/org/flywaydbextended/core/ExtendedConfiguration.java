package org.flywaydbextended.core;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;

public class ExtendedConfiguration extends ClassicConfiguration {

    private String undoSqlMigrationPrefix = "U";

    public ExtendedConfiguration() {
        super();
    }

    public ExtendedConfiguration(Configuration configuration) {
        super(configuration);
    }

    @Override
    public String getUndoSqlMigrationPrefix() {
        return undoSqlMigrationPrefix;
    }

    @Override
    public void setUndoSqlMigrationPrefix(String undoSqlMigrationPrefix) {
        this.undoSqlMigrationPrefix = undoSqlMigrationPrefix;
    }
}
