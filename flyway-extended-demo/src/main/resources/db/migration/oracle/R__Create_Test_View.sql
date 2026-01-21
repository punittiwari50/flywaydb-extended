-- R__ scripts are "Repeatable Migrations"
-- They run AFTER all Versioned migrations (V1, V2, etc.)
-- They run ONLY when their Checksum changes (you edit the file)
-- They are perfect for Views, Procedures, Functions, and Packages

CREATE OR REPLACE VIEW test_table_summary AS
SELECT count(*) as total_rows, 'Active' as status
FROM TEST_TABLE;
