package com.suhanamart.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central place for opening a JDBC connection to the SUHANAMART
 * MySQL database.
 *
 * <p>Every servlet or DAO that needs the database calls
 * {@link #getConnection()} and gets back a fresh {@link Connection}.
 * Credentials are never hardcoded here. They are resolved, in order,
 * from:</p>
 *
 * <ol>
 *   <li>Environment variables {@code SUHANAMART_DB_USER} /
 *       {@code SUHANAMART_DB_PASSWORD}</li>
 *   <li>JVM system properties {@code -Ddb.user=...} /
 *       {@code -Ddb.password=...}</li>
 *   <li>A local {@code db.properties} file on the classpath</li>
 * </ol>
 */
public class DBConnection {

    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/suhanamart?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String ENV_DB_USER = "SUHANAMART_DB_USER";
    private static final String ENV_DB_PASSWORD = "SUHANAMART_DB_PASSWORD";

    private static final String PROP_DB_USER = "db.user";
    private static final String PROP_DB_PASSWORD = "db.password";

    private static final String PROPERTIES_FILE = "/db.properties";

    private DBConnection() {
        // Utility class: no instances needed.
    }

    /**
     * Opens and returns a new connection to the suhanamart database.
     */
    public static Connection getConnection() throws SQLException {
        Credentials credentials = resolveCredentials();

        if (credentials == null) {
            throw new SQLException(
                    "Database credentials are missing. Set the " + ENV_DB_USER + " and "
                            + ENV_DB_PASSWORD + " environment variables for the process running "
                            + "Tomcat, OR pass -D" + PROP_DB_USER + "=... -D" + PROP_DB_PASSWORD
                            + "=... as JVM options, OR create db.properties on the classpath "
                            + "(copy db.properties.example and fill in real values).");
        }

        try {
            /*
             * Explicitly load the MySQL JDBC driver.
             *
             * This is added because the Tomcat runtime reported:
             * "No suitable driver found for jdbc:mysql://..."
             *
             * The MySQL Connector/J JAR is already present in the WAR,
             * but explicitly loading the driver removes the runtime
             * driver-registration ambiguity.
             */
            Class.forName("com.mysql.cj.jdbc.Driver");

            return DriverManager.getConnection(
                    DB_URL,
                    credentials.user,
                    credentials.password
            );

        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "MySQL JDBC driver was not found. Make sure mysql-connector-j "
                            + "is included in the application.",
                    e
            );

        } catch (SQLException e) {
            throw new SQLException(
                    "Failed to connect to the suhanamart database (credentials came from "
                            + credentials.source + "): " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Tries each credential source in order and returns the first
     * complete pair found.
     */
    private static Credentials resolveCredentials() {

        String envUser = System.getenv(ENV_DB_USER);
        String envPassword = System.getenv(ENV_DB_PASSWORD);

        if (envUser != null && envPassword != null) {
            return new Credentials(
                    envUser,
                    envPassword,
                    "environment variables"
            );
        }

        String propUser = System.getProperty(PROP_DB_USER);
        String propPassword = System.getProperty(PROP_DB_PASSWORD);

        if (propUser != null && propPassword != null) {
            return new Credentials(
                    propUser,
                    propPassword,
                    "JVM system properties (-D" + PROP_DB_USER + ")"
            );
        }

        Credentials fileCredentials = loadFromPropertiesFile();

        if (fileCredentials != null) {
            return fileCredentials;
        }

        return null;
    }

    /**
     * Loads database credentials from db.properties.
     */
    private static Credentials loadFromPropertiesFile() {

        try (InputStream in =
                     DBConnection.class.getResourceAsStream(PROPERTIES_FILE)) {

            if (in == null) {
                return null;
            }

            Properties props = new Properties();
            props.load(in);

            String fileUser = props.getProperty("db.user");
            String filePassword = props.getProperty("db.password");

            if (fileUser != null && filePassword != null) {
                return new Credentials(
                        fileUser,
                        filePassword,
                        "db.properties on the classpath"
                );
            }

            return null;

        } catch (IOException e) {
            return null;
        }
    }

    private static final class Credentials {

        final String user;
        final String password;
        final String source;

        Credentials(
                String user,
                String password,
                String source
        ) {
            this.user = user;
            this.password = password;
            this.source = source;
        }
    }
}