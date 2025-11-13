package com.bank.core.state;

import com.bank.core.domain.Account;
import com.bank.core.exception.AccountNotFoundException;
import java.util.UUID;

/**
 * Интерфейс, определяющий контракт для хранилища состояний счетов
 */

public interface AccountStateProvider {
    Account getAccount(UUID accountId) throws AccountNotFoundException;
    void createOrUpdateAccount(Account account);
}
