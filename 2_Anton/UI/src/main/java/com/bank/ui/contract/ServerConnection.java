package com.bank.ui.contract;

import com.bank.api.dto.CommandResponse;
import com.bank.api.dto.SystemStateDto;
import java.math.BigDecimal;
import java.util.UUID;

public interface ServerConnection {
    CommandResponse createUser(String nickname);
    CommandResponse createAccount(UUID userId);
    
    CommandResponse deposit(UUID accountId, BigDecimal amount);
    CommandResponse withdraw(UUID accountId, BigDecimal amount);
    CommandResponse transfer(UUID from, UUID to, BigDecimal amount);
    
    CommandResponse freeze(UUID accountId);
    CommandResponse unfreeze(UUID accountId);
    CommandResponse close(UUID accountId);
    
    SystemStateDto getSystemState();
    
    void disconnect();
    void kill(); 
    CommandResponse reset(); 

    void addObserver(TransactionObserver observer);
    void setBlocked(boolean blocked);
    void flushBuffer(); 
    boolean isBlocked();
}
