package com.carryon.ratings;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<Rating, UUID> {
    List<Rating> findByRateeIdOrderByCreatedAtDesc(UUID rateeId);

    /** Rows of [rateeId, average score]. */
    @Query("select r.rateeId, avg(r.score) from Rating r where r.rateeId in :ids group by r.rateeId")
    List<Object[]> averages(Collection<UUID> ids);
}
