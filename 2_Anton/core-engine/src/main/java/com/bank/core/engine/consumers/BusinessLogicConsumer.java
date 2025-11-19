package com.bank.core.engine.consumers;

import com.bank.core.command.ActionType;
import com.bank.core.command.TransactionCommand;
import com.bank.core.command.action.SingleAccountAction;
import com.bank.core.command.action.TransferAction;
import com.bank.core.command.factory.TransactionActionFactory;
import com.bank.core.engine.TransactionEvent;
import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.exception.InsufficientFundsException;
import com.bank.core.state.AccountStateProvider;
import com.bank.domain.Account;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик, выполняющий бизнес-логику
 */

public class BusinessLogicConsumer implements EventHandler<TransactionEvent> {

    private static final Logger log = LoggerFactory.getLogger(BusinessLogicConsumer.class);

    private final AccountStateProvider accountState;
    private final TransactionActionFactory actionFactory;

    public BusinessLogicConsumer(AccountStateProvider accountState,
            TransactionActionFactory actionFactory) {
        this.accountState = accountState;
        this.actionFactory = actionFactory;
    }

    @Override
    public void onEvent(TransactionEvent event, long sequence, boolean endOfBatch) {
        if (!event.shouldProcess()) {
            return;
        }

        try {
            TransactionCommand command = event.getCommand();
            ActionType type = command.getActionType();

            if (type == ActionType.TRANSFER) {
                TransferAction action = actionFactory.getTransferAction();
                Account sourceAccount = accountState.getAccount(command.getAccountId());
                Account targetAccount = accountState.getAccount(command.getTargetAccountId());

                action.execute(sourceAccount, targetAccount, command);

                event.addModifiedAccount(sourceAccount);
                event.addModifiedAccount(targetAccount);

            } else {
                SingleAccountAction action = actionFactory.getSingleAccountAction(type);
                Account account = accountState.getAccount(command.getAccountId());

                action.execute(account, command);

                event.addModifiedAccount(account);
            }

        } catch (AccountNotFoundException | InsufficientFundsException | IllegalStateException
                | IllegalArgumentException e) {
            log.warn("Business rule violation for transaction {}: {}", event.getCommand().getTransactionId(),
                    e.getMessage());
            event.setBusinessException(e);
        } catch (Exception e) {
            log.error("CRITICAL: Unhandled exception during business logic execution for transaction {}. Sequence: {}.",
                    event.getCommand().getTransactionId(), sequence, e);
            event.setBusinessException(e);
        }
    }
}
