package com.example.animegame.repository;

import com.example.animegame.model.HighScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface HighScoreRepository extends JpaRepository<HighScore, Long> {

    @Query("SELECT MAX(h.score) FROM HighScore h")
    Integer findMaxScore();
}