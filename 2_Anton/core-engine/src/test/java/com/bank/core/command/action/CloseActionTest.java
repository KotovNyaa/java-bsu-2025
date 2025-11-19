package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CloseActionTest {

    @Mock
    private Account account;

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
    void should_call_close_method_on_account_when_executed() {
        CloseAction closeAction = new CloseAction();
        UUID idempotencyKey = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TransactionCommand command = TransactionCommand.createCloseCommand(idempotencyKey, accountId);

        closeAction.execute(account, command);

        verify(account, times(1)).close();
    }
}
