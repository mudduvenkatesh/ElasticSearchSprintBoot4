package com.example.search.repository;

import com.example.search.document.DqViolationTriageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data Elasticsearch repository for {@link DqViolationTriageDocument}.
 *
 * <p>Provides standard CRUD methods (from {@link ElasticsearchRepository}) plus
 * domain-specific query methods using both derived-query naming and explicit
 * {@link Query} annotations with ES query DSL.
 *
 * <p><b>Document ID</b>: the {@code uniqueTriageId} field is mapped to {@code @Id},
 * so all {@code findById} / {@code save} operations use it as the ES {@code _id}.
 */
@Repository
public interface DqViolationTriageRepository
        extends ElasticsearchRepository<DqViolationTriageDocument, String> {

    // ------------------------------------------------------------------
    // Derived query methods
    // ------------------------------------------------------------------

    /** Find by the trackingId field (may differ from the document _id). */
    Optional<DqViolationTriageDocument> findByTrackingId(String trackingId);

    /** Find all documents for a given scorecard IRI. */
    List<DqViolationTriageDocument> findByScoreCardSubject(String scoreCardSubject);

    /** Find documents assigned to a specific data steward (email/IRI). */
    Page<DqViolationTriageDocument> findByDataSteward(String dataSteward, Pageable pageable);

    /** Find documents within a given extraction time window. */
    Page<DqViolationTriageDocument> findByExtractDateTimeBetween(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    /** Find documents whose total violated rule count exceeds a threshold. */
    //ist<DqViolationTriageDocument> findByTotalViolatedRulesGreaterThan(int threshold);

    /** Find documents with a specific workflow ID. */
    List<DqViolationTriageDocument> findByWorkflowId(String workflowId);

    // ------------------------------------------------------------------
    // Custom @Query methods (Elasticsearch query DSL)
    // ------------------------------------------------------------------

    /**
     * Full-text search on the {@code businessReason} field.
     *
     * @param text     search text (tokenised with standard analyser)
     * @param pageable pagination
     */
    @Query("""
            {
              "match": {
                "businessReason": {
                  "query": "?0",
                  "operator": "AND"
                }
              }
            }
            """)
    Page<DqViolationTriageDocument> searchByBusinessReason(String text, Pageable pageable);

    /**
     * Find documents that contain a violated rule with the given offendingRuleLabel
     * (keyword exact match on the denormalised keyword field).
     *
     * @param ruleLabel exact label value
     */
    @Query("""
            {
              "nested": {
                "path": "violatedRules",
                "query": {
                  "term": {
                    "violatedRules.offendingRuleLabelKeyword": "?0"
                  }
                }
              }
            }
            """)
    List<DqViolationTriageDocument> findByViolatedRuleLabel(String ruleLabel);

    /**
     * Find documents that contain a specific offending data record (by IRI)
     * in any of their violated rules.
     *
     * <p>Uses a nested query to reach into the double-nested structure:
     * {@code violatedRules} → {@code offendingRecords}.
     *
     * @param dataRecordIri the IRI of the offending data record
     */
    @Query("""
            {
              "nested": {
                "path": "violatedRules",
                "query": {
                  "nested": {
                    "path": "violatedRules.offendingRecords",
                    "query": {
                      "term": {
                        "violatedRules.offendingRecords.dataRecordSubject": "?0"
                      }
                    }
                  }
                }
              }
            }
            """)
    List<DqViolationTriageDocument> findByOffendingDataRecord(String dataRecordIri);

    /**
     * Find documents that contain violations for a specific predicate IRI
     * (e.g. {@code fundJurisdictionDataProperty}).
     *
     * @param predicateIRI the full predicate IRI
     */
    @Query("""
            {
              "nested": {
                "path": "violatedRules",
                "query": {
                  "nested": {
                    "path": "violatedRules.offendingRecords",
                    "query": {
                      "term": {
                        "violatedRules.offendingRecords.dqErrorPropertyValue.predicateIRI": "?0"
                      }
                    }
                  }
                }
              }
            }
            """)
    List<DqViolationTriageDocument> findByPredicateIRI(String predicateIRI);

    /**
     * Find documents where total offending records exceeds a threshold,
     * ordered by severity, within a date range.
     *
     * @param minRecords minimum total offending records
     * @param from       start of extract date range (ISO string)
     * @param to         end of extract date range (ISO string)
     * @param pageable   pagination
     */
    @Query("""
            {
              "bool": {
                "must": [
                  {
                    "range": {
                      "totalOffendingRecords": { "gte": ?0 }
                    }
                  },
                  {
                    "range": {
                      "extractDateTime": {
                        "gte": "?1",
                        "lte": "?2"
                      }
                    }
                  }
                ]
              }
            }
            """)
    Page<DqViolationTriageDocument> findHighSeverityInDateRange(
            int minRecords, String from, String to, Pageable pageable);
}
