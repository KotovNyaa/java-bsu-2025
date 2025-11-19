package com.bank.application.engine;

import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class DisruptorConsumersTest {

    private TransactionEvent event;

    private final TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(),
            UUID.randomUUID(), BigDecimal.TEN);

    @BeforeEach
    void setUp() {
        event = new TransactionEvent();
    }

    @Nested
    class IdempotencyCheckConsumerTests {

        private final IdempotencyCheckConsumer idempotencyCheckConsumer = new IdempotencyCheckConsumer();

        @Test
        void onEvent_whenTransactionIsNew_shouldKeepShouldProcessAsTrue() throws Exception {
            event.setCommand(command);

            idempotencyCheckConsumer.onEvent(event, 1L, true);

            assertTrue(event.shouldProcess(), "New transaction should be processed");

            assertEquals(command.getIdempotencyKey(), event.getIdempotencyKeyToPersist(),
                    "New key should be marked for persistence");
        }

        @Test
        void onEvent_whenTransactionIsDuplicate_shouldSetShouldProcessToFalse() throws Exception {
            event.setCommand(command);

            java.util.Set<java.util.UUID> existingKeys = java.util.Set.of(command.getIdempotencyKey());
            idempotencyCheckConsumer.initializeCache(existingKeys);

            idempotencyCheckConsumer.onEvent(event, 1L, true);

            assertFalse(event.shouldProcess(), "Duplicate transaction should NOT be processed");

            assertNull(event.getIdempotencyKeyToPersist(), "Duplicate key should NOT be marked for persistence");
        }

        @Test
        void onEvent_whenTwoIdenticalTransactionsInSameBatch_shouldProcessFirstAndSkipSecond() throws Exception {
            TransactionEvent event1 = new TransactionEvent();
            event1.setCommand(command);

            TransactionEvent event2 = new TransactionEvent();
            event2.setCommand(command);

            idempotencyCheckConsumer.onEvent(event1, 1L, false);

            idempotencyCheckConsumer.onEvent(event2, 2L, true);

            assertTrue(event1.shouldProcess(), "First occurrence should be processed");
            assertFalse(event2.shouldProcess(), "Second occurrence (in-flight duplicate) should be skipped");
        }
    }
}
