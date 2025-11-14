package com.bank.application;

import com.bank.application.config.DataSourceConfig;
import com.bank.application.engine.IdempotencyCheckConsumer;
import com.bank.application.engine.OutboxMarkingConsumer;
import com.bank.application.port.out.ProcessedTransactionRepository;
import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.application.port.out.TransactionStatusProvider;
import com.bank.application.service.TransactionService;
import com.bank.application.service.TransactionStatus;
import com.bank.application.service.impl.JdbcProcessedTransactionRepository;
import com.bank.application.service.impl.JdbcTransactionalOutboxRepository;
import com.bank.application.service.impl.TransactionServiceImpl;
import com.bank.core.command.factory.TransactionActionFactory;
import com.bank.core.engine.TransactionEventProducer;
import com.bank.core.engine.TransactionRingBuffer;
import com.bank.core.engine.consumers.BusinessLogicConsumer;
import com.bank.core.engine.consumers.JournalingConsumer;
import com.bank.core.engine.consumers.ReplicationConsumer;
import com.bank.core.state.AccountStateProvider;
import com.lmax.disruptor.BlockingWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Сборка системы
 */

public class BankApplication {
    private static final Logger log = LoggerFactory.getLogger(BankApplication.class);

    private final TransactionRingBuffer ringBuffer;
    private final TransactionService transactionService;
    private final OutboxPoller outboxPoller;
    private final ExecutorService pollerExecutor = Executors.newSingleThreadExecutor();

    public BankApplication() {
        DataSource dataSource = DataSourceConfig.createDataSource();
        TransactionalOutboxRepository outboxRepository = new JdbcTransactionalOutboxRepository(dataSource);
        ProcessedTransactionRepository processedRepo = new JdbcProcessedTransactionRepository(dataSource);
        TransactionStatusProvider statusProvider = idempotencyKey -> TransactionStatus.PENDING;
        AccountStateProvider stateProvider = restoreState(null, null, null);
        TransactionActionFactory actionFactory = new TransactionActionFactory();
        
        IdempotencyCheckConsumer idempotencyConsumer = new IdempotencyCheckConsumer(processedRepo);
        JournalingConsumer journalingConsumer = new JournalingConsumer();
        ReplicationConsumer replicationConsumer = new ReplicationConsumer.NoOpReplicationConsumer();
        BusinessLogicConsumer businessLogicConsumer = new BusinessLogicConsumer(stateProvider, actionFactory);
        OutboxMarkingConsumer outboxMarkingConsumer = new OutboxMarkingConsumer(outboxRepository);

        this.ringBuffer = new TransactionRingBuffer(
                new BlockingWaitStrategy(),
                journalingConsumer,
                replicationConsumer,
                businessLogicConsumer
        );

        ringBuffer.getDisruptor()
                .handleEventsWith(idempotencyConsumer)
                .then(journalingConsumer, replicationConsumer)
                .then(businessLogicConsumer)
                .then(outboxMarkingConsumer); 

        TransactionEventProducer producer = new TransactionEventProducer(ringBuffer.getRingBuffer());
        this.outboxPoller = new OutboxPoller(outboxRepository, producer);
        this.transactionService = new TransactionServiceImpl(stateProvider, statusProvider, outboxRepository);
    }
    
    private AccountStateProvider restoreState(Object a, Object b, Object c) { return null; }

    public void start() {
        log.info("Starting Bank Application...");
        ringBuffer.start();
        pollerExecutor.submit(outboxPoller);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        log.info("Bank Application started successfully.");
    }

    public void stop() {
        log.info("Stopping Bank Application (Graceful Shutdown)...");
        outboxPoller.stop();
        pollerExecutor.shutdown();
        try {
            if (!pollerExecutor.awaitTermination(5, TimeUnit.SECONDS)) pollerExecutor.shutdownNow();
        } catch (InterruptedException e) {
            pollerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ringBuffer.stop();
        log.info("Bank Application stopped.");
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }
}
