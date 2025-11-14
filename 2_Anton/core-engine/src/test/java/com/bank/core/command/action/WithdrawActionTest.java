package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
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
class WithdrawActionTest {

    @Mock
    private Account account;

    @InjectMocks
    private WithdrawAction withdrawAction;

    @Test
    void execute_shouldCallWithdrawWhenFundsAreSufficient() throws InsufficientFundsException {
        TransactionCommand command = TransactionCommand.createWithdrawCommand(UUID.randomUUID(), new BigDecimal("50"));
        when(account.getBalance()).thenReturn(new BigDecimal("100"));

        withdrawAction.execute(account, command);

        verify(account).withdraw(new BigDecimal("50"));
    }

    @Test
    void execute_shouldThrowExceptionAndNotCallWithdrawWhenFundsAreInsufficient() {
        TransactionCommand command = TransactionCommand.createWithdrawCommand(UUID.randomUUID(), new BigDecimal("150"));
        when(account.getBalance()).thenReturn(new BigDecimal("100"));

        assertThrows(InsufficientFundsException.class, () -> {
            withdrawAction.execute(account, command);
        });

        verify(account, never()).withdraw(any());
    }
}
