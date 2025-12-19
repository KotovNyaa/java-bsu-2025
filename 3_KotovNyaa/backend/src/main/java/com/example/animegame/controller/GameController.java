package com.example.animegame.controller;

import com.example.animegame.dto.AnimeDTO;
import com.example.animegame.dto.CheckResultDTO;
import com.example.animegame.model.Anime;
import com.example.animegame.service.GameService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GameController {

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @GetMapping("/game/next")
    public List<AnimeDTO> getNextRound(@RequestParam(required = false, defaultValue = "medium") String difficulty) {
        return service.getNextRound(difficulty);
    }

    @PostMapping("/game/check")
    public CheckResultDTO checkAnswer(@RequestBody Map<String, Long> payload) {
        Long selectedId = payload.get("selectedId");
        Long otherId = payload.get("otherId");
        return service.checkAnswer(selectedId, otherId);
    }

    @GetMapping("/spy/all")
    public List<Anime> getAllAnime() {
        return service.getAllAnimeForSpy();
    }

    @GetMapping("/game/highscore")
    public Integer getHighScore() {
        return service.getHighScore();
    }

    @PostMapping("/game/score")
    public void submitScore(@RequestBody Map<String, Integer> payload) {
        service.submitScore(payload.get("score"));
    }
}
