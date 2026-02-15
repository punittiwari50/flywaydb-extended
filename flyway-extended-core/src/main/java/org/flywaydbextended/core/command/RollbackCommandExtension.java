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

import static org.flywaydbextended.core.utils.FlywayUtils.validateAndCheck;
import static org.flywaydbextended.core.utils.FlywayUtils.addCallback;

import java.util.List;
import org.flywaydb.core.FlywayExecutor;
import org.flywaydb.core.FlywayExecutor.Command;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.extensibility.CommandExtension;
import org.flywaydb.core.internal.NullFlywayTelemetryManager;
import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.resolver.CompositeMigrationResolver;
import org.flywaydb.core.internal.schemahistory.SchemaHistory;
import org.flywaydbextended.core.api.callback.VersionedScopedFlywayCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollbackCommandExtension implements CommandExtension<MigrateResult>, Command<MigrateResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RollbackCommandExtension.class);

    public static final String UNDO_COMMAND = "undo";

    public static final String DEFAULT_UDNO_SQL_PREFIX = "U";

    public static final String UNDO_SQL_PREFIX_PROPERTY = "FLYWAY_UNDO_SQL_PREFIX";

    private Configuration configuration;

    private List<String> flags;

    @Override
    public boolean handlesCommand(String command) {
        return UNDO_COMMAND.equals(command);
    }

    @Override
    public boolean handlesParameter(String parameter) {
        return false; // We can handle specific parameters if needed, e.g. -undo.target
    }

    @Override
    public MigrateResult handle(Configuration configuration, List<String> flags) throws FlywayException {
        LOGGER.info("handle - Executing undo command");
        this.configuration = configuration;
        this.flags = flags;
        String undoSqlPrefix = configuration.getPlaceholders().get("UNDO_SQL_PREFIX_PROPERTY");
        undoSqlPrefix = undoSqlPrefix == null ? DEFAULT_UDNO_SQL_PREFIX : undoSqlPrefix;

        // Configure 'U' prefix for undo scripts
        Configuration undoConfiguration = new FluentConfiguration()
                .configuration(configuration)
                .sqlMigrationPrefix(undoSqlPrefix);

        addCallback(undoConfiguration, new VersionedScopedFlywayCallback());
        // Create executor to set up the environment (DB, SchemaHistory, etc.)
        FlywayExecutor executor = new FlywayExecutor(undoConfiguration);

        // Execute this command within the initialized environment
        return executor.execute(this, true, new NullFlywayTelemetryManager());
    }

    @Override
    public MigrateResult execute(CompositeMigrationResolver migrationResolver, SchemaHistory schemaHistory,
            Database database, Schema defaultSchema, Schema[] schemas, CallbackExecutor<Event> callbackExecutor,
            StatementInterceptor statementInterceptor) {
        LOGGER.info("execute - Executing undo command is flag empty: {}", flags == null || flags.isEmpty());
        validateAndCheck(configuration, database, schemaHistory, defaultSchema, migrationResolver, callbackExecutor);
        return new DbRollback(configuration, database, schemaHistory, defaultSchema,
                migrationResolver, callbackExecutor, statementInterceptor).undo();
    }
}
