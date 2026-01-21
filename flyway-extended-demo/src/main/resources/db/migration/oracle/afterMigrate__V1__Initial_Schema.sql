-- 1. Log the Table Status (Visible via outputQueryResults=true)
SELECT table_name AS "VERIFIED_TABLE", status AS "STATE" 
FROM user_tables 
WHERE table_name = 'TEST_TABLE';

-- 2. Enforce Verification (Fails validation if missing)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT count(*) INTO v_count FROM user_tables WHERE table_name = 'TEST_TABLE';
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Verification FAILED: TEST_TABLE (from V1) is missing!');
    END IF;

    SELECT count(*) INTO v_count FROM user_tab_columns WHERE table_name = 'TEST_TABLE' AND column_name = 'NAME';
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'Verification FAILED: Column TEST_TABLE.name is missing!');
    END IF;
END;
/
