package com.learning.urlshortnerbc.service;

import com.learning.urlshortnerbc.exception.ShortCodeNotFoundException;
import com.learning.urlshortnerbc.model.UrlMapping;
import com.learning.urlshortnerbc.repository.UrlMappingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class UrlResolverService {

    private static final String CACHE_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final UrlMappingRepository repository;
    private final RedisTemplate<String, String> redisTemplate;

    public UrlResolverService(UrlMappingRepository repository, RedisTemplate<String, String> redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    public String resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        if (cached != null) return cached;

        String longUrl = repository.findByShortCode(shortCode)
                .map(UrlMapping::getLongUrl)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, longUrl, CACHE_TTL);
        return longUrl;
    }
}
