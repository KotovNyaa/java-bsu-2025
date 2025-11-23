package com.bank.api;

import com.bank.api.dto.CommandResponse;
import com.bank.api.dto.SystemStateDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ApiIntegrationTest {

    private BankServerFacade facade;

    @BeforeEach
    void setUp() {
        facade = new BankServerFacade(true);
    }

    @AfterEach
    void tearDown() {
        if (facade != null) {
            facade.stop();
        }
    }

    @Test
    void shouldCreateUserAndAccountSuccessfully() {
        CommandResponse userResp = facade.createUser("TestUser");
        assertThat(userResp.success()).isTrue();
        UUID userId = (UUID) userResp.data();

        CommandResponse accResp = facade.createAccount(userId);
        assertThat(accResp.success()).isTrue();
        
        SystemStateDto state = facade.getSystemState();
        assertThat(state.users()).hasSize(1);
        assertThat(state.users().get(0).id()).isEqualTo(userId);
        assertThat(state.users().get(0).accounts()).hasSize(1);
        assertThat(state.users().get(0).accounts().get(0).balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldProcessDepositAsyncThroughApi() {
        UUID userId = (UUID) facade.createUser("RichUser").data();
        UUID accountId = (UUID) facade.createAccount(userId).data();

        BigDecimal amount = new BigDecimal("500.00");
        CommandResponse depositResp = facade.deposit(accountId, amount);

        assertThat(depositResp.success()).isTrue();
        assertThat(depositResp.message()).isEqualTo("Task accepted");

        await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            SystemStateDto state = facade.getSystemState();
            BigDecimal balance = state.users().get(0).accounts().get(0).balance();
            assertThat(balance).isEqualByComparingTo("500.00");
        });
    }
    
    @Test
    void shouldFailCreatingAccountForNonExistentUser() {
        CommandResponse resp = facade.createAccount(UUID.randomUUID());
        assertThat(resp.success()).isFalse();
        assertThat(resp.message()).contains("User not found");
    }
}
