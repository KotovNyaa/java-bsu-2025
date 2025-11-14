package com.bank.application.service.impl;

import com.bank.core.command.TransactionCommand;
import com.bank.application.port.out.TransactionStatusProvider;
import com.bank.core.command.ActionType;
import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.state.AccountStateProvider;
import com.bank.application.port.out.TransactionalOutboxRepository;
import com.bank.domain.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private AccountStateProvider stateProvider;

    @Mock
    private TransactionalOutboxRepository outboxRepository;

    @Mock
    private TransactionStatusProvider statusProvider;
    @Mock
    private com.bank.application.visitor.ReportVisitor reportVisitor; // Используем полное имя, чтобы избежать 
  
    @InjectMocks
    private TransactionServiceImpl transactionService;

    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TARGET_ACCOUNT_ID = UUID.randomUUID();
    private static final BigDecimal POSITIVE_AMOUNT = new BigDecimal("100.50");

    @Nested
    class HappyPathTests {

        @Test
        void deposit_whenValid_shouldSaveDepositCommand() {
            ArgumentCaptor<TransactionCommand> commandCaptor = ArgumentCaptor.forClass(TransactionCommand.class);

            transactionService.deposit(IDEMPOTENCY_KEY, ACCOUNT_ID, POSITIVE_AMOUNT);

            verify(outboxRepository).save(commandCaptor.capture());
            TransactionCommand captured = commandCaptor.getValue();

            assertNotNull(captured);
            assertEquals(IDEMPOTENCY_KEY, captured.getIdempotencyKey());
            assertEquals(ACCOUNT_ID, captured.getAccountId());
            assertEquals(POSITIVE_AMOUNT, captured.getAmount());
            assertEquals(ActionType.DEPOSIT, captured.getActionType());
            assertNull(captured.getTargetAccountId());
        }

        @Test
        void withdraw_whenValid_shouldSaveWithdrawCommand() {
            ArgumentCaptor<TransactionCommand> commandCaptor = ArgumentCaptor.forClass(TransactionCommand.class);

            transactionService.withdraw(IDEMPOTENCY_KEY, ACCOUNT_ID, POSITIVE_AMOUNT);

            verify(outboxRepository).save(commandCaptor.capture());
            TransactionCommand captured = commandCaptor.getValue();
            assertEquals(ActionType.WITHDRAW, captured.getActionType());
        }

        @Test
        void transfer_whenValid_shouldSaveTransferCommand() {
            ArgumentCaptor<TransactionCommand> commandCaptor = ArgumentCaptor.forClass(TransactionCommand.class);

            transactionService.transfer(IDEMPOTENCY_KEY, ACCOUNT_ID, TARGET_ACCOUNT_ID, POSITIVE_AMOUNT);

            verify(outboxRepository).save(commandCaptor.capture());
            TransactionCommand captured = commandCaptor.getValue();

            assertEquals(ActionType.TRANSFER, captured.getActionType());
            assertEquals(ACCOUNT_ID, captured.getAccountId());
            assertEquals(TARGET_ACCOUNT_ID, captured.getTargetAccountId());
        }

        @Test
        void freezeAccount_whenValid_shouldSaveFreezeCommand() {
            ArgumentCaptor<TransactionCommand> commandCaptor = ArgumentCaptor.forClass(TransactionCommand.class);

            transactionService.freezeAccount(IDEMPOTENCY_KEY, ACCOUNT_ID);

            verify(outboxRepository).save(commandCaptor.capture());
            TransactionCommand captured = commandCaptor.getValue();

            assertEquals(ActionType.FREEZE, captured.getActionType());
            assertNull(captured.getAmount());
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void anyCommand_whenIdempotencyKeyIsNull_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> {
                transactionService.deposit(null, ACCOUNT_ID, POSITIVE_AMOUNT);
            });
            verify(outboxRepository, never()).save(any());
        }

        @Test
        void moneyOperation_whenAmountIsNegative_shouldThrowException() {
            BigDecimal negativeAmount = new BigDecimal("-50");
            assertThrows(IllegalArgumentException.class, () -> {
                transactionService.withdraw(IDEMPOTENCY_KEY, ACCOUNT_ID, negativeAmount);
            });
            verify(outboxRepository, never()).save(any());
        }

        @Test
        void moneyOperation_whenAmountIsZero_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> {
                transactionService.deposit(IDEMPOTENCY_KEY, ACCOUNT_ID, BigDecimal.ZERO);
            });
            verify(outboxRepository, never()).save(any());
        }

        @Test
        void transfer_whenAccountsAreSame_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> {
                transactionService.transfer(IDEMPOTENCY_KEY, ACCOUNT_ID, ACCOUNT_ID, POSITIVE_AMOUNT);
            });
            verify(outboxRepository, never()).save(any());
        }
    }

    @Nested
    class QueryMethodTests {

        @Test
        void getBalance_whenAccountExists_shouldReturnBalance() throws AccountNotFoundException {
            Account mockAccount = new Account(ACCOUNT_ID, POSITIVE_AMOUNT);
            when(stateProvider.getAccount(ACCOUNT_ID)).thenReturn(mockAccount);

            BigDecimal balance = transactionService.getBalance(ACCOUNT_ID);

            assertEquals(POSITIVE_AMOUNT, balance);
            verify(stateProvider).getAccount(ACCOUNT_ID);
        }

        @Test
        void getBalance_whenAccountNotFound_shouldThrowException() throws AccountNotFoundException {
            when(stateProvider.getAccount(any(UUID.class))).thenThrow(new AccountNotFoundException("Account not found"));

            assertThrows(AccountNotFoundException.class, () -> {
                transactionService.getBalance(UUID.randomUUID());
            });
        }
    }

    @Nested
    class AdditionalHappyPathTests {

        @Test
        void unfreezeAccount_whenValid_shouldSaveUnfreezeCommand() {
            ArgumentCaptor<TransactionCommand> commandCaptor = ArgumentCaptor.forClass(TransactionCommand.class);
            
            transactionService.unfreezeAccount(IDEMPOTENCY_KEY, ACCOUNT_ID);

            verify(outboxRepository).save(commandCaptor.capture());
            assertEquals(ActionType.UNFREEZE, commandCaptor.getValue().getActionType());
        }

        @Test
        void closeAccount_whenValid_shouldSaveCloseCommand() {
            ArgumentCaptor<TransactionCommand> commandCaptor = ArgumentCaptor.forClass(TransactionCommand.class);

            transactionService.closeAccount(IDEMPOTENCY_KEY, ACCOUNT_ID);

            verify(outboxRepository).save(commandCaptor.capture());
            assertEquals(ActionType.CLOSE, commandCaptor.getValue().getActionType());
        }
    }


    @Nested
    class AdditionalValidationTests {

        @Test
        void anyCommand_whenAccountIdIsNull_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> {
                transactionService.deposit(IDEMPOTENCY_KEY, null, POSITIVE_AMOUNT);
            });
            verify(outboxRepository, never()).save(any());
        }

        @Test
        void moneyOperation_whenAmountIsNull_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> {
                transactionService.withdraw(IDEMPOTENCY_KEY, ACCOUNT_ID, null);
            });
            verify(outboxRepository, never()).save(any());
        }

        @Test
        void transfer_whenToAccountIsNull_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> {
                transactionService.transfer(IDEMPOTENCY_KEY, ACCOUNT_ID, null, POSITIVE_AMOUNT);
            });
            verify(outboxRepository, never()).save(any());
        }
    }


    @Nested
    class StatusAndReportApiTests {

        @Test
        void getTransactionStatus_whenKeyIsValid_shouldCallProviderAndReturnStatus() {
            when(statusProvider.findStatusByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(com.bank.application.service.TransactionStatus.COMPLETED);

            com.bank.application.service.TransactionStatus status = transactionService.getTransactionStatus(IDEMPOTENCY_KEY);

            assertEquals(com.bank.application.service.TransactionStatus.COMPLETED, status);
            verify(statusProvider).findStatusByIdempotencyKey(IDEMPOTENCY_KEY);
        }

        @Test
        void getTransactionStatus_whenKeyIsNull_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> {
                transactionService.getTransactionStatus(null);
            });
        }

        @Test
        void generateReport_whenValid_shouldCallVisitor() throws AccountNotFoundException {
            Account mockAccount = new Account(ACCOUNT_ID, POSITIVE_AMOUNT);
            when(stateProvider.getAccount(ACCOUNT_ID)).thenReturn(mockAccount);
            when(reportVisitor.visit(mockAccount)).thenReturn("FINAL REPORT");

            String report = transactionService.generateReport(ACCOUNT_ID, reportVisitor);

            assertEquals("FINAL REPORT", report);
            verify(stateProvider).getAccount(ACCOUNT_ID);
            verify(reportVisitor).visit(mockAccount);
        }

        @Test
        void generateReport_whenVisitorIsNull_shouldThrowException() {
            assertThrows(NullPointerException.class, () -> {
                transactionService.generateReport(ACCOUNT_ID, null);
            });
        }
    }
}
