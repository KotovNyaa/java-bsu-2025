package com.bank.core.domain;

import java.util.List;
import java.util.UUID;

/**
 * POJO пользователя системы
 */

public class User {
    private final UUID id;
    private String nickname;
    private List<UUID> accountIds;

    public User(UUID id, String nickname, List<UUID> accountIds) {
        this.id = id;
        this.nickname = nickname;
        this.accountIds = accountIds;
    }

    public UUID getId() { return id; }
    public String getNickname() { return nickname; }
    public List<UUID> getAccountIds() { return accountIds; }
}
