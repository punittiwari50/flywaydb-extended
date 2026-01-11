package org.flywaydbextended.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.flywaydb.core.api.output.OperationResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes OperationResult objects to files in various formats.
 */
public class ResultOutputWriter {

    private final ResultOutputConfiguration config;
    private final ObjectMapper objectMapper;

    public ResultOutputWriter(ResultOutputConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Write operation result to configured location.
     */
    public void writeResult(String operationType, OperationResult result) {
        if (!config.isSaveResults()) {
            return; // Output saving disabled
        }

        try {
            String format = config.getFormat().toUpperCase();
            String location = config.getLocation();

            // Create parent directories if needed
            File outputFile = new File(location);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }

            switch (format) {
                case "JSON":
                    writeJsonResult(outputFile, operationType, result);
                    break;
                case "XML":
                    writeXmlResult(outputFile, operationType, result);
                    break;
                case "CSV":
                    writeCsvResult(outputFile, operationType, result);
                    break;
                default:
                    System.err.println("Unknown output format: " + format);
            }

        } catch (Exception e) {
            System.err.println("Failed to write result output: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeJsonResult(File file, String operationType, OperationResult result)
            throws IOException {
        Map<String, Object> output = new HashMap<>();
        output.put("timestamp", LocalDateTime.now().toString());
        output.put("operation", operationType);
        output.put("result", result != null ? result.toString() : "null");

        // Append to existing file or create new
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(objectMapper.writeValueAsString(output));
            writer.write("\n");
        }
    }

    private void writeXmlResult(File file, String operationType, OperationResult result)
            throws IOException {
        String resultStr = result != null ? result.toString() : "null";
        StringBuilder xml = new StringBuilder();
        xml.append("<operationResult>\n");
        xml.append("  <timestamp>").append(LocalDateTime.now()).append("</timestamp>\n");
        xml.append("  <operation>").append(operationType).append("</operation>\n");
        xml.append("  <result>").append(escapeXml(resultStr)).append("</result>\n");
        xml.append("</operationResult>\n");

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(xml.toString());
        }
    }

    private void writeCsvResult(File file, String operationType, OperationResult result)
            throws IOException {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String resultStr = result != null ? result.toString().replace(",", ";") : "null";

        String csv = String.format("%s,%s,%s\n",
                timestamp, operationType, resultStr);

        try (FileWriter writer = new FileWriter(file, true)) {
            // Write header if file is new
            if (!file.exists() || file.length() == 0) {
                writer.write("Timestamp,Operation,Result\n");
            }
            writer.write(csv);
        }
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
