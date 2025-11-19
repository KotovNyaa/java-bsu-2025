package com.bank.core.port.out;

/**
 * порт для сохранения в персистентное хранилище батчами
 */

public interface BatchPersister {
    void persistBatch(BatchUnitOfWork unitOfWork);
}
