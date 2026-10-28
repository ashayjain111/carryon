package com.carryon.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * MongoDB replaces the relational constraints with: unique indexes (one payment per parcel, one rating per
 * rater per parcel, unique email), atomic conditional updates (capacity), optimistic versioning (parcel state)
 * and multi-document transactions (accept = reserve capacity + hold payment + update parcel).
 * Transactions need a replica set (MongoDB Atlas free tier and docker-compose.yml both provide one).
 */
@Configuration
public class MongoConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /** Indexes are created explicitly at startup so correctness never depends on an auto-index flag. */
    @Bean
    @Order(0)
    ApplicationRunner indexes(MongoTemplate mongo) {
        return args -> {
            mongo.indexOps("users").createIndex(new Index().on("email", Sort.Direction.ASC).unique());
            mongo.indexOps("trips").createIndex(new Index()
                    .on("originKey", Sort.Direction.ASC).on("destinationKey", Sort.Direction.ASC)
                    .on("departureDate", Sort.Direction.ASC));
            mongo.indexOps("trips").createIndex(new Index().on("travellerId", Sort.Direction.ASC));
            mongo.indexOps("parcels").createIndex(new Index().on("senderId", Sort.Direction.ASC));
            mongo.indexOps("parcels").createIndex(new Index().on("tripId", Sort.Direction.ASC));
            mongo.indexOps("parcel_events").createIndex(new Index()
                    .on("parcelId", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC));
            mongo.indexOps("payments").createIndex(new Index().on("parcelId", Sort.Direction.ASC).unique());
            mongo.indexOps("ratings").createIndex(new Index()
                    .on("parcelId", Sort.Direction.ASC).on("raterId", Sort.Direction.ASC).unique());
            mongo.indexOps("ratings").createIndex(new Index().on("rateeId", Sort.Direction.ASC));
            mongo.indexOps("disputes").createIndex(new Index().on("parcelId", Sort.Direction.ASC).unique()
                    .partial(PartialIndexFilter.of(Criteria.where("status").is("OPEN"))));
            mongo.indexOps("notifications").createIndex(new Index()
                    .on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        };
    }
}
