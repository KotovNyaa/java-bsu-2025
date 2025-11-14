package com.bank.application.visitor;

import com.bank.domain.Account;

/**
 * Интерфейс ReportVisitor
 */

public interface ReportVisitor {
    String visit(Account account);
}
