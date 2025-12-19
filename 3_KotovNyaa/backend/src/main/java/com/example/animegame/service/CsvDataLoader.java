package com.example.animegame.service;

import com.example.animegame.model.Anime;
import com.example.animegame.repository.AnimeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CsvDataLoader {

    private final AnimeRepository repository;

    public CsvDataLoader(AnimeRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    @Transactional
    public void loadData() {
        if (repository.count() > 0) return;

        System.out.println("--- ЗАГРУЗКА CSV: УМНЫЙ ПАРСИНГ + РАСЧЕТ РАНГОВ ---");

        Pattern csvPattern = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        try {
            var resource = new ClassPathResource("data/anime_data.csv");
            if (!resource.exists()) {
                System.err.println("Файл не найден!");
                return;
            }

            List<Anime> buffer = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;

                while ((line = reader.readLine()) != null) {
                    line = line.replace("\u0000", "");
                    if (line.trim().isEmpty()) continue;

                    if (isHeader) { isHeader = false; continue; }

                    try {
                        String[] parts = csvPattern.split(line);

                        if (parts.length < 3) continue;

                        String rawId = parts[0].replaceAll("[^0-9]", "");
                        if (rawId.isEmpty()) continue;
                        Long id = Long.parseLong(rawId);

                        String rawImage = parts[parts.length - 1].trim().replace("\"", "");

                        String rawMembers = parts[parts.length - 2].replaceAll("[^0-9]", "");
                        if (rawMembers.isEmpty()) rawMembers = "0";
                        Long members = Long.parseLong(rawMembers);

                        String title = parts[1];
                        if (title.startsWith("\"") && title.endsWith("\"")) {
                            title = title.substring(1, title.length() - 1);
                        }
                        title = title.replace("\"\"", "\"");

                        Anime anime = new Anime();
                        anime.setId(id);
                        anime.setTitle(title);
                        anime.setMembersCount(members);
                        anime.setImagePath(rawImage);
                        
                        buffer.add(anime);

                    } catch (Exception e) {
                    }
                }
            }

            System.out.println("Сортировка и присвоение рангов...");
            buffer.sort(Comparator.comparing(Anime::getMembersCount).reversed());

            int rank = 1;
            for (Anime a : buffer) {
                a.setRank(rank++);
            }

            repository.saveAll(buffer);
            System.out.println("--- ЗАГРУЖЕНО: " + buffer.size() + " аниме. Топ-1: " + buffer.get(0).getTitle());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
