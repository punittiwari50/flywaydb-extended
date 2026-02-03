package org.flywaydbextended.core;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlywayExtended extends Flyway {

	private static final Logger LOGGER = LoggerFactory.getLogger(FlywayExtended.class);

	public FlywayExtended(Configuration configuration) {
		super(configuration);
	}
}
