package io.github.eyupuk.transactions.infrastructure.jdbc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTestDatabaseTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://127.0.0.1:5433/transaction_jdbc_test",
            "jdbc:postgresql://localhost/transaction_jdbc_test?sslmode=disable",
            "jdbc:postgresql:transaction_jdbc_test",
            "jdbc:postgresql://[::1]:5433/transaction_jdbc_test",
            "jdbc:postgresql://host1:5432,host2:5432/transaction_jdbc_test",
            "jdbc:postgresql://localhost/%74ransaction_jdbc_test",
            "jdbc:postgresql://localhost/transaction_db?dbname=transaction_jdbc_test"
    })
    void acceptsUrlsWhoseEffectiveDatabaseIsTheDedicatedTestDatabase(String url) {
        assertDoesNotThrow(() -> JdbcTestDatabase.requireDedicatedDatabase(url));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "jdbc:postgresql://localhost/transaction_db",
            "jdbc:postgresql://localhost/postgres",
            "jdbc:postgresql://localhost/transaction_jdbc_test_backup",
            "jdbc:postgresql://localhost/not_transaction_jdbc_test",
            "jdbc:postgresql://transaction_jdbc_test/transaction_db",
            "jdbc:postgresql://localhost/transaction_db?ApplicationName=transaction_jdbc_test",
            "jdbc:postgresql://localhost/transaction_jdbc_test?dbname=transaction_db",
            "jdbc:postgresql://localhost/transaction_jdbc_test?PGDBNAME=postgres",
            "jdbc:postgresql://localhost/transaction_jdbc_test?dbname=transaction_jdbc_test&dbname=transaction_db",
            "jdbc:postgresql://localhost/transaction_jdbc_test%2Ftransaction_db",
            "jdbc:postgresql://localhost/transaction_jdbc_test?dbname=transaction_jdbc_test%20",
            "jdbc:postgresql://localhost/",
            "jdbc:postgresql:",
            "jdbc:postgresql://localhost",
            "jdbc:postgresql://localhost/transaction_jdbc_test/extra",
            "jdbc:postgresql://localhost/%not-valid",
            "jdbc:mysql://localhost/transaction_jdbc_test",
            "not-a-jdbc-url"
    })
    void rejectsUnsafeOrMalformedUrlsWithoutConnecting(String url) {
        var failure = assertThrows(
                IllegalStateException.class,
                () -> JdbcTestDatabase.requireDedicatedDatabase(url)
        );

        assertTrue(failure.getMessage().contains("transaction_jdbc_test"));
    }
}
