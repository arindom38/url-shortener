package com.learning.urlshortnerbc.service;

import com.learning.urlshortnerbc.config.AppProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CounterService {

    private static final String COUNTER_KEY = "url:counter";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final RedisTemplate<String, String> redisTemplate;
    private final int batchSize;

    private long currentId = 0;
    private long batchEnd = 0;

    public CounterService(RedisTemplate<String, String> redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.batchSize = appProperties.counterBatchSize();
    }

    // synchronized: single-instance learning project; for multi-instance, each JVM holds its own batch
    public synchronized long nextId() {
        if (currentId >= batchEnd) {
            reserveNextBatch();
        }
        return currentId++;
    }

    public String encodeBase62(long id) {
        if (id == 0) return String.valueOf(BASE62.charAt(0));
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }

    private void reserveNextBatch() {
        Long end = redisTemplate.opsForValue().increment(COUNTER_KEY, batchSize);
        if (end == null) throw new IllegalStateException("Redis counter returned null");
        currentId = end - batchSize;
        batchEnd = end;
    }
}
