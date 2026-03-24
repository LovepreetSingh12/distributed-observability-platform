package com.lovepreet.injestionservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.lovepreet.injestionservice.model.PersistedEvent;

public interface EventRepository extends MongoRepository<PersistedEvent, String> {
    List<PersistedEvent> findByServiceNameAndTimestampBetweenOrderByTimestampDesc(
            String serviceName, Instant from, Instant to);
    
    List<PersistedEvent> findByLevelAndTimestampBetweenOrderByTimestampDesc(
            String level, Instant from, Instant to);
    
    List<PersistedEvent> findByServiceNameAndLevelAndTimestampBetweenOrderByTimestampDesc(
            String serviceName, String level, Instant from, Instant to);

    List<PersistedEvent> findByTraceIdOrderByTimestampAsc(String traceId);

    @Query(value  = "{ 'serviceName': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }",
           fields = "{ 'level': 1, 'timestamp': 1, 'message': 1 }")
    List<PersistedEvent> findSummaryByServiceAndTimeRange(
            String serviceName, Instant from, Instant to);

    long countByServiceNameAndTimestampBetween(String serviceName, Instant from, Instant to);
    long countByLevelAndTimestampBetween(String level, Instant from, Instant to);
}
