# DQ Violation Triage — Spring Boot + Elasticsearch

A REST API for indexing and searching **Data Quality (DQ) violation triage reports**. Built with Spring Boot 4 and Elasticsearch 9.x, it supports both Elastic Cloud and local Docker deployments.

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Data Elasticsearch | 6.x |
| Elasticsearch Java Client | 9.x |
| REST Assured (tests) | 6.0.0 |
| Testcontainers (tests) | 2.0.5 BOM |

## Prerequisites

- Java 21
- Maven 3.8+
- Elasticsearch 9.x — either local Docker or an Elastic Cloud cluster

## Running Locally

**1. Start a local Elasticsearch instance:**

```bash
docker run -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  docker.elastic.co/elasticsearch/elasticsearch:9.0.0
```

**2. Run the application with the `local` profile:**

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The app starts on port **22222**.

| URL | Description |
|-----|-------------|
| `http://localhost:22222/dq-triage` | API base path |
| `http://localhost:22222/swagger-ui.html` | Swagger UI |
| `http://localhost:22222/actuator/health` | Health check |

## Running Against Elastic Cloud

Set the following environment variables before starting the app (no profile flag needed):

```bash
export ELASTIC_CLOUD_ID="your-cloud-id"
export ELASTIC_API_KEY="base64-encoded-id:key"

./mvnw spring-boot:run
```

## Building

```bash
./mvnw clean package
java -jar target/elasticsearch-springboot-1.0.0.jar
```

## Running Tests

```bash
./mvnw test                          # unit tests only
./mvnw verify                        # unit tests + integration tests
./mvnw verify -Dsurefire.skip=true   # integration tests only (skips unit tests)

# Run a single integration test class
./mvnw verify -Dsurefire.skip=true -Dit.test=DqViolationTriageRealPayloadIT
```

Docker must be running for integration tests — Testcontainers pulls and starts `elasticsearch:9.4.2` automatically.

### Test execution map

| Test class | Runs via | Needs Docker |
|------------|----------|:------------:|
| `DomainModelTest` | surefire (`mvn test`) | No |
| `ElasticsearchConfigTest` | surefire (`mvn test`) | No |
| `DqViolationTriageControllerTest` | surefire (`mvn test`) | No |
| `DqViolationTriageServiceTest` | surefire (`mvn test`) | No |
| `DqViolationTriageRepositoryTest` | surefire (`mvn test`) | No |
| `ElasticsearchConnectionIT` | failsafe (`mvn verify`) | Yes |
| `DqViolationTriageRealPayloadIT` | failsafe (`mvn verify`) | Yes |

### Golden-file (snapshot) testing

`DqViolationTriageRealPayloadIT` uses a **capture-on-first-run** pattern to compare every API response against a committed expected JSON file.

#### How it works

```
First run  → expected file absent  → writes actual response to file (no assertion)
Every run after → file present → JSONAssert LENIENT comparison
```

`JSONCompareMode.LENIENT` means:
- Every field in the expected file must appear in the actual response with the same value — this catches regressions.
- Extra fields in the actual response are silently allowed — prevents false positives from dynamic fields like `indexedAt` (which is stripped before the file is written).

#### File naming convention

Expected files live in `src/test/resources/expected/` and are numbered to match the `@Order` of the test that produces them:

```
src/test/resources/expected/
├── 01-post-create.json            POST /dq-triage              → 201 full document
├── 02-get-by-id.json              GET  /dq-triage/{id}         → 200 full document
├── 03-get-by-tracking-id.json     GET  /dq-triage/tracking/{id}→ 200 full document
├── 05-search-scorecard.json       GET  /search/scorecard        → 200 array
├── 06-search-steward-page.json    GET  /search/steward          → 200 Page
├── 07-search-full-text-page.json  GET  /search?q=              → 200 Page
├── 08-search-rule-label.json      GET  /search/rule-label       → 200 array
├── 09-search-offending-record.json GET /search/offending-record → 200 array
├── 10-search-predicate.json       GET  /search/predicate        → 200 array
├── 11-search-date-range-page.json GET  /search/date-range       → 200 Page
├── 12-search-high-severity-page.json GET /search/high-severity  → 200 Page
├── 13-upsert-response.json        POST /dq-triage (re-index)    → 200/201 document
└── 14-put-update.json             PUT  /dq-triage/{id}          → 200 updated document
```

Numbers 4, 15, 16, and 17 are omitted because those tests use the `ElasticsearchTemplate` directly (no HTTP response body) or return HTTP 204 (no content).

#### Regenerating expected files after an intentional API change

Delete the affected file(s) and run the integration tests once — the new response is captured automatically and becomes the new baseline:

```bash
# Regenerate a single file
rm src/test/resources/expected/02-get-by-id.json
./mvnw verify -Dsurefire.skip=true

# Regenerate all files
rm src/test/resources/expected/*.json
./mvnw verify -Dsurefire.skip=true
```

