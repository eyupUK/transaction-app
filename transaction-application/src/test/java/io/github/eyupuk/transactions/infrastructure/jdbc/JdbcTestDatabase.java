package io.github.eyupuk.transactions.infrastructure.jdbc;

import org.postgresql.Driver;
import org.postgresql.PGProperty;

import java.util.Properties;

final class JdbcTestDatabase {

    private static final String DATABASE_NAME = "transaction_jdbc_test";

    private JdbcTestDatabase() {}

    static SqlConnectionFactory fromEnvironment() {
        requireDedicatedDatabase(System.getenv("DB_URL"));
        return SqlConnectionFactory.fromEnvironment();
    }

    static void requireDedicatedDatabase(String url) {
        Properties properties = url == null || url.isBlank()
                ? null
                : Driver.parseURL(url, new Properties());

        if (properties == null
                || !DATABASE_NAME.equals(PGProperty.PG_DBNAME.getOrNull(properties))) {
            throw new IllegalStateException(
                    "JDBC integration tests require DB_URL to target " + DATABASE_NAME
            );
        }
    }
}
