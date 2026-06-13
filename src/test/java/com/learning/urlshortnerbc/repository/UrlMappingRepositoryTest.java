package com.learning.urlshortnerbc.repository;

import com.learning.urlshortnerbc.model.UrlMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UrlMappingRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private UrlMappingRepository repository;

    @Test
    void findByShortCode_existingCode_returnsMapping() {
        repository.save(UrlMapping.builder()
                .id(1L).shortCode("abc123").longUrl("https://example.com")
                .createdAt(Instant.now()).build());

        Optional<UrlMapping> result = repository.findByShortCode("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().getLongUrl()).isEqualTo("https://example.com");
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void findByShortCode_missingCode_returnsEmpty() {
        Optional<UrlMapping> result = repository.findByShortCode("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void save_withExpiresAt_persistsExpirationDate() {
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        repository.save(UrlMapping.builder()
                .id(2L).shortCode("exp01").longUrl("https://example.com")
                .createdAt(Instant.now()).expiresAt(expiry).build());

        Optional<UrlMapping> result = repository.findByShortCode("exp01");
        assertThat(result).isPresent();
        assertThat(result.get().getExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void save_withoutExpiresAt_expiresAtIsNull() {
        repository.save(UrlMapping.builder()
                .id(3L).shortCode("noexp").longUrl("https://example.com")
                .createdAt(Instant.now()).build());

        Optional<UrlMapping> result = repository.findByShortCode("noexp");
        assertThat(result).isPresent();
        assertThat(result.get().getExpiresAt()).isNull();
    }
}
