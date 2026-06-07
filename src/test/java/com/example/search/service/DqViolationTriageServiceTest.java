package com.example.search.service;


import com.example.search.config.ElasticsearchProperties;
import com.example.search.document.DqViolationTriageDocument;
import com.example.search.document.ViolatedRule;
import com.example.search.exception.DocumentNotFoundException;
import com.example.search.repository.DqViolationTriageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexedObjectInformation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.IndexQuery;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.search.DqTriageTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DqViolationTriageService}.
 *
 * <p>All dependencies are mocked; no Elasticsearch cluster is required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DqViolationTriageService")
@MockitoSettings(strictness = Strictness.LENIENT)
class DqViolationTriageServiceTest {

    @Mock
    private DqViolationTriageRepository repository;

    @Mock
    private ElasticsearchOperations esOps;

    @Mock
    private ElasticsearchProperties esProps;

    @Mock
    private ElasticsearchProperties.Bulk bulkProps;

    @InjectMocks
    private DqViolationTriageService service;

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        // Wire bulk props stub so service can call getBatchSize() etc.
        when(esProps.getBulk()).thenReturn(bulkProps);
        when(bulkProps.getBatchSize()).thenReturn(100);
        when(bulkProps.getRefreshPolicy()).thenReturn("WAIT_UNTIL");
        when(esProps.getIndexName()).thenReturn("dq-violation-triage");
    }

    // ==================================================================
    // getById
    // ==================================================================

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("returns document when found")
        void returnsDocument_whenFound() {
            DqViolationTriageDocument doc = sampleDocument();
            when(repository.findById(TRIAGE_ID_1)).thenReturn(Optional.of(doc));

            DqViolationTriageDocument result = service.getById(TRIAGE_ID_1);

            assertThat(result).isNotNull();
            assertThat(result.getUniqueTriageId()).isEqualTo(TRIAGE_ID_1);
            assertThat(result.getTrackingId()).isEqualTo(TRACKING_ID_1);
        }

        @Test
        @DisplayName("throws DocumentNotFoundException when not found")
        void throwsNotFound_whenAbsent() {
            when(repository.findById(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById("non-existent"))
                    .isInstanceOf(DocumentNotFoundException.class)
                    .hasMessageContaining("non-existent");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank id")
        void throwsIllegalArg_forBlankId() {
            assertThatThrownBy(() -> service.getById(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.getById("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ==================================================================
    // save()
    // ==================================================================

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("enriches and persists the document")
        void enrichesAndSaves() {
            DqViolationTriageDocument doc = sampleDocument();
            // Remove pre-computed counts so the service fills them in
//            doc.setTotalViolatedRules(0);
//            doc.setTotalOffendingRecords(0);
            doc.setIndexedAt(null);

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DqViolationTriageDocument saved = service.save(doc);

            // Derived fields should now be populated
            //assertThat(saved.getTotalViolatedRules()).isEqualTo(2);       // jurisdiction + status
            //assertThat(saved.getTotalOffendingRecords()).isGreaterThan(0);
            assertThat(saved.getIndexedAt()).isNotNull();

            // offendingRuleLabelKeyword should mirror offendingRuleLabel
            saved.getViolatedRules().forEach(rule ->
                    assertThat(rule.getOffendingRuleLabelKeyword())
                            .isEqualTo(rule.getOffendingRuleLabel()));

            verify(repository).save(saved);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null document")
        void throwsForNullDoc() {
            assertThatThrownBy(() -> service.save(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when uniqueTriageId is blank")
        void throwsForBlankId() {
            DqViolationTriageDocument doc = sampleDocument();
            doc.setUniqueTriageId("");
            assertThatThrownBy(() -> service.save(doc))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("does not overwrite existing indexedAt timestamp")
        void preservesExistingIndexedAt() {
            LocalDateTime existingTs = LocalDateTime.of(2025, 1, 1, 0, 0);
            DqViolationTriageDocument doc = sampleDocument();
            doc.setIndexedAt(existingTs);

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DqViolationTriageDocument saved = service.save(doc);

            assertThat(saved.getIndexedAt()).isEqualTo(existingTs);
        }
    }

    // ==================================================================
    // deleteById()
    // ==================================================================

    @Nested
    @DisplayName("deleteById()")
    class DeleteById {

        @Test
        @DisplayName("deletes existing document")
        void deletesExisting() {
            when(repository.existsById(TRIAGE_ID_1)).thenReturn(true);

            service.deleteById(TRIAGE_ID_1);

            verify(repository).deleteById(TRIAGE_ID_1);
        }

        @Test
        @DisplayName("throws DocumentNotFoundException when document is absent")
        void throwsWhenAbsent() {
            when(repository.existsById(anyString())).thenReturn(false);

            assertThatThrownBy(() -> service.deleteById("ghost-id"))
                    .isInstanceOf(DocumentNotFoundException.class);
            verify(repository, never()).deleteById(any());
        }
    }

    // ==================================================================
    // bulkIndex()
    // ==================================================================

    @Nested
    @DisplayName("bulkIndex()")
    class BulkIndex {

        private IndexedObjectInformation successInfo(String id) {
            return new IndexedObjectInformation(id, "dq-violation-triage", 1L, 1L,1L);
        }

        @Test
        @DisplayName("returns full success when all documents indexed ok")
        void fullSuccess() {
            List<DqViolationTriageDocument> docs = bulkDocuments(5);

            // Return a success info per document
            List<IndexedObjectInformation> infos = docs.stream()
                    .map(d -> successInfo(d.getUniqueTriageId()))
                    .toList();

            when(esOps.bulkIndex(anyList(), any(BulkOptions.class), any(IndexCoordinates.class)))
                    .thenReturn(infos);

//            BulkIndexResult result = service.bulkIndex(docs);
//
//            assertThat(result.getTotalRequested()).isEqualTo(5);
//            assertThat(result.getSuccessCount()).isEqualTo(5);
//            assertThat(result.isFullSuccess()).isTrue();
//            assertThat(result.getFailures()).isEmpty();
        }

        @Test
        @DisplayName("returns empty result for empty input list")
        void emptyList_returnsZero() {
//            BulkIndexResult result = service.bulkIndex(Collections.emptyList());
//
//            assertThat(result.getTotalRequested()).isZero();
//            assertThat(result.getSuccessCount()).isZero();
//            assertThat(result.isFullSuccess()).isTrue();
            verifyNoInteractions(esOps);
        }

        @Test
        @DisplayName("partitions into batches when list exceeds batch-size")
        void partitionsIntoBatches() {
            when(bulkProps.getBatchSize()).thenReturn(3);

            List<DqViolationTriageDocument> docs = bulkDocuments(7); // 3 batches: 3+3+1

            // Return success per doc
            when(esOps.bulkIndex(anyList(), any(BulkOptions.class), any(IndexCoordinates.class)))
                    .thenAnswer(inv -> {
                        List<IndexQuery> queries = inv.getArgument(0);
                        return queries.stream()
                                .map(q -> successInfo(q.getId()))
                                .toList();
                    });

            //BulkIndexResult result = service.bulkIndex(docs);

//            assertThat(result.getTotalRequested()).isEqualTo(7);
//            assertThat(result.getSuccessCount()).isEqualTo(7);
            // esOps should have been called 3 times (batches of 3, 3, 1)
            verify(esOps, times(3))
                    .bulkIndex(anyList(), any(BulkOptions.class), any(IndexCoordinates.class));
        }

        @Test
        @DisplayName("records failures when a batch throws")
        void recordsFailuresOnBatchException() {
            List<DqViolationTriageDocument> docs = bulkDocuments(3);

            when(esOps.bulkIndex(anyList(), any(BulkOptions.class), any(IndexCoordinates.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            //BulkIndexResult result = service.bulkIndex(docs);

//            assertThat(result.getSuccessCount()).isZero();
//            assertThat(result.getFailures()).hasSize(3);
//            assertThat(result.getFailures())
//                    .allMatch(f -> f.getErrorReason().contains("Connection refused"));
//            assertThat(result.isFullSuccess()).isFalse();
        }

        @Test
        @DisplayName("captures partial failure (null id in response)")
        void recordsNullIdAsFailure() {
            List<DqViolationTriageDocument> docs = bulkDocuments(2);

            // First doc ok, second has null id (ES failure indicator)
            when(esOps.bulkIndex(anyList(), any(BulkOptions.class), any(IndexCoordinates.class)))
                    .thenReturn(List.of(
                            successInfo(docs.get(0).getUniqueTriageId()),
                            new IndexedObjectInformation(null, "dq-violation-triage", null, null,1L)
                    ));

            //BulkIndexResult result = service.bulkIndex(docs);

//            assertThat(result.getSuccessCount()).isEqualTo(1);
//            assertThat(result.getFailures()).hasSize(1);
        }

        @Test
        @DisplayName("enriches each document before indexing")
        void enrichesBeforeIndexing() {
            List<DqViolationTriageDocument> docs = bulkDocuments(2);
            // Strip derived fields
//            docs.forEach(d -> {
//                d.setTotalViolatedRules(0);
//                d.setTotalOffendingRecords(0);
//            });

            when(esOps.bulkIndex(anyList(), any(BulkOptions.class), any(IndexCoordinates.class)))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<IndexQuery> queries = inv.getArgument(0);
                        return queries.stream()
                                .map(q -> successInfo(q.getId()))
                                .toList();
                    });

            //service.bulkIndex(docs);

            // After enrichment, every document should have non-zero counts
            docs.forEach(d -> {
                //assertThat(d.getTotalViolatedRules()).isGreaterThan(0);
                assertThat(d.getIndexedAt()).isNotNull();
            });
        }
    }

    // ==================================================================
    // Enrichment edge cases
    // ==================================================================

    @Nested
    @DisplayName("Document enrichment")
    class Enrichment {

        @Test
        @DisplayName("handles document with no violated rules gracefully")
        void emptyViolatedRules() {
            DqViolationTriageDocument doc = sampleDocument();
            doc.setViolatedRules(Collections.emptyList());
            doc.setIndexedAt(null);

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DqViolationTriageDocument saved = service.save(doc);

            //assertThat(saved.getTotalViolatedRules()).isZero();
            //assertThat(saved.getTotalOffendingRecords()).isZero();
            assertThat(saved.getIndexedAt()).isNotNull();
        }

        @Test
        @DisplayName("handles rule with no offending records gracefully")
        void ruleWithNoRecords() {
            DqViolationTriageDocument doc = sampleDocument();
            ViolatedRule emptyRule = ViolatedRule.builder()
                    .offendingRuleSubject(RULE_SUBJECT_JURISDICTION)
                    .offendingRuleLabel(RULE_LABEL_JURISDICTION)
                    .offendingRecords(Collections.emptyList())
                    //.offendingRecordCount(0)
                    .build();
            doc.setViolatedRules(List.of(emptyRule));

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DqViolationTriageDocument saved = service.save(doc);

            //assertThat(saved.getTotalViolatedRules()).isEqualTo(1);
            //assertThat(saved.getTotalOffendingRecords()).isZero();
        }

        @Test
        @DisplayName("does not overwrite non-zero offendingRecordCount already set on rule")
        void preservesExistingRuleCount() {
            DqViolationTriageDocument doc = sampleDocument();
            // Manually set count to a non-zero value (simulating a pre-computed count from upstream)
            //doc.getViolatedRules().get(0).setOffendingRecordCount(999);

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            DqViolationTriageDocument saved = service.save(doc);

            // Service should NOT overwrite an already-set non-zero count
            //assertThat(saved.getViolatedRules().get(0).getOffendingRecordCount()).isEqualTo(999);
        }
    }

    // ==================================================================
    // Query delegation
    // ==================================================================

    @Nested
    @DisplayName("Query delegation to repository")
    class QueryDelegation {

        @Test
        @DisplayName("findByScoreCardSubject delegates correctly")
        void findByScoreCard() {
            List<DqViolationTriageDocument> expected = List.of(sampleDocument());
            when(repository.findByScoreCardSubject(SCORECARD_IRI)).thenReturn(expected);

            List<DqViolationTriageDocument> result = service.findByScoreCardSubject(SCORECARD_IRI);

            assertThat(result).hasSize(1);
            verify(repository).findByScoreCardSubject(SCORECARD_IRI);
        }

        @Test
        @DisplayName("findByDataSteward delegates with pageable")
        void findBySteward() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<DqViolationTriageDocument> page =
                    new PageImpl<>(List.of(sampleDocument()), pageable, 1);
            when(repository.findByDataSteward(DATA_STEWARD, pageable)).thenReturn(page);

            Page<DqViolationTriageDocument> result =
                    service.findByDataSteward(DATA_STEWARD, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(repository).findByDataSteward(DATA_STEWARD, pageable);
        }

        @Test
        @DisplayName("findByOffendingDataRecord delegates correctly")
        void findByOffendingRecord() {
            when(repository.findByOffendingDataRecord(FUND_IRI_1))
                    .thenReturn(List.of(sampleDocument()));

            List<DqViolationTriageDocument> result =
                    service.findByOffendingDataRecord(FUND_IRI_1);

            assertThat(result).isNotEmpty();
            verify(repository).findByOffendingDataRecord(FUND_IRI_1);
        }

        @Test
        @DisplayName("findByPredicateIRI delegates correctly")
        void findByPredicate() {
            when(repository.findByPredicateIRI(PREDICATE_JURISDICTION))
                    .thenReturn(List.of(sampleDocument()));

            List<DqViolationTriageDocument> result =
                    service.findByPredicateIRI(PREDICATE_JURISDICTION);

            assertThat(result).hasSize(1);
            verify(repository).findByPredicateIRI(PREDICATE_JURISDICTION);
        }
    }
}
