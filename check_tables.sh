#!/bin/bash
export ORACLE_SID=XE
sqlplus -S system/mysecretpassword <<EOF
SELECT table_name FROM user_tables;
SELECT * FROM flyway_schema_history;
EXIT;
EOF
