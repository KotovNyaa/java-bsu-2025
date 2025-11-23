package com.bank.ui.cli.session;

import java.util.UUID;

public class SessionContext {
    private UUID activeUserId;
    private String activeUserNickname;
    private UUID activeAccountId;

    public void setActiveUser(UUID id, String nickname) {
        this.activeUserId = id;
        this.activeUserNickname = nickname;
        System.out.println(">> Session: Active User set to " + nickname);
    }

    public void setActiveAccount(UUID id) {
        this.activeAccountId = id;
        System.out.println(">> Session: Active Account set to " + id);
    }

    public UUID getActiveUserId() { return activeUserId; }
    public String getActiveUserNickname() { return activeUserNickname; }
    public UUID getActiveAccountId() { return activeAccountId; }
}