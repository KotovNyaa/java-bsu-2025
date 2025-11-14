package com.bank.application.engine;

import com.bank.application.port.out.ProcessedTransactionRepository;
import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisruptorConsumersTest {

    private TransactionEvent event;

    private final TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);

    @BeforeEach
    void setUp() {
        event = new TransactionEvent();
    }

    @Nested
    class IdempotencyCheckConsumerTests {

        @Mock
        private ProcessedTransactionRepository repository;

        @InjectMocks
        private IdempotencyCheckConsumer idempotencyCheckConsumer;

        @Test
        void onEvent_whenTransactionIsNew_shouldKeepShouldProcessAsTrue() throws Exception {
            event.setCommand(command);
            when(repository.isProcessedAndMark(command.getTransactionId())).thenReturn(true);

            idempotencyCheckConsumer.onEvent(event, 1L, true);

            assertTrue(event.shouldProcess());
            verify(repository).isProcessedAndMark(command.getTransactionId());
        }

        @Test
        void onEvent_whenTransactionIsDuplicate_shouldSetShouldProcessToFalse() throws Exception {
            event.setCommand(command);
            when(repository.isProcessedAndMark(command.getTransactionId())).thenReturn(false);

            idempotencyCheckConsumer.onEvent(event, 1L, true);

            assertFalse(event.shouldProcess());
            verify(repository).isProcessedAndMark(command.getTransactionId());
        }
    }

    @Nested
    class OutboxMarkingConsumerTests {

        @Mock
        private TransactionalOutboxRepository repository;

        @InjectMocks
        private OutboxMarkingConsumer outboxMarkingConsumer;

        @Test
        void onEvent_whenShouldProcessIsTrue_shouldMarkCommandAsProcessed() throws Exception {
            event.setShouldProcess(true);
            event.setCommand(command);

            outboxMarkingConsumer.onEvent(event, 1L, true);

            verify(repository).markAsProcessed(command);
        }

        @Test
        void onEvent_whenShouldProcessIsFalse_shouldDoNothing() throws Exception {
            event.setShouldProcess(false);
            event.setCommand(command); 

            outboxMarkingConsumer.onEvent(event, 1L, true);

            verify(repository, never()).markAsProcessed(any(TransactionCommand.class));
        }
    }
}
