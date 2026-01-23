package org.flywaydbextended.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedConfigurationTest {

    @Test
    void testStandardConfigurationProperties() {
        ExtendedConfiguration config = new ExtendedConfiguration();
        config.setUndoSqlMigrationPrefix("UNDO_");

        // These now map to standard Flyway properties
        config.setSqlMigrationSeparator("__SEP__");
        config.setSqlMigrationSuffixes(".undo");

        assertEquals("UNDO_", config.getUndoSqlMigrationPrefix());
        assertEquals("__SEP__", config.getSqlMigrationSeparator());

        String[] suffixes = config.getSqlMigrationSuffixes();
        assertNotNull(suffixes);
        assertTrue(suffixes.length > 0);
        assertEquals(".undo", suffixes[0]);
    }
}
