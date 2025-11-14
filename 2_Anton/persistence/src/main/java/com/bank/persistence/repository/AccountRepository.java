package com.bank.persistence.repository;

import com.bank.domain.Account;
import java.util.Map;
import java.util.UUID;

/**
 * Шаблон для AccountRepository
 */

public interface AccountRepository {
    Map<UUID, Account> loadAllAccounts();
}
