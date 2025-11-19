package com.bank.core.engine;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Событие, содержащее команду транзакции. Единица данных в RingBuffer
 */

public class TransactionEvent {

    private TransactionCommand command;
    private boolean shouldProcess;
    private UUID idempotencyKeyToPersist;
    private Exception businessException;

    private final List<Account> modifiedAccounts = new ArrayList<>();

    public void setCommand(TransactionCommand command) {
        this.command = command;
    }

    public TransactionCommand getCommand() {
        return command;
    }

    public void setShouldProcess(boolean shouldProcess) {
        this.shouldProcess = shouldProcess;
    }

    public boolean shouldProcess() {
        return shouldProcess;
    }

    public void setIdempotencyKeyToPersist(UUID idempotencyKeyToPersist) {
        this.idempotencyKeyToPersist = idempotencyKeyToPersist;
    }

    public UUID getIdempotencyKeyToPersist() {
        return idempotencyKeyToPersist;
    }

    public void setBusinessException(Exception businessException) {
        this.businessException = businessException;
    }

    public Exception getBusinessException() {
        return businessException;
    }

    public void addModifiedAccount(Account account) {
        if (account != null) {
            this.modifiedAccounts.add(account);
        }
    }

    public List<Account> getModifiedAccounts() {
        return modifiedAccounts;
    }

    public void clear() {
        this.command = null;
        this.shouldProcess = false;
        this.idempotencyKeyToPersist = null;
        this.businessException = null;
        this.modifiedAccounts.clear();
    }
}
