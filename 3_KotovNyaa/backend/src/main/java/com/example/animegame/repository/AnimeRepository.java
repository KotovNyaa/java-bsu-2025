package com.example.animegame.repository;

import com.example.animegame.model.Anime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnimeRepository extends JpaRepository<Anime, Long> {

    @Query(value = """
        SELECT * FROM anime 
        WHERE popularity_rank BETWEEN :minRank AND :maxRank 
        ORDER BY RANDOM() 
        LIMIT 1
    """, nativeQuery = true)
    Optional<Anime> findRandomByRankRange(@Param("minRank") int minRank, @Param("maxRank") int maxRank);

    @Query(value = """
        SELECT * FROM anime 
        WHERE popularity_rank BETWEEN :minRank AND :maxRank
          AND id != :leftId
          AND (members_count < :minMembers OR members_count > :maxMembers)
        ORDER BY RANDOM() 
        LIMIT 1
    """, nativeQuery = true)
    Optional<Anime> findOpponentWithGap(
            @Param("minRank") int minRank, 
            @Param("maxRank") int maxRank,
            @Param("leftId") Long leftId,
            @Param("minMembers") Long minMembers,
            @Param("maxMembers") Long maxMembers
    );
}
