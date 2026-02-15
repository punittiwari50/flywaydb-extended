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
package org.flywaydbextended.core.utils;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.executor.Context;
import org.flywaydb.core.api.executor.MigrationExecutor;
import org.flywaydb.core.api.output.ValidateResult;
import org.flywaydb.core.api.pattern.ValidatePattern;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.NullFlywayTelemetryManager;
import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.callback.DefaultCallbackExecutor;
import org.flywaydb.core.internal.command.DbValidate;
import org.flywaydb.core.internal.database.DatabaseType;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.Results;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resolver.CompositeMigrationResolver;
import org.flywaydb.core.internal.resolver.sql.SqlMigrationExecutor;
import org.flywaydb.core.internal.scanner.Scanner;
import org.flywaydb.core.internal.schemahistory.SchemaHistory;
import org.flywaydb.core.internal.sqlscript.SqlScript;
import org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory;
import org.flywaydb.core.internal.sqlscript.SqlScriptFactory;

public class FlywayUtils {

    private FlywayUtils() {
    }

    public static void validateAndCheck(Configuration configuration, Database database, SchemaHistory schemaHistory,
            Schema defaultSchema, CompositeMigrationResolver migrationResolver,
            CallbackExecutor<Event> callbackExecutor) {
        if (configuration.isValidateOnMigrate()) {
            final Collection<ValidatePattern> ignorePatterns = new ArrayList<>(
                    Arrays.asList(configuration.getIgnoreMigrationPatterns()));
            ignorePatterns.add(ValidatePattern.fromPattern("*:pending"));
            final ValidateResult validateResult = doValidate(configuration, database, migrationResolver, schemaHistory,
                    defaultSchema, callbackExecutor, ignorePatterns.toArray(new ValidatePattern[0]));
            if (!validateResult.validationSuccessful) {
                throw new FlywayValidateException(validateResult.errorDetails, validateResult.getAllErrorMessages());
            }
        }
    }

    private static ValidateResult doValidate(Configuration configuration, Database database,
            CompositeMigrationResolver migrationResolver,
            SchemaHistory schemaHistory, Schema defaultSchema,
            CallbackExecutor<Event> callbackExecutor, ValidatePattern[] ignorePatterns) {
        return new DbValidate(database, schemaHistory, defaultSchema, migrationResolver, configuration,
                callbackExecutor, ignorePatterns).validate();
    }

    public static ResourceProvider createResourceProvider(Configuration configuration, Location[] locations) {
        if (locations == null || locations.length == 0) {
            return configuration.getResourceProvider();
        }
        return new Scanner(ClassLoader.class, configuration, locations);
    }

    public static ResourceProvider createResourceProvider(Configuration configuration, String... locations) {
        if (locations == null || locations.length == 0) {
            return createResourceProvider(configuration, new Location[0]);
        }
        Location[] locationArray = new Location[locations.length];
        for (int i = 0; i < locations.length; i++) {
            locationArray[i] = new Location(locations[i]);
        }
        return createResourceProvider(configuration, locationArray);
    }

    public static Context createContext(Configuration configuration, Connection connection) {
        return new Context() {

            @Override
            public Configuration getConfiguration() {
                return configuration;
            }

            @Override
            public Connection getConnection() {
                return connection;
            }
        };
    }

    public static ParsingContext createParserContext(Configuration configuration, Database database) {
        ParsingContext parsingContext = new ParsingContext();
        parsingContext.populate(database, configuration);
        return parsingContext;
    }

    public static MigrationExecutor createMigrationExecutor(LoadableResource loadableResource,
            org.flywaydb.core.api.callback.Context context, Schema schema, StatementInterceptor statementInterceptor)
            throws Exception {
        Configuration configuration = context.getConfiguration();
        List<Callback> callbacks = getCallbacks(configuration);
        DatabaseType databaseType = DatabaseTypeRegister.getDatabaseTypeForConnection(context.getConnection(),
                configuration);
        JdbcConnectionFactory jdbcConnectionFactory = new JdbcConnectionFactory(configuration.getDataSource(),
                configuration, statementInterceptor);
        Database database = databaseType.createDatabase(configuration, jdbcConnectionFactory, statementInterceptor);

        ParsingContext parsingContext = createParserContext(configuration, database);
        SqlScriptFactory sqlScriptFactory = databaseType.createSqlScriptFactory(configuration, parsingContext);
        SqlScript sqlScript = sqlScriptFactory.createSqlScript(loadableResource, configuration.isMixed(),
                configuration.getResourceProvider());

        CallbackExecutor callbackExecutor = new DefaultCallbackExecutor(configuration, database, schema,
                new NullFlywayTelemetryManager(), callbacks);
        SqlScriptExecutorFactory sqlScriptExecutorFactory = databaseType
                .createSqlScriptExecutorFactory(jdbcConnectionFactory, callbackExecutor, statementInterceptor);
        return new SqlMigrationExecutor(sqlScriptExecutorFactory, sqlScript, false, configuration.isBatch());
    }

    public static List<Callback> getCallbacks(Configuration configuration) {
        return (configuration.getCallbacks() == null || configuration.getCallbacks().length == 0)
                ? Collections.emptyList()
                : Arrays.asList(configuration.getCallbacks());
    }

    public static List<Results> executeWithFlyway(LoadableResource loadableResource,
            org.flywaydb.core.api.callback.Context context) throws Exception {
        MigrationExecutor migrationExecutor = createMigrationExecutor(loadableResource, context, null, null);
        return migrationExecutor.execute(createContext(context.getConfiguration(), context.getConnection()));
    }

    public static void addCallback(Configuration configuration, Callback callback) {
        List<Callback> callbacks = new ArrayList<>(getCallbacks(configuration));
        callbacks.add(callback);
        if (configuration instanceof ClassicConfiguration classicConfiguration) {
            classicConfiguration.setCallbacks(callbacks.toArray(Callback[]::new));
        }
    }
}
