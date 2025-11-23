package com.bank.api.dto;

public record CommandResponse(boolean success, String message, Object data) {
    public static CommandResponse ok(String msg, Object data) {
        return new CommandResponse(true, msg, data);
    }
    public static CommandResponse error(String msg) {
        return new CommandResponse(false, msg, null);
    }
}