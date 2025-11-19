package com.bank.core.command.action;

import com.bank.core.command.TransactionCommand;
import com.bank.domain.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UnfreezeActionTest {

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
    void should_call_activate_method_on_account_when_executed() {
        UnfreezeAction unfreezeAction = new UnfreezeAction();
        UUID idempotencyKey = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        TransactionCommand command = TransactionCommand.createUnfreezeCommand(idempotencyKey, accountId);

        unfreezeAction.execute(account, command);

        verify(account, times(1)).activate();
    }
}
