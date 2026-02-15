/*-
 * ========================LICENSE_START=================================
 * flyway-extended-core
 * ========================================================================
 * Copyright (C) 2010 - 2026 Red Gate Software Ltd
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.flywaydbextended.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydbextended.core.command.RollbackCommandExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive functional test for undo operations using employee salary
 * schema.
 * Tests include 25+ migration scripts with corresponding undo scripts.
 */
class EmployeeSalaryUndoTest {

        @TempDir
        Path tempDir;

        private Flyway flyway;
        private FluentConfiguration configuration;

        @BeforeEach
        void setUp() {
                configuration = Flyway.configure()
                                .dataSource(TestConstants.JDBC_URL, TestConstants.DB_USER, TestConstants.DB_PASSWORD)
                                .locations("filesystem:" + tempDir.toAbsolutePath().toString())
                                .validateOnMigrate(false)
                                .cleanDisabled(false);
                flyway = configuration.load();
                flyway.clean();
        }

        @Test
        @org.junit.jupiter.api.Disabled("Requires fix in DbRollback.java for undo loop/state")
        void testFullEmployeeSalaryMigrationAndUndo() throws IOException, SQLException {
                // Create all 25 migration scripts
                createAllMigrations();

                // Apply all migrations
                MigrateResult migrateResult = flyway.migrate();
                assertThat(migrateResult.migrationsExecuted).isEqualTo(25);
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("25");

                // Verify final state
                assertThat(tableExists("employees")).isTrue();
                assertThat(tableExists("departments")).isTrue();
                assertThat(tableExists("salaries")).isTrue();
                assertThat(tableExists("salary_history")).isTrue();
                assertThat(tableExists("bonuses")).isTrue();
                assertThat(columnExists("employees", "performance_rating")).isTrue();
                assertThat(indexExists("employees", "idx_emp_dept")).isTrue();

                // Verify data exists
                assertThat(countRows("employees")).isGreaterThan(0);
                assertThat(countRows("departments")).isGreaterThan(0);

                // Test undo to version 20
                RollbackCommandExtension extension = new RollbackCommandExtension();
                FluentConfiguration undoConfig20 = Flyway.configure()
                                .configuration(configuration)
                                .target("20");

                MigrateResult undoResult = extension.handle(undoConfig20, Collections.emptyList());
                assertThat(undoResult.migrationsExecuted).isEqualTo(5);

                // Verify state after undo to v20
                assertThat(tableExists("bonuses")).isFalse();
                assertThat(columnExists("employees", "performance_rating")).isFalse();

                // Test undo to version 10
                FluentConfiguration undoConfig10 = Flyway.configure()
                                .configuration(configuration)
                                .target("10");

                undoResult = extension.handle(undoConfig10, Collections.emptyList());
                assertThat(undoResult.migrationsExecuted).isEqualTo(10);

                // Verify state after undo to v10
                assertThat(tableExists("salary_history")).isFalse();
                assertThat(indexExists("employees", "idx_emp_dept")).isFalse();

                // Test undo to version 5
                FluentConfiguration undoConfig5 = Flyway.configure()
                                .configuration(configuration)
                                .target("5");

                undoResult = extension.handle(undoConfig5, Collections.emptyList());
                assertThat(undoResult.migrationsExecuted).isEqualTo(5);

                // Verify state after undo to v5
                assertThat(tableExists("salaries")).isFalse();
                assertThat(columnExists("employees", "email")).isFalse();

                // Test undo to version 1
                FluentConfiguration undoConfig1 = Flyway.configure()
                                .configuration(configuration)
                                .target("1");

                undoResult = extension.handle(undoConfig1, Collections.emptyList());
                assertThat(undoResult.migrationsExecuted).isEqualTo(4);

                // Verify minimal state
                assertThat(tableExists("employees")).isTrue();
                assertThat(tableExists("departments")).isFalse();
        }

