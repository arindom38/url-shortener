# URL Shortener

A production-style URL shortener built with Spring Boot, PostgreSQL, and Redis — designed as a learning project that demonstrates real architectural trade-offs.

## Table of Contents

- [Project Description](#project-description)
- [Architecture](#architecture)
- [Architecture Decisions](#architecture-decisions)
- [Tech Stack](#tech-stack)
- [API Reference](#api-reference)
- [Running the Project](#running-the-project)
- [Running Tests](#running-tests)
- [CI / Coverage](#ci--coverage)
- [Project Structure](#project-structure)

---

## Project Description

Given a long URL, the service generates a short alphanumeric code and stores the mapping. Visiting the short URL redirects the browser to the original URL via an HTTP 302. The system is optimised for fast reads using a Redis cache in front of the PostgreSQL store.

---

## Architecture

```
Client
  │
  ├── POST /api/urls          ──► UrlController
  │                                    │
  │                               UrlShortenerService
  │                                  /          \
  │                          CounterService   UrlMappingRepository
  │                         (Redis counter)    (PostgreSQL via JPA)
  │                                  \
  │                               Redis cache (write-through)
  │
  └── GET /{shortCode}        ──► RedirectController
                                       │
                                  UrlResolverService
                                   /            \
                           Redis cache        UrlMappingRepository
                          (cache-aside)        (PostgreSQL fallback)
```

### Shorten flow

1. `CounterService` reserves the next ID from a Redis atomic counter (in batches).
2. The ID is Base62-encoded into a short code (e.g. `aB3x`).
3. The mapping is persisted to PostgreSQL.
4. The mapping is written to Redis with a 7-day TTL (write-through).
5. The short URL is returned to the caller.

### Redirect flow

1. Redis is checked first (cache-aside).
2. On a cache miss, PostgreSQL is queried and the result is back-filled into Redis.
3. An HTTP 302 redirect is returned.

---

## Architecture Decisions

### ADR-1: Base62 encoding over random strings

**Decision:** Use a monotonically-increasing counter encoded in Base62 rather than generating random strings.

**Rationale:**
- No collision risk — each ID is unique by construction.
- Sequential IDs produce short codes that stay compact as the dataset grows (a 7-character Base62 code covers ~3.5 trillion URLs).
- Avoids the need for a uniqueness check on every write.

**Trade-off:** Short codes reveal ordering information. For a production system with privacy requirements, a random approach (with collision retry) or a hash-based approach would be preferred.

---

### ADR-2: Redis counter with batch reservation

**Decision:** The global counter lives in Redis (`INCR url:counter`). Each JVM instance reserves a batch of IDs (default: 1000) to avoid a Redis round-trip on every request.

**Rationale:**
- A single Redis `INCR` is atomic, making it safe across multiple instances.
- Batching amortises the Redis round-trip cost over many requests.

**Trade-off:** If a JVM crashes mid-batch, the unused IDs in that batch are lost (gaps in the sequence). This is acceptable — short codes do not need to be contiguous. The current `synchronized` block inside `CounterService` is sufficient for a single-instance deployment; for multi-instance, each JVM holds its own batch independently, which is by design.

---

### ADR-3: Write-through Redis caching

**Decision:** On every write, the mapping is placed in Redis immediately, not lazily.

**Rationale:** The first redirect after a shorten would always be a cache miss if the cache were populated lazily. Write-through ensures the first visitor gets a fast response.

**Trade-off:** Slightly higher write latency (two writes per shorten: PostgreSQL + Redis). Acceptable given that reads vastly outnumber writes in a URL shortener.

---

### ADR-4: PostgreSQL as the source of truth with Flyway migrations

**Decision:** PostgreSQL holds the durable mapping. Schema changes are managed by Flyway. Hibernate DDL auto is disabled.

**Rationale:**
- Flyway gives explicit, auditable schema evolution with rollback capability.
- Disabling Hibernate DDL ensures the schema is never silently modified at startup.

---

### ADR-5: Redirect uses HTTP 302 (temporary) not 301 (permanent)

**Decision:** The redirect endpoint returns `302 Found`.

**Rationale:** A 301 is cached by browsers permanently, which means future requests never reach the server. If a short URL is later updated or deleted, the browser would still go to the old destination. 302 ensures every redirect passes through the service.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| ORM | Spring Data JPA (Hibernate) |
| Migrations | Flyway |
| Build | Maven |
| Testing | JUnit 5, Mockito, Testcontainers |
| Coverage | JaCoCo (80% instruction threshold) |
| CI | GitHub Actions |

---

## API Reference

### Shorten a URL

```
POST /api/urls
Content-Type: application/json

{
  "longUrl": "https://example.com/some/very/long/path"
}
```

**Response** `201 Created`

```json
{
  "shortCode": "aB3x",
  "shortUrl": "http://localhost:8080/aB3x",
  "longUrl": "https://example.com/some/very/long/path"
}
```

---

### Redirect

```
GET /{shortCode}
```

**Response** `302 Found` with `Location` header pointing to the original URL.

Returns `404` if the short code does not exist.

---

## Running the Project

### Prerequisites

- Java 25
- Docker and Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL on port `5432` and Redis on port `6379`.

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The server starts on `http://localhost:8080`.

### 3. Test with curl

```bash
# Shorten a URL
curl -s -X POST http://localhost:8080/api/urls \
  -H 'Content-Type: application/json' \
  -d '{"longUrl": "https://example.com"}' | jq

# Follow the redirect
curl -v http://localhost:8080/<shortCode>
```

Or run the included test script:

```bash
bash test-api.sh
```

### Environment / Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `app.base-url` | `http://localhost:8080` | Prefix used when building short URLs |
| `app.counter-batch-size` | `1000` | IDs reserved per batch from Redis |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/urlshortener` | PostgreSQL connection |
| `spring.data.redis.host` | `localhost` | Redis host |

---

## Running Tests

```bash
# Unit + integration tests with coverage check
./mvnw verify
```

Integration tests use Testcontainers to spin up a real PostgreSQL instance — no manual setup required. The JaCoCo check enforces a minimum of **80% instruction coverage** and will fail the build if the threshold is not met.

```bash
# Unit tests only (faster, no Testcontainers)
./mvnw test -Dtest="!*IntegrationTest"
```

---

## CI / Coverage

GitHub Actions runs `./mvnw verify` on every push and pull request to `main`. The workflow:

1. Compiles the project.
2. Runs all unit and integration tests.
3. Enforces 80% JaCoCo instruction coverage (build fails if below threshold).
4. Uploads the JaCoCo HTML report as a build artifact.
5. Posts a coverage summary to the GitHub Actions job summary.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/learning/urlshortnerbc/
│   │   ├── config/
│   │   │   ├── AppProperties.java       # Typed config binding (app.*)
│   │   │   ├── FlywayConfig.java
│   │   │   └── RedisConfig.java
│   │   ├── controller/
│   │   │   ├── UrlController.java       # POST /api/urls
│   │   │   └── RedirectController.java  # GET /{shortCode}
│   │   ├── dto/
│   │   │   ├── ShortenRequest.java
│   │   │   └── ShortenResponse.java
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ShortCodeNotFoundException.java
│   │   ├── model/
│   │   │   └── UrlMapping.java
│   │   ├── repository/
│   │   │   └── UrlMappingRepository.java
│   │   └── service/
│   │       ├── CounterService.java      # ID generation + Base62 encoding
│   │       ├── UrlShortenerService.java # Shorten flow
│   │       └── UrlResolverService.java  # Redirect / cache-aside flow
│   └── resources/
│       ├── application.properties
│       └── db/migration/
│           └── V1__create_url_mappings.sql
└── test/
    └── java/com/learning/urlshortnerbc/
        ├── controller/                  # @WebMvcTest slice tests
        ├── service/                     # Unit tests with Mockito
        ├── repository/                  # @DataJpaTest slice tests
        └── integration/                 # Full-stack tests with Testcontainers
```