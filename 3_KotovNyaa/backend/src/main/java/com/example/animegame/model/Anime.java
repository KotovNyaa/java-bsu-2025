package com.example.animegame.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "anime", indexes = @Index(columnList = "popularity_rank"))
public class Anime {
    @Id
    private Long id;

    private String title;
    
    @Column(name = "members_count")
    private Long membersCount;

    private String imagePath;

    @Column(name = "popularity_rank")
    private Integer rank;
}
