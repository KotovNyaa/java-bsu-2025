package com.bank.core.engine;

import com.lmax.disruptor.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Логи
 */

public class Slf4jExceptionHandler implements ExceptionHandler<TransactionEvent> {
    private static final Logger log = LoggerFactory.getLogger(Slf4jExceptionHandler.class);

    @Override
    public void handleEventException(Throwable ex, long sequence, TransactionEvent event) {
        log.error("CRITICAL DISRUPTOR EXCEPTION processing sequence {}, event: {}", sequence, event, ex);
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        log.error("CRITICAL DISRUPTOR EXCEPTION on start.", ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        log.error("CRITICAL DISRUPTOR EXCEPTION on shutdown.", ex);
    }
}