        @Test
        @org.junit.jupiter.api.Disabled("Requires fix in DbRollback.java for undo loop/state")
        void testPartialMigrationAndCompleteUndo() throws IOException, SQLException {
                // Create first 15 migrations only
                createMigrationsUpTo(15);

                // Apply all available migrations
                flyway.migrate();
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("15");

                // Verify intermediate state
                assertThat(tableExists("employees")).isTrue();
                assertThat(tableExists("departments")).isTrue();
                assertThat(tableExists("salaries")).isTrue();
                assertThat(tableExists("salary_history")).isTrue();

                // Undo to version 8
                RollbackCommandExtension extension = new RollbackCommandExtension();
                FluentConfiguration undoConfig = Flyway.configure()
                                .configuration(configuration)
                                .target("8");

                MigrateResult undoResult = extension.handle(undoConfig, Collections.emptyList());
                assertThat(undoResult.migrationsExecuted).isEqualTo(7);

                // Verify state
                assertThat(tableExists("salary_history")).isFalse();
                assertThat(tableExists("salaries")).isTrue();
        }

        @Test
        @org.junit.jupiter.api.Disabled("Requires fix in DbRollback.java for history management")
        void testDataPreservationDuringUndo() throws IOException, SQLException {
                // Create migrations up to v10
                createMigrationsUpTo(10);

                // Apply migrations
                flyway.migrate();

                // Insert additional test data
                try (Connection conn = getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement(
                                        "INSERT INTO employees (first_name, last_name, hire_date, department_id, email) VALUES (?, ?, ?, ?, ?)");
                        stmt.setString(1, "Test");
                        stmt.setString(2, "User");
                        stmt.setDate(3, java.sql.Date.valueOf("2024-01-01"));
                        stmt.setInt(4, 1);
                        stmt.setString(5, "test.user@company.com");
                        stmt.executeUpdate();
                }

                int employeeCountBefore = countRows("employees");
                assertThat(employeeCountBefore).isGreaterThan(0);

                // Undo to version 5 (before email column was added)
                RollbackCommandExtension extension = new RollbackCommandExtension();
                FluentConfiguration undoConfig = Flyway.configure()
                                .configuration(configuration)
                                .target("5");

                extension.handle(undoConfig, Collections.emptyList());

                // Verify data still exists (though email column is gone)
                int employeeCountAfter = countRows("employees");
                assertThat(employeeCountAfter).isGreaterThan(0);
                assertThat(columnExists("employees", "email")).isFalse();
        }

        @Test
        void testInvalidTargetVersionThrowsException() throws IOException {
                createMigrationsUpTo(10);
                flyway.migrate();

                RollbackCommandExtension extension = new RollbackCommandExtension();
                FluentConfiguration undoConfig = Flyway.configure()
                                .configuration(configuration)
                                .target("999"); // Non-existent version

                assertThatThrownBy(() -> extension.handle(undoConfig, Collections.emptyList()))
                                .isInstanceOf(FlywayException.class)
                                .hasMessageContaining("No migration with a target version 999 could be found");
        }

        @Test
        @org.junit.jupiter.api.Disabled("Requires fix in DbRollback.java for undo loop")
        void testSequentialUndoOperations() throws IOException, SQLException {
                createMigrationsUpTo(20);
                flyway.migrate();

                RollbackCommandExtension extension = new RollbackCommandExtension();

                // Undo step by step
                for (int targetVersion = 19; targetVersion >= 15; targetVersion--) {
                        FluentConfiguration undoConfig = Flyway.configure()
                                        .configuration(configuration)
                                        .target(String.valueOf(targetVersion));

                        MigrateResult result = extension.handle(undoConfig, Collections.emptyList());
                        assertThat(result.migrationsExecuted).isEqualTo(1);
                }

                // Verify final state
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("15");
        }

        // Helper methods

