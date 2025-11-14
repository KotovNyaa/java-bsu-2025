package com.bank.core.engine;

import com.bank.core.command.TransactionCommand;
import com.lmax.disruptor.EventFactory;

/**
 * Событие, содержащее команду транзакции. Единица данных в RingBuffer
 */

public final class TransactionEvent {

    private TransactionCommand command;
    private boolean shouldProcess = true; 

    public TransactionCommand getCommand() {
        return command;
    }

    public void setCommand(TransactionCommand command) {
        this.command = command;
    }

    public boolean shouldProcess() {
        return shouldProcess;
    }

    public void setShouldProcess(boolean shouldProcess) {
        this.shouldProcess = shouldProcess;
    }

    public void clear() {
        this.command = null;
        this.shouldProcess = true; 
    }

    public static final EventFactory<TransactionEvent> EVENT_FACTORY = TransactionEvent::new;
}
