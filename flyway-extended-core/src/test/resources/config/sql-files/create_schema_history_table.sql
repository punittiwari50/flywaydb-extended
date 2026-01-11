-- Create flyway_schema_history table for testing
-- This table uses quoted lowercase identifiers to match H2's behavior with quoted names

CREATE TABLE "flyway_schema_history" (
    "installed_rank" INT NOT NULL,
    "version" VARCHAR(50),
    "description" VARCHAR(200) NOT NULL,
    "type" VARCHAR(20) NOT NULL,
    "script" VARCHAR(1000) NOT NULL,
    "checksum" INT,
    "installed_by" VARCHAR(100) NOT NULL,
    "installed_on" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "execution_time" INT NOT NULL,
    "success" BOOLEAN NOT NULL,
    PRIMARY KEY ("installed_rank")
);
