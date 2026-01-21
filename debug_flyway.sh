#!/bin/bash
export ORACLE_SID=XE
sqlplus -S system/mysecretpassword <<EOF
set markup csv on
SELECT * FROM "flyway_schema_history";
EXIT;
EOF
