package com.carryon.ratings;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RatingRepository extends MongoRepository<Rating, UUID> {
    List<Rating> findByRateeIdOrderByCreatedAtDesc(UUID rateeId);

    List<Rating> findByRateeIdIn(Collection<UUID> rateeIds);
}
