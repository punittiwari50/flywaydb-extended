DO $$
BEGIN
    RAISE NOTICE 'DB: Executing AFTER V1 migration. Verifying V1 Schema...';

    -- Verify V1: test_table must exist
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'test_table'
    ) THEN
        RAISE EXCEPTION 'Verification FAILED: test_table (from V1) is missing!';
    END IF;

    RAISE NOTICE 'Verification PASSED: V1 Schema is correct.';
END $$;
