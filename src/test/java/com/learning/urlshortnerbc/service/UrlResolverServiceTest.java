package com.learning.urlshortnerbc.service;

import com.learning.urlshortnerbc.exception.ShortCodeNotFoundException;
import com.learning.urlshortnerbc.model.UrlMapping;
import com.learning.urlshortnerbc.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlResolverServiceTest {

    @Mock private UrlMappingRepository repository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private UrlResolverService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new UrlResolverService(repository, redisTemplate);
    }

    @Test
    void resolve_cacheHit_returnsImmediatelyWithoutDbQuery() {
        when(valueOperations.get("url:abc")).thenReturn("https://example.com");

        String result = service.resolve("abc");

        assertThat(result).isEqualTo("https://example.com");
        verifyNoInteractions(repository);
    }

    @Test
    void resolve_cacheMiss_queriesDbAndPopulatesCache() {
        when(valueOperations.get("url:abc")).thenReturn(null);
        when(repository.findByShortCode("abc")).thenReturn(Optional.of(
                UrlMapping.builder().id(1L).shortCode("abc").longUrl("https://example.com")
                        .createdAt(Instant.now()).build()
        ));

        String result = service.resolve("abc");

        assertThat(result).isEqualTo("https://example.com");
        verify(valueOperations).set(eq("url:abc"), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void resolve_notFoundAnywhere_throwsShortCodeNotFoundException() {
        when(valueOperations.get("url:missing")).thenReturn(null);
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(ShortCodeNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void resolve_cacheMiss_doesNotWriteToCacheOnMiss() {
        when(valueOperations.get("url:gone")).thenReturn(null);
        when(repository.findByShortCode("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("gone"))
                .isInstanceOf(ShortCodeNotFoundException.class);
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }
}
