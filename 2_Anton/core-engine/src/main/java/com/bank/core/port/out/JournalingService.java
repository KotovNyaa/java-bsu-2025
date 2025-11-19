package com.bank.core.port.out;

import com.bank.core.command.TransactionCommand;

/**
 * порт для WAL
 */

public interface JournalingService {
    void log(TransactionCommand command);
}