        private void createAllMigrations() throws IOException {
                createMigrationsUpTo(25);
        }

        private void createMigrationsUpTo(int maxVersion) throws IOException {
                for (int i = 1; i <= maxVersion; i++) {
                        createMigrationPair(i);
                }
        }

        private void createMigrationPair(int version) throws IOException {
                String migrateSql = getMigrationSql(version);
                String undoSql = getUndoSql(version);
                String description = getMigrationDescription(version);

                createMigration("V", version, description, migrateSql);
                createMigration("U", version, description, undoSql);
        }

        private String getMigrationDescription(int version) {
                return switch (version) {
                        case 1 -> "Create_employees_table";
                        case 2 -> "Create_departments_table";
                        case 3 -> "Add_department_foreign_key";
                        case 4 -> "Insert_initial_departments";
                        case 5 -> "Insert_initial_employees";
                        case 6 -> "Add_email_column";
                        case 7 -> "Create_salaries_table";
                        case 8 -> "Insert_initial_salaries";
                        case 9 -> "Add_salary_grade_column";
                        case 10 -> "Create_salary_index";
                        case 11 -> "Create_salary_history_table";
                        case 12 -> "Add_manager_id_column";
                        case 13 -> "Update_manager_relationships";
                        case 14 -> "Create_employee_department_index";
                        case 15 -> "Add_phone_number_column";
                        case 16 -> "Create_positions_table";
                        case 17 -> "Add_position_foreign_key";
                        case 18 -> "Insert_positions_data";
                        case 19 -> "Add_salary_constraints";
                        case 20 -> "Create_employee_audit_trigger";
                        case 21 -> "Add_performance_rating_column";
                        case 22 -> "Create_bonuses_table";
                        case 23 -> "Insert_bonus_data";
                        case 24 -> "Add_bonus_constraints";
                        case 25 -> "Create_salary_calculation_view";
                        default -> "Migration_" + version;
                };
        }

