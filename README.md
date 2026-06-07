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
./mvnw clean test          # unit tests
./mvnw clean verify        # unit + integration tests (uses Testcontainers)
```

Integration tests spin up an embedded Elasticsearch container automatically — no external cluster needed.

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
└── test/java/com/example/search/
    ├── DqTriageTestFixtures.java   # Shared test data factories
    ├── controller/
    ├── service/
    ├── repository/
    └── integration/                # Testcontainers-based integration tests
```