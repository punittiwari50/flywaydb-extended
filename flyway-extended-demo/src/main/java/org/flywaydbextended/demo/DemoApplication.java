package org.flywaydbextended.demo;

import java.lang.reflect.Field;

import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.ReflectionUtils;

@SpringBootApplication
public class DemoApplication implements org.springframework.boot.CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Flyway Extended Demo Application started!");
        // Logic will go here
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer flywayConfigurationCustomizer(
            javax.sql.DataSource dataSource) {
        return configuration -> {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                String databaseProductName = connection.getMetaData().getDatabaseProductName().toLowerCase();
                System.out.println("Detected Database: " + databaseProductName);
                // Simple mapping: 'postgresql' -> 'postgresql'
                // You might need more complex mapping for other DBs (e.g. 'oracle' from 'Oracle
                // Database...')
                String location = "classpath:db/migration/" + databaseProductName;
                configuration.locations(location);
                Field configField = ReflectionUtils.findField(FluentConfiguration.class, "config"); 
                ReflectionUtils.makeAccessible(configField);
                
                ClassicConfiguration configObj = (ClassicConfiguration) ReflectionUtils.getField(configField, configuration);
                
                Field outputQueryResultField = ReflectionUtils.findField(ClassicConfiguration.class, "outputQueryResults");
                ReflectionUtils.makeAccessible(outputQueryResultField);
                
               // ReflectionUtils.setField(outputQueryResultField, configObj, Boolean.TRUE);
                System.out.println("Set Flyway Location to: " + configObj);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Failed to determine database vendor", e);
            }
        };
    }

}