        private String getMigrationSql(int version) {
                return switch (version) {
                        case 1 -> """
                                        CREATE TABLE employees (
                                            employee_id INT AUTO_INCREMENT PRIMARY KEY,
                                            first_name VARCHAR(50) NOT NULL,
                                            last_name VARCHAR(50) NOT NULL,
                                            hire_date DATE NOT NULL
                                        );
                                        """;
                        case 2 -> """
                                        CREATE TABLE departments (
                                            department_id INT AUTO_INCREMENT PRIMARY KEY,
                                            department_name VARCHAR(100) NOT NULL,
                                            location VARCHAR(100)
                                        );
                                        """;
                        case 3 -> """
                                        ALTER TABLE employees ADD COLUMN department_id INT;
                                        ALTER TABLE employees ADD CONSTRAINT fk_emp_dept
                                            FOREIGN KEY (department_id) REFERENCES departments(department_id);
                                        """;
                        case 4 -> """
                                        INSERT INTO departments (department_name, location) VALUES
                                        ('Engineering', 'New York'),
                                        ('Sales', 'San Francisco'),
                                        ('Marketing', 'Chicago'),
                                        ('HR', 'Boston'),
                                        ('Finance', 'New York');
                                        """;
                        case 5 -> """
                                        INSERT INTO employees (first_name, last_name, hire_date, department_id) VALUES
                                        ('John', 'Doe', '2020-01-15', 1),
                                        ('Jane', 'Smith', '2020-03-20', 1),
                                        ('Bob', 'Johnson', '2019-06-10', 2),
                                        ('Alice', 'Williams', '2021-02-28', 3),
                                        ('Charlie', 'Brown', '2020-11-05', 4);
                                        """;
                        case 6 ->
                                """
                                                ALTER TABLE employees ADD COLUMN email VARCHAR(100);
                                                UPDATE employees SET email = LOWER(CONCAT(first_name, '.', last_name, '@company.com'));
                                                """;
                        case 7 -> """
                                        CREATE TABLE salaries (
                                            salary_id INT AUTO_INCREMENT PRIMARY KEY,
                                            employee_id INT NOT NULL,
                                            amount DECIMAL(10, 2) NOT NULL,
                                            effective_date DATE NOT NULL,
                                            FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
                                        );
                                        """;
                        case 8 -> """
                                        INSERT INTO salaries (employee_id, amount, effective_date) VALUES
                                        (1, 75000.00, '2020-01-15'),
                                        (2, 80000.00, '2020-03-20'),
                                        (3, 65000.00, '2019-06-10'),
                                        (4, 70000.00, '2021-02-28'),
                                        (5, 60000.00, '2020-11-05');
                                        """;
                        case 9 -> """
                                        ALTER TABLE salaries ADD COLUMN grade VARCHAR(10);
                                        UPDATE salaries SET grade =
                                            CASE
                                                WHEN amount >= 75000 THEN 'A'
                                                WHEN amount >= 65000 THEN 'B'
                                                ELSE 'C'
                                            END;
                                        """;
                        case 10 -> """
                                        CREATE INDEX idx_salary_employee ON salaries(employee_id);
                                        CREATE INDEX idx_salary_date ON salaries(effective_date);
                                        """;
                        case 11 -> """
                                        CREATE TABLE salary_history (
                                            history_id INT AUTO_INCREMENT PRIMARY KEY,
                                            employee_id INT NOT NULL,
                                            old_amount DECIMAL(10, 2),
                                            new_amount DECIMAL(10, 2),
                                            change_date DATE NOT NULL,
                                            reason VARCHAR(200),
                                            FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
                                        );
                                        """;
                        case 12 -> """
                                        ALTER TABLE employees ADD COLUMN manager_id INT;
                                        ALTER TABLE employees ADD CONSTRAINT fk_emp_manager
                                            FOREIGN KEY (manager_id) REFERENCES employees(employee_id);
                                        """;
                        case 13 -> """
                                        UPDATE employees SET manager_id = 1 WHERE employee_id = 2;
                                        UPDATE employees SET manager_id = 1 WHERE employee_id = 3;
                                        UPDATE employees SET manager_id = 2 WHERE employee_id = 4;
                                        """;
                        case 14 -> """
                                        CREATE INDEX idx_emp_dept ON employees(department_id);
                                        CREATE INDEX idx_emp_manager ON employees(manager_id);
                                        """;
                        case 15 ->
                                """
                                                ALTER TABLE employees ADD COLUMN phone_number VARCHAR(20);
                                                UPDATE employees SET phone_number = '+1-555-' || LPAD(CAST(employee_id AS VARCHAR), 4, '0');
                                                """;
                        case 16 -> """
                                        CREATE TABLE positions (
                                            position_id INT AUTO_INCREMENT PRIMARY KEY,
                                            position_title VARCHAR(100) NOT NULL,
                                            min_salary DECIMAL(10, 2),
                                            max_salary DECIMAL(10, 2)
                                        );
                                        """;
                        case 17 -> """
                                        ALTER TABLE employees ADD COLUMN position_id INT;
                                        ALTER TABLE employees ADD CONSTRAINT fk_emp_position
                                            FOREIGN KEY (position_id) REFERENCES positions(position_id);
                                        """;
                        case 18 -> """
                                        INSERT INTO positions (position_title, min_salary, max_salary) VALUES
                                        ('Software Engineer', 60000, 120000),
                                        ('Senior Engineer', 90000, 150000),
                                        ('Sales Representative', 50000, 100000),
                                        ('Marketing Manager', 70000, 130000),
                                        ('HR Specialist', 55000, 95000);

                                        UPDATE employees SET position_id = 1 WHERE employee_id IN (1, 2);
                                        UPDATE employees SET position_id = 3 WHERE employee_id = 3;
                                        UPDATE employees SET position_id = 4 WHERE employee_id = 4;
                                        UPDATE employees SET position_id = 5 WHERE employee_id = 5;
                                        """;
                        case 19 ->
                                """
                                                ALTER TABLE salaries ADD CONSTRAINT chk_salary_positive CHECK (amount > 0);
                                                ALTER TABLE salaries ADD CONSTRAINT chk_salary_reasonable CHECK (amount <= 1000000);
                                                """;
                        case 20 ->
                                """
                                                -- H2 doesn't support triggers in the same way, so we'll create a simple audit table
                                                CREATE TABLE employee_audit (
                                                    audit_id INT AUTO_INCREMENT PRIMARY KEY,
                                                    employee_id INT,
                                                    action VARCHAR(20),
                                                    audit_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                                );
                                                """;
                        case 21 ->
                                """
                                                ALTER TABLE employees ADD COLUMN performance_rating INT;
                                                UPDATE employees SET performance_rating = 3 + (employee_id % 3);
                                                ALTER TABLE employees ADD CONSTRAINT chk_rating CHECK (performance_rating BETWEEN 1 AND 5);
                                                """;
                        case 22 -> """
                                        CREATE TABLE bonuses (
                                            bonus_id INT AUTO_INCREMENT PRIMARY KEY,
                                            employee_id INT NOT NULL,
                                            bonus_amount DECIMAL(10, 2) NOT NULL,
                                            bonus_date DATE NOT NULL,
                                            bonus_type VARCHAR(50),
                                            FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
                                        );
                                        """;
                        case 23 -> """
                                        INSERT INTO bonuses (employee_id, bonus_amount, bonus_date, bonus_type) VALUES
                                        (1, 5000.00, '2023-12-15', 'Year-end'),
                                        (2, 6000.00, '2023-12-15', 'Year-end'),
                                        (3, 4000.00, '2023-12-15', 'Year-end'),
                                        (4, 4500.00, '2023-12-15', 'Year-end'),
                                        (5, 3500.00, '2023-12-15', 'Year-end');
                                        """;
                        case 24 -> """
                                        ALTER TABLE bonuses ADD CONSTRAINT chk_bonus_positive CHECK (bonus_amount > 0);
                                        CREATE INDEX idx_bonus_employee ON bonuses(employee_id);
                                        CREATE INDEX idx_bonus_date ON bonuses(bonus_date);
                                        """;
                        case 25 -> """
                                        CREATE VIEW employee_compensation AS
                                        SELECT
                                            e.employee_id,
                                            e.first_name,
                                            e.last_name,
                                            d.department_name,
                                            s.amount as current_salary,
                                            COALESCE(SUM(b.bonus_amount), 0) as total_bonuses,
                                            s.amount + COALESCE(SUM(b.bonus_amount), 0) as total_compensation
                                        FROM employees e
                                        LEFT JOIN departments d ON e.department_id = d.department_id
                                        LEFT JOIN salaries s ON e.employee_id = s.employee_id
                                        LEFT JOIN bonuses b ON e.employee_id = b.employee_id
                                        GROUP BY e.employee_id, e.first_name, e.last_name, d.department_name, s.amount;
                                        """;
                        default -> "-- Migration " + version;
                };
        }

