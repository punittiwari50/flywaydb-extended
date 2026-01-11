package org.flywaydbextended.api;

import org.flywaydb.core.api.output.OperationResultBase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result object for rollback operations.
 * Implements OperationResult to integrate with Flyway's result handling.
 */
public class RollbackResult extends OperationResultBase {

    public final String database;
    public final int migrationsRolledBack;
    public final List<String> rolledBackVersions;
    public final boolean success;
    public final String rollbackUser;
    public final LocalDateTime rollbackTime;
    public final String errorMessage;

    public RollbackResult(String database, int migrationsRolledBack,
            List<String> rolledBackVersions, boolean success,
            String rollbackUser, String errorMessage) {
        this.database = database;
        this.migrationsRolledBack = migrationsRolledBack;
        this.rolledBackVersions = rolledBackVersions;
        this.success = success;
        this.rollbackUser = rollbackUser;
        this.rollbackTime = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return String.format(
                "RollbackResult{database='%s', migrationsRolledBack=%d, versions=%s, success=%s, user='%s', time=%s}",
                database, migrationsRolledBack, rolledBackVersions, success, rollbackUser, rollbackTime);
    }
}
