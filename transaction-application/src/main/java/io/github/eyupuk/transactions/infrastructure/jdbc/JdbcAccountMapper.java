
package io.github.eyupuk.transactions.infrastructure.jdbc;

import io.github.eyupuk.transactions.domain.Account;
import io.github.eyupuk.transactions.domain.Money;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.UUID;

final class JdbcAccountMapper {

    private JdbcAccountMapper() {}

    static Account from(ResultSet rs)
            throws SQLException {

        UUID id = rs.getObject("id", UUID.class);

        Money balance = new Money(
                rs.getBigDecimal("balance"),
                Currency.getInstance(
                        rs.getString("currency")
                )
        );

        return new Account(id, balance);
    }
}
