package com.example.search;

import com.example.search.document.BulkIndexResult;
import com.example.search.document.DqViolationTriageDocument;
import com.example.search.document.OffendingRecord;
import com.example.search.document.ViolatedRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.search.DqTriageTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for model builders and fixture consistency.
 * No Spring context required.
 */
@DisplayName("Domain model tests")
class DomainModelTest {

    @Test
    @DisplayName("sampleDocument() has correct top-level fields")
    void sampleDocumentFields() {
        DqViolationTriageDocument doc = sampleDocument();

        assertThat(doc.getUniqueTriageId()).isEqualTo(TRIAGE_ID_1);
        assertThat(doc.getTrackingId()).isEqualTo(TRACKING_ID_1);
        assertThat(doc.getScoreCardSubject()).isEqualTo(SCORECARD_IRI);
        assertThat(doc.getDataSteward()).isEqualTo(DATA_STEWARD);
        assertThat(doc.getExtractDateTime())
                .isEqualTo(LocalDateTime.of(2026, 5, 25, 14, 30, 0));
        assertThat(doc.getBusinessReason()).isNotBlank();
    }

    @Test
    @DisplayName("sampleDocument() has two violated rules")
    void sampleDocumentRuleCount() {
        DqViolationTriageDocument doc = sampleDocument();
        assertThat(doc.getViolatedRules()).hasSize(2);
    }

    @Test
    @DisplayName("jurisdictionRule() has correct offending records")
    void jurisdictionRuleRecords() {
        ViolatedRule rule = jurisdictionRule();

        assertThat(rule.getOffendingRuleLabel()).isEqualTo(RULE_LABEL_JURISDICTION);
        assertThat(rule.getOffendingRecords()).hasSize(2);
        assertThat(rule.getOffendingRecords())
                .extracting(OffendingRecord::getDataRecordSubject)
                .containsExactlyInAnyOrder(FUND_IRI_1, FUND_IRI_2);
    }

    @Test
    @DisplayName("OffendingRecord contains correct error property value")
    void offendingRecordErrorValue() {
        OffendingRecord record = offendingFund1Jurisdiction();

        assertThat(record.getDataRecordSubject()).isEqualTo(FUND_IRI_1);
        assertThat(record.getDqErrorPropertyValue()).isNotNull();
        assertThat(record.getDqErrorPropertyValue().getPredicateIRI())
                .isEqualTo(PREDICATE_JURISDICTION);
        assertThat(record.getDqErrorPropertyValue().getStringValue()).isNull();
        assertThat(record.getDqErrorPropertyValue().isObfuscated()).isFalse();
        assertThat(record.getDqErrorPropertyValue().getErrorDescription()).isNotBlank();
    }

    @Test
    @DisplayName("BulkIndexResult.isFullSuccess() is correct")
    void bulkResultFullSuccess() {
        BulkIndexResult full = BulkIndexResult.builder()
                .totalRequested(5).successCount(5).failures(List.of()).build();
        assertThat(full.isFullSuccess()).isTrue();

        BulkIndexResult partial = BulkIndexResult.builder()
                .totalRequested(5).successCount(3)
                .failures(List.of(
                        BulkIndexResult.FailedDocument.builder()
                                .documentId("x").errorReason("err").build()))
                .build();
        assertThat(partial.isFullSuccess()).isFalse();
    }

    @Test
    @DisplayName("bulkDocuments() generates unique IDs")
    void bulkDocumentsUniqueIds() {
        List<DqViolationTriageDocument> docs = DqTriageTestFixtures.bulkDocuments(10);
        long distinctIds = docs.stream()
                .map(DqViolationTriageDocument::getUniqueTriageId)
                .distinct()
                .count();
        assertThat(distinctIds).isEqualTo(10);
    }
}
