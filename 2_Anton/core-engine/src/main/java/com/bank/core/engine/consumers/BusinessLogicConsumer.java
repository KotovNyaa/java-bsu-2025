package com.bank.core.engine.consumers;

import com.bank.core.command.ActionType;
import com.bank.core.command.TransactionCommand;
import com.bank.core.command.action.SingleAccountAction;
import com.bank.core.command.action.TransferAction;
import com.bank.core.command.factory.TransactionActionFactory;
import com.bank.core.domain.Account;
import com.bank.core.engine.TransactionEvent;
import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.exception.InsufficientFundsException;
import com.bank.core.state.AccountStateProvider;
import com.lmax.disruptor.EventHandler;

/**
 * Обработчик, выполняющий бизнес-логику
 */

public class BusinessLogicConsumer implements EventHandler<TransactionEvent> {

    private final AccountStateProvider accountState;
    private final TransactionActionFactory actionFactory;

    public BusinessLogicConsumer(AccountStateProvider accountState, TransactionActionFactory actionFactory) {
        this.accountState = accountState;
        this.actionFactory = actionFactory;
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        try {
            TransactionCommand command = event.getCommand();
            ActionType type = command.getActionType();

            if (type == ActionType.TRANSFER) {
                TransferAction action = actionFactory.getTransferAction();
                Account sourceAccount = accountState.getAccount(command.getAccountId());
                Account targetAccount = accountState.getAccount(command.getTargetAccountId());
                action.execute(sourceAccount, targetAccount, command);
            } else {
                SingleAccountAction action = actionFactory.getSingleAccountAction(type);
                Account account = accountState.getAccount(command.getAccountId());
                action.execute(account, command);
            }

        } catch (AccountNotFoundException | InsufficientFundsException e) {
            System.err.println("Failed to process transaction " + event.getCommand().getTransactionId() + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Critical business logic error on sequence " + sequence + ": " + e.getMessage());
            throw new RuntimeException("Unhandled exception in business logic consumer", e);
        } finally {
            event.clear();
        }
    }
}
