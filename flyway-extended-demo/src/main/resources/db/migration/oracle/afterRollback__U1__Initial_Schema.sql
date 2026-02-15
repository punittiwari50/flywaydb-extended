---
-- ========================LICENSE_START=================================
-- flyway-extended-demo
-- ========================================================================
-- Copyright (C) 2010 - 2026 Red Gate Software Ltd
-- ========================================================================
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
-- 
--      http://www.apache.org/licenses/LICENSE-2.0
-- 
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- =========================LICENSE_END==================================
---
-- 1. Log the Table Status (Visible via outputQueryResults=true)
-- 2. Enforce Verification (Fails validation if missing)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT count(*) INTO v_count FROM user_tables WHERE table_name = 'TEST_TABLE';
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Verification FAILED: TEST_TABLE (from V1) is missing!');
    END IF;

    SELECT count(*) INTO v_count FROM user_tab_columns WHERE table_name = 'TEST_TABLE' AND column_name = 'NAME';
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'Verification FAILED: Column TEST_TABLE.name is missing!');
    END IF;
END;
/

SELECT table_name AS "VERIFIED_TABLE", status AS "STATE" 
FROM user_tables 
WHERE table_name = 'TEST_TABLE';

SELECT column_name AS "VERIFIED_COLUMN", data_type AS "DATA_TYPE", data_length AS "DATA_LENGTH" 
FROM user_tab_columns 
WHERE table_name = 'TEST_TABLE'
AND column_name = 'NAME';

-- Debug: List all tables
SELECT table_name FROM user_tables;

-- Verify History Table Update (assuming lowercase quoted)
SELECT * FROM "flyway_schema_history" ORDER BY "installed_rank" DESC;


SELECT CURRENT_TIMESTAMP AS "CURRENT_TIMESTAMP", 
    'After Rollback COMPLETED' AS "PROCESS", 
    sys_context('userenv','current_schema') AS "CURRENT_SCHEMA", 
    'COMPLETED' AS "STATUS", 
    SYS_CONTEXT('USERENV', 'NETWORK_PROTOCOL') AS "NETWORK_PROTOCOL" ,
    SYS_CONTEXT('USERENV', 'SESSION_USER') AS "DB_USERNAME",
    SYS_CONTEXT('USERENV', 'DB_NAME') AS "DB_NAME"
FROM dual;
