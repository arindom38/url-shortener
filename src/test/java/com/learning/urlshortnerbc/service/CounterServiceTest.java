package com.learning.urlshortnerbc.service;

import com.learning.urlshortnerbc.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CounterServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private CounterService counterService;

    @BeforeEach
    void setUp() {
        // Only construct — do NOT stub redisTemplate here; encodeBase62 tests don't touch Redis
        counterService = new CounterService(redisTemplate, new AppProperties("http://localhost:8080", 3));
    }

    // ── nextId tests (need Redis stub) ──────────────────────────────────────

    @Test
    void nextId_reservesFirstBatchOnFirstCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("url:counter", 3)).thenReturn(3L);

        assertThat(counterService.nextId()).isEqualTo(0L);
        verify(valueOperations, times(1)).increment("url:counter", 3);
    }

    @Test
    void nextId_exhaustsBatchThenReservesNextOne() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("url:counter", 3)).thenReturn(3L, 6L);

        assertThat(counterService.nextId()).isEqualTo(0L);
        assertThat(counterService.nextId()).isEqualTo(1L);
        assertThat(counterService.nextId()).isEqualTo(2L);
        assertThat(counterService.nextId()).isEqualTo(3L); // triggers second reservation

        verify(valueOperations, times(2)).increment("url:counter", 3);
    }

    @Test
    void nextId_redisReturnsNull_throwsIllegalStateException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("url:counter", 3)).thenReturn(null);

        assertThatThrownBy(() -> counterService.nextId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null");
    }

    // ── encodeBase62 tests (pure math, no Redis) ────────────────────────────

    @Test
    void encodeBase62_zero_returnsFirstChar() {
        assertThat(counterService.encodeBase62(0)).isEqualTo("a");
    }

    @Test
    void encodeBase62_one() {
        assertThat(counterService.encodeBase62(1)).isEqualTo("b");
    }

    @Test
    void encodeBase62_lastSingleDigitValue() {
        assertThat(counterService.encodeBase62(61)).isEqualTo("9");
    }

    @Test
    void encodeBase62_firstTwoDigitValue() {
        // 62 = 1*62 + 0  →  "ba"
        assertThat(counterService.encodeBase62(62)).isEqualTo("ba");
    }

    @Test
    void encodeBase62_largeValue_produces6CharCode() {
        // 62^5 = 916_132_832 is the smallest 6-digit Base62 number
        assertThat(counterService.encodeBase62(916_132_832L)).hasSize(6);
    }
}
