package com.example.search.repository;

import com.example.search.DqTriageTestFixtures;
import com.example.search.document.DqViolationTriageDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
// After (Spring Boot 4 / 3.4+)
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static com.example.search.DqTriageTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Repository contract tests that verify the query method signatures and
 * delegation behaviour using a mocked repository bean.
 *
 * <p>These tests do NOT require a running Elasticsearch node — they verify
 * the repository interface contract only.  Integration tests (against a real
 * or Testcontainers ES node) would extend this with actual data assertions.
 */
@SpringBootTest
@DisplayName("DqViolationTriageRepository – contract tests")
@ActiveProfiles("test")
class DqViolationTriageRepositoryTest {

    @MockitoBean
    private DqViolationTriageRepository repository;

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findById returns empty Optional when document absent")
    void findById_empty() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());

        Optional<DqViolationTriageDocument> result = repository.findById("ghost");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById returns document when present")
    void findById_present() {
        DqViolationTriageDocument doc = DqTriageTestFixtures.sampleDocument();
        when(repository.findById(TRIAGE_ID_1)).thenReturn(Optional.of(doc));

        Optional<DqViolationTriageDocument> result = repository.findById(TRIAGE_ID_1);
        assertThat(result).isPresent();
        assertThat(result.get().getTrackingId()).isEqualTo(TRACKING_ID_1);
    }

    // ------------------------------------------------------------------
    // save / findByTrackingId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save returns the saved document")
    void save_returnsDocument() {
        DqViolationTriageDocument doc = DqTriageTestFixtures.sampleDocument();
        when(repository.save(doc)).thenReturn(doc);

        DqViolationTriageDocument saved = repository.save(doc);
        assertThat(saved.getUniqueTriageId()).isEqualTo(TRIAGE_ID_1);
        verify(repository).save(doc);
    }

    @Test
    @DisplayName("findByTrackingId finds the right document")
    void findByTrackingId() {
        DqViolationTriageDocument doc = DqTriageTestFixtures.sampleDocument();
        when(repository.findByTrackingId(TRACKING_ID_1)).thenReturn(Optional.of(doc));

        Optional<DqViolationTriageDocument> result =
                repository.findByTrackingId(TRACKING_ID_1);
        assertThat(result).isPresent();
        assertThat(result.get().getUniqueTriageId()).isEqualTo(TRIAGE_ID_1);
    }

    // ------------------------------------------------------------------
    // findByScoreCardSubject
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findByScoreCardSubject returns correct list")
    void findByScoreCardSubject() {
        List<DqViolationTriageDocument> docs = List.of(DqTriageTestFixtures.sampleDocument());
        when(repository.findByScoreCardSubject(SCORECARD_IRI)).thenReturn(docs);

        List<DqViolationTriageDocument> result =
                repository.findByScoreCardSubject(SCORECARD_IRI);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScoreCardSubject()).isEqualTo(SCORECARD_IRI);
    }

    // ------------------------------------------------------------------
    // findByDataSteward (paged)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findByDataSteward returns paged results")
    void findByDataSteward() {
        PageRequest pageable = PageRequest.of(0, 10);
        DqViolationTriageDocument doc = DqTriageTestFixtures.sampleDocument();
        Page<DqViolationTriageDocument> page = new PageImpl<>(List.of(doc), pageable, 1);
        when(repository.findByDataSteward(DATA_STEWARD, pageable)).thenReturn(page);

        Page<DqViolationTriageDocument> result =
                repository.findByDataSteward(DATA_STEWARD, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getDataSteward()).isEqualTo(DATA_STEWARD);
    }

    // ------------------------------------------------------------------
    // Custom @Query methods
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findByViolatedRuleLabel returns matching docs")
    void findByViolatedRuleLabel() {
        List<DqViolationTriageDocument> docs = List.of(DqTriageTestFixtures.sampleDocument());
        when(repository.findByViolatedRuleLabel(RULE_LABEL_JURISDICTION)).thenReturn(docs);

        List<DqViolationTriageDocument> result =
                repository.findByViolatedRuleLabel(RULE_LABEL_JURISDICTION);
        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("findByOffendingDataRecord returns matching docs")
    void findByOffendingDataRecord() {
        List<DqViolationTriageDocument> docs = List.of(DqTriageTestFixtures.sampleDocument());
        when(repository.findByOffendingDataRecord(FUND_IRI_1)).thenReturn(docs);

        List<DqViolationTriageDocument> result =
                repository.findByOffendingDataRecord(FUND_IRI_1);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findByPredicateIRI returns matching docs")
    void findByPredicateIRI() {
        List<DqViolationTriageDocument> docs = List.of(DqTriageTestFixtures.sampleDocument());
        when(repository.findByPredicateIRI(PREDICATE_JURISDICTION)).thenReturn(docs);

        List<DqViolationTriageDocument> result =
                repository.findByPredicateIRI(PREDICATE_JURISDICTION);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findHighSeverityInDateRange returns paged results")
    void findHighSeverityInDateRange() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<DqViolationTriageDocument> page =
                new PageImpl<>(List.of(DqTriageTestFixtures.sampleDocument()), pageable, 1);
        when(repository.findHighSeverityInDateRange(anyInt(), anyString(), anyString(), any()))
                .thenReturn(page);

        Page<DqViolationTriageDocument> result =
                repository.findHighSeverityInDateRange(
                        5, "2026-01-01T00:00:00", "2026-12-31T23:59:59", pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Bulk save
    // ------------------------------------------------------------------

    @Test
    @DisplayName("saveAll persists all documents")
    void saveAll() {
        List<DqViolationTriageDocument> docs = DqTriageTestFixtures.bulkDocuments(5);
        when(repository.saveAll(docs)).thenReturn(docs);

        Iterable<DqViolationTriageDocument> saved = repository.saveAll(docs);
        assertThat(saved).hasSize(5);
        verify(repository).saveAll(docs);
    }

    // ------------------------------------------------------------------
    // existsById / deleteById
    // ------------------------------------------------------------------

    @Test
    @DisplayName("existsById returns true when document exists")
    void existsById_true() {
        when(repository.existsById(TRIAGE_ID_1)).thenReturn(true);
        assertThat(repository.existsById(TRIAGE_ID_1)).isTrue();
    }

    @Test
    @DisplayName("existsById returns false when document absent")
    void existsById_false() {
        when(repository.existsById("missing")).thenReturn(false);
        assertThat(repository.existsById("missing")).isFalse();
    }

    @Test
    @DisplayName("deleteById invokes repository delete")
    void deleteById() {
        doNothing().when(repository).deleteById(TRIAGE_ID_1);
        repository.deleteById(TRIAGE_ID_1);
        verify(repository).deleteById(TRIAGE_ID_1);
    }
}
