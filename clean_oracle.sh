#!/bin/bash
export ORACLE_SID=XE
sqlplus -S system/mysecretpassword <<EOF
DROP TABLE "flyway_schema_history";
DROP TABLE TEST_TABLE PURGE;
EXIT;
EOF
