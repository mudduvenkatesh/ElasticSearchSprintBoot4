package com.example.search.service;

import com.example.search.config.ElasticsearchProperties;
import com.example.search.document.DqViolationTriageDocument;
import com.example.search.document.ViolatedRule;
import com.example.search.exception.DocumentNotFoundException;
import com.example.search.repository.DqViolationTriageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexedObjectInformation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for DQ violation triage document operations.
 *
 * <p>Provides:
 * <ul>
 *   <li>Single-document save and retrieval</li>
 *   <li>Batched bulk indexing via {@link ElasticsearchOperations#bulkIndex}</li>
 *   <li>Derived-field computation before indexing</li>
 *   <li>Search / query delegation to the repository</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DqViolationTriageService {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DqViolationTriageRepository repository;
    private final ElasticsearchOperations esOps;
    private final ElasticsearchProperties esProps;

    // ------------------------------------------------------------------
    // Single document operations
    // ------------------------------------------------------------------

    /**
     * Retrieve a document by its {@code uniqueTriageId} (_id).
     *
     * @throws DocumentNotFoundException if no document with that id exists
     */
    public DqViolationTriageDocument getById(String uniqueTriageId) {
        Assert.hasText(uniqueTriageId, "uniqueTriageId must not be blank");
        return repository.findById(uniqueTriageId)
                .orElseThrow(() -> new DocumentNotFoundException(
                        "DqViolationTriageDocument not found for id: " + uniqueTriageId));
    }

    /**
     * Find by tracking ID (may differ from document _id).
     */
    public Optional<DqViolationTriageDocument> findByTrackingId(String trackingId) {
        Assert.hasText(trackingId, "trackingId must not be blank");
        return repository.findByTrackingId(trackingId);
    }

    /**
     * Save (index/upsert) a single document.
     * Computes derived fields before persisting.
     *
     * @param doc the document to save
     * @return the saved document (with ES-generated metadata)
     */
    public DqViolationTriageDocument save(DqViolationTriageDocument doc) {
        Assert.notNull(doc, "Document must not be null");
        Assert.hasText(doc.getUniqueTriageId(), "uniqueTriageId must not be blank");

        enrichDocument(doc);
        DqViolationTriageDocument saved = repository.save(doc);
        log.info("Indexed document id={} trackingId={}", saved.getUniqueTriageId(), saved.getTrackingId());
        return saved;
    }

    /**
     * Delete a document by id.
     *
     * @throws DocumentNotFoundException if the document does not exist
     */
    public void deleteById(String uniqueTriageId) {
        Assert.hasText(uniqueTriageId, "uniqueTriageId must not be blank");
        if (!repository.existsById(uniqueTriageId)) {
            throw new DocumentNotFoundException(
                    "Cannot delete: document not found for id: " + uniqueTriageId);
        }
        repository.deleteById(uniqueTriageId);
        log.info("Deleted document id={}", uniqueTriageId);
    }

    // ------------------------------------------------------------------
    // Bulk indexing
    // ------------------------------------------------------------------

    /**
     * Bulk-index a list of documents, processing them in batches of
     * {@code app.elasticsearch.bulk.batch-size}.
     *
     * <p>Each document is enriched (derived fields) before indexing.
     * Failed items are collected and returned in the {@link }
     * without aborting the entire batch.
     *
     * @return aggregated result with success/failure counts
     */
//    public BulkIndexResult bulkIndex(List<DqViolationTriageDocument> documents) {
//        Assert.notNull(documents, "Document list must not be null");
//
//        if (documents.isEmpty()) {
//            log.warn("bulkIndex called with empty list – nothing to do");
//            return BulkIndexResult.builder()
//                    .totalRequested(0)
//                    .successCount(0)
//                    .failures(Collections.emptyList())
//                    .build();
//        }
//
//        int batchSize = esProps.getBulk().getBatchSize();
//        int total = documents.size();
//        int successCount = 0;
//        List<BulkIndexResult.FailedDocument> failures = new ArrayList<>();
//
//        log.info("Starting bulk index of {} documents (batch size={})", total, batchSize);
//
//        // Partition into batches
//        for (int offset = 0; offset < total; offset += batchSize) {
//            int end = Math.min(offset + batchSize, total);
//            List<DqViolationTriageDocument> batch = documents.subList(offset, end);
//
//            log.debug("Processing batch [{}-{}] of {}", offset, end - 1, total);
//
//            try {
//                List<IndexQuery> queries = batch.stream()
//                        .peek(this::enrichDocument)
//                        .map(doc -> new IndexQueryBuilder()
//                                .withId(doc.getUniqueTriageId())
//                                .withObject(doc)
//                                .build())
//                        .collect(Collectors.toList());
//
//                BulkOptions bulkOptions = BulkOptions.builder()
//                        .withRefreshPolicy(resolveBulkRefreshPolicy())
//                        .build();
//
//                List<IndexedObjectInformation> results =
//                        esOps.bulkIndex(queries, bulkOptions,
//                                IndexCoordinates.of(esProps.getIndexName()));
//
//                // Tally successes (failed items returned with null id or error)
//                for (IndexedObjectInformation info : results) {
//                    if (info.id() != null) {
//                        successCount++;
//                    } else {
//                        failures.add(BulkIndexResult.FailedDocument.builder()
//                                .documentId("unknown")
//                                .errorReason("Null id returned from bulk response")
//                                .build());
//                    }
//                }
//
//                log.debug("Batch [{}-{}] indexed: {} ok", offset, end - 1, results.size());
//
//            } catch (Exception ex) {
//
//
//                log.error("Batch [{}-{}] failed: {}", offset, end - 1, ex.getMessage(), ex);
//                // Mark every document in this batch as failed
//                batch.forEach(doc -> failures.add(
//                        BulkIndexResult.FailedDocument.builder()
//                                .documentId(doc.getUniqueTriageId())
//                                .errorReason(ex.getMessage())
//                                .build()));
//            }
//        }
//
//        BulkIndexResult result = BulkIndexResult.builder()
//                .totalRequested(total)
//                .successCount(successCount)
//                .failures(failures)
//                .build();
//
//        log.info("Bulk index complete: {}/{} succeeded, {} failed",
//                successCount, total, failures.size());
//
//        return result;
//    }

    // ------------------------------------------------------------------
    // Search / query methods
    // ------------------------------------------------------------------

    public List<DqViolationTriageDocument> findByScoreCardSubject(String scoreCardIri) {
        Assert.hasText(scoreCardIri, "scoreCardSubject must not be blank");
        return repository.findByScoreCardSubject(scoreCardIri);
    }

    public Page<DqViolationTriageDocument> findByDataSteward(
            String dataSteward, Pageable pageable) {
        Assert.hasText(dataSteward, "dataSteward must not be blank");
        return repository.findByDataSteward(dataSteward, pageable);
    }

    public Page<DqViolationTriageDocument> findByExtractDateRange(
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Assert.notNull(from, "from date must not be null");
        Assert.notNull(to, "to date must not be null");
        return repository.findByExtractDateTimeBetween(from, to, pageable);
    }

    public Page<DqViolationTriageDocument> searchByBusinessReason(
            String text, Pageable pageable) {
        Assert.hasText(text, "search text must not be blank");
        return repository.searchByBusinessReason(text, pageable);
    }

    public List<DqViolationTriageDocument> findByViolatedRuleLabel(String label) {
        Assert.hasText(label, "rule label must not be blank");
        return repository.findByViolatedRuleLabel(label);
    }

    public List<DqViolationTriageDocument> findByOffendingDataRecord(String dataRecordIri) {
        Assert.hasText(dataRecordIri, "dataRecordIri must not be blank");
        return repository.findByOffendingDataRecord(dataRecordIri);
    }

    public List<DqViolationTriageDocument> findByPredicateIRI(String predicateIRI) {
        Assert.hasText(predicateIRI, "predicateIRI must not be blank");
        return repository.findByPredicateIRI(predicateIRI);
    }

    public Page<DqViolationTriageDocument> findHighSeverityInDateRange(
            int minRecords, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return repository.findHighSeverityInDateRange(
                minRecords,
                from.format(ISO_FMT),
                to.format(ISO_FMT),
                pageable);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Populate derived / audit fields before indexing:
     * <ul>
     *   <li>{@code totalViolatedRules} – size of the violatedRules list</li>
     *   <li>{@code totalOffendingRecords} – sum of offending records across rules</li>
     *   <li>{@code offendingRecordCount} per rule – size of that rule's list</li>
     *   <li>{@code offendingRuleLabelKeyword} – copy of label for keyword aggregation</li>
     *   <li>{@code indexedAt} – current UTC timestamp</li>
     * </ul>
     */
    private void enrichDocument(DqViolationTriageDocument doc) {
        if (doc.getIndexedAt() == null) {
            doc.setIndexedAt(LocalDateTime.now());
        }

        List<ViolatedRule> rules = doc.getViolatedRules();
        if (CollectionUtils.isEmpty(rules)) {
            //doc.setTotalViolatedRules(0);
            //doc.setTotalOffendingRecords(0);
            return;
        }

        int totalRecords = 0;
        for (ViolatedRule rule : rules) {
            // Denormalise keyword copy of label
            rule.setOffendingRuleLabelKeyword(rule.getOffendingRuleLabel());

            // Compute offendingRecordCount if not already set
            int ruleCount = CollectionUtils.isEmpty(rule.getOffendingRecords())
                    ? 0 : rule.getOffendingRecords().size();
            if (rule.getOffendingRecordCount() == null) {
                rule.setOffendingRecordCount(ruleCount);
            }
        }

        //doc.setTotalViolatedRules(rules.size());
        //doc.setTotalOffendingRecords(totalRecords);
    }

    /**
     * Resolve the {@link org.springframework.data.elasticsearch.core.RefreshPolicy}
     * enum from the string configured in {@code app.elasticsearch.bulk.refresh-policy}.
     */
    private org.springframework.data.elasticsearch.core.RefreshPolicy resolveBulkRefreshPolicy() {
        try {
            return org.springframework.data.elasticsearch.core.RefreshPolicy
                    .valueOf(esProps.getBulk().getRefreshPolicy().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown refresh policy '{}', falling back to WAIT_UNTIL",
                    esProps.getBulk().getRefreshPolicy());
            return org.springframework.data.elasticsearch.core.RefreshPolicy.WAIT_UNTIL;
        }
    }
}