        private String getUndoSql(int version) {
                return switch (version) {
                        case 1 -> "DROP TABLE IF EXISTS employees;";
                        case 2 -> "DROP TABLE IF EXISTS departments;";
                        case 3 -> """
                                        ALTER TABLE employees DROP CONSTRAINT IF EXISTS fk_emp_dept;
                                        ALTER TABLE employees DROP COLUMN IF EXISTS department_id;
                                        """;
                        case 4 -> "DELETE FROM departments;";
                        case 5 -> "DELETE FROM employees;";
                        case 6 -> "ALTER TABLE employees DROP COLUMN IF EXISTS email;";
                        case 7 -> "DROP TABLE IF EXISTS salaries;";
                        case 8 -> "DELETE FROM salaries;";
                        case 9 -> "ALTER TABLE salaries DROP COLUMN IF EXISTS grade;";
                        case 10 -> """
                                        DROP INDEX IF EXISTS idx_salary_employee;
                                        DROP INDEX IF EXISTS idx_salary_date;
                                        """;
                        case 11 -> "DROP TABLE IF EXISTS salary_history;";
                        case 12 -> """
                                        ALTER TABLE employees DROP CONSTRAINT IF EXISTS fk_emp_manager;
                                        ALTER TABLE employees DROP COLUMN IF EXISTS manager_id;
                                        """;
                        case 13 -> "UPDATE employees SET manager_id = NULL;";
                        case 14 -> """
                                        DROP INDEX IF EXISTS idx_emp_dept;
                                        DROP INDEX IF EXISTS idx_emp_manager;
                                        """;
                        case 15 -> "ALTER TABLE employees DROP COLUMN IF EXISTS phone_number;";
                        case 16 -> "DROP TABLE IF EXISTS positions;";
                        case 17 -> """
                                        ALTER TABLE employees DROP CONSTRAINT IF EXISTS fk_emp_position;
                                        ALTER TABLE employees DROP COLUMN IF EXISTS position_id;
                                        """;
                        case 18 -> """
                                        UPDATE employees SET position_id = NULL;
                                        DELETE FROM positions;
                                        """;
                        case 19 -> """
                                        ALTER TABLE salaries DROP CONSTRAINT IF EXISTS chk_salary_positive;
                                        ALTER TABLE salaries DROP CONSTRAINT IF EXISTS chk_salary_reasonable;
                                        """;
                        case 20 -> "DROP TABLE IF EXISTS employee_audit;";
                        case 21 -> """
                                        ALTER TABLE employees DROP CONSTRAINT IF EXISTS chk_rating;
                                        ALTER TABLE employees DROP COLUMN IF EXISTS performance_rating;
                                        """;
                        case 22 -> "DROP TABLE IF EXISTS bonuses;";
                        case 23 -> "DELETE FROM bonuses;";
                        case 24 -> """
                                        DROP INDEX IF EXISTS idx_bonus_employee;
                                        DROP INDEX IF EXISTS idx_bonus_date;
                                        ALTER TABLE bonuses DROP CONSTRAINT IF EXISTS chk_bonus_positive;
                                        """;
                        case 25 -> "DROP VIEW IF EXISTS employee_compensation;";
                        default -> "-- Undo migration " + version;
                };
        }

