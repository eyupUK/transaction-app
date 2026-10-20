
CREATE TABLE accounts (
                          id UUID PRIMARY KEY,

                          owner_name VARCHAR(120) NOT NULL,

                          currency VARCHAR(3) NOT NULL
                              CHECK (currency IN ('GBP', 'EUR')),

                          balance NUMERIC(19,2) NOT NULL
                              DEFAULT 0
                              CHECK (balance >= 0),

                          version BIGINT NOT NULL
                              DEFAULT 0
                              CHECK (version >= 0),

                          created_at TIMESTAMPTZ NOT NULL
                              DEFAULT CURRENT_TIMESTAMP,

                          UNIQUE (id, currency)
);



CREATE TABLE transfers (
                           id UUID PRIMARY KEY,

                           source_account_id UUID NOT NULL,
                           destination_account_id UUID NOT NULL,

                           amount NUMERIC(19,2) NOT NULL
                               CHECK (amount > 0),

                           currency VARCHAR(3) NOT NULL
                               CHECK (currency IN ('GBP', 'EUR')),

                           status VARCHAR(16) NOT NULL
                               DEFAULT 'PENDING'
                               CHECK (status IN (
                                                 'PENDING', 'COMPLETED', 'FAILED'
                                   )),

                           created_at TIMESTAMPTZ NOT NULL
                               DEFAULT CURRENT_TIMESTAMP,

                           CHECK (
                               source_account_id <> destination_account_id
                               ),

                           FOREIGN KEY (source_account_id, currency)
                               REFERENCES accounts (id, currency),

                           FOREIGN KEY (destination_account_id, currency)
                               REFERENCES accounts (id, currency)
);


CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,

                                transfer_id UUID NOT NULL
                                    REFERENCES transfers(id),

                                account_id UUID NOT NULL
                                    REFERENCES accounts(id),

                                entry_type VARCHAR(6) NOT NULL
                                    CHECK (entry_type IN ('DEBIT', 'CREDIT')),

                                amount NUMERIC(19,2) NOT NULL
                                    CHECK (amount > 0),

                                created_at TIMESTAMPTZ NOT NULL
                                    DEFAULT CURRENT_TIMESTAMP,

                                UNIQUE (transfer_id, entry_type)
);
