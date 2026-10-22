
package io.github.eyupuk.transactions.infrastructure.jdbc;

public final class JdbcPersistenceException
        extends RuntimeException {

    public JdbcPersistenceException(
            String message, Throwable cause) {
        super(message, cause);
    }
}
