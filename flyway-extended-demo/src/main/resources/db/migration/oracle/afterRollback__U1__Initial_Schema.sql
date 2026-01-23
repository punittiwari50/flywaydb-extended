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
