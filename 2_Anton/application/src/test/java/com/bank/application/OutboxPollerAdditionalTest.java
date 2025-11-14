package com.bank.application;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEventProducer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerAdditionalTest {

    @Mock private TransactionalOutboxRepository outboxRepository;
    @Mock private TransactionEventProducer producer;

    @InjectMocks
    private OutboxPoller outboxPoller;

    private Thread pollerThread;
    
    private final TransactionCommand command1 = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);

    @BeforeEach
    void setUp() {
        pollerThread = new Thread(outboxPoller);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (pollerThread != null && pollerThread.isAlive()) {
            outboxPoller.stop();
            pollerThread.interrupt();
            pollerThread.join(500);
        }
    }

    @Test
    void run_shouldMarkAsProcessed_strictlyAfterSuccessfulPublish() {
        when(producer.publish(command1)).thenReturn(CompletableFuture.completedFuture(null));
        when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
            .thenReturn(List.of(command1))
            .thenReturn(Collections.emptyList());

        pollerThread.start();
        
        InOrder inOrder = Mockito.inOrder(producer, outboxRepository);
        
        inOrder.verify(producer, timeout(500)).publish(command1);
        inOrder.verify(outboxRepository, timeout(500)).markAsProcessed(command1);
    }
}
