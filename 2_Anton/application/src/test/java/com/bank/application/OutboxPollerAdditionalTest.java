package com.bank.application;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEventProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerAdditionalTest {

    @Mock
    private TransactionalOutboxRepository outboxRepository;
    @Mock
    private TransactionEventProducer producer;

    @InjectMocks
    private OutboxPoller outboxPoller;

    private Thread pollerThread;

    private final TransactionCommand command1 = TransactionCommand.createDepositCommand(UUID.randomUUID(),
            UUID.randomUUID(), BigDecimal.TEN);

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
    void run_shouldPublishFetchedCommandsToProducer() {
        List<TransactionCommand> commandsToReturn = List.of(command1);
        when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
                .thenReturn(commandsToReturn)
                .thenReturn(Collections.emptyList());

        pollerThread.start();

        ArgumentCaptor<List<TransactionCommand>> captor = ArgumentCaptor.forClass(List.class);

        verify(producer, timeout(500).times(1)).publishBatch(captor.capture());

        List<TransactionCommand> capturedList = captor.getValue();
        assertThat(capturedList)
                .isNotNull()
                .hasSize(1)
                .containsExactly(command1);

        verify(producer, never()).publish(any(TransactionCommand.class));

        verify(outboxRepository, timeout(500).atLeastOnce()).fetchAndLockUnprocessed(anyInt());
    }
}
