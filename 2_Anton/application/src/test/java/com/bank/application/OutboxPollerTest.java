package com.bank.application;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.core.command.TransactionCommand;
import com.bank.core.engine.TransactionEventProducer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
class OutboxPollerTest {

    @Mock
    private TransactionalOutboxRepository outboxRepository;
    @Mock
    private TransactionEventProducer producer;

    @Captor
    private ArgumentCaptor<List<TransactionCommand>> batchCaptor;

    @InjectMocks
    private OutboxPoller outboxPoller;

    private Thread pollerThread;

    private final TransactionCommand command1 = TransactionCommand.createDepositCommand(UUID.randomUUID(),
            UUID.randomUUID(), BigDecimal.TEN);
    private final TransactionCommand command2 = TransactionCommand.createWithdrawCommand(UUID.randomUUID(),
            UUID.randomUUID(), BigDecimal.ONE);

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
    void shouldPublishSingleFetchedCommand() {
        when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
                .thenReturn(List.of(command1))
                .thenReturn(Collections.emptyList());

        pollerThread.start();

        verify(producer, timeout(500).times(1)).publishBatch(batchCaptor.capture());

        List<TransactionCommand> capturedBatch = batchCaptor.getValue();
        assertThat(capturedBatch).isNotNull();
        assertThat(capturedBatch).hasSize(1);
        assertThat(capturedBatch).containsExactly(command1);

        verify(producer, never()).publish(any(TransactionCommand.class));
    }

    @Test
    void shouldPublishAllCommandsFromBatch() {
        List<TransactionCommand> commandBatch = List.of(command1, command2);
        when(outboxRepository.fetchAndLockUnprocessed(anyInt()))
                .thenReturn(commandBatch)
                .thenReturn(Collections.emptyList());

        pollerThread.start();

        verify(producer, timeout(500).times(1)).publishBatch(batchCaptor.capture());

        List<TransactionCommand> capturedBatch = batchCaptor.getValue();
        assertThat(capturedBatch).isNotNull();
        assertThat(capturedBatch).hasSize(2);
        assertThat(capturedBatch).containsExactlyInAnyOrder(command1, command2);

        verify(producer, never()).publish(any(TransactionCommand.class));
    }

    @Test
    void shouldNotPublishAnythingWhenRepoReturnsEmpty() {
        when(outboxRepository.fetchAndLockUnprocessed(anyInt())).thenReturn(Collections.emptyList());

        pollerThread.start();

        verify(producer, after(100).never()).publish(any(TransactionCommand.class));
        verify(producer, after(100).never()).publishBatch(anyList());
    }
}
