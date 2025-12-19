package com.example.animegame.service;

import com.example.animegame.dto.AnimeDTO;
import com.example.animegame.dto.CheckResultDTO;
import com.example.animegame.model.Anime;
import com.example.animegame.model.HighScore;
import com.example.animegame.repository.AnimeRepository;
import com.example.animegame.repository.HighScoreRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GameService {

    private final AnimeRepository animeRepository;
    private final HighScoreRepository highScoreRepository;

    public GameService(AnimeRepository animeRepository, HighScoreRepository highScoreRepository) {
        this.animeRepository = animeRepository;
        this.highScoreRepository = highScoreRepository;
    }

    public List<AnimeDTO> getNextRound(String difficulty) {
        DifficultyParams params = getParams(difficulty);

        Anime left = animeRepository.findRandomByRankRange(params.minRank, params.maxRank)
                .orElseThrow(() -> new RuntimeException("DB is empty or range invalid"));

        long gapValue = (long) (left.getMembersCount() * params.gapPercent);
        long minMembers = left.getMembersCount() - gapValue;
        long maxMembers = left.getMembersCount() + gapValue;

        Anime right = animeRepository.findOpponentWithGap(
                params.minRank, params.maxRank, 
                left.getId(), 
                minMembers, maxMembers
        ).orElseGet(() -> {
            return animeRepository.findRandomByRankRange(params.minRank, params.maxRank)
                    .filter(a -> !a.getId().equals(left.getId()))
                    .orElse(left); 
        });

        AnimeDTO dto1 = new AnimeDTO(left.getId(), left.getTitle(), left.getImagePath(), left.getMembersCount());
        AnimeDTO dto2 = new AnimeDTO(right.getId(), right.getTitle(), right.getImagePath(), null);

        return List.of(dto1, dto2);
    }

    public CheckResultDTO checkAnswer(Long selectedId, Long otherId) {
        Anime selected = animeRepository.findById(selectedId).orElseThrow();
        Anime other = animeRepository.findById(otherId).orElseThrow();

        boolean correct = selected.getMembersCount() >= other.getMembersCount();
        
        return new CheckResultDTO(correct, selected.getMembersCount(), other.getMembersCount());
    }

    public List<Anime> getAllAnimeForSpy() {
        return animeRepository.findAll();
    }

    public Integer getHighScore() {
        Integer max = highScoreRepository.findMaxScore();
        return max == null ? 0 : max;
    }

    public void submitScore(Integer score) {
        HighScore hs = new HighScore();
        hs.setScore(score);
        hs.setTimestamp(LocalDateTime.now());
        highScoreRepository.save(hs);
    }

    private record DifficultyParams(int minRank, int maxRank, double gapPercent) {}

    private DifficultyParams getParams(String diff) {
        if (diff == null) diff = "medium";
        return switch (diff.toLowerCase()) {
            case "easy" -> new DifficultyParams(1, 200, 0.25);
            
            case "medium" -> new DifficultyParams(1, 400, 0.15);
            
            case "hard" -> new DifficultyParams(30, 600, 0.10);
            
            case "extreme" -> new DifficultyParams(200, 1000, 0.05);
            
            case "impossible" -> new DifficultyParams(500, 1000, 0.0);
            
            default -> new DifficultyParams(1, 400, 0.15);
        };
    }
}
