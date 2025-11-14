package com.bank.core.engine.consumers;

import com.bank.core.command.TransactionCommand;
import com.bank.core.command.action.SingleAccountAction;
import com.bank.core.command.action.TransferAction;
import com.bank.core.command.ActionType;
import com.bank.core.command.factory.TransactionActionFactory;
import com.bank.core.engine.TransactionEvent;
import com.bank.core.state.AccountStateProvider;
import com.bank.domain.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessLogicConsumerTest {

    @Mock private AccountStateProvider stateProvider;
    @Mock private TransactionActionFactory actionFactory;
    @Mock private SingleAccountAction mockSingleAccountAction;
    @Mock private TransferAction mockTransferAction;

    @InjectMocks
    private BusinessLogicConsumer businessLogicConsumer;

    private TransactionEvent event;
    
    private final UUID fromAccountId = UUID.randomUUID();
    private final UUID toAccountId = UUID.randomUUID();
    private final Account fromAccount = new Account(fromAccountId, new BigDecimal("1000"));
    private final Account toAccount = new Account(toAccountId, new BigDecimal("500"));


    @BeforeEach
    void setUp() {
        event = new TransactionEvent();
    }

    @Nested
    class SingleAccountActionTests {

        @Test
        void onEvent_whenDepositCommand_shouldLoadAccountAndExecute() throws Exception {
            TransactionCommand depositCommand = TransactionCommand.createDepositCommand(UUID.randomUUID(), fromAccountId, BigDecimal.TEN);
            event.setCommand(depositCommand);
            event.setShouldProcess(true);

            when(actionFactory.getSingleAccountAction(ActionType.DEPOSIT)).thenReturn(mockSingleAccountAction);
            when(stateProvider.getAccount(fromAccountId)).thenReturn(fromAccount);

            businessLogicConsumer.onEvent(event, 1L, true);

            verify(stateProvider).getAccount(fromAccountId);
            verify(mockSingleAccountAction).execute(fromAccount, depositCommand);
        }
    }

    @Nested
    class TransferActionTests {

        @Test
        void onEvent_whenTransferCommand_shouldLoadBothAccountsAndExecute() throws Exception {
            TransactionCommand transferCommand = TransactionCommand.createTransferCommand(UUID.randomUUID(), fromAccountId, toAccountId, BigDecimal.TEN);
            event.setCommand(transferCommand);
            event.setShouldProcess(true);

            when(actionFactory.getTransferAction()).thenReturn(mockTransferAction);
            when(stateProvider.getAccount(fromAccountId)).thenReturn(fromAccount);
            when(stateProvider.getAccount(toAccountId)).thenReturn(toAccount);

            businessLogicConsumer.onEvent(event, 1L, true);

            verify(stateProvider).getAccount(fromAccountId);
            verify(stateProvider).getAccount(toAccountId);
            verify(mockTransferAction).execute(fromAccount, toAccount, transferCommand);
        }
    }

    @Nested
    class GeneralScenarios {

        @Test
        void onEvent_whenShouldNotProcess_shouldDoNothing() throws Exception {
            TransactionCommand anyCommand = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
            event.setCommand(anyCommand);
            event.setShouldProcess(false);

            businessLogicConsumer.onEvent(event, 1L, true);

            verifyNoInteractions(actionFactory, stateProvider);
        }
    }
}
