package org.flywaydbextended.core;


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.FlywayExecutor;
import org.flywaydb.core.ProgressLogger;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.executor.Context;
import org.flywaydb.core.api.output.CommandResultFactory;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.extensibility.AppliedMigration;
import org.flywaydb.core.extensibility.CommandExtension;
import org.flywaydb.core.extensibility.MigrationType;
import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.database.base.Connection;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.exception.FlywayMigrateException;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.flywaydb.core.internal.info.MigrationInfoServiceImpl;
import org.flywaydb.core.internal.jdbc.ExecutionTemplateFactory;
import org.flywaydb.core.internal.resolver.CompositeMigrationResolver;
import org.flywaydb.core.internal.schemahistory.SchemaHistory;
import org.flywaydb.core.internal.util.StopWatch;
import org.flywaydb.core.internal.util.StringUtils;
import org.flywaydb.core.internal.util.TimeFormat;
import org.flywaydb.core.internal.util.ValidatePatternUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndoCommandExtension implements CommandExtension<MigrateResult>, FlywayExecutor.Command<MigrateResult> {
	private static final Logger LOG = LoggerFactory.getLogger(UndoCommandExtension.class);
	
	public static final String UNDO_COMMAND = "undo";
	
    private Configuration configuration;
    private List<String> flags;
    
    // Command State
    private Database database;
    private SchemaHistory schemaHistory;
    private Schema schema;
    private CompositeMigrationResolver migrationResolver;
    private CallbackExecutor<Event> callbackExecutor;
    private Connection connectionUserObjects;
    private MigrateResult migrateResult;
    private boolean isPreviousVersioned;
    private final List<ResolvedMigration> appliedResolvedMigrations = new ArrayList<>();
    private ProgressLogger progress;

    @Override
    public boolean handlesCommand(String command) {
        return UNDO_COMMAND.equals(command);
    }

    @Override
    public boolean handlesParameter(String parameter) {
        return false; // We can handle specific parameters if needed, e.g. -undo.target
    }

    @Override
    public MigrateResult handle(Configuration config, List<String> flags) throws FlywayException {
        this.configuration = config;
        this.flags = flags;
        
        // Create executor to set up the environment (DB, SchemaHistory, etc.)
        FlywayExecutor executor = new FlywayExecutor(config);
        
        // Execute this command within the initialized environment
        return executor.execute(this, false, null); // passing telemetry as null for now
    }

    @Override
    public MigrateResult execute(CompositeMigrationResolver migrationResolver,
                                 SchemaHistory schemaHistory,
                                 Database database,
                                 Schema defaultSchema,
                                 Schema[] schemas,
                                 CallbackExecutor<Event> callbackExecutor,
                                 org.flywaydb.core.internal.jdbc.StatementInterceptor statementInterceptor) {

        this.database = database;
        this.connectionUserObjects = database.getMigrationConnection();
        this.schemaHistory = schemaHistory;
        this.schema = defaultSchema;
        this.migrationResolver = migrationResolver;
        this.callbackExecutor = callbackExecutor;
        this.progress = configuration.createProgress("undo");

        return undo();
    }

    private MigrateResult undo() throws FlywayException {
        callbackExecutor.onMigrateOrUndoEvent(Event.BEFORE_UNDO);

        migrateResult = CommandResultFactory.createMigrateResult(database.getCatalog(),
                                                                 database.getDatabaseType().getName(),
                                                                 configuration);

        int count;
        try {
            // Reusing the migration logic structure for undo
            count = configuration.isGroup() ?
                    schemaHistory.lock(this::undoAll) :
                    undoAll();

            migrateResult.targetSchemaVersion = getTargetVersion();
            migrateResult.migrationsExecuted = count;

            logSummary(count, migrateResult.getTotalMigrationTime(), migrateResult.targetSchemaVersion);

        } catch (FlywayException e) {
            callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_UNDO_ERROR);
            throw e;
        }

        if (count > 0) {
            // Event.AFTER_UNDO_APPLIED does not exist, skipping.
        }
        callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_UNDO);

        return migrateResult;
    }

    private String getTargetVersion() {
        if (!migrateResult.migrations.isEmpty()) {
            for (int i = migrateResult.migrations.size() - 1; i >= 0; i--) {
                String targetVersion = migrateResult.migrations.get(i).version;
                if (!targetVersion.isEmpty()) {
                    return targetVersion;
                }
            }
        }
        return null;
    }

    private int undoAll() {
        int total = 0;
        isPreviousVersioned = true;

        if (configuration.isGroup() && !database.supportsDdlTransactions()) {
            LOG.warn("Enabling the 'group' parameter is recommended only for databases that support DDL transactions.");
        }

        while (true) {
            final boolean firstRun = total == 0;
            int count = configuration.isGroup()
                    ? undoGroup(firstRun)
                    : schemaHistory.lock(() -> undoGroup(firstRun));

            migrateResult.migrationsExecuted += count;
            total += count;
            
            // For Undo, we typically do one pass or strictly follow target.
            if (count == 0 || total > 0) {
                 break; 
            }
        }

        if (isPreviousVersioned) {
            callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_VERSIONED);
        }

        return total;
    }

    private Integer undoGroup(boolean firstRun) {

        // 2. Refresh infoService to see everything (applied)
        MigrationInfoServiceImpl infoService =
                new MigrationInfoServiceImpl(migrationResolver, schemaHistory, database, configuration,
                                             configuration.getTarget(), configuration.isOutOfOrder(), ValidatePatternUtils.getIgnoreAllPattern(), configuration.getCherryPick());
        infoService.refresh();

        MigrationInfo current = infoService.current();
        MigrationVersion currentSchemaVersion = current == null ? MigrationVersion.EMPTY : current.getVersion();
        if (firstRun) {
            LOG.info("Current version of schema " + schema + ": " + currentSchemaVersion);
            MigrationVersion schemaVersionToOutput = currentSchemaVersion == null ? MigrationVersion.EMPTY : currentSchemaVersion;
            migrateResult.initialSchemaVersion = schemaVersionToOutput.getVersion();
        }

        // 3. Manually resolve 'Undo' (U-prefixed) migrations using the proxy config
        Collection<ResolvedMigration> resolvedUndoMigrations = migrationResolver.resolveMigrations(this.configuration);

        // 4. Build a map of Version -> ResolvedUndoMigration
        Map<MigrationVersion, ResolvedMigration> undoMap = new HashMap<>();
        for (ResolvedMigration rm : resolvedUndoMigrations) {
            if (rm.getVersion() != null) {
                undoMap.put(rm.getVersion(), rm);
            }
        }

        // 5. Determine Target Versions from Flags or Config
        // For this example, we'll try to parse flags or use config.getTarget() as a simpler proxy for "undo target"
        // In a real CLI extension, we'd parse flags like "-target=1.0"
        List<MigrationVersion> targetVersions = new ArrayList<>();
        // Example: if flags contains specific versions, etc.
        // For now, simpler equality:
        
        // 6. Iterate APPLIED migrations in REVERSE order
        LinkedHashMap<MigrationInfo, Boolean> group = new LinkedHashMap<>();
        List<AppliedMigration> appliedMigrations = schemaHistory.allAppliedMigrations();
        List<AppliedMigration> reverseApplied = new ArrayList<>(appliedMigrations);
        Collections.reverse(reverseApplied);

        for (AppliedMigration applied : reverseApplied) {
            if (applied.getType().isUndo()) continue; 

            // Logic to determine if we should undo this specific migration would go here.
            // For example: if (applied.getVersion().compareTo(target) > 0) ...
            
            ResolvedMigration undoMigration = undoMap.get(applied.getVersion());
            if (undoMigration != null) {
                // Use UndoMigrationInfo instead of reflection
                group.put(new UndoMigrationInfo(undoMigration), false);
                
                if (!configuration.isGroup()) {
                     break; 
                }
            }
        }

        if (!group.isEmpty()) {
            applyMigrations(group, configuration.isSkipExecutingMigrations());
        }
        return group.size();
    }

    private void applyMigrations(final LinkedHashMap<MigrationInfo, Boolean> group, boolean skipExecutingMigrations) {
        boolean executeGroupInTransaction = isExecuteGroupInTransaction(group);
        final StopWatch stopWatch = new StopWatch();
        try {
            if (executeGroupInTransaction) {
                ExecutionTemplateFactory.createExecutionTemplate(connectionUserObjects.getJdbcConnection(), database).execute(() -> {
                    doMigrateGroup(group, stopWatch, skipExecutingMigrations, true);
                    return null;
                });
            } else {
                doMigrateGroup(group, stopWatch, skipExecutingMigrations, false);
            }
        } catch (FlywayMigrateException e) {
             MigrationInfo migration = e.getMigration();
            String failedMsg = "Undo of " + toMigrationText(migration, e.isExecutableInTransaction(), e.isOutOfOrder()) + " failed!";
            stopWatch.stop();
            int executionTime = (int) stopWatch.getTotalTimeMillis();

            migrateResult.putFailedMigration(migration, executionTime);

            if (database.supportsDdlTransactions() && executeGroupInTransaction) {
                LOG.error(failedMsg + " Changes successfully rolled back.");
                List<MigrationInfoImpl> rolledBack = group.keySet().stream()
                    .filter(info -> info instanceof MigrationInfoImpl)
                    .map(info -> (MigrationInfoImpl) info)
                    .collect(Collectors.toList());
                if (!rolledBack.isEmpty()) {
                    migrateResult.markAsRolledBack(rolledBack);
                }
            } else {
                LOG.error(failedMsg + " Please restore backups and roll back database and code!");
                // For undo failure, we might want to log it in history as failed logic?
                // schemaHistory.addAppliedMigration(...)
            }
            throw e;
        }
    }

    private boolean isExecuteGroupInTransaction(LinkedHashMap<MigrationInfo, Boolean> group) {
        boolean executeGroupInTransaction = true;
        boolean first = true;

        for (Map.Entry<MigrationInfo, Boolean> entry : group.entrySet()) {
            ResolvedMigration resolvedMigration = getResolvedMigration(entry.getKey());
            boolean inTransaction = resolvedMigration.getExecutor().canExecuteInTransaction();

            if (first) {
                executeGroupInTransaction = inTransaction;
                first = false;
                continue;
            }
            executeGroupInTransaction &= inTransaction;
        }
        return executeGroupInTransaction;
    }

    private ResolvedMigration getResolvedMigration(MigrationInfo info) {
        if (info instanceof MigrationInfoImpl) {
            return ((MigrationInfoImpl) info).getResolvedMigration();
        } else if (info instanceof UndoMigrationInfo) {
            return ((UndoMigrationInfo) info).getResolvedMigration();
        }
        throw new FlywayException("Unknown MigrationInfo type: " + info.getClass());
    }

    private void doMigrateGroup(LinkedHashMap<MigrationInfo, Boolean> group, StopWatch stopWatch, boolean skipExecutingMigrations, boolean isExecuteInTransaction) {
        Context context = new Context() {
            @Override
            public Configuration getConfiguration() {
                return configuration;
            }

            @Override
            public java.sql.Connection getConnection() {
                return connectionUserObjects.getJdbcConnection();
            }
        };

        progress.pushSteps(group.size());
        for (Map.Entry<MigrationInfo, Boolean> entry : group.entrySet()) {
            final MigrationInfo migration = entry.getKey();
            boolean isOutOfOrder = entry.getValue();

            ResolvedMigration resolvedMigration = getResolvedMigration(migration);
            final String migrationText = toMigrationText(migration, resolvedMigration.getExecutor().canExecuteInTransaction(), isOutOfOrder);

            stopWatch.start();

            if (isPreviousVersioned && migration.getVersion() == null) {
                callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_VERSIONED);
                callbackExecutor.onMigrateOrUndoEvent(Event.BEFORE_REPEATABLES);
                isPreviousVersioned = false;
            }

            if (skipExecutingMigrations) {
                LOG.debug("Skipping execution of undo of " + migrationText);
                progress.log("Skipping undo of " + migration.getScript());
            } else {
                LOG.debug("Starting undo of " + migrationText + " ...");
                progress.log("Starting undo of " + migration.getScript() + " ...");

                connectionUserObjects.restoreOriginalState();
                connectionUserObjects.changeCurrentSchemaTo(schema);

                try {
                    callbackExecutor.setMigrationInfo(migration);
                    callbackExecutor.onEachMigrateOrUndoEvent(Event.BEFORE_EACH_UNDO);
                    try {
                        LOG.info("Undoing " + migrationText);
                        progress.log("Undoing " + migration.getScript());

                        boolean oldAutoCommit = context.getConnection().getAutoCommit();
                        if (database.useSingleConnection() && !isExecuteInTransaction) {
                            context.getConnection().setAutoCommit(true);
                        }
                        resolvedMigration.getExecutor().execute(context);
                        if (database.useSingleConnection() && !isExecuteInTransaction) {
                            context.getConnection().setAutoCommit(oldAutoCommit);
                        }

                        appliedResolvedMigrations.add(resolvedMigration);
                    } catch (FlywayException e) {
                        callbackExecutor.onEachMigrateOrUndoEvent(Event.AFTER_EACH_UNDO_ERROR);
                        throw new FlywayMigrateException(migration, isOutOfOrder, e, resolvedMigration.getExecutor().canExecuteInTransaction(), migrateResult);
                    } catch (SQLException e) {
                        callbackExecutor.onEachMigrateOrUndoEvent(Event.AFTER_EACH_UNDO_ERROR);
                        throw new FlywayMigrateException(migration, isOutOfOrder, e, resolvedMigration.getExecutor().canExecuteInTransaction(), migrateResult);
                    }

                    LOG.debug("Successfully completed undo of " + migrationText);
                    progress.log("Successfully completed undo of " + migration.getScript());
                    callbackExecutor.onEachMigrateOrUndoEvent(Event.AFTER_EACH_UNDO);
                } finally {
                    callbackExecutor.setMigrationInfo(null);
                }
            }

            stopWatch.stop();
            int executionTime = (int) stopWatch.getTotalTimeMillis();

            migrateResult.migrations.add(CommandResultFactory.createMigrateOutput(migration, executionTime, null));
            migrateResult.putSuccessfulMigration(migration, executionTime);

            // Add as APPLIED first to record the execution
            schemaHistory.addAppliedMigration(migration.getVersion(), migration.getDescription(), migration.getType(),
                                              migration.getScript(), resolvedMigration.getChecksum(), executionTime, true);
            
            // Update to UNDO_CUSTOM
            AppliedMigration applied = schemaHistory.allAppliedMigrations().stream()
                .filter(am -> java.util.Objects.equals(am.getVersion(), migration.getVersion()))
                .max(Comparator.comparingInt(AppliedMigration::getInstalledRank))
                .orElse(null);

            if (applied != null) {
                 schemaHistory.update(applied, new ResolvedMigration() {
                        @Override
                        public MigrationVersion getVersion() { return migration.getVersion(); }
                        @Override
                        public String getDescription() { return migration.getDescription(); }
                        @Override
                        public String getScript() { return migration.getScript(); }
                        @Override
                        public Integer getChecksum() { return resolvedMigration.getChecksum(); }
                        @Override
                        public MigrationType getType() {
                            return new MigrationType() {
                                @Override public String name() { return "UNDO_CUSTOM"; }
                                @Override public boolean isSynthetic() { return false; }
                                @Override public boolean isUndo() { return true; }
                                @Override public boolean isBaseline() { return false; }
                            };
                        }
                        @Override
                        public String getPhysicalLocation() { return migration.getPhysicalLocation(); }
                        @Override
                        public org.flywaydb.core.api.executor.MigrationExecutor getExecutor() { return resolvedMigration.getExecutor(); }
                         @Override
                        public boolean checksumMatches(Integer checksum) {
                            return java.util.Objects.equals(applied.getChecksum(), checksum);
                        }
                        @Override
                        public boolean checksumMatchesWithoutBeingIdentical(Integer checksum) {
                            return false;
                        }
                    });
                 LOG.info("Updated schema history type to UNDO_CUSTOM for version " + migration.getVersion());
            }
        }
    }

    private String toMigrationText(MigrationInfo migration, boolean canExecuteInTransaction, boolean isOutOfOrder) {
        return "schema " + schema + " version " + doQuote(migration.getVersion() != null ? migration.getVersion().toString() : "?");
    }

    private String doQuote(String text) {
        return "\"" + text + "\"";
    }

    private void logSummary(int migrationSuccessCount, long executionTime, String targetVersion) {
        if (migrationSuccessCount == 0) {
            LOG.info("Schema " + schema + " is up to date. No undo necessary.");
            return;
        }
        String targetText = (targetVersion != null) ? ", now at version v" + targetVersion : "";
        String migrationText = "migration" + StringUtils.pluralizeSuffix(migrationSuccessCount);
        LOG.info("Successfully undid " + migrationSuccessCount + " " + migrationText + " to schema " + schema
                         + targetText + " (execution time " + TimeFormat.format(executionTime) + ")");
    }

    private static class UndoMigrationInfo implements MigrationInfo {
        private final ResolvedMigration resolvedMigration;

        public UndoMigrationInfo(ResolvedMigration resolvedMigration) {
            this.resolvedMigration = resolvedMigration;
        }

        public ResolvedMigration getResolvedMigration() {
            return resolvedMigration;
        }

        @Override public MigrationType getType() { return resolvedMigration.getType(); }
        @Override public Integer getChecksum() { return resolvedMigration.getChecksum(); }
        @Override public MigrationVersion getVersion() { return resolvedMigration.getVersion(); }
        @Override public String getDescription() { return resolvedMigration.getDescription(); }
        @Override public String getScript() { return resolvedMigration.getScript(); }
        @Override public MigrationState getState() { return MigrationState.PENDING; }
        @Override public Date getInstalledOn() { return null; }
        @Override public String getInstalledBy() { return null; }
        @Override public Integer getInstalledRank() { return null; }
        @Override public Integer getExecutionTime() { return null; }
        @Override public String getPhysicalLocation() { return resolvedMigration.getPhysicalLocation(); }
        @Override public int compareTo(MigrationInfo o) { return getVersion().compareTo(o.getVersion()); }
        @Override public int compareVersion(MigrationInfo o) { return getVersion().compareTo(o.getVersion()); }
    }
}
