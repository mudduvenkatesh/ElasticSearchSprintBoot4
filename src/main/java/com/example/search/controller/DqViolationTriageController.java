package com.example.search.controller;


import com.example.search.document.BulkIndexResult;
import com.example.search.document.DqViolationTriageDocument;
import com.example.search.service.DqViolationTriageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST API for DQ violation triage document operations.
 *
 * <p>Base path: {@code /api/dq-triage}
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/dq-triage")
@RequiredArgsConstructor
public class DqViolationTriageController {

    private final DqViolationTriageService service;

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    /**
     * GET /dq-triage/{uniqueTriageId}
     * Retrieve a single document by its unique triage id.
     */
    @GetMapping("/{uniqueTriageId}")
    public ResponseEntity<DqViolationTriageDocument> getById(
            @PathVariable @NotBlank String uniqueTriageId) {

        log.debug("GET /dq-triage/{}", uniqueTriageId);
        return ResponseEntity.ok(service.getById(uniqueTriageId));
    }

    /**
     * GET /dq-triage/tracking/{trackingId}
     * Retrieve by business tracking id.
     */
    @GetMapping("/tracking/{trackingId}")
    public ResponseEntity<DqViolationTriageDocument> getByTrackingId(
            @PathVariable @NotBlank String trackingId) {

        Optional<DqViolationTriageDocument> doc = service.findByTrackingId(trackingId);
        return doc.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /dq-triage
     * Index (create/upsert) a single document.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DqViolationTriageDocument create(
            @RequestBody @Valid DqViolationTriageDocument document) {

        log.debug("POST /dq-triage id={}", document.getUniqueTriageId());
        return service.save(document);
    }

    /**
     * PUT /dq-triage/{uniqueTriageId}
     * Replace / update an existing document.
     */
    @PutMapping("/{uniqueTriageId}")
    public DqViolationTriageDocument update(
            @PathVariable @NotBlank String uniqueTriageId,
            @RequestBody @Valid DqViolationTriageDocument document) {

        document.setUniqueTriageId(uniqueTriageId);
        return service.save(document);
    }

    /**
     * DELETE /dq-triage/{uniqueTriageId}
     */
    @DeleteMapping("/{uniqueTriageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @NotBlank String uniqueTriageId) {
        log.info("DELETE /dq-triage/{}", uniqueTriageId);
        service.deleteById(uniqueTriageId);
    }

    // ------------------------------------------------------------------
    // Bulk indexing
    // ------------------------------------------------------------------

    /**
     * POST /dq-triage/bulk
     * Bulk-index a list of documents.
     *
     * <p>Returns a {@link BulkIndexResult} with success/failure counts.
     * HTTP 207 (Multi-Status) is returned regardless of partial failures;
     * callers should inspect the response body.
     */
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.MULTI_STATUS)
    public BulkIndexResult bulkIndex(
            @RequestBody List<@Valid DqViolationTriageDocument> documents) {

        log.info("POST /dq-triage/bulk – {} documents", documents.size());
        return service.bulkIndex(documents);
    }

    // ------------------------------------------------------------------
    // Search endpoints
    // ------------------------------------------------------------------

    /**
     * GET /dq-triage/search/scorecard?scoreCardSubject=...
     */
    @GetMapping("/search/scorecard")
    public List<DqViolationTriageDocument> findByScoreCard(
            @RequestParam @NotBlank String scoreCardSubject) {
        return service.findByScoreCardSubject(scoreCardSubject);
    }

    /**
     * GET /dq-triage/search/steward?dataSteward=...&page=0&size=20
     */
    @GetMapping("/search/steward")
    public Page<DqViolationTriageDocument> findBySteward(
            @RequestParam @NotBlank String dataSteward,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {

        return service.findByDataSteward(dataSteward,
                PageRequest.of(page, size, Sort.by("extractDateTime").descending()));
    }

    /**
     * GET /dq-triage/search/date-range?from=...&to=...
     */
    @GetMapping("/search/date-range")
    public Page<DqViolationTriageDocument> findByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.findByExtractDateRange(from, to,
                PageRequest.of(page, size, Sort.by("extractDateTime").descending()));
    }

    /**
     * GET /dq-triage/search?q=...
     * Full-text search on businessReason.
     */
    @GetMapping("/search")
    public Page<DqViolationTriageDocument> search(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.searchByBusinessReason(q, PageRequest.of(page, size));
    }

    /**
     * GET /dq-triage/search/rule-label?label=...
     */
    @GetMapping("/search/rule-label")
    public List<DqViolationTriageDocument> findByRuleLabel(
            @RequestParam @NotBlank String label) {
        return service.findByViolatedRuleLabel(label);
    }

    /**
     * GET /dq-triage/search/offending-record?dataRecordIri=...
     */
    @GetMapping("/search/offending-record")
    public List<DqViolationTriageDocument> findByOffendingRecord(
            @RequestParam @NotBlank String dataRecordIri) {
        return service.findByOffendingDataRecord(dataRecordIri);
    }

    /**
     * GET /dq-triage/search/predicate?predicateIRI=...
     */
    @GetMapping("/search/predicate")
    public List<DqViolationTriageDocument> findByPredicate(
            @RequestParam @NotBlank String predicateIRI) {
        return service.findByPredicateIRI(predicateIRI);
    }

    /**
     * GET /dq-triage/search/high-severity?minRecords=10&from=...&to=...
     */
    @GetMapping("/search/high-severity")
    public Page<DqViolationTriageDocument> findHighSeverity(
            @RequestParam(defaultValue = "10") @Min(1) int minRecords,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.findHighSeverityInDateRange(minRecords, from, to, PageRequest.of(page, size));
    }
}
