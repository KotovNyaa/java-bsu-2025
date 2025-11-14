package com.bank.core.command.action;

import com.bank.domain.AccountStatus;
import org.junit.jupiter.api.DisplayName;
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
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);

        withdrawAction.execute(account, command);

        verify(account).withdraw(new BigDecimal("50"));
    }

    @Test
    void execute_shouldThrowExceptionAndNotCallWithdrawWhenFundsAreInsufficient() {
        TransactionCommand command = TransactionCommand.createWithdrawCommand(UUID.randomUUID(), new BigDecimal("150"));
        when(account.getBalance()).thenReturn(new BigDecimal("100"));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        
        assertThrows(InsufficientFundsException.class, () -> {
            withdrawAction.execute(account, command);
        });

        verify(account, never()).withdraw(any());
    }

    @Test
    void should_throw_exception_when_attempting_to_withdraw_from_frozen_account() {
        UUID accountId = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createWithdrawCommand(accountId, new BigDecimal("100"));

        when(account.getStatus()).thenReturn(AccountStatus.FROZEN);

        assertThrows(
            IllegalStateException.class,
            () -> withdrawAction.execute(account, command),
            "Должно быть выброшено исключение при операции с замороженным счетом"
        );

        verify(account, never()).withdraw(any(BigDecimal.class));
    }

    @Test
    void should_throw_illegal_argument_exception_for_negative_amount() {
        TransactionCommand command = TransactionCommand.createWithdrawCommand(
            UUID.randomUUID(), new BigDecimal("-100")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> withdrawAction.execute(account, command),
            "Сумма для снятия не может быть отрицательной"
        );
    }
}
