package com.example.search;

import com.example.search.document.DqErrorPropertyValue;
import com.example.search.document.DqViolationTriageDocument;
import com.example.search.document.OffendingRecord;
import com.example.search.document.ViolatedRule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reusable test fixture factory.
 *
 * <p>All builder methods produce fully-populated objects that mirror the
 * real JSON document from the specification, so every test starts from a
 * consistent, meaningful baseline.
 */
public final class DqTriageTestFixtures {

    // Matches the real sample document
    public static final String TRIAGE_ID_1   = "ixleY7JTGoLsJWfe82a2e5";
    public static final String TRACKING_ID_1 = "dqviolation-opiuyht56";
    public static final String SCORECARD_IRI =
            "http://metadata.pmog.mymac.net/dqscorecard#fivfibedax3p0enbeosffg";
    public static final String DATA_STEWARD  = "vmuddu@myamc.com";

    public static final String FUND_IRI_1 =
            "http://data.pmog.mymac.net/fund#qnhf35k7ttqshoh4uj5quv";
    public static final String FUND_IRI_2 =
            "http://data.pmog.mymac.net/fund#1occhlykvpwpxjmyvnysoc";

    public static final String RULE_SUBJECT_JURISDICTION =
            "http://metadata.pmog.mymac.net/dqruleset#u8ytnp6ajlto6tdgdvgvyi";
    public static final String RULE_LABEL_JURISDICTION =
            "ValueRequiredConstraintRule-Fund Jurisdiction";
    public static final String PREDICATE_JURISDICTION =
            "http://spec.pmog.mymac.net/dataproperty#fundJurisdictionDataProperty";

    public static final String RULE_SUBJECT_STATUS =
            "http://metadata.pmog.mymac.net/dqruleset#1iwyred2qmkg8jgkwgelji";
    public static final String RULE_LABEL_STATUS =
            "ValueRequiredConstraintRule-Fund Status";
    public static final String PREDICATE_STATUS =
            "http://spec.pmog.mymac.net/dataproperty#fundStatusDataProperty";

    private DqTriageTestFixtures() { /* utility */ }

    // ------------------------------------------------------------------
    // DqErrorPropertyValue
    // ------------------------------------------------------------------

    public static DqErrorPropertyValue missingJurisdictionError() {
        return DqErrorPropertyValue.builder()
                .predicateIRI(PREDICATE_JURISDICTION)
                .stringValue(null)
                .obfuscated(false)
                .errorDescription("Required value Missing/empty for " + RULE_LABEL_JURISDICTION)
                .build();
    }

    public static DqErrorPropertyValue missingStatusError() {
        return DqErrorPropertyValue.builder()
                .predicateIRI(PREDICATE_STATUS)
                .stringValue(null)
                .obfuscated(false)
                .errorDescription("Required value Missing/empty for " + RULE_LABEL_STATUS)
                .build();
    }

    // ------------------------------------------------------------------
    // OffendingRecord
    // ------------------------------------------------------------------

    public static OffendingRecord offendingFund1Jurisdiction() {
        return OffendingRecord.builder()
                .dataRecordSubject(FUND_IRI_1)
                .dqRuleSubject(RULE_SUBJECT_JURISDICTION)
                .dataRecordId(null)
                .dqErrorPropertyValue(missingJurisdictionError())
                .build();
    }

    public static OffendingRecord offendingFund2Jurisdiction() {
        return OffendingRecord.builder()
                .dataRecordSubject(FUND_IRI_2)
                .dqRuleSubject(RULE_SUBJECT_JURISDICTION)
                .dataRecordId(null)
                .dqErrorPropertyValue(missingJurisdictionError())
                .build();
    }

    public static OffendingRecord offendingFund1Status() {
        return OffendingRecord.builder()
                .dataRecordSubject(FUND_IRI_1)
                .dqRuleSubject(RULE_SUBJECT_STATUS)
                .dataRecordId(null)
                .dqErrorPropertyValue(missingStatusError())
                .build();
    }

    // ------------------------------------------------------------------
    // ViolatedRule
    // ------------------------------------------------------------------

    public static ViolatedRule jurisdictionRule() {
        return ViolatedRule.builder()
                .ruleExecutionSubject(
                        "http://metadata.pmog.mymac.net/dqruleexecution#q99utilq3wun0rewlzhw6h")
                .offendingRuleSubject(RULE_SUBJECT_JURISDICTION)
                .offendingRuleLabel(RULE_LABEL_JURISDICTION)
                .offendingRuleLabelKeyword(RULE_LABEL_JURISDICTION)
                .offendingRecords(List.of(offendingFund1Jurisdiction(), offendingFund2Jurisdiction()))
                //.offendingRecordCount(2)
                .build();
    }

    public static ViolatedRule statusRule() {
        return ViolatedRule.builder()
                .ruleExecutionSubject(
                        "http://metadata.pmog.mymac.net/dqruleexecution#vanxbbnucw03yolkx5lxci")
                .offendingRuleSubject(RULE_SUBJECT_STATUS)
                .offendingRuleLabel(RULE_LABEL_STATUS)
                .offendingRuleLabelKeyword(RULE_LABEL_STATUS)
                .offendingRecords(List.of(offendingFund1Status()))
                //.offendingRecordCount(1)
                .build();
    }

    // ------------------------------------------------------------------
    // Full document
    // ------------------------------------------------------------------

    /** Returns the canonical test document (mirrors the JSON in the spec). */
    public static DqViolationTriageDocument sampleDocument() {
        return DqViolationTriageDocument.builder()
                .uniqueTriageId(TRIAGE_ID_1)
                .trackingId(TRACKING_ID_1)
                .scoreCardSubject(SCORECARD_IRI)
                .extractDateTime(LocalDateTime.of(2026, 5, 25, 14, 30, 0))
                .businessReason("Violations for The score card for Steward reporting")
                .workflowId(null)
                .dataSteward(DATA_STEWARD)
                .dataCustodian(DATA_STEWARD)
                .dataOwner(DATA_STEWARD)
                .violatedRules(List.of(jurisdictionRule(), statusRule()))
                .build();
    }

    /** Returns a minimal document (only required fields populated). */
    public static DqViolationTriageDocument minimalDocument(String id) {
        return DqViolationTriageDocument.builder()
                .uniqueTriageId(id)
                .trackingId("tracking-" + id)
                .scoreCardSubject(SCORECARD_IRI)
                .extractDateTime(LocalDateTime.now())
                .businessReason("Minimal test document")
                .dataSteward(DATA_STEWARD)
                .dataCustodian(DATA_STEWARD)
                .dataOwner(DATA_STEWARD)
                .violatedRules(List.of(jurisdictionRule()))
                .build();
    }

    /** Build a list of N distinct minimal documents for bulk-index tests. */
    public static List<DqViolationTriageDocument> bulkDocuments(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> minimalDocument("bulk-doc-" + i))
                .toList();
    }
}
