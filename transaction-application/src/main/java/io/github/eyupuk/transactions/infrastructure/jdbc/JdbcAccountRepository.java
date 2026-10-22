
package io.github.eyupuk.transactions.infrastructure.jdbc;

import io.github.eyupuk.transactions.application.AccountRepository;
import io.github.eyupuk.transactions.domain.Account;

import java.sql.*;
import java.util.*;
import java.util.function.UnaryOperator;

public final class JdbcAccountRepository
        implements AccountRepository {

    private final SqlConnectionFactory connections;

    public JdbcAccountRepository(
            SqlConnectionFactory connections) {
        this.connections = Objects.requireNonNull(
                connections, "connections"
        );
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");

        String sql = """
                SELECT id, balance, currency
                FROM accounts
                WHERE id = ?
                """;

        try (Connection connection = connections.open();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setObject(1, accountId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        JdbcAccountMapper.from(rs)
                );
            }

        } catch (SQLException e) {
            throw new JdbcPersistenceException(
                    "Failed to find account", e
            );
        }
    }

    @Override
    public Account update(
            UUID accountId,
            UnaryOperator<Account> change) {

        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(change, "change");

        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);

            try {
                Account current =
                        findAndLock(connection, accountId);

                Account updated = change.apply(current);

                if (updated == null) {
                    throw new IllegalStateException(
                            "Updated account cannot be null"
                    );
                }

                if (!updated.id().equals(accountId)) {
                    throw new IllegalStateException(
                            "Account ID cannot change"
                    );
                }

                if (!updated.balance().currency().equals(
                        current.balance().currency())) {
                    throw new IllegalArgumentException(
                            "Account currency cannot change"
                    );
                }

                persistBalance(connection, updated);

                connection.commit();
                return updated;

            } catch (SQLException e) {
                rollback(connection, e);
                throw new JdbcPersistenceException(
                        "Failed to update account", e
                );

            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            }

        } catch (SQLException e) {
            throw new JdbcPersistenceException(
                    "Database connection failure", e
            );
        }
    }

    private Account findAndLock(
            Connection connection, UUID accountId)
            throws SQLException {

        String sql = """
                SELECT id, balance, currency
                FROM accounts
                WHERE id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setObject(1, accountId);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new NoSuchElementException(
                            "Account not found: " + accountId
                    );
                }

                return JdbcAccountMapper.from(rs);
            }
        }
    }

    private void persistBalance(
            Connection connection, Account account)
            throws SQLException {

        String sql = """
                UPDATE accounts
                SET balance = ?,
                    version = version + 1
                WHERE id = ?
                  AND currency = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setBigDecimal(
                    1, account.balance().amount()
            );
            statement.setObject(2, account.id());
            statement.setString(
                    3,
                    account.balance()
                            .currency()
                            .getCurrencyCode()
            );

            int rows = statement.executeUpdate();

            if (rows != 1) {
                throw new IllegalStateException(
                        "Expected one account update"
                );
            }
        }
    }

    private static void rollback(
            Connection connection, Throwable original) {

        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
