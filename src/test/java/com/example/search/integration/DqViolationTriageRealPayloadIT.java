package com.example.search.integration;

import com.example.search.ElasticsearchContainerBase;
import com.example.search.document.DqViolationTriageDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end integration tests for the DQ Violation Triage REST API.
 *
 * Lifecycle:
 *   Order 1   — POST: index the real fixture document
 *   Orders 2-16 — verify each API endpoint (expected vs actual from fixture)
 *   Order 17  — DELETE: remove document and confirm 404
 *   @AfterAll — safety-net cleanup via ES template
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

    // -------------------------------------------------------------------------
    // Expected values — taken directly from dq-triage-real-payload.json
    // -------------------------------------------------------------------------

    private static final String UNIQUE_TRIAGE_ID   = "ixleY7JTGoLsJWfe82a2e5";
    private static final String TRACKING_ID        = "dqviolation-opiuyht56";
    private static final String SCORECARD_SUBJECT  =
            "http://metadata.pmog.mymac.net/dqscorecard#fivfibedax3p0enbeosffg";
    private static final String DATA_STEWARD       = "vmuddu@myamc.com";
    private static final String BUSINESS_REASON    = "Violations for The score card for Steward reporting";
    private static final String EXTRACT_DATE_TIME  = "2026-05-25T14:30:00";

    // violated rules
    private static final String RULE_EXEC_1  =
            "http://metadata.pmog.mymac.net/dqruleexecution#q99utilq3wun0rewlzhw6h";
    private static final String RULE_SUBJ_1  =
            "http://metadata.pmog.mymac.net/dqruleset#u8ytnp6ajlto6tdgdvgvyi";
    private static final String RULE_LABEL_1 = "ValueRequiredConstraintRule-Fund Jurisdiction";
    private static final String RULE_LABEL_2 = "ValueRequiredConstraintRule-Fund is part of Product";
    private static final String RULE_LABEL_3 = "ValueRequiredConstraintRule-Fund Status";
    private static final String RULE_LABEL_4 = "ValueRequiredConstraintRule-Nav FrequencyDataProperty";

    // offending records (rule 1 has 2)
    private static final String FUND_IRI_1 =
            "http://data.pmog.mymac.net/fund#qnhf35k7ttqshoh4uj5quv";
    private static final String FUND_IRI_2 =
            "http://data.pmog.mymac.net/fund#1occhlykvpwpxjmyvnysoc";

    // predicates
    private static final String PREDICATE_JURISDICTION =
            "http://spec.pmog.mymac.net/dataproperty#fundJurisdictionDataProperty";
    private static final String PREDICATE_PRODUCT =
            "http://spec.pmog.mymac.net/dataproperty#belongsToProductDataProperty";

    private static final String FIXTURE = "fixtures/dq-triage-real-payload.json";

    // -------------------------------------------------------------------------
    // Golden-file infrastructure
    // -------------------------------------------------------------------------

    private static final ObjectMapper MAPPER =
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Set<String> DYNAMIC_FIELDS = Set.of("indexedAt");
    private static final String EXPECTED_DIR = "src/test/resources/expected";

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    @LocalServerPort  private int port;
    @Autowired private ElasticsearchTemplate esTemplate;

    private static String rawJson;

    @BeforeAll
    static void loadFixture() throws IOException {
        try (InputStream is = new ClassPathResource(FIXTURE).getInputStream()) {
            rawJson = new String(is.readAllBytes());
        }
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port     = port;
        RestAssured.basePath = "/";
    }

    @AfterAll
    static void cleanup(@Autowired ElasticsearchTemplate esTemplate) {
        try { esTemplate.delete(UNIQUE_TRIAGE_ID, DqViolationTriageDocument.class); }
        catch (Exception ignored) {}
    }

    // -------------------------------------------------------------------------
    // Golden-file helper methods
    // -------------------------------------------------------------------------

    /**
     * Capture-on-first-run golden file assertion.
     * If the expected file does not exist it writes the actual JSON (minus
     * dynamic fields like indexedAt) to it so the developer can review and
     * commit it. On every subsequent run it asserts with JSONAssert LENIENT
     * (actual may have extra fields; expected is the subset we care about).
     */
    private void assertMatchesExpected(String filename, String actualJson) throws Exception {
        Path filePath = Paths.get(EXPECTED_DIR, filename);
        if (!Files.exists(filePath)) {
            JsonNode tree = MAPPER.readTree(actualJson);
            removeDynamicFields(tree);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, MAPPER.writeValueAsString(tree));
            return;  // first run: file written, no comparison yet
        }
        JSONAssert.assertEquals(Files.readString(filePath), actualJson, JSONCompareMode.LENIENT);
    }

    /** Recursively strips every field whose name is in DYNAMIC_FIELDS. */
    private void removeDynamicFields(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            DYNAMIC_FIELDS.forEach(obj::remove);
            node.fields().forEachRemaining(e -> removeDynamicFields(e.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::removeDynamicFields);
        }
    }

    // =========================================================================
    // 1. POST — index the real payload
    // =========================================================================

    @Test @Order(1)
    @DisplayName("POST /dq-triage → 201, response body matches fixture")
    void shouldIndexRealPayload() throws Exception {
        String actual = given()
            .contentType(ContentType.JSON)
            .body(rawJson)
        .when()
            .post("/dq-triage")
        .then()
            .statusCode(201)
            // expected                    actual (from response body)
            .body("uniqueTriageId",        equalTo(UNIQUE_TRIAGE_ID))
            .body("trackingId",            equalTo(TRACKING_ID))
            .body("scoreCardSubject",      equalTo(SCORECARD_SUBJECT))
            .body("businessReason",        equalTo(BUSINESS_REASON))
            .body("dataSteward",           equalTo(DATA_STEWARD))
            .body("dataCustodian",         equalTo(DATA_STEWARD))
            .body("dataOwner",             equalTo(DATA_STEWARD))
            .body("workflowId",            nullValue())
            .body("workflowStartTime",     nullValue())
            .body("workflowEndTime",       nullValue())
            .body("violatedRules",         hasSize(4))
            .body("indexedAt",             notNullValue())  // enriched by service
            .extract().body().asString();

        assertMatchesExpected("01-post-create.json", actual);
    }

    // =========================================================================
    // 2. GET by ID — REST API
    // =========================================================================

    @Test @Order(2)
    @DisplayName("GET /dq-triage/{id} → 200, all fields match fixture")
    void shouldGetDocumentById() throws Exception {
        String actual = given()
            .pathParam("id", UNIQUE_TRIAGE_ID)
        .when()
            .get("/dq-triage/{id}")
        .then()
            .statusCode(200)
            // top-level scalar fields
            .body("uniqueTriageId",   equalTo(UNIQUE_TRIAGE_ID))
            .body("trackingId",       equalTo(TRACKING_ID))
            .body("scoreCardSubject", equalTo(SCORECARD_SUBJECT))
            .body("businessReason",   equalTo(BUSINESS_REASON))
            .body("dataSteward",      equalTo(DATA_STEWARD))
            .body("dataCustodian",    equalTo(DATA_STEWARD))
            .body("dataOwner",        equalTo(DATA_STEWARD))
            // violated rules count + labels (order matches fixture)
            .body("violatedRules",                              hasSize(4))
            .body("violatedRules[0].offendingRuleLabel",        equalTo(RULE_LABEL_1))
            .body("violatedRules[1].offendingRuleLabel",        equalTo(RULE_LABEL_2))
            .body("violatedRules[2].offendingRuleLabel",        equalTo(RULE_LABEL_3))
            .body("violatedRules[3].offendingRuleLabel",        equalTo(RULE_LABEL_4))
            // rule 1 has 2 offending records
            .body("violatedRules[0].offendingRecords",          hasSize(2))
            .body("violatedRules[0].offendingRecords[0].dataRecordSubject", equalTo(FUND_IRI_1))
            .body("violatedRules[0].offendingRecords[1].dataRecordSubject", equalTo(FUND_IRI_2))
            // nested error property for rule 1, record 1
            .body("violatedRules[0].offendingRecords[0].dqErrorPropertyValue.predicateIRI",
                    equalTo(PREDICATE_JURISDICTION))
            .body("violatedRules[0].offendingRecords[0].dqErrorPropertyValue.obfuscated",
                    equalTo(false))
            .extract().body().asString();

        assertMatchesExpected("02-get-by-id.json", actual);
    }

    // =========================================================================
    // 3. GET by tracking ID — REST API
    // =========================================================================

    @Test @Order(3)
    @DisplayName("GET /dq-triage/tracking/{trackingId} → 200, correct document")
    void shouldGetByTrackingId() throws Exception {
        String actual = given()
            .pathParam("trackingId", TRACKING_ID)
        .when()
            .get("/dq-triage/tracking/{trackingId}")
        .then()
            .statusCode(200)
            .body("uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .body("trackingId",     equalTo(TRACKING_ID))
            .body("dataSteward",    equalTo(DATA_STEWARD))
            .extract().body().asString();

        assertMatchesExpected("03-get-by-tracking-id.json", actual);
    }

    // =========================================================================
    // 4. ES direct get — verify full document structure in index
    // =========================================================================

    @Test @Order(4)
    @DisplayName("ES direct get → document indexed with correct structure")
    void shouldBeIndexedInElasticsearch() {
        DqViolationTriageDocument doc =
            esTemplate.get(UNIQUE_TRIAGE_ID, DqViolationTriageDocument.class);

        assertThat(doc).isNotNull();
        assertThat(doc.getUniqueTriageId()).isEqualTo(UNIQUE_TRIAGE_ID);
        assertThat(doc.getTrackingId()).isEqualTo(TRACKING_ID);
        assertThat(doc.getScoreCardSubject()).isEqualTo(SCORECARD_SUBJECT);
        assertThat(doc.getBusinessReason()).isEqualTo(BUSINESS_REASON);
        assertThat(doc.getDataSteward()).isEqualTo(DATA_STEWARD);
        assertThat(doc.getDataCustodian()).isEqualTo(DATA_STEWARD);
        assertThat(doc.getDataOwner()).isEqualTo(DATA_STEWARD);
        assertThat(doc.getExtractDateTime()).isNotNull();
        assertThat(doc.getIndexedAt()).isNotNull();         // enriched by service
        assertThat(doc.getWorkflowId()).isNull();
        assertThat(doc.getWorkflowStartTime()).isNull();
        assertThat(doc.getWorkflowEndTime()).isNull();

        assertThat(doc.getViolatedRules()).hasSize(4);
        assertThat(doc.getViolatedRules().get(0).getOffendingRuleLabel()).isEqualTo(RULE_LABEL_1);
        assertThat(doc.getViolatedRules().get(1).getOffendingRuleLabel()).isEqualTo(RULE_LABEL_2);
        assertThat(doc.getViolatedRules().get(2).getOffendingRuleLabel()).isEqualTo(RULE_LABEL_3);
        assertThat(doc.getViolatedRules().get(3).getOffendingRuleLabel()).isEqualTo(RULE_LABEL_4);

        // rule 1: ruleExecution IRI and 2 offending records
        var rule1 = doc.getViolatedRules().get(0);
        assertThat(rule1.getRuleExecutionSubject()).isEqualTo(RULE_EXEC_1);
        assertThat(rule1.getOffendingRuleSubject()).isEqualTo(RULE_SUBJ_1);
        assertThat(rule1.getOffendingRecords()).hasSize(2);

        // record 1 of rule 1
        var rec1 = rule1.getOffendingRecords().get(0);
        assertThat(rec1.getDataRecordSubject()).isEqualTo(FUND_IRI_1);
        assertThat(rec1.getDqRuleSubject()).isEqualTo(RULE_SUBJ_1);
        assertThat(rec1.getDataRecordId()).isNull();
        assertThat(rec1.getDqErrorPropertyValue().getPredicateIRI()).isEqualTo(PREDICATE_JURISDICTION);
        assertThat(rec1.getDqErrorPropertyValue().getStringValue()).isNull();
        assertThat(rec1.getDqErrorPropertyValue().isObfuscated()).isFalse();
        assertThat(rec1.getDqErrorPropertyValue().getErrorDescription())
            .contains("Required value Missing/empty");

        // record 2 of rule 1
        var rec2 = rule1.getOffendingRecords().get(1);
        assertThat(rec2.getDataRecordSubject()).isEqualTo(FUND_IRI_2);
        assertThat(rec2.getDqErrorPropertyValue().getPredicateIRI()).isEqualTo(PREDICATE_JURISDICTION);

        // rules 2-4 have 1 offending record each
        assertThat(doc.getViolatedRules().get(1).getOffendingRecords()).hasSize(1);
        assertThat(doc.getViolatedRules().get(1).getOffendingRecords().get(0)
            .getDqErrorPropertyValue().getPredicateIRI()).isEqualTo(PREDICATE_PRODUCT);
        assertThat(doc.getViolatedRules().get(2).getOffendingRecords()).hasSize(1);
        assertThat(doc.getViolatedRules().get(3).getOffendingRecords()).hasSize(1);
    }

    // =========================================================================
    // 5. Search by scoreCardSubject
    // =========================================================================

    @Test @Order(5)
    @DisplayName("GET /search/scorecard → 200, returns document with matching scorecard IRI")
    void shouldSearchByScoreCardSubject() throws Exception {
        String actual = given()
            .queryParam("scoreCardSubject", SCORECARD_SUBJECT)
        .when()
            .get("/dq-triage/search/scorecard")
        .then()
            .statusCode(200)
            .body("$",                  hasSize(greaterThanOrEqualTo(1)))
            .body("[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .body("[0].scoreCardSubject", equalTo(SCORECARD_SUBJECT))
            .extract().body().asString();

        assertMatchesExpected("05-search-scorecard.json", actual);
    }

    // =========================================================================
    // 6. Search by dataSteward (paginated)
    // =========================================================================

    @Test @Order(6)
    @DisplayName("GET /search/steward → 200, paginated result contains document")
    void shouldSearchByDataSteward() throws Exception {
        String actual = given()
            .queryParam("dataSteward", DATA_STEWARD)
            .queryParam("page", 0)
            .queryParam("size", 10)
        .when()
            .get("/dq-triage/search/steward")
        .then()
            .statusCode(200)
            .body("content",                     hasSize(greaterThanOrEqualTo(1)))
            .body("content[0].uniqueTriageId",   equalTo(UNIQUE_TRIAGE_ID))
            .body("content[0].dataSteward",      equalTo(DATA_STEWARD))
            .body("totalElements",               greaterThanOrEqualTo(1))
            .body("size",                        equalTo(10))
            .extract().body().asString();

        assertMatchesExpected("06-search-steward-page.json", actual);
    }

    // =========================================================================
    // 7. Full-text search on businessReason
    // =========================================================================

    @Test @Order(7)
    @DisplayName("GET /search?q=Steward → 200, document found via full-text")
    void shouldSearchByBusinessReason() throws Exception {
        String actual = given()
            .queryParam("q", "Steward")
            .queryParam("page", 0)
            .queryParam("size", 10)
        .when()
            .get("/dq-triage/search")
        .then()
            .statusCode(200)
            .body("content",                   hasSize(greaterThanOrEqualTo(1)))
            .body("content[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .body("content[0].businessReason", equalTo(BUSINESS_REASON))
            .extract().body().asString();

        assertMatchesExpected("07-search-full-text-page.json", actual);
    }

    // =========================================================================
    // 8. Search by violated rule label
    // =========================================================================

    @Test @Order(8)
    @DisplayName("GET /search/rule-label?label=Fund Jurisdiction → 200, document found")
    void shouldSearchByRuleLabel() throws Exception {
        String actual = given()
            .queryParam("label", RULE_LABEL_1)
        .when()
            .get("/dq-triage/search/rule-label")
        .then()
            .statusCode(200)
            .body("$",                  hasSize(greaterThanOrEqualTo(1)))
            .body("[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .extract().body().asString();

        assertMatchesExpected("08-search-rule-label.json", actual);
    }

    // =========================================================================
    // 9. Search by offending record IRI (nested query)
    // =========================================================================

    @Test @Order(9)
    @DisplayName("GET /search/offending-record?dataRecordIri=FUND_IRI_1 → 200, document found")
    void shouldSearchByOffendingRecord() throws Exception {
        String actual = given()
            .queryParam("dataRecordIri", FUND_IRI_1)
        .when()
            .get("/dq-triage/search/offending-record")
        .then()
            .statusCode(200)
            .body("$",                  hasSize(greaterThanOrEqualTo(1)))
            .body("[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .extract().body().asString();

        assertMatchesExpected("09-search-offending-record.json", actual);
    }

    // =========================================================================
    // 10. Search by predicate IRI (double-nested query)
    // =========================================================================

    @Test @Order(10)
    @DisplayName("GET /search/predicate?predicateIRI=fundJurisdiction → 200, document found")
    void shouldSearchByPredicateIRI() throws Exception {
        String actual = given()
            .queryParam("predicateIRI", PREDICATE_JURISDICTION)
        .when()
            .get("/dq-triage/search/predicate")
        .then()
            .statusCode(200)
            .body("$",                  hasSize(greaterThanOrEqualTo(1)))
            .body("[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .extract().body().asString();

        assertMatchesExpected("10-search-predicate.json", actual);
    }

    // =========================================================================
    // 11. Date range search
    // =========================================================================

    @Test @Order(11)
    @DisplayName("GET /search/date-range → 200, document within 2026-05 range found")
    void shouldSearchByDateRange() throws Exception {
        // extractDateTime in fixture: 2026-05-25T14:30:00
        String actual = given()
            .queryParam("from", "2026-05-01T00:00:00")
            .queryParam("to",   "2026-06-01T00:00:00")
            .queryParam("page", 0)
            .queryParam("size", 10)
        .when()
            .get("/dq-triage/search/date-range")
        .then()
            .statusCode(200)
            .body("content",                   hasSize(greaterThanOrEqualTo(1)))
            .body("content[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .body("content[0].extractDateTime", notNullValue())
            .extract().body().asString();

        assertMatchesExpected("11-search-date-range-page.json", actual);
    }

    // =========================================================================
    // 12. High-severity search (rule 1 has 2 offending records → minRecords=2)
    // =========================================================================

    @Test @Order(12)
    @DisplayName("GET /search/high-severity?minRecords=2 → 200, document found")
    void shouldFindHighSeverityViolations() throws Exception {
        String actual = given()
            .queryParam("minRecords", 2)
            .queryParam("from",  "2026-05-01T00:00:00")
            .queryParam("to",    "2026-06-01T00:00:00")
            .queryParam("page",  0)
            .queryParam("size",  10)
        .when()
            .get("/dq-triage/search/high-severity")
        .then()
            .statusCode(200)
            .body("content",                   hasSize(greaterThanOrEqualTo(1)))
            .body("content[0].uniqueTriageId", equalTo(UNIQUE_TRIAGE_ID))
            .extract().body().asString();

        assertMatchesExpected("12-search-high-severity-page.json", actual);
    }

    // =========================================================================
    // 13. Idempotent re-index (upsert same document)
    // =========================================================================

    @Test @Order(13)
    @DisplayName("POST same payload again → 200/201, count still 1")
    void shouldUpsertIdempotently() throws Exception {
        String actual = given()
            .contentType(ContentType.JSON)
            .body(rawJson)
        .when()
            .post("/dq-triage")
        .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract().body().asString();

        assertMatchesExpected("13-upsert-response.json", actual);

        CriteriaQuery query = new CriteriaQuery(
            Criteria.where("uniqueTriageId").is(UNIQUE_TRIAGE_ID));
        SearchHits<DqViolationTriageDocument> hits =
            esTemplate.search(query, DqViolationTriageDocument.class);
        assertThat(hits.getTotalHits()).isEqualTo(1);
    }

    // =========================================================================
    // 14. PUT — update businessReason
    // =========================================================================

    @Test @Order(14)
    @DisplayName("PUT /dq-triage/{id} → 200, updated businessReason reflected")
    void shouldUpdateDocument() throws Exception {
        String updatedJson = rawJson.replace(
            BUSINESS_REASON,
            "Updated reason for testing"
        );

        String actual = given()
            .contentType(ContentType.JSON)
            .body(updatedJson)
            .pathParam("id", UNIQUE_TRIAGE_ID)
        .when()
            .put("/dq-triage/{id}")
        .then()
            .statusCode(200)
            .body("uniqueTriageId",  equalTo(UNIQUE_TRIAGE_ID))
            .body("businessReason",  equalTo("Updated reason for testing"))
            .extract().body().asString();

        assertMatchesExpected("14-put-update.json", actual);
    }

    // =========================================================================
    // 15. ES CriteriaQuery by trackingId
    // =========================================================================

    @Test @Order(15)
    @DisplayName("ES CriteriaQuery by trackingId → exactly 1 hit")
    void shouldFindByTrackingIdViaTemplate() {
        SearchHits<DqViolationTriageDocument> hits = esTemplate.search(
            new CriteriaQuery(Criteria.where("trackingId").is(TRACKING_ID)),
            DqViolationTriageDocument.class);

        assertThat(hits.getTotalHits()).isEqualTo(1);
        assertThat(hits.getSearchHit(0).getContent().getUniqueTriageId())
            .isEqualTo(UNIQUE_TRIAGE_ID);
    }

    // =========================================================================
    // 16. ES CriteriaQuery by ruleExecutionSubject (nested field)
    // =========================================================================

    @Test @Order(16)
    @DisplayName("ES CriteriaQuery on nested ruleExecutionSubject → returns document")
    void shouldSearchByRuleExecutionSubject() {
        SearchHits<DqViolationTriageDocument> hits = esTemplate.search(
            new CriteriaQuery(
                Criteria.where("violatedRules.ruleExecutionSubject").is(RULE_EXEC_1)),
            DqViolationTriageDocument.class);

        assertThat(hits.getTotalHits()).isGreaterThanOrEqualTo(1);
        assertThat(hits.getSearchHit(0).getContent().getUniqueTriageId())
            .isEqualTo(UNIQUE_TRIAGE_ID);
    }

    // =========================================================================
    // 17. DELETE — remove document, confirm 404 on subsequent GET
    // =========================================================================

    @Test @Order(17)
    @DisplayName("DELETE /dq-triage/{id} → 204, subsequent GET returns 404")
    void shouldDeleteDocument() {
        // delete
        given()
            .pathParam("id", UNIQUE_TRIAGE_ID)
        .when()
            .delete("/dq-triage/{id}")
        .then()
            .statusCode(204);

        // verify gone
        given()
            .pathParam("id", UNIQUE_TRIAGE_ID)
        .when()
            .get("/dq-triage/{id}")
        .then()
            .statusCode(404);
    }
}
