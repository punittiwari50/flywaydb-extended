/*-
 * ========================LICENSE_START=================================
 * flyway-extended-core
 * ========================================================================
 * Copyright (C) 2010 - 2026 Red Gate Software Ltd
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.flywaydbextended.core.command;

import static java.util.Optional.ofNullable;
import static org.flywaydb.core.api.CoreMigrationType.UNDO_SCRIPT;
import static org.flywaydbextended.core.utils.FlywayUtils.createContext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.executor.Context;
import org.flywaydb.core.api.output.CommandResultFactory;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.database.base.Connection;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.exception.FlywayMigrateException;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.flywaydb.core.internal.info.MigrationInfoServiceImpl;
import org.flywaydb.core.internal.jdbc.ExecutionTemplateFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.resolver.CompositeMigrationResolver;
import org.flywaydb.core.internal.schemahistory.SchemaHistory;
import org.flywaydb.core.internal.util.StopWatch;
import org.flywaydb.core.internal.util.TimeFormat;
import org.flywaydb.core.internal.util.ValidatePatternUtils;
import org.flywaydbextended.core.api.output.UndoMigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.flywaydb.core.extensibility.AppliedMigration;

public class DbRollback {

	private static final Logger LOGGER = LoggerFactory.getLogger(DbRollback.class);

	private final Configuration configuration;
	private final Database<?> database;
	private final SchemaHistory schemaHistory;
	private final Schema<?, ?> schema;
	private final CompositeMigrationResolver migrationResolver;
	private final CallbackExecutor<Event> callbackExecutor;
	private final Connection<?> connection;
	private final MigrationVersion targetVersion;

	private MigrateResult migrateResult;
	private boolean isPreviousVersioned;
	private final List<ResolvedMigration> appliedResolvedMigrations = new ArrayList<>();

	public DbRollback(Configuration configuration,
			Database<?> database,
			SchemaHistory schemaHistory,
			Schema<?, ?> schema,
			CompositeMigrationResolver migrationResolver,
			CallbackExecutor<Event> callbackExecutor,
			StatementInterceptor statementInterceptor) {
		this.configuration = configuration;
		this.database = database;
		this.schemaHistory = schemaHistory;
		this.schema = schema;
		this.migrationResolver = migrationResolver;
		this.callbackExecutor = callbackExecutor;
		this.connection = database.getMigrationConnection();
		this.targetVersion = configuration.getTarget();
	}

	public MigrateResult undo() throws FlywayException {
		try {
			callbackExecutor.onMigrateOrUndoEvent(Event.BEFORE_UNDO);

			migrateResult = CommandResultFactory.createMigrateResult(database.getCatalog(),
					database.getDatabaseType().getName(), configuration);
			migrateResult.setOperation(UNDO_SCRIPT.name());
			int count;
			try {
				count = configuration.isGroup() ?
				// When group is enabled, start the transaction boundary early to ensure that
				// all undo scripts are executed in a single transaction
						schemaHistory.lock(this::undoAll) :
						// When group is disabled, start the transaction boundary late to ensure that
						// each undo script is executed in its own transaction
						undoAll();

				migrateResult.targetSchemaVersion = getTargetVersion();
				migrateResult.migrationsExecuted = count;

				logSummary(count, migrateResult.getTotalMigrationTime(), migrateResult.targetSchemaVersion);

			} catch (FlywayException e) {
				callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_UNDO_ERROR);
				throw e;
			}

			if (count > 0) {
				LOGGER.info("Command {} undone count: {} with target version: {}", migrateResult.getOperation(), count,
						targetVersion);
			}
			callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_UNDO);
			return migrateResult;
		} catch (FlywayException e) {
			callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_UNDO_ERROR);
			throw e;
		} finally {
			callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_UNDO_OPERATION_FINISH);
		}
	}

	private String getTargetVersion() {
		return migrateResult.migrations.stream().map(m -> m.version).filter(v -> v != null && !v.isEmpty())
				.reduce((first, second) -> second).orElse(null);
	}

	private int undoAll() {
		int total = 0;
		this.isPreviousVersioned = true;

		if (configuration.isGroup() && !database.supportsDdlTransactions()) {
			LOGGER.info(
					"Enabling the 'group' parameter is recommended only for databases that support DDL transactions. Usomg this parameter with {} may cause undefined behavior in flyway",
					database.getDatabaseType().getName());
		}

		while (true) {
			final boolean firstRun = total == 0;
			int count = configuration.isGroup() ? undoGroup(firstRun) : schemaHistory.lock(() -> undoGroup(firstRun));

			migrateResult.migrationsExecuted += count;
			total += count;

			if (count == 0 || total > 0) {
				LOGGER.info("Command {} undone count: {} with target version: {}", migrateResult.getOperation(), count,
						targetVersion);
				break;
			}
		}

		if (isPreviousVersioned) {
			callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_VERSIONED);
		}

		return total;
	}

	private Integer undoGroup(boolean firstRun) {
		MigrationVersion targetVersion = configuration.getTarget();

		MigrationInfoServiceImpl infoService = new MigrationInfoServiceImpl(migrationResolver, schemaHistory, database,
				configuration, targetVersion, configuration.isOutOfOrder(),
				ValidatePatternUtils.getIgnoreAllPattern(), configuration.getCherryPick());
		infoService.refresh();

		MigrationInfo current = infoService.current();
		MigrationVersion currentSchemaVersion = ofNullable(current).map(MigrationInfo::getVersion)
				.orElse(MigrationVersion.EMPTY);

		if (firstRun) {
			LOGGER.info("Current schema version {}: {}", schema, currentSchemaVersion);
			MigrationVersion schemaVersionToOutput = ofNullable(currentSchemaVersion).orElse(MigrationVersion.EMPTY);
			migrateResult.initialSchemaVersion = ofNullable(schemaVersionToOutput).map(MigrationVersion::getVersion)
					.orElse("");
			if (configuration.isOutOfOrder()) {
				String outOfOrderWarning = String.format(
						"\"Out of order is enabled, Migration of schema %s will be set to the target version: %s",
						schema, targetVersion);
				LOGGER.info(outOfOrderWarning);
				migrateResult.addWarning(outOfOrderWarning);
			}
		}

		Collection<ResolvedMigration> resolvedUndoMigrations = migrationResolver.resolveMigrations(this.configuration);

		List<AppliedMigration> appliedMigrations = schemaHistory.allAppliedMigrations();
		validateUndoPrerequisites(targetVersion, resolvedUndoMigrations, appliedMigrations);

		List<AppliedMigration> reverseApplied = new ArrayList<>(appliedMigrations);
		Collections.reverse(reverseApplied);

		Map<MigrationVersion, ResolvedMigration> undoMap = new HashMap<>();
		for (ResolvedMigration rm : resolvedUndoMigrations) {
			if (rm.getVersion() != null) {
				undoMap.put(rm.getVersion(), rm);
			}
		}

		Set<MigrationVersion> undoneVersions = new HashSet<>();
		LinkedHashMap<MigrationInfo, Boolean> group = new LinkedHashMap<>();

		for (AppliedMigration applied : reverseApplied) {
			MigrationVersion version = applied.getVersion();
			if (version == null) {
				continue;
			}

			if (applied.getType().isUndo()) {
				undoneVersions.add(version);
				continue;
			}

			if (targetVersion != null && version.equals(targetVersion)) {
				break;
			}

			if (undoneVersions.contains(version)) {
				continue;
			}

			ResolvedMigration undoMigration = undoMap.get(version);
			if (undoMigration != null) {
				group.put(new UndoMigrationInfo(undoMigration), false);
				if (!configuration.isGroup()) {
					break;
				}
			} else {
				LOGGER.info("Migration version " + version + " needs undoing but no undo (U) script found.");
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
				ExecutionTemplateFactory.createExecutionTemplate(connection.getJdbcConnection(), database)
						.execute(() -> {
							doMigrateGroup(group, stopWatch, skipExecutingMigrations, true);
							return null;
						});
			} else {
				doMigrateGroup(group, stopWatch, skipExecutingMigrations, false);
			}
		} catch (FlywayMigrateException e) {
			MigrationInfo migration = e.getMigration();
			String migrationText = toMigrationText(migration, e.isExecutableInTransaction(), e.isOutOfOrder());
			String failedMsg = "Undo of " + migrationText + " failed!";
			stopWatch.stop();
			int executionTime = (int) stopWatch.getTotalTimeMillis();
			migrateResult.putFailedMigration(migration, executionTime);

			String totalExecutionTime = TimeFormat.format(executionTime);
			if (database.supportsDdlTransactions() && executeGroupInTransaction) {
				LOGGER.error("{} Changes successfully rolled back (execution time {})", failedMsg, totalExecutionTime);
				List<MigrationInfoImpl> rollbackMigrationInfos = group.keySet()
						.stream()
						.filter(MigrationInfoImpl.class::isInstance)
						.map(MigrationInfoImpl.class::cast)
						.toList();
				if (!rollbackMigrationInfos.isEmpty()) {
					migrateResult.markAsRolledBack(rollbackMigrationInfos);
				}
			} else {
				LOGGER.error("{} Please restore backups and roll back database and code! (execution time {})",
						failedMsg, totalExecutionTime);
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
		if (info instanceof MigrationInfoImpl migrationInfoImpl) {
			return migrationInfoImpl.getResolvedMigration();
		} else if (info instanceof UndoMigrationInfo undoMigrationInfo) {
			return undoMigrationInfo.getResolvedMigration();
		}
		throw new FlywayException("Unknown MigrationInfo type: " + info.getClass());
	}

	private void doMigrateGroup(LinkedHashMap<MigrationInfo, Boolean> group, StopWatch stopWatch,
			boolean skipExecutingMigrations, boolean isExecuteInTransaction) {
		Context context = createContext(this.configuration, this.connection.getJdbcConnection());

		for (Map.Entry<MigrationInfo, Boolean> entry : group.entrySet()) {
			final MigrationInfo migration = entry.getKey();
			boolean isOutOfOrder = entry.getValue();

			ResolvedMigration resolvedMigration = getResolvedMigration(migration);
			final String migrationText = toMigrationText(migration,
					resolvedMigration.getExecutor().canExecuteInTransaction(), isOutOfOrder);

			stopWatch.start();

			if (this.isPreviousVersioned && migration.getVersion() == null) {
				this.callbackExecutor.onMigrateOrUndoEvent(Event.AFTER_VERSIONED);
				this.callbackExecutor.onMigrateOrUndoEvent(Event.BEFORE_REPEATABLES);
				this.isPreviousVersioned = false;
			}

			if (skipExecutingMigrations) {
				LOGGER.info("Skipping undo of {}", migration.getScript());
			} else {
				LOGGER.info("Starting undo of {} ...", migration.getScript());

				this.connection.restoreOriginalState();
				this.connection.changeCurrentSchemaTo(schema);

				try {
					this.callbackExecutor.setMigrationInfo(migration);
					this.callbackExecutor.onEachMigrateOrUndoEvent(Event.BEFORE_EACH_UNDO);
					try {
						LOGGER.info("Undoing script:{}", migration.getScript());

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
						throw new FlywayMigrateException(migration, isOutOfOrder, e,
								resolvedMigration.getExecutor().canExecuteInTransaction(), migrateResult);
					} catch (SQLException e) {
						callbackExecutor.onEachMigrateOrUndoEvent(Event.AFTER_EACH_UNDO_ERROR);
						throw new FlywayMigrateException(migration, isOutOfOrder, e,
								resolvedMigration.getExecutor().canExecuteInTransaction(), migrateResult);
					}
				} finally {
					callbackExecutor.onEachMigrateOrUndoEvent(Event.AFTER_EACH_UNDO);
					callbackExecutor.setMigrationInfo(null);
				}
			}

			stopWatch.stop();
			int executionTime = (int) stopWatch.getTotalTimeMillis();

			migrateResult.migrations.add(CommandResultFactory.createMigrateOutput(migration, executionTime));
			migrateResult.putSuccessfulMigration(migration, executionTime);

			// Insert a version to the history table for audit purpose.
			schemaHistory.addAppliedMigration(migration.getVersion(),
					migration.getDescription(),
					UNDO_SCRIPT,
					migration.getScript(), resolvedMigration.getChecksum(), executionTime, true);
			String totalExecutionTime = TimeFormat.format(executionTime);
			LOGGER.info("Successfully undo {} (execution time {})", migration.getScript(), totalExecutionTime);
		}
	}

	private String toMigrationText(MigrationInfo migration, boolean canExecuteInTransaction, boolean isOutOfOrder) {
		return "schema " + schema + " version "
				+ doQuote(migration.getVersion() != null ? migration.getVersion().toString() : "?");
	}

	private String doQuote(String text) {
		return "\"" + text + "\"";
	}

	private void logSummary(int migrationSuccessCount, long executionTime, String targetVersion) {
		if (migrationSuccessCount == 0) {
			LOGGER.info("Schema {} is up to date. No undo necessary.", schema);
			return;
		}
		String targetText = (targetVersion != null) ? ", now at version v" + targetVersion : "";
		String migrationText = "undo migration" + (migrationSuccessCount == 1 ? "" : "s");
		String totalExecutionTime = TimeFormat.format(executionTime);
		LOGGER.info("Successfully undid {} {} to schema {}{} (execution time {})", migrationSuccessCount,
				migrationText, schema, targetText, totalExecutionTime);
	}

	private void validateUndoPrerequisites(MigrationVersion targetVersion,
			Collection<ResolvedMigration> resolvedUndoMigrations, List<AppliedMigration> appliedMigrations) {
		if (targetVersion == null || targetVersion.getVersion() == null
				|| targetVersion.getVersion().trim().isEmpty()) {
			throw new FlywayException("Target version must not be empty or null for undo operation.");
		}

		boolean existsInHistory = appliedMigrations.stream().anyMatch(
				am -> am.getVersion() != null && am.getVersion().equals(targetVersion) && !am.getType().isUndo());
		if (!existsInHistory && !targetVersion.getVersion().equals("0")) {
			throw new FlywayException("Target version " + targetVersion + " does not exist in schema history.");
		}
	}
}
