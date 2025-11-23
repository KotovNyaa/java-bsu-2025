package com.bank.application;

import com.bank.application.config.DataSourceConfig;
import com.bank.application.engine.IdempotencyCheckConsumer;
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
import com.bank.core.engine.consumers.BatchDatabasePersistenceConsumer;
import com.bank.core.engine.consumers.BusinessLogicConsumer;
import com.bank.core.port.out.BatchPersister;
import com.bank.core.state.AccountState;
import com.bank.core.state.AccountStateProvider;
import com.bank.domain.Account;
import com.bank.persistence.repository.AccountRepository;
import com.bank.persistence.repository.impl.JdbcAccountRepository;
import com.bank.persistence.repository.impl.JdbcBatchPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lmax.disruptor.YieldingWaitStrategy;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BankApplication {
    private static final Logger log = LoggerFactory.getLogger(BankApplication.class);

    private final TransactionRingBuffer ringBuffer;
    private final TransactionService transactionService;
    private final OutboxPoller outboxPoller;
    private final ExecutorService pollerExecutor = Executors.newSingleThreadExecutor();
    private final IdempotencyCheckConsumer idempotencyConsumer;

    public BankApplication() {
        this(DataSourceConfig.createDataSource());
    }

    public BankApplication(DataSource dataSource) {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        TransactionalOutboxRepository outboxRepository = new JdbcTransactionalOutboxRepository(dataSource,
                transactionTemplate);
        ProcessedTransactionRepository processedRepo = new JdbcProcessedTransactionRepository(dataSource);
        BatchPersister batchPersister = new JdbcBatchPersister(dataSource, transactionTemplate);

        TransactionStatusProvider statusProvider = idempotencyKey -> TransactionStatus.PENDING;
        AccountStateProvider stateProvider = restoreState(dataSource);
        TransactionActionFactory actionFactory = new TransactionActionFactory();

        this.idempotencyConsumer = new IdempotencyCheckConsumer();

        Set<UUID> existingKeys = processedRepo.loadAllProcessedKeys();
        this.idempotencyConsumer.initializeCache(existingKeys);

        BusinessLogicConsumer businessLogicConsumer = new BusinessLogicConsumer(stateProvider, actionFactory);
        BatchDatabasePersistenceConsumer persistenceConsumer = new BatchDatabasePersistenceConsumer(batchPersister);

        this.ringBuffer = new TransactionRingBuffer(
                new com.lmax.disruptor.SleepingWaitStrategy(),
                null,
                null,
                null);

        ringBuffer.getDisruptor()
                .handleEventsWith(this.idempotencyConsumer)
                .then(businessLogicConsumer)
                .then(persistenceConsumer);

        outboxRepository.resetProcessingToPending();
        TransactionEventProducer producer = new TransactionEventProducer(ringBuffer.getRingBuffer());
        this.outboxPoller = new OutboxPoller(outboxRepository, producer);
        this.transactionService = new TransactionServiceImpl(stateProvider, statusProvider, outboxRepository);
    }

    private AccountStateProvider restoreState(DataSource dataSource) {
        log.info("Starting state restoration from database...");
        AccountRepository accountRepository = new JdbcAccountRepository(dataSource);
        Map<UUID, Account> allAccounts = accountRepository.loadAllAccounts();
        log.info("Loaded {} accounts from the database.", allAccounts.size());

        AccountState singletonInstance = AccountState.INSTANCE;
        singletonInstance.loadAll(allAccounts);
        log.info("State restoration complete.");
        return singletonInstance;
    }

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
            if (!pollerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Poller executor did not terminate in 5 seconds. Forcing shutdown.");
                pollerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for poller to stop. Forcing shutdown.", e);
            pollerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        ringBuffer.stop();
        log.info("Bank Application stopped.");
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }

    public void clearIdempotencyCache() {
        if (this.idempotencyConsumer != null) {
            this.idempotencyConsumer.clearCache();
        }
    }
}
