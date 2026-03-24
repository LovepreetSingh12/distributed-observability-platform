package com.lovepreet.injestionservice.config;

import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

@Configuration
public class MongoConfig {

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory,
                                       MongoConverter converter) {
        MongoTemplate template = new MongoTemplate(factory, converter);

        // W1 + journal: one primary confirms the write is in the journal.
        // This is the sweet spot between durability and throughput for bulk ingestion.
        // Use W_MAJORITY if you need stronger guarantees at the cost of ~2x latency.
        template.setWriteConcern(WriteConcern.JOURNALED);

        return template;
    }
    
}