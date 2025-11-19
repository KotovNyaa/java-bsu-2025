package com.bank.persistence.repository.impl;

import com.bank.core.port.out.BatchPersister;
import com.bank.core.port.out.BatchUnitOfWork;
import com.bank.persistence.exception.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JdbcBatchPersister Сохраняет батчами и обновляет счета и чистит Outbox
 */

public class JdbcBatchPersister implements BatchPersister {
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final String INSERT_PROCESSED_KEY_SQL = "INSERT INTO \"processed_transactions\" (\"idempotency_key\", \"processed_at\") VALUES (?, ?)";
    private static final String UPDATE_ACCOUNT_SQL = "UPDATE \"accounts\" SET \"balance\" = ?, \"status\" = ? WHERE \"id\" = ?";
    private static final String DELETE_OUTBOX_SQL = "DELETE FROM \"transaction_outbox\" WHERE \"idempotency_key\" = ?";
    private static final String INSERT_JOURNAL_SQL = "INSERT INTO \"transaction_journal\" (\"transaction_id\", \"idempotency_key\", \"command_type\", \"amount\", \"account_id_from\", \"account_id_to\", \"timestamp\") VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_OUTBOX_FOR_DLQ_SQL_IN = "SELECT \"transaction_id\", \"payload\", \"idempotency_key\" FROM \"transaction_outbox\" WHERE \"idempotency_key\" IN (:keys)";
    private static final String INSERT_DLQ_SQL = "INSERT INTO \"transaction_outbox_dlq\" (\"id\", \"payload\", \"reason\", \"moved_at\") VALUES (?, ?, ?, ?)";

    public JdbcBatchPersister(DataSource dataSource, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void persistBatch(BatchUnitOfWork uow) {
        transactionTemplate.execute(status -> {
            try {
                if (!uow.keysToInsert.isEmpty()) {
                    jdbcTemplate.batchUpdate(INSERT_PROCESSED_KEY_SQL, uow.keysToInsert, 100, (ps, key) -> {
                        ps.setObject(1, key);
                        ps.setTimestamp(2, Timestamp.from(Instant.now()));
                    });
                }
                if (!uow.commandsToJournal.isEmpty()) {
                    jdbcTemplate.batchUpdate(INSERT_JOURNAL_SQL, uow.commandsToJournal, 100, (ps, cmd) -> {
                        ps.setObject(1, cmd.getTransactionId());
                        ps.setObject(2, cmd.getIdempotencyKey());
                        ps.setString(3, cmd.getActionType().name());
                        ps.setBigDecimal(4, cmd.getAmount());
                        ps.setObject(5, cmd.getAccountId());
                        ps.setObject(6, cmd.getTargetAccountId());
                        Instant timestampInstant = Instant.ofEpochMilli(cmd.getTimestamp());
                        ps.setTimestamp(7, Timestamp.from(timestampInstant));
                    });
                }
                if (!uow.accountsToUpdate.isEmpty()) {
                    jdbcTemplate.batchUpdate(UPDATE_ACCOUNT_SQL, new ArrayList<>(uow.accountsToUpdate.values()), 100,
                            (ps, account) -> {
                                ps.setBigDecimal(1, account.getBalance());
                                ps.setString(2, account.getStatus().name());
                                ps.setObject(3, account.getId());
                            });
                }
                if (!uow.successfulOutboxKeysToRemove.isEmpty()) {
                    jdbcTemplate.batchUpdate(DELETE_OUTBOX_SQL, uow.successfulOutboxKeysToRemove, 100,
                            (PreparedStatement ps, UUID key) -> ps.setObject(1, key));
                }

                if (!uow.failedOutboxKeysToDlq.isEmpty()) {
                    MapSqlParameterSource parameters = new MapSqlParameterSource();
                    parameters.addValue("keys", uow.failedOutboxKeysToDlq.keySet());

                    List<DlqTransferObject> itemsToMove = namedParameterJdbcTemplate.query(
                            SELECT_OUTBOX_FOR_DLQ_SQL_IN,
                            parameters,
                            (rs, rowNum) -> new DlqTransferObject(
                                    rs.getObject("transaction_id", UUID.class),
                                    rs.getString("payload"),
                                    uow.failedOutboxKeysToDlq.get(rs.getObject("idempotency_key", UUID.class))));

                    if (!itemsToMove.isEmpty()) {
                        jdbcTemplate.batchUpdate(INSERT_DLQ_SQL, itemsToMove, 100,
                                (ps, item) -> {
                                    ps.setObject(1, item.transactionId);
                                    ps.setString(2, item.payload);
                                    ps.setString(3, item.reason);
                                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                                });
                    }

                    jdbcTemplate.batchUpdate(DELETE_OUTBOX_SQL, uow.failedOutboxKeysToDlq.keySet(), 100,
                            (PreparedStatement ps, UUID key) -> ps.setObject(1, key));
                }
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new DataAccessException("Failed to persist batch unit of work", e);
            }
            return null;
        });
    }

    private static class DlqTransferObject {
        final UUID transactionId;
        final String payload;
        final String reason;

        DlqTransferObject(UUID transactionId, String payload, String reason) {
            this.transactionId = transactionId;
            this.payload = payload;
            this.reason = reason;
        }
    }
}
