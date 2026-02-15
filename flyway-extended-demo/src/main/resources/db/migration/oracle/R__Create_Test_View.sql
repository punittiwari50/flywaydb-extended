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
-- R__ scripts are "Repeatable Migrations"
-- They run AFTER all Versioned migrations (V1, V2, etc.)
-- They run ONLY when their Checksum changes (you edit the file)
-- They are perfect for Views, Procedures, Functions, and Packages

CREATE OR REPLACE VIEW test_table_summary AS
SELECT count(*) as total_rows, 'Active' as status
FROM TEST_TABLE;
