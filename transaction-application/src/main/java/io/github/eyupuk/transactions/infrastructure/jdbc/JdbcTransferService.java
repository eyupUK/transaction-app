
package io.github.eyupuk.transactions.infrastructure.jdbc;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;

import java.sql.*;
import java.util.*;

public final class JdbcTransferService {

    private final SqlConnectionFactory connections;

    public JdbcTransferService(SqlConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public UUID transfer(
            UUID transferId,
            UUID sourceId,
            UUID destinationId,
            Money amount) {

        Objects.requireNonNull(transferId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(destinationId);
        Objects.requireNonNull(amount);

        if (sourceId.equals(destinationId)) {
            throw new IllegalArgumentException("Self-transfer");
        }

        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Transfer amount must be positive"
            );
        }

        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);

            try {
                Map<UUID, Account> accounts = lockAccounts(
                        connection, sourceId, destinationId
                );

                if (accounts.size() != 2) {
                    throw new NoSuchElementException(
                            "Source or destination account not found"
                    );
                }

                Account debited =
                        accounts.get(sourceId).withdraw(amount);

                Account credited =
                        accounts.get(destinationId).deposit(amount);

                insertTransfer(
                        connection, transferId,
                        sourceId, destinationId, amount
                );

                updateBalance(connection, debited);
                updateBalance(connection, credited);

                insertLedger(
                        connection, transferId,
                        sourceId, "DEBIT", amount
                );

                insertLedger(
                        connection, transferId,
                        destinationId, "CREDIT", amount
                );

                completeTransfer(connection, transferId);

                connection.commit();

                return transferId;

            } catch (SQLException e) {
                rollback(connection, e);
                throw new JdbcPersistenceException(
                        "Transfer database failure", e
                );

            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            }

        } catch (SQLException e) {
            throw new JdbcPersistenceException(
                    "Transfer connection failure", e
            );
        }
    }

    private Map<UUID, Account> lockAccounts(
            Connection connection,
            UUID sourceId,
            UUID destinationId) throws SQLException {

        String sql = """
                SELECT id, balance, currency
                FROM accounts
                WHERE id IN (?, ?)
                ORDER BY id
                FOR UPDATE
                """;

        Map<UUID, Account> result = new HashMap<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setObject(1, sourceId);
            statement.setObject(2, destinationId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Account account =
                            JdbcAccountMapper.from(rs);

                    result.put(account.id(), account);
                }
            }
        }

        return result;
    }

    private void insertTransfer(
            Connection connection,
            UUID transferId,
            UUID sourceId,
            UUID destinationId,
            Money amount) throws SQLException {

        String sql = """
                INSERT INTO transfers (
                    id, source_account_id,
                    destination_account_id,
                    amount, currency, status
                )
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setObject(1, transferId);
            statement.setObject(2, sourceId);
            statement.setObject(3, destinationId);
            statement.setBigDecimal(4, amount.amount());
            statement.setString(
                    5, amount.currency().getCurrencyCode()
            );

            statement.executeUpdate();
        }
    }

    private void updateBalance(
            Connection connection,
            Account account) throws SQLException {

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

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Account update failed"
                );
            }
        }
    }

    private void insertLedger(
            Connection connection,
            UUID transferId,
            UUID accountId,
            String entryType,
            Money amount) throws SQLException {

        String sql = """
                INSERT INTO ledger_entries (
                    id, transfer_id, account_id,
                    entry_type, amount
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, transferId);
            statement.setObject(3, accountId);
            statement.setString(4, entryType);
            statement.setBigDecimal(5, amount.amount());

            statement.executeUpdate();
        }
    }

    private void completeTransfer(
            Connection connection, UUID transferId)
            throws SQLException {

        String sql = """
                UPDATE transfers
                SET status = 'COMPLETED'
                WHERE id = ?
                  AND status = 'PENDING'
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setObject(1, transferId);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Transfer completion failed"
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
