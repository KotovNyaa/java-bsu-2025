package com.bank.core.command.factory;

import com.bank.core.command.ActionType;
import com.bank.core.command.action.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * Фабрика, предоставляющая экземпляры стратегий для выполнения транзакций
 */

public class TransactionActionFactory {

    private final Map<ActionType, SingleAccountAction> singleAccountActionMap;
    private final TransferAction transferAction;

    public TransactionActionFactory() {
        this.singleAccountActionMap = new EnumMap<>(ActionType.class);
        this.singleAccountActionMap.put(ActionType.DEPOSIT, new DepositAction());
        this.singleAccountActionMap.put(ActionType.WITHDRAW, new WithdrawAction());
        this.singleAccountActionMap.put(ActionType.FREEZE, new FreezeAction());
        this.singleAccountActionMap.put(ActionType.UNFREEZE, new UnfreezeAction());
        this.singleAccountActionMap.put(ActionType.CLOSE, new CloseAction());

        this.transferAction = new TransferActionImpl();
    }

    public SingleAccountAction getSingleAccountAction(ActionType actionType) {
        SingleAccountAction action = singleAccountActionMap.get(actionType);
        if (action == null) {
            throw new IllegalArgumentException("Unsupported single-account action type: " + actionType);
        }
        return action;
    }

    public TransferAction getTransferAction() {
        return transferAction;
    }
}
