package com.learning.urlshortnerbc.service;

import com.learning.urlshortnerbc.config.AppProperties;
import com.learning.urlshortnerbc.dto.ShortenRequest;
import com.learning.urlshortnerbc.dto.ShortenResponse;
import com.learning.urlshortnerbc.model.UrlMapping;
import com.learning.urlshortnerbc.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock private UrlMappingRepository repository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private CounterService counterService;
    @Mock private ValueOperations<String, String> valueOperations;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new UrlShortenerService(repository, redisTemplate, counterService,
                new AppProperties("http://localhost:8080", 1000));
    }

    @Test
    void shorten_returnsCorrectResponse() {
        when(counterService.nextId()).thenReturn(42L);
        when(counterService.encodeBase62(42L)).thenReturn("Q");

        ShortenResponse resp = service.shorten(new ShortenRequest("https://example.com"));

        assertThat(resp.shortCode()).isEqualTo("Q");
        assertThat(resp.shortUrl()).isEqualTo("http://localhost:8080/Q");
        assertThat(resp.longUrl()).isEqualTo("https://example.com");
    }

    @Test
    void shorten_persistsMappingWithCorrectFields() {
        when(counterService.nextId()).thenReturn(7L);
        when(counterService.encodeBase62(7L)).thenReturn("h");

        service.shorten(new ShortenRequest("https://example.com/path"));

        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).save(captor.capture());
        UrlMapping saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getShortCode()).isEqualTo("h");
        assertThat(saved.getLongUrl()).isEqualTo("https://example.com/path");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isNull();
    }

    @Test
    void shorten_writesToCacheWithTtl() {
        when(counterService.nextId()).thenReturn(1L);
        when(counterService.encodeBase62(1L)).thenReturn("b");

        service.shorten(new ShortenRequest("https://example.com"));

        verify(valueOperations).set(eq("url:b"), eq("https://example.com"), any(Duration.class));
    }
}
