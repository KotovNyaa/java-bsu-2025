package com.bank.core.port.out;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;

import java.util.*;

/**
 * порт для агрегригации всех изменений
 */

public class BatchUnitOfWork {
    public final Set<UUID> keysToInsert = new HashSet<>();
    public final List<TransactionCommand> commandsToJournal = new ArrayList<>();

    public final Map<UUID, Account> accountsToUpdate = new HashMap<>();

    public final Set<UUID> successfulOutboxKeysToRemove = new HashSet<>();

    public final Map<UUID, String> failedOutboxKeysToDlq = new HashMap<>();

    public boolean isEmpty() {
        return keysToInsert.isEmpty() && commandsToJournal.isEmpty() &&
                accountsToUpdate.isEmpty() && successfulOutboxKeysToRemove.isEmpty() &&
                failedOutboxKeysToDlq.isEmpty();
    }
}
