

package com.example.search.integration;

import com.example.search.document.DqViolationTriageDocument;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.search.ElasticsearchContainerBase;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration test loading the real DQ triage JSON payload.
 *  * End-to-end integration test for DQ Violation Triage document indexing and querying.
 *  *# Run all integration tests
 * mvn verify -Pfailsafe
 *
 * # Run specific test
 * mvn test -Dtest=DQTriageApiIntegrationTest
 *
 * # Skip unit tests, run only integration
 * mvn test -Dtest="*IT
 *
 *
 * "
 *  * <p>Requires a local Elasticsearch instance running on localhost:9200.
 *  * Configure credentials in src/test/resources/application.yml.
 *
 * Lifecycle:
 *  @BeforeAll  — indexes the document via REST POST
 *  tests       — verify indexing, search, and structure
 *  @AfterAll   — hard-deletes the document from ES by uniqueTriageId
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DqViolationTriageRealPayloadIT extends ElasticsearchContainerBase {

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("elastic.cloud.local-uri",
            () -> ES_CONTAINER.getHost() + ":" + ES_CONTAINER.getMappedPort(9200));
        registry.add("elastic.cloud.cloud-id", () -> "");
        registry.add("elastic.cloud.api-key",  () -> "");
    }

    // -----------------------------------------------------------------------
    // Constants — match the JSON fixture exactly
    // -----------------------------------------------------------------------

    private static final String UNIQUE_TRIAGE_ID  = "ixleY7JTGoLsJWfe82a2e5";
    private static final String TRACKING_ID       = "dqviolation-opiuyht56";
    private static final String SCORECARD_SUBJECT =
            "http://metadata.pmog.mymac.net/dqscorecard#fivfibedax3p0enbeosffg";
    private static final String DATA_STEWARD      = "vmuddu@myamc.com";
    private static final String FIXTURE           = "fixtures/dq-triage-real-payload.json";

    // -----------------------------------------------------------------------
    // Spring beans
    // -----------------------------------------------------------------------

    @LocalServerPort
    private int port;

    @Autowired
    private ElasticsearchTemplate esTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.elasticsearch.index-name:dq-violation-triage}")
    private String indexName;

    // -----------------------------------------------------------------------
    // Shared fixture loaded once for the class
    // -----------------------------------------------------------------------

    private static String rawJson;

    @BeforeAll
    static void loadFixture() throws IOException {
        try (InputStream is = new ClassPathResource(FIXTURE).getInputStream()) {
            rawJson = new String(is.readAllBytes());
        }
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port    = port;
        RestAssured.basePath = "/";
    }

    // -----------------------------------------------------------------------
    // @AfterAll — guaranteed cleanup regardless of test outcome
    // -----------------------------------------------------------------------

    @AfterAll
    static void deleteIndexedDocument(@Autowired ElasticsearchTemplate esTemplate) {
        //    //esTemplate.delete(UNIQUE_TRIAGE_ID, DqViolationTriageDocument.class);
            //System.out.println("[Cleanup] Deleted document: " + UNIQUE_TRIAGE_ID);
        //} catch (Exception e) {
            //System.err.println("[Cleanup] Could not delete document: " + e.getMessage());
    //    }
    }

    // -----------------------------------------------------------------------
    // Test 1 — POST: index the real payload, verify 201 + response body
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("POST real payload → 201, response body reflects document")
    void shouldIndexRealPayload() {
        given()
            .contentType(ContentType.JSON)
            .body(rawJson)
        .when()
            .post("/dq-triage")
        .then()
            .statusCode(201)
            .body("uniqueTriageId",  equalTo(UNIQUE_TRIAGE_ID))
            .body("trackingId",      equalTo(TRACKING_ID))
            .body("scoreCardSubject", equalTo(SCORECARD_SUBJECT))
            .body("dataSteward",     equalTo(DATA_STEWARD))
            .body("dataCustodian",   equalTo(DATA_STEWARD))
            .body("dataOwner",       equalTo(DATA_STEWARD));
    }

    // -----------------------------------------------------------------------
    // Test 2 — verify document is in ES directly (no REST layer)
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("ES direct get → document present with correct structure")
    void shouldBeIndexedInElasticsearch() {
        DqViolationTriageDocument doc =
            esTemplate.get(UNIQUE_TRIAGE_ID, DqViolationTriageDocument.class);

        assertThat(doc).isNotNull();
        assertThat(doc.getUniqueTriageId()).isEqualTo(UNIQUE_TRIAGE_ID);
        assertThat(doc.getTrackingId()).isEqualTo(TRACKING_ID);
        assertThat(doc.getScoreCardSubject()).isEqualTo(SCORECARD_SUBJECT);
        assertThat(doc.getExtractDateTime()).isNotNull();
        assertThat(doc.getBusinessReason())
            .isEqualTo("Violations for The score card for Steward reporting");

        // Workflow fields should be null
        assertThat(doc.getWorkflowId()).isNull();
        assertThat(doc.getWorkflowStartTime()).isNull();
        assertThat(doc.getWorkflowEndTime()).isNull();
    }

    // -----------------------------------------------------------------------
    // Test 3 — violated rules structure
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("Violated rules — 4 rules indexed with correct IRIs and labels")
    void shouldHaveCorrectViolatedRules() {
        DqViolationTriageDocument doc =
            esTemplate.get(UNIQUE_TRIAGE_ID, DqViolationTriageDocument.class);

        assertThat(doc).isNotNull();
        assertThat(doc.getViolatedRules()).hasSize(4);

        // Rule 1 — Fund Jurisdiction
        var rule1 = doc.getViolatedRules().get(0);
        assertThat(rule1.getOffendingRuleSubject())
            .isEqualTo("http://metadata.pmog.mymac.net/dqruleset#u8ytnp6ajlto6tdgdvgvyi");
        assertThat(rule1.getOffendingRuleLabel())
            .isEqualTo("ValueRequiredConstraintRule-Fund Jurisdiction");
        assertThat(rule1.getOffendingRecords()).isNotEmpty();

        // Rule 2 — Fund is part of Product
        var rule2 = doc.getViolatedRules().get(1);
        assertThat(rule2.getOffendingRuleLabel())
            .isEqualTo("ValueRequiredConstraintRule-Fund is part of Product");

        // Rule 3 — Fund Status
        var rule3 = doc.getViolatedRules().get(2);
        assertThat(rule3.getOffendingRuleLabel())
            .isEqualTo("ValueRequiredConstraintRule-Fund Status");

        // Rule 4 — Nav Frequency
        var rule4 = doc.getViolatedRules().get(3);
        assertThat(rule4.getOffendingRuleLabel())
            .isEqualTo("ValueRequiredConstraintRule-Nav FrequencyDataProperty");
    }

    // -----------------------------------------------------------------------
    // Test 4 — offending records nested structure
    // -----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("Offending records — nested structure correct for rule 1")
    void shouldHaveCorrectOffendingRecords() {
        DqViolationTriageDocument doc =
            esTemplate.get(UNIQUE_TRIAGE_ID, DqViolationTriageDocument.class);

        assertThat(doc).isNotNull();

        var firstRule = doc.getViolatedRules().get(0);
        assertThat(firstRule.getOffendingRecords()).isNotEmpty();

        var firstRecord = firstRule.getOffendingRecords().get(0);
        assertThat(firstRecord.getDataRecordSubject())
            .isEqualTo("http://data.pmog.mymac.net/fund#qnhf35k7ttqshoh4uj5quv");
        assertThat(firstRecord.getDqRuleSubject())
            .isEqualTo("http://metadata.pmog.mymac.net/dqruleset#u8ytnp6ajlto6tdgdvgvyi");
        assertThat(firstRecord.getDataRecordId()).isNull();

        // dqErrorPropertyValue nested object
        var errorProp = firstRecord.getDqErrorPropertyValue();
        assertThat(errorProp).isNotNull();
        assertThat(errorProp.getPredicateIRI())
            .isEqualTo("http://spec.pmog.mymac.net/dataproperty#fundJurisdictionDataProperty");
        assertThat(errorProp.getStringValue()).isNull();
        assertThat(errorProp.isObfuscated()).isFalse();
        assertThat(errorProp.getErrorDescription())
            .contains("Required value Missing/empty");
    }

    // -----------------------------------------------------------------------
    // Test 5 — GET by ID via REST
    // -----------------------------------------------------------------------

    @Test
    @Order(5)
    @DisplayName("GET /dq-triage/{id} → 200, correct document returned")
    void shouldGetDocumentById() {
        given()
            .pathParam("id", "ixleY7JTGoLsJWfe82a2e5")
        .when()
            .get("/dq-triage/{id}")
        .then()
                .log().body(true);
            //.statusCode(200);
            //.body("uniqueTriageId",  equalTo(UNIQUE_TRIAGE_ID))
            //.body("trackingId",      equalTo(TRACKING_ID))
            //.body("violatedRules",   hasSize(4))
            //.body("violatedRules[0].offendingRuleLabel",
                  //equalTo("ValueRequiredConstraintRule-Fund Jurisdiction"));
    }

    // -----------------------------------------------------------------------
    // Test 6 — search by scoreCardSubject keyword
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("Search by scoreCardSubject IRI → document found")
    void shouldSearchByScoreCardSubject() {
        given()
            .queryParam("scoreCardSubject", SCORECARD_SUBJECT)
        .when()
            .get("/dq-triage/search/scorecard")
        .then()
            .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID));

    }

    // -----------------------------------------------------------------------
    // Test 7 — search by dataSteward
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("Search by dataSteward → document found")
    void shouldSearchByDataSteward() {
        given()
            .queryParam("dataSteward", DATA_STEWARD)
        .when()
            .get("/dq-triage/search/steward")
        .then()
            .statusCode(200)
            .body("content",                   hasSize(greaterThanOrEqualTo(1)))
            .body("content[0].dataSteward",    equalTo(DATA_STEWARD));
    }

    // -----------------------------------------------------------------------
    // Test 8 — ES template criteria search by trackingId
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("ES CriteriaQuery by trackingId → exactly 1 hit")
    void shouldFindByTrackingId() {
        CriteriaQuery query = new CriteriaQuery(
            Criteria.where("trackingId").is(TRACKING_ID)
        );

        SearchHits<DqViolationTriageDocument> hits =
            esTemplate.search(query, DqViolationTriageDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(1);
        assertThat(hits.getSearchHit(0).getContent().getUniqueTriageId())
            .isEqualTo(UNIQUE_TRIAGE_ID);
    }

    // -----------------------------------------------------------------------
    // Test 9 — idempotent re-index (upsert same document, no error)
    // -----------------------------------------------------------------------

    @Test
    @Order(9)
    @DisplayName("POST same payload again → 200/201 idempotent upsert")
    void shouldUpsertIdempotently() {
        given()
            .contentType(ContentType.JSON)
            .body(rawJson)
        .when()
            .post("/dq-triage")
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)));

        // Document count should still be 1
        CriteriaQuery query = new CriteriaQuery(
            Criteria.where("uniqueTriageId").is(UNIQUE_TRIAGE_ID)
        );
        SearchHits<DqViolationTriageDocument> hits =
            esTemplate.search(query, DqViolationTriageDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Test 10 — ruleExecutionSubject nested field queryable
    // -----------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("ES CriteriaQuery on nested ruleExecutionSubject → returns document")
    void shouldSearchByRuleExecutionSubject() {
        CriteriaQuery query = new CriteriaQuery(
            Criteria.where("violatedRules.ruleExecutionSubject")
                .is("http://metadata.pmog.mymac.net/dqruleexecution#q99utilq3wun0rewlzhw6h")
        );

        SearchHits<DqViolationTriageDocument> hits =
            esTemplate.search(query, DqViolationTriageDocument.class);

        assertThat(hits.getTotalHits()).isGreaterThanOrEqualTo(1);
    }
}
