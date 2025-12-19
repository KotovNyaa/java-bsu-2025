package com.example.animegame.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnimeDTO {
    private Long id;
    private String title;
    private String imagePath;
    private Long membersCount;
}