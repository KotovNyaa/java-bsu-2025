package com.bank.api.service;

import com.bank.api.dto.SystemStateDto;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QueryService {
    private final JdbcTemplate jdbcTemplate;

    public QueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SystemStateDto getSystemState() {
        List<SystemStateDto.UserDto> users = new ArrayList<>();
        
        try {
            jdbcTemplate.query("SELECT * FROM \"users\"", rs -> {
                UUID userId = UUID.fromString(rs.getString("id"));
                String nick = rs.getString("nickname");
                
                List<SystemStateDto.AccountDto> accounts = new ArrayList<>();
                
                jdbcTemplate.query(
                    "SELECT a.* FROM \"accounts\" a JOIN \"user_accounts\" ua ON a.id = ua.account_id WHERE ua.user_id = ?",
                    (rs2) -> {
                        accounts.add(new SystemStateDto.AccountDto(
                                UUID.fromString(rs2.getString("id")),
                                rs2.getBigDecimal("balance"),
                                rs2.getString("status")
                        ));
                    }, userId
                );
                
                users.add(new SystemStateDto.UserDto(userId, nick, accounts));
            });
        } catch (Exception e) {
            // Silent error
        }
        
        return new SystemStateDto(users);
    }
}