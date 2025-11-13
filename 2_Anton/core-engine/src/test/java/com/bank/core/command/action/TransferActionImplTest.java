package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.core.domain.Account;
import com.bank.core.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferActionImplTest {

    @Mock
    private Account sourceAccount;
    @Mock
    private Account targetAccount;

    @InjectMocks
    private TransferActionImpl transferAction;

    @Test
    void execute_shouldWithdrawAndDepositOnSuccessfulTransfer() throws InsufficientFundsException {
        TransactionCommand command = TransactionCommand.createTransferCommand(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"));
        when(sourceAccount.getBalance()).thenReturn(new BigDecimal("200"));

        transferAction.execute(sourceAccount, targetAccount, command);

        verify(sourceAccount).withdraw(new BigDecimal("100"));
        verify(targetAccount).deposit(new BigDecimal("100"));
    }

    @Test
    void execute_shouldBeAtomicAndThrowExceptionOnInsufficientFunds() {
        TransactionCommand command = TransactionCommand.createTransferCommand(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("300"));
        when(sourceAccount.getBalance()).thenReturn(new BigDecimal("200"));

        assertThrows(InsufficientFundsException.class, () -> {
            transferAction.execute(sourceAccount, targetAccount, command);
        });

        verify(sourceAccount, never()).withdraw(any());
        verify(targetAccount, never()).deposit(any());
    }
}