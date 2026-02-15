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
package org.flywaydbextended.core.api.output;

import java.util.Date;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.extensibility.MigrationType;

public class UndoMigrationInfo implements MigrationInfo {
	
    private final ResolvedMigration resolvedMigration;

    public UndoMigrationInfo(ResolvedMigration resolvedMigration) {
        this.resolvedMigration = resolvedMigration;
    }

    public ResolvedMigration getResolvedMigration() {
        return resolvedMigration;
    }

    @Override
    public MigrationType getType() {
        return resolvedMigration.getType();
    }

    @Override
    public Integer getChecksum() {
        return resolvedMigration.getChecksum();
    }

    @Override
    public MigrationVersion getVersion() {
        return resolvedMigration.getVersion();
    }

    @Override
    public String getDescription() {
        return resolvedMigration.getDescription();
    }

    @Override
    public String getScript() {
        return resolvedMigration.getScript();
    }

    @Override
    public MigrationState getState() {
        return MigrationState.PENDING;
    }

    @Override
    public Date getInstalledOn() {
        return null;
    }

    @Override
    public String getInstalledBy() {
        return null;
    }

    @Override
    public Integer getInstalledRank() {
        return null;
    }

    @Override
    public Integer getExecutionTime() {
        return null;
    }

    @Override
    public String getPhysicalLocation() {
        return resolvedMigration.getPhysicalLocation();
    }

    @Override
    public int compareTo(MigrationInfo o) {
        return getVersion().compareTo(o.getVersion());
    }

    @Override
    public int compareVersion(MigrationInfo o) {
        return getVersion().compareTo(o.getVersion());
    }
}
