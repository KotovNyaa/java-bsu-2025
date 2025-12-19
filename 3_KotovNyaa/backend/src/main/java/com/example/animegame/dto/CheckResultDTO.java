package com.example.animegame.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckResultDTO {
    private boolean correct;
    private Long selectedMembers;
    private Long otherMembers;
}