package com.bank.application.service.impl;

import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.application.port.out.TransactionStatusProvider;
import com.bank.application.service.TransactionStatus;
import com.bank.application.visitor.ReportVisitor;
import com.bank.core.command.ActionType;
import com.bank.core.state.AccountStateProvider;
import com.bank.domain.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplAdditionalTest {

    @Mock private AccountStateProvider stateProvider;
    @Mock private TransactionStatusProvider statusProvider;
    @Mock private TransactionalOutboxRepository outboxRepository;
    @Mock private ReportVisitor reportVisitor;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TARGET_ACCOUNT_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("100");

    @Test
    void unfreezeAccount_shouldCreateUnfreezeCommand() {
        transactionService.unfreezeAccount(IDEMPOTENCY_KEY, ACCOUNT_ID);
        verify(outboxRepository).save(argThat(cmd -> cmd.getActionType() == ActionType.UNFREEZE));
    }

    @Test
    void closeAccount_shouldCreateCloseCommand() {
        transactionService.closeAccount(IDEMPOTENCY_KEY, ACCOUNT_ID);
        verify(outboxRepository).save(argThat(cmd -> cmd.getActionType() == ActionType.CLOSE));
    }

    @Test
    void transfer_whenToAccountIdIsNull_shouldThrowException() {
        assertThrows(NullPointerException.class, () ->
            transactionService.transfer(IDEMPOTENCY_KEY, ACCOUNT_ID, null, AMOUNT)
        );
        verify(outboxRepository, never()).save(any());
    }
    
    @Test
    void getTransactionStatus_shouldReturnStatusFromProvider() {
        when(statusProvider.findStatusByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(TransactionStatus.COMPLETED);
        
        TransactionStatus result = transactionService.getTransactionStatus(IDEMPOTENCY_KEY);
        
        assertEquals(TransactionStatus.COMPLETED, result);
        verify(statusProvider).findStatusByIdempotencyKey(IDEMPOTENCY_KEY);
    }

    @Test
    void generateReport_shouldCallVisitor() throws Exception {
        Account mockAccount = new Account(ACCOUNT_ID, AMOUNT);
        when(stateProvider.getAccount(ACCOUNT_ID)).thenReturn(mockAccount);
        when(reportVisitor.visit(mockAccount)).thenReturn("REPORT DATA");

        String report = transactionService.generateReport(ACCOUNT_ID, reportVisitor);
        
        assertEquals("REPORT DATA", report);
        verify(reportVisitor).visit(mockAccount);
    }
    
    @Test
    void generateReport_whenVisitorIsNull_shouldThrowException() {
        assertThrows(NullPointerException.class, () -> 
            transactionService.generateReport(ACCOUNT_ID, null)
        );
    }
}
