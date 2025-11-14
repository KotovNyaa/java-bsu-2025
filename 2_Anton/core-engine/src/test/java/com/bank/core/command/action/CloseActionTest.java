package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CloseActionTest {

    @Mock
    private Account account;

    @Test
    void should_call_close_method_on_account_when_executed() {
        CloseAction closeAction = new CloseAction();
        TransactionCommand command = TransactionCommand.createCloseCommand(UUID.randomUUID());

        closeAction.execute(account, command);

        verify(account, times(1)).close();
    }
}
