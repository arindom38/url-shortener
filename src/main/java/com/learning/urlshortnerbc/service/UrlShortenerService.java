package com.learning.urlshortnerbc.service;

import com.learning.urlshortnerbc.config.AppProperties;
import com.learning.urlshortnerbc.dto.ShortenRequest;
import com.learning.urlshortnerbc.dto.ShortenResponse;
import com.learning.urlshortnerbc.model.UrlMapping;
import com.learning.urlshortnerbc.repository.UrlMappingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class UrlShortenerService {

    private static final String CACHE_PREFIX = "url:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final UrlMappingRepository repository;
    private final RedisTemplate<String, String> redisTemplate;
    private final CounterService counterService;
    private final AppProperties appProperties;

    public UrlShortenerService(UrlMappingRepository repository,
                               RedisTemplate<String, String> redisTemplate,
                               CounterService counterService,
                               AppProperties appProperties) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.counterService = counterService;
        this.appProperties = appProperties;
    }

    public ShortenResponse shorten(ShortenRequest request) {
        long id = counterService.nextId();
        String shortCode = counterService.encodeBase62(id);

        UrlMapping mapping = UrlMapping.builder()
                .id(id)
                .shortCode(shortCode)
                .longUrl(request.longUrl())
                .createdAt(Instant.now())
                .build();

        repository.save(mapping);
        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, request.longUrl(), CACHE_TTL);

        return new ShortenResponse(shortCode, appProperties.baseUrl() + "/" + shortCode, request.longUrl());
    }
}
