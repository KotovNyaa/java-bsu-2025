package com.bank.ui.contract;

import java.util.UUID;

public interface TransactionObserver {
    void onTransactionProcessed(UUID idempotencyKey);
}