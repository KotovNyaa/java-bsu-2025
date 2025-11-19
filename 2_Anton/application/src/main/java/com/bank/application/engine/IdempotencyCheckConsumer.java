package com.bank.application.engine;

import com.bank.core.engine.TransactionEvent;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Первый потребитель в цепочке, обеспечивающий идемпотентность на уровне
 * обработчика
 */

public class IdempotencyCheckConsumer implements EventHandler<TransactionEvent> {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCheckConsumer.class);

    private final Set<UUID> processedKeysCache;

    public IdempotencyCheckConsumer() {
        this.processedKeysCache = ConcurrentHashMap.newKeySet();
    }

    public void initializeCache(Set<UUID> existingKeys) {
        if (existingKeys == null || existingKeys.isEmpty()) {
            log.info("Idempotency cache initialized with 0 existing keys.");
            return;
        }
        log.info("Initializing idempotency cache with {} existing keys...", existingKeys.size());
        this.processedKeysCache.addAll(existingKeys);
        log.info("Idempotency cache initialization complete.");
    }

    public void clearCache() {
        this.processedKeysCache.clear();
        log.warn("Idempotency cache cleared explicitly!");
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) throws Exception {
        final UUID key = event.getCommand().getIdempotencyKey();

        if (processedKeysCache.contains(key)) {
            log.trace("Skipping duplicate transaction (found in cache): {}", key);
            event.setShouldProcess(false);
            return;
        }

        event.setShouldProcess(true);

        boolean added = processedKeysCache.add(key);
        if (added) {
            event.setIdempotencyKeyToPersist(key);
        } else {
            log.trace("Skipping duplicate transaction (found in-flight in the same batch): {}", key);
            event.setShouldProcess(false);
        }
    }
}
