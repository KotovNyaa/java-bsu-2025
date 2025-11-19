package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.core.exception.InsufficientFundsException;
import com.bank.domain.Account;
import com.bank.domain.AccountStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TransferActionImplTest {

    @Mock
    private Account sourceAccount;
    @Mock
    private Account targetAccount;

    @InjectMocks
    private TransferActionImpl transferAction;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void execute_shouldWithdrawAndDepositOnSuccessfulTransfer() throws InsufficientFundsException {
        TransactionCommand command = TransactionCommand.createTransferCommand(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("100"));
        when(sourceAccount.getBalance()).thenReturn(new BigDecimal("200"));

        when(sourceAccount.getId()).thenReturn(UUID.randomUUID());
        when(targetAccount.getId()).thenReturn(UUID.randomUUID());
        when(sourceAccount.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(targetAccount.getStatus()).thenReturn(AccountStatus.ACTIVE);

        transferAction.execute(sourceAccount, targetAccount, command);

        verify(sourceAccount).withdraw(new BigDecimal("100"));
        verify(targetAccount).deposit(new BigDecimal("100"));
    }

    @Test
    void execute_shouldBeAtomicAndThrowExceptionOnInsufficientFunds() {
        TransactionCommand command = TransactionCommand.createTransferCommand(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("300"));
        when(sourceAccount.getBalance()).thenReturn(new BigDecimal("200"));

        when(sourceAccount.getId()).thenReturn(UUID.randomUUID());
        when(targetAccount.getId()).thenReturn(UUID.randomUUID());
        when(sourceAccount.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(targetAccount.getStatus()).thenReturn(AccountStatus.ACTIVE);

        assertThrows(InsufficientFundsException.class, () -> {
            transferAction.execute(sourceAccount, targetAccount, command);
        });

        verify(sourceAccount, never()).withdraw(any());
        verify(targetAccount, never()).deposit(any());
    }

    @Test
    void should_throw_exception_when_transferring_from_frozen_account() {
        UUID fromAccountId = UUID.randomUUID();
        UUID toAccountId = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createTransferCommand(
                UUID.randomUUID(), fromAccountId, toAccountId, new BigDecimal("200"));

        when(sourceAccount.getStatus()).thenReturn(AccountStatus.FROZEN);

        when(sourceAccount.getId()).thenReturn(UUID.randomUUID());
        when(targetAccount.getId()).thenReturn(UUID.randomUUID());

        assertThrows(
                IllegalStateException.class,
                () -> transferAction.execute(sourceAccount, targetAccount, command),
                "Должно быть выброшено исключение при переводе с замороженного счета");

        verify(sourceAccount, never()).withdraw(any(BigDecimal.class));
        verify(targetAccount, never()).deposit(any(BigDecimal.class));
    }

    @Test
    void should_throw_illegal_argument_exception_for_transfer_to_same_account() {
        UUID sameAccountId = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createTransferCommand(
                UUID.randomUUID(), sameAccountId, sameAccountId, new BigDecimal("100"));

        when(sourceAccount.getId()).thenReturn(sameAccountId);

        assertThrows(
                IllegalArgumentException.class,
                () -> transferAction.execute(sourceAccount, sourceAccount, command),
                "Счет-отправитель и счет-получатель не могут совпадать");
    }
}
