package com.bank.ui.javafx;

import com.bank.api.dto.SystemStateDto;
import java.util.UUID;

public class TreeWrapper {

    public enum ActionType { CREATE_ACCOUNT }

    public static class UserW {
        public final SystemStateDto.UserDto user;
        public UserW(SystemStateDto.UserDto u) { this.user = u; }
        @Override public String toString() { return "👤 " + user.nickname(); }
    }

    public static class AccountW {
        public final SystemStateDto.AccountDto acc;
        public AccountW(SystemStateDto.AccountDto a) { this.acc = a; }
        @Override public String toString() { 
            String statusIcon = switch (acc.status()) {
                case "ACTIVE" -> "🟢";
                case "FROZEN" -> "❄️";
                default -> "🔴";
            };
            return statusIcon + " " + acc.id().toString().substring(0, 6) + ".. (" + acc.balance() + "$)"; 
        }
    }

    public static class ActionItem {
        public final String label;
        public final ActionType type;
        public final UUID parentId;

        public ActionItem(String label, ActionType type, UUID parentId) {
            this.label = label;
            this.type = type;
            this.parentId = parentId;
        }
        @Override public String toString() { return label; }
    }
}
