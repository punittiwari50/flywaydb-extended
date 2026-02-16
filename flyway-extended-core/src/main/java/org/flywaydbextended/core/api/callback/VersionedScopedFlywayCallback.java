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
package org.flywaydbextended.core.api.callback;

import static org.flywaydbextended.core.utils.FlywayUtils.executeWithFlyway;
import static org.flywaydbextended.core.utils.FlywayUtils.createResourceProvider;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.api.resource.Resource;
import org.flywaydb.core.internal.jdbc.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionedScopedFlywayCallback implements Callback {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionedScopedFlywayCallback.class);

    public static final String VERSIONED_SCOPED_CALLBACK_NAME = "VersionedScopedFlywayCallback";

    public static final String FLYWAY_VERSIONSCOPED_LOCATION_PROPERTY = "FLYWAY_VERSIONSCOPED_LOCATION";

    public static final String VALIDATE_LOCATION = "validate";

    @Override
    public boolean supports(Event event, Context context) {
        return Stream
                .of(Event.BEFORE_EACH_MIGRATE, Event.AFTER_EACH_MIGRATE, Event.BEFORE_EACH_UNDO, Event.AFTER_EACH_UNDO)
                .anyMatch(e -> e == event);
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Configuration configuration = context.getConfiguration();
        MigrationInfo migrationInfo = context.getMigrationInfo();

        if (migrationInfo == null || migrationInfo.getVersion() == null || configuration.getPlaceholders() == null
                || configuration.getPlaceholders().get(FLYWAY_VERSIONSCOPED_LOCATION_PROPERTY) == null) {
            return;
        }
        String parentLocation = configuration.getPlaceholders().get(FLYWAY_VERSIONSCOPED_LOCATION_PROPERTY);
        String eventName = getEventName(event);
        MigrationVersion version = migrationInfo.getVersion();
        String prefix = new StringBuilder().append(eventName)
                .append("__")
                .append(configuration.getSqlMigrationPrefix())
                .append(version)
                .append("__")
                .toString();
        String location = Path.of(parentLocation).resolveSibling(VALIDATE_LOCATION).toString();
        // Ensure location is treated correctly by normalize path separators for
        // classpath
        ResourceProvider resourceProvider = createResourceProvider(configuration, "filesystem", location);
        Collection<LoadableResource> loadableResources = resourceProvider.getResources(prefix,
                configuration.getSqlMigrationSuffixes());

        LOGGER.info("Scanning for callbacks in location: [{}], prefix: [{}], found: [{}] resources",
                location, prefix, (loadableResources == null ? 0 : loadableResources.size()));

        if (loadableResources == null || loadableResources.isEmpty()) {
            return;
        }
        List<LoadableResource> resources = loadableResources.stream()
                .sorted(Comparator.comparing(Resource::getFilename)).toList();
        for (LoadableResource resource : resources) {
            LOGGER.info("Executing callback resource: [{}]", resource.getFilename());
            execute(resource, context);
        }
    }

    private void execute(LoadableResource loadableResource, Context context) {
        try {
            List<Results> results = executeWithFlyway(loadableResource, context);
            results.stream().forEach(result -> {
                LOGGER.info("Executed script file-name: [{}] ", loadableResource.getFilename());
            });
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("script file-name: [{}] with error message: [{}]", loadableResource.getFilename(),
                    e.getMessage(), e);
        }
    }

    private String getEventName(Event event) {
        return event.getId().replaceAll("Each", "");
    }

    @Override
    public String getCallbackName() {
        return VERSIONED_SCOPED_CALLBACK_NAME;
    }

}
