package com.learning.urlshortnerbc.integration;

import com.learning.urlshortnerbc.dto.ShortenRequest;
import com.learning.urlshortnerbc.dto.ShortenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UrlShortenerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shorten_thenRedirect_endToEndFlow() {
        ResponseEntity<ShortenResponse> createResp = restTemplate.postForEntity(
                "/api/urls",
                new ShortenRequest("https://example.com/very/long/path"),
                ShortenResponse.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ShortenResponse body = createResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.shortCode()).isNotBlank();
        assertThat(body.shortUrl()).contains(body.shortCode());
        assertThat(body.longUrl()).isEqualTo("https://example.com/very/long/path");

        // TestRestTemplate does NOT follow redirects — we get the 302 directly
        ResponseEntity<Void> redirectResp = restTemplate.getForEntity(
                "/" + body.shortCode(), Void.class);

        assertThat(redirectResp.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResp.getHeaders().getLocation()).isNotNull();
        assertThat(redirectResp.getHeaders().getLocation().toString())
                .isEqualTo("https://example.com/very/long/path");
    }

    @Test
    void shorten_sameUrlTwice_producesDifferentShortCodes() {
        String url = "https://example.com/duplicate";

        ResponseEntity<ShortenResponse> resp1 = restTemplate.postForEntity(
                "/api/urls", new ShortenRequest(url), ShortenResponse.class);
        ResponseEntity<ShortenResponse> resp2 = restTemplate.postForEntity(
                "/api/urls", new ShortenRequest(url), ShortenResponse.class);

        assertThat(resp1.getBody().shortCode()).isNotEqualTo(resp2.getBody().shortCode());
    }

    @Test
    void redirect_unknownShortCode_returns404() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/doesnotexist99", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shorten_invalidUrl_returns400() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/urls", new ShortenRequest("not-a-url"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void redirect_cacheHit_resolvesSameUrl() {
        // First call populates cache; second call should hit Redis (same result)
        ResponseEntity<ShortenResponse> createResp = restTemplate.postForEntity(
                "/api/urls", new ShortenRequest("https://example.com/cached"), ShortenResponse.class);
        String shortCode = createResp.getBody().shortCode();

        ResponseEntity<Void> first = restTemplate.getForEntity("/" + shortCode, Void.class);
        ResponseEntity<Void> second = restTemplate.getForEntity("/" + shortCode, Void.class);

        assertThat(first.getHeaders().getLocation().toString()).isEqualTo("https://example.com/cached");
        assertThat(second.getHeaders().getLocation().toString()).isEqualTo("https://example.com/cached");
    }
}
