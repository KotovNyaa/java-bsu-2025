package com.bank.application.port.out;

import com.bank.application.service.TransactionStatus;
import java.util.UUID;

/**
 * Исходящий порт для получения статуса транзакции из хранилища
 */

public interface TransactionStatusProvider {
    TransactionStatus findStatusByIdempotencyKey(UUID idempotencyKey);
}