Review the newly written files with `git diff` before committing them as the updated baseline.

## API Reference

All endpoints are under `/dq-triage` on port `22222`. Full interactive documentation is available at `/swagger-ui.html`.

### CRUD

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/dq-triage/` | Create a new triage document (returns 201) |
| `GET` | `/dq-triage/{uniqueTriageId}` | Get document by ID |
| `GET` | `/dq-triage/tracking/{trackingId}` | Get document by business tracking ID |
| `PUT` | `/dq-triage/{uniqueTriageId}` | Replace an existing document |
| `DELETE` | `/dq-triage/{uniqueTriageId}` | Delete a document (returns 204) |

### Search

| Method | Path | Query Parameters |
|--------|------|-----------------|
| `GET` | `/dq-triage/search` | `q`, `page`, `size` — full-text on `businessReason` |
| `GET` | `/dq-triage/search/scorecard` | `scoreCardSubject` |
| `GET` | `/dq-triage/search/steward` | `dataSteward`, `page`, `size` |
| `GET` | `/dq-triage/search/date-range` | `from`, `to` (ISO datetime), `page`, `size` |
| `GET` | `/dq-triage/search/rule-label` | `label` |
| `GET` | `/dq-triage/search/offending-record` | `dataRecordIri` |
| `GET` | `/dq-triage/search/predicate` | `predicateIRI` |
| `GET` | `/dq-triage/search/high-severity` | `minRecords`, `from`, `to`, `page`, `size` |

Error responses follow [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807).

## Document Structure

The Elasticsearch index `dq-violation-triage` stores hierarchical documents:

```
DqViolationTriageDocument
├── uniqueTriageId        (keyword, document ID)
├── trackingId            (keyword)
├── scoreCardSubject      (keyword — IRI)
├── extractDateTime       (date)
├── businessReason        (text — full-text searchable)
├── dataSteward           (keyword)
├── dataCustodian         (keyword)
├── dataOwner             (keyword)
├── workflowId            (keyword)
├── workflowStartTime     (date)
├── workflowEndTime       (date)
├── indexedAt             (date — set on write)
└── violatedRules[]       (object array)
    ├── ruleExecutionSubject     (keyword)
    ├── offendingRuleSubject     (keyword)
    ├── offendingRuleLabel       (text)
    ├── offendingRuleLabelKeyword (keyword — denormalized copy)
    └── offendingRecords[]       (nested)
        ├── dataRecordSubject    (keyword)
        ├── dqRuleSubject        (keyword)
        ├── dataRecordId         (keyword)
        └── dqErrorPropertyValue (object)
            ├── predicateIRI     (keyword)
            ├── stringValue      (keyword)
            ├── obfuscated       (boolean — PII flag)
            └── errorDescription (text)
```

The full Elasticsearch mapping is in `src/main/resources/es-settings/dq-violation-triage-mapping.json`.

## Configuration

### `application.yml` (Elastic Cloud / production)

```yaml
server:
  port: 22222

elastic.cloud:
  cloud-id: ${ELASTIC_CLOUD_ID}
  api-key: ${ELASTIC_API_KEY}
  connection-timeout-seconds: 10
  socket-timeout-seconds: 60

app.elasticsearch:
  index-name: dq-violation-triage
  number-of-shards: 1
  number-of-replicas: 1
  bulk:
    batch-size: 100
    refresh-policy: WAIT_UNTIL
```

### `application-local.yml` (local Docker)

```yaml
elastic.cloud:
  cloud-id: ""
  username: elastic
  password: changeme123

app.elasticsearch:
  index-name: dq-violation-triage-test
  number-of-shards: 1
  number-of-replicas: 0
  bulk:
    batch-size: 10
    refresh-policy: IMMEDIATE

spring:
  elasticsearch:
    uris: http://localhost:9200
```

## Project Structure

```
src/
├── main/java/com/example/search/
│   ├── SearchApplication.java
│   ├── config/          # Elasticsearch client, Jackson, OpenAPI config
│   ├── controller/      # DqViolationTriageController
│   ├── document/        # Elasticsearch document models
│   ├── exception/       # GlobalExceptionHandler, custom exceptions
│   ├── repository/      # Spring Data ES repository with custom @Query methods
│   └── service/         # Business logic and document enrichment
├── main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── es-settings/dq-violation-triage-mapping.json
└── test/
    ├── java/com/example/search/
    │   ├── DqTriageTestFixtures.java   # Shared test data factories
    │   ├── controller/
    │   ├── service/
    │   ├── repository/
    │   └── integration/                # Testcontainers-based integration tests
    └── resources/
        ├── fixtures/                   # Input payloads for integration tests
        │   └── dq-triage-real-payload.json
        └── expected/                   # Golden-file snapshots (one per API test)
            ├── 01-post-create.json
            └── ...                     # 13 files total, numbered by @Order
```