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

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CounterService counterService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        counterService = new CounterService(redisTemplate, new AppProperties("http://localhost:8080", 3));
    }

    @Test
    void nextId_reservesFirstBatchOnFirstCall() {
        when(valueOperations.increment("url:counter", 3)).thenReturn(3L);

        long id = counterService.nextId();

        assertThat(id).isEqualTo(0L);
        verify(valueOperations, times(1)).increment("url:counter", 3);
    }

    @Test
    void nextId_exhaustsBatchThenReservesNextOne() {
        when(valueOperations.increment("url:counter", 3)).thenReturn(3L, 6L);

        assertThat(counterService.nextId()).isEqualTo(0L);
        assertThat(counterService.nextId()).isEqualTo(1L);
        assertThat(counterService.nextId()).isEqualTo(2L);
        assertThat(counterService.nextId()).isEqualTo(3L); // triggers second reservation

        verify(valueOperations, times(2)).increment("url:counter", 3);
    }

    @Test
    void nextId_redisReturnsNull_throwsIllegalStateException() {
        when(valueOperations.increment("url:counter", 3)).thenReturn(null);

        assertThatThrownBy(() -> counterService.nextId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null");
    }

    @Test
    void encodeBase62_zero_returnsFirstChar() {
        assertThat(counterService.encodeBase62(0)).isEqualTo("a");
    }

    @Test
    void encodeBase62_one() {
        assertThat(counterService.encodeBase62(1)).isEqualTo("b");
    }

    @Test
    void encodeBase62_lastSingleDigit() {
        // index 61 is the last char '9' in the Base62 alphabet
        assertThat(counterService.encodeBase62(61)).isEqualTo("9");
    }

    @Test
    void encodeBase62_firstTwoDigitValue() {
        // 62 in Base62 is "ba" (1*62 + 0)
        assertThat(counterService.encodeBase62(62)).isEqualTo("ba");
    }

    @Test
    void encodeBase62_largeValue_produces6CharCode() {
        // 62^5 = 916_132_832 is the first 6-digit Base62 number
        assertThat(counterService.encodeBase62(916_132_832L)).hasSize(6);
    }
}