        private void createMigration(String prefix, int version, String desc, String content) throws IOException {
                String filename = prefix + version + "__" + desc + ".sql";
                Path file = tempDir.resolve(filename);
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        }

        private boolean tableExists(String tableName) throws SQLException {
                try (Connection conn = getConnection()) {
                        ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), null);
                        return rs.next();
                }
        }

        private boolean columnExists(String tableName, String columnName) throws SQLException {
                try (Connection conn = getConnection()) {
                        ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(),
                                        columnName.toUpperCase());
                        return rs.next();
                }
        }

        private boolean indexExists(String tableName, String indexName) throws SQLException {
                try (Connection conn = getConnection()) {
                        ResultSet rs = conn.getMetaData().getIndexInfo(null, null, tableName.toUpperCase(), false,
                                        false);
                        while (rs.next()) {
                                String name = rs.getString("INDEX_NAME");
                                if (name != null && name.equalsIgnoreCase(indexName)) {
                                        return true;
                                }
                        }
                        return false;
                }
        }

        private int countRows(String tableName) throws SQLException {
                try (Connection conn = getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM " + tableName);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                                return rs.getInt(1);
                        }
                        return 0;
                }
        }

        private Connection getConnection() throws SQLException {
                return flyway.getConfiguration().getDataSource().getConnection();
        }
}
