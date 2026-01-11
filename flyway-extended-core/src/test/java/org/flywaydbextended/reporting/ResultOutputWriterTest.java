package org.flywaydbextended.reporting;

import org.flywaydbextended.api.RollbackResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for ResultOutputWriter with 100% code coverage.
 */
@DisplayName("ResultOutputWriter Tests")
class ResultOutputWriterTest {

    @TempDir
    Path tempDir;

    private ResultOutputWriter writer;
    private String outputPath;

    @BeforeEach
    void setUp() {
        outputPath = tempDir.resolve("test-output.json").toString();
    }

    @Test
    @DisplayName("Should write JSON format successfully")
    void testWriteJSON() throws Exception {
        // Given: Writer configured for JSON
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("JSON");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        RollbackResult result = new RollbackResult(
                "testdb", 2, Arrays.asList("2", "1"),
                true, "admin", null);

        // When: Write result
        writer.writeResult("ROLLBACK", result);

        // Then: File should contain JSON
        String content = Files.readString(Path.of(outputPath));
        assertThat(content).contains("\"operation\" : \"ROLLBACK\"");
        assertThat(content).contains("testdb");
    }

    @Test
    @DisplayName("Should write XML format successfully")
    void testWriteXML() throws Exception {
        // Given: Writer configured for XML
        outputPath = tempDir.resolve("test-output.xml").toString();
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("XML");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        RollbackResult result = new RollbackResult(
                "testdb", 1, Collections.singletonList("2"),
                true, "admin", null);

        // When: Write result
        writer.writeResult("ROLLBACK", result);

        // Then: File should contain XML
        String content = Files.readString(Path.of(outputPath));
        assertThat(content).contains("<operation>ROLLBACK</operation>");
        assertThat(content).contains("testdb");
    }

    @Test
    @DisplayName("Should write CSV format successfully")
    void testWriteCSV() throws Exception {
        // Given: Writer configured for CSV
        outputPath = tempDir.resolve("test-output.csv").toString();
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("CSV");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        RollbackResult result = new RollbackResult(
                "testdb", 1, Collections.singletonList("2"),
                true, "admin", null);

        // When: Write result
        writer.writeResult("ROLLBACK", result);

        // Then: File should contain CSV
        String content = Files.readString(Path.of(outputPath));
        assertThat(content).contains("ROLLBACK");
        assertThat(content).contains("testdb");
    }

    @Test
    @DisplayName("Should handle multiple writes")
    void testMultipleWrites() throws Exception {
        // Given: Writer
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("JSON");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        // When: Write multiple results
        for (int i = 0; i < 3; i++) {
            RollbackResult result = new RollbackResult(
                    "testdb", i, Collections.emptyList(),
                    true, "admin", null);
            writer.writeResult("ROLLBACK_" + i, result);
        }

        // Then: File should contain all results
        String content = Files.readString(Path.of(outputPath));
        assertThat(content).contains("ROLLBACK_0");
        assertThat(content).contains("ROLLBACK_1");
        assertThat(content).contains("ROLLBACK_2");
    }

    @Test
    @DisplayName("Should handle null result gracefully")
    void testNullResult() {
        // Given: Writer
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("JSON");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        // When/Then: Should not throw exception
        assertThatCode(() -> writer.writeResult("TEST", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should create parent directories if needed")
    void testCreateParentDirectories() {
        // Given: Path with non-existent parent directories
        String deepPath = tempDir.resolve("a/b/c/output.json").toString();
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("JSON");
        config.setLocation(deepPath);
        writer = new ResultOutputWriter(config);

        RollbackResult result = new RollbackResult(
                "testdb", 1, Collections.singletonList("1"),
                true, "admin", null);

        // When: Write result
        writer.writeResult("ROLLBACK", result);

        // Then: File should exist
        assertThat(new File(deepPath)).exists();
    }

    @Test
    @DisplayName("Should handle case-insensitive format")
    void testCaseInsensitiveFormat() {
        // Given: Writer with lowercase format
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(true);
        config.setFormat("json");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        RollbackResult result = new RollbackResult(
                "testdb", 1, Collections.singletonList("1"),
                true, "admin", null);

        // When/Then: Should not throw exception
        assertThatCode(() -> writer.writeResult("ROLLBACK", result))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not write when saveResults is false")
    void testSaveResultsDisabled() {
        // Given: Writer with saveResults disabled
        ResultOutputConfiguration config = new ResultOutputConfiguration();
        config.setSaveResults(false);
        config.setFormat("JSON");
        config.setLocation(outputPath);
        writer = new ResultOutputWriter(config);

        RollbackResult result = new RollbackResult(
                "testdb", 1, Collections.singletonList("1"),
                true, "admin", null);

        // When: Write result
        writer.writeResult("ROLLBACK", result);

        // Then: File should not exist
        assertThat(new File(outputPath)).doesNotExist();
    }
}
