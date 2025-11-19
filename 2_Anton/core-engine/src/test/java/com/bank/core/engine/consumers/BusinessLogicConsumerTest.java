package com.bank.core.engine.consumers;

import com.bank.core.command.ActionType;
import com.bank.core.command.TransactionCommand;
import com.bank.core.command.action.SingleAccountAction;
import com.bank.core.command.action.TransferAction;
import com.bank.core.command.factory.TransactionActionFactory;
import com.bank.domain.Account;
import com.bank.core.engine.TransactionEvent;
import com.bank.core.exception.InsufficientFundsException;
import com.bank.core.state.AccountStateProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessLogicConsumerTest {

    @Mock
    private AccountStateProvider accountState;
    @Mock
    private TransactionActionFactory actionFactory;
    @Mock
    private SingleAccountAction singleAccountAction;
    @Mock
    private TransferAction transferAction;
    @Mock
    private Account account;
    @Mock
    private Account sourceAccount;
    @Mock
    private Account targetAccount;

    @InjectMocks
    private BusinessLogicConsumer consumer;

    private TransactionEvent event;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        event = new TransactionEvent();

        event.setShouldProcess(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void onEvent_shouldUseSingleAccountActionForDeposit() throws Exception {
        TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN);
        event.setCommand(command);

        when(actionFactory.getSingleAccountAction(ActionType.DEPOSIT)).thenReturn(singleAccountAction);
        when(accountState.getAccount(any(UUID.class))).thenReturn(account);

        consumer.onEvent(event, 1, true);

        verify(actionFactory).getSingleAccountAction(ActionType.DEPOSIT);
        verify(singleAccountAction).execute(account, command);
        verify(actionFactory, never()).getTransferAction();
    }

    @Test
    void onEvent_shouldUseTransferActionForTransfer() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        TransactionCommand command = TransactionCommand.createTransferCommand(UUID.randomUUID(), sourceId, targetId,
                BigDecimal.TEN);
        event.setCommand(command);

        when(actionFactory.getTransferAction()).thenReturn(transferAction);
        when(accountState.getAccount(sourceId)).thenReturn(sourceAccount);
        when(accountState.getAccount(targetId)).thenReturn(targetAccount);

        consumer.onEvent(event, 1, true);

        verify(actionFactory).getTransferAction();
        verify(transferAction).execute(sourceAccount, targetAccount, command);
        verify(actionFactory, never()).getSingleAccountAction(any());
    }

    @Test
    void onEvent_shouldNotThrowRuntimeExceptionOnBusinessException() throws Exception {
        TransactionCommand command = TransactionCommand.createWithdrawCommand(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN);
        event.setCommand(command);

        when(actionFactory.getSingleAccountAction(any())).thenReturn(singleAccountAction);
        when(accountState.getAccount(any())).thenReturn(account);
        doThrow(new InsufficientFundsException("test")).when(singleAccountAction).execute(any(), any());

        assertDoesNotThrow(() -> consumer.onEvent(event, 1, true));
    }

    @Test
    void onEvent_shouldSetExceptionInEvent_onUnexpectedError() throws Exception {
        TransactionCommand command = TransactionCommand.createDepositCommand(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN);
        event.setCommand(command);

        NullPointerException simulatedCrash = new NullPointerException("system crash");
        when(actionFactory.getSingleAccountAction(any())).thenReturn(singleAccountAction);
        when(accountState.getAccount(any())).thenReturn(account);
        doThrow(simulatedCrash).when(singleAccountAction).execute(any(), any());

        assertDoesNotThrow(() -> consumer.onEvent(event, 1, true));

        assertThat(event.getBusinessException())
                .isNotNull()
                .isInstanceOf(NullPointerException.class)
                .isEqualTo(simulatedCrash);
    }
}
