package com.bank.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SystemStateDto(List<UserDto> users) {
    public record UserDto(UUID id, String nickname, List<AccountDto> accounts) {}
    public record AccountDto(UUID id, BigDecimal balance, String status) {}
}