package com.bank.core.state;

import com.bank.core.domain.Account;
import com.bank.core.exception.AccountNotFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация хранилища состояний счетов
 */

public enum AccountState implements AccountStateProvider {
    INSTANCE;

    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Account getAccount(UUID accountId) throws AccountNotFoundException {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException("Account with ID " + accountId + " not found.");
        }
        return account;
    }

    @Override
    public void createOrUpdateAccount(Account account) {
        accounts.put(account.getId(), account);
    }

    public void loadAll(Map<UUID, Account> initialAccounts) {
        accounts.clear();
        accounts.putAll(initialAccounts);
    }
}
