package com.bank.core.engine;

import com.bank.core.command.TransactionCommand;
import com.lmax.disruptor.EventFactory;

/**
 * Событие, содержащее команду транзакции. Единица данных в RingBuffer
 */

public final class TransactionEvent {

    private TransactionCommand command;

    public TransactionCommand getCommand() {
        return command;
    }

    public void setCommand(TransactionCommand command) {
        this.command = command;
    }

    public void clear() {
        this.command = null;
    }

    public static final EventFactory<TransactionEvent> EVENT_FACTORY = TransactionEvent::new;
}
