DROP TABLE IF EXISTS transaction_journal;
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    balance DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE transaction_journal (
    sequence_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id UUID NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    account_id_from UUID NOT NULL,
    account_id_to UUID,
    amount DECIMAL(19, 2)
);
