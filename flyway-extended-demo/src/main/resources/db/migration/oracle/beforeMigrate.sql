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
SELECT CURRENT_TIMESTAMP AS "CURRENT_TIMESTAMP", 
    'Before Migration STARTING' AS "PROCESS", 
    sys_context('userenv','current_schema') AS "CURRENT_SCHEMA", 
    'STARTING' AS "STATUS", 
    SYS_CONTEXT('USERENV', 'NETWORK_PROTOCOL') AS "NETWORK_PROTOCOL" ,
    SYS_CONTEXT('USERENV', 'SESSION_USER') AS "DB_USERNAME",
    SYS_CONTEXT('USERENV', 'DB_NAME') AS "DB_NAME"
FROM dual;
