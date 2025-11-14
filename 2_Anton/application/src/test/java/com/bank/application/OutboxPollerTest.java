package com.bank.application;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEventProducer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerAdvancedTest {

    @Mock
    private TransactionalOutboxRepository outboxRepository;

    @Mock
    private TransactionEventProducer producer;

    @InjectMocks
    private OutboxPoller outboxPoller;

    private Thread pollerThread;

    private final TransactionCommand command1 = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
    private final TransactionCommand command2 = TransactionCommand.createWithdrawCommand(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE);

    @BeforeEach
    void setUp() {
        pollerThread = new Thread(outboxPoller);
        pollerThread.start();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        outboxPoller.stop();
        pollerThread.interrupt();
        pollerThread.join(500);
    }

    @Nested
    class OperationOrderTests {

        @Test
        void run_shouldMarkAsProcessed_afterFutureCompletesSuccessfully() {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            when(producer.publish(command1)).thenReturn(completableFuture);
            when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
                .thenReturn(List.of(command1)) // Возвращаем команду один раз
                .thenReturn(Collections.emptyList()); // Затем возвращаем пустоту, чтобы цикл простаивал

            verify(producer, timeout(500)).publish(command1);
            verify(outboxRepository, never()).markAsProcessed(any());

            completableFuture.complete(null);

            verify(outboxRepository, timeout(500)).markAsProcessed(command1);
            verify(outboxRepository, never()).incrementFailureCount(any());
        }

        @Test
        void run_shouldNotMarkAsProcessed_whenFutureFails() {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            when(producer.publish(command1)).thenReturn(completableFuture);
            when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
                .thenReturn(List.of(command1))
                .thenReturn(Collections.emptyList());
            when(outboxRepository.getFailureCount(any())).thenReturn(1);

            verify(producer, timeout(500)).publish(command1);
            
            completableFuture.completeExceptionally(new RuntimeException("Test failure"));

            verify(outboxRepository, timeout(500)).incrementFailureCount(command1.getTransactionId());
            verify(outboxRepository, never()).markAsProcessed(any());
        }
    }

    @Nested
    class EdgeCaseTests {
        
        @Test
        void run_whenMixedBatch_shouldProcessEachCommandIndependently() {
            CompletableFuture<Void> successFuture = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> failedFuture = CompletableFuture.failedFuture(new RuntimeException("Failure"));

            when(producer.publish(command1)).thenReturn(successFuture);
            when(producer.publish(command2)).thenReturn(failedFuture);
            when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
                .thenReturn(List.of(command1, command2))
                .thenReturn(Collections.emptyList());
            when(outboxRepository.getFailureCount(command2.getTransactionId())).thenReturn(1);

            verify(outboxRepository, timeout(500)).markAsProcessed(command1);
            verify(outboxRepository, never()).incrementFailureCount(command1.getTransactionId());

            verify(outboxRepository, timeout(500)).incrementFailureCount(command2.getTransactionId());
            verify(outboxRepository, never()).markAsProcessed(command2);
        }
    }
}
