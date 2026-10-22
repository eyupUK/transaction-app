
package io.github.eyupuk.transactions.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlConnectionFactory {

    Connection open() throws SQLException;

    static SqlConnectionFactory fromEnvironment() {
        String url = required("DB_URL");
        String user = required("DB_USER");
        String password = required("DB_PASSWORD");

        return () -> DriverManager.getConnection(
                url, user, password
        );
    }

    private static String required(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }

        return value;
    }
}
