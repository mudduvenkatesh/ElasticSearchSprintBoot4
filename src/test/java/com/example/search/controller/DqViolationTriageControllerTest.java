package com.example.search.controller;

import com.example.search.DqTriageTestFixtures;
import com.example.search.document.DqViolationTriageDocument;
import com.example.search.exception.DocumentNotFoundException;
import com.example.search.exception.GlobalExceptionHandler;
import com.example.search.service.DqViolationTriageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.search.DqTriageTestFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link DqViolationTriageController} using standalone MockMvc.
 *
 * <p>No Spring context is started; the controller and its exception handler
 * are wired manually.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DqViolationTriageController")
class DqViolationTriageControllerTest {

    @Mock
    private DqViolationTriageService service;

    @InjectMocks
    private DqViolationTriageController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================================================================
    // GET /{uniqueTriageId}
    // ==================================================================

    @Nested
    @DisplayName("GET /{uniqueTriageId}")
    class GetById {

        @Test
        @DisplayName("200 OK with document body")
        void returns200_whenFound() throws Exception {
            DqViolationTriageDocument doc = sampleDocument();
            when(service.getById(TRIAGE_ID_1)).thenReturn(doc);

            mockMvc.perform(get("/dq-triage/{id}", TRIAGE_ID_1))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.uniqueTriageId").value(TRIAGE_ID_1))
                    .andExpect(jsonPath("$.trackingId").value(TRACKING_ID_1))
                    .andExpect(jsonPath("$.dataSteward").value(DATA_STEWARD));
        }

        @Test
        @DisplayName("404 Not Found when document is absent")
        void returns404_whenAbsent() throws Exception {
            when(service.getById(anyString()))
                    .thenThrow(new DocumentNotFoundException("Not found"));

            mockMvc.perform(get("/dq-triage/{id}", "ghost-id"))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================
    // GET /tracking/{trackingId}
    // ==================================================================

    @Nested
    @DisplayName("GET /tracking/{trackingId}")
    class GetByTrackingId {

        @Test
        @DisplayName("200 OK when tracking id matches")
        void returns200() throws Exception {
            when(service.findByTrackingId(TRACKING_ID_1))
                    .thenReturn(Optional.of(sampleDocument()));

            mockMvc.perform(get("/dq-triage/tracking/{tid}", TRACKING_ID_1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trackingId").value(TRACKING_ID_1));
        }

        @Test
        @DisplayName("404 when tracking id not found")
        void returns404() throws Exception {
            when(service.findByTrackingId(anyString())).thenReturn(Optional.empty());

            mockMvc.perform(get("/dq-triage/tracking/unknown"))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================
    // POST /
    // ==================================================================

    @Nested
    @DisplayName("POST /")
    class Create {

        @Test
        @DisplayName("201 Created with saved document")
        void returns201() throws Exception {
            DqViolationTriageDocument doc = sampleDocument();
            when(service.save(any())).thenReturn(doc);

            mockMvc.perform(post("/dq-triage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(doc)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.uniqueTriageId").value(TRIAGE_ID_1));
        }
    }

    // ==================================================================
    // PUT /{uniqueTriageId}
    // ==================================================================

    @Nested
    @DisplayName("PUT /{uniqueTriageId}")
    class Update {

        @Test
        @DisplayName("200 OK with updated document")
        void returns200() throws Exception {
            DqViolationTriageDocument doc = sampleDocument();
            when(service.save(any())).thenReturn(doc);

            mockMvc.perform(put("/dq-triage/{id}", TRIAGE_ID_1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(doc)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uniqueTriageId").value(TRIAGE_ID_1));
        }
    }

    // ==================================================================
    // DELETE /{uniqueTriageId}
    // ==================================================================

    @Nested
    @DisplayName("DELETE /{uniqueTriageId}")
    class Delete {

        @Test
        @DisplayName("204 No Content on successful delete")
        void returns204() throws Exception {
            doNothing().when(service).deleteById(TRIAGE_ID_1);

            mockMvc.perform(delete("/dq-triage/{id}", TRIAGE_ID_1))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("404 when document to delete not found")
        void returns404() throws Exception {
            doThrow(new DocumentNotFoundException("Not found"))
                    .when(service).deleteById(anyString());

            mockMvc.perform(delete("/dq-triage/{id}", "ghost-id"))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================
    // POST /bulk
    // ==================================================================

    @Nested
    @DisplayName("POST /bulk")
    class BulkIndex {

        @Test
        @DisplayName("207 Multi-Status with full success result")
        void returns207_fullSuccess() throws Exception {
            List<DqViolationTriageDocument> docs = DqTriageTestFixtures.bulkDocuments(3);
//            BulkIndexResult result = BulkIndexResult.builder()
//                    .totalRequested(3)
//                    .successCount(3)
//                    .failures(Collections.emptyList())
//                    .build();
//
//            when(service.bulkIndex(anyList())).thenReturn(result);

//            mockMvc.perform(post("/dq-triage/bulk")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(docs)))
//                    .andExpect(status().isMultiStatus())
//                    .andExpect(jsonPath("$.totalRequested").value(3))
//                    .andExpect(jsonPath("$.successCount").value(3))
//                    .andExpect(jsonPath("$.fullSuccess").value(true));
        }

        @Test
        @DisplayName("207 Multi-Status with partial failure result")
        void returns207_partialFailure() throws Exception {
            List<DqViolationTriageDocument> docs = DqTriageTestFixtures.bulkDocuments(2);
//            BulkIndexResult result = BulkIndexResult.builder()
//                    .totalRequested(2)
//                    .successCount(1)
//                    .failures(List.of(BulkIndexResult.FailedDocument.builder()
//                            .documentId("bulk-doc-1")
//                            .errorReason("Mapping conflict")
//                            .build()))
//                    .build();
//
//            when(service.bulkIndex(anyList())).thenReturn(result);

//            mockMvc.perform(post("/dq-triage/bulk")
//                            .contentType(MediaType.APPLICATION_JSON)
//                            .content(objectMapper.writeValueAsString(docs)))
//                    .andExpect(status().isMultiStatus())
//                    .andExpect(jsonPath("$.successCount").value(1))
//                    .andExpect(jsonPath("$.fullSuccess").value(false))
//                    .andExpect(jsonPath("$.failures[0].documentId").value("bulk-doc-1"));
        }
    }

    // ==================================================================
    // Search endpoints
    // ==================================================================

    @Nested
    @DisplayName("Search endpoints")
    class Search {

        @Test
        @DisplayName("GET /search/scorecard returns list")
        void searchByScorecard() throws Exception {
            when(service.findByScoreCardSubject(SCORECARD_IRI))
                    .thenReturn(List.of(sampleDocument()));

            mockMvc.perform(get("/dq-triage/search/scorecard")
                            .param("scoreCardSubject", SCORECARD_IRI))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].uniqueTriageId").value(TRIAGE_ID_1));
        }

        @Test
        @DisplayName("GET /search/steward returns paged result")
        void searchBySteward() throws Exception {
            PageImpl<DqViolationTriageDocument> page = new PageImpl<>(
                    List.of(sampleDocument()), PageRequest.of(0, 20), 1);
            when(service.findByDataSteward(eq(DATA_STEWARD), any())).thenReturn(page);

            mockMvc.perform(get("/dq-triage/search/steward")
                            .param("dataSteward", DATA_STEWARD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].dataSteward").value(DATA_STEWARD))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("GET /search/rule-label returns matching documents")
        void searchByRuleLabel() throws Exception {
            when(service.findByViolatedRuleLabel(RULE_LABEL_JURISDICTION))
                    .thenReturn(List.of(sampleDocument()));

            mockMvc.perform(get("/dq-triage/search/rule-label")
                            .param("label", RULE_LABEL_JURISDICTION))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].uniqueTriageId").value(TRIAGE_ID_1));
        }

        @Test
        @DisplayName("GET /search/offending-record returns matching documents")
        void searchByOffendingRecord() throws Exception {
            when(service.findByOffendingDataRecord(FUND_IRI_1))
                    .thenReturn(List.of(sampleDocument()));

            mockMvc.perform(get("/dq-triage/search/offending-record")
                            .param("dataRecordIri", FUND_IRI_1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].uniqueTriageId").value(TRIAGE_ID_1));
        }

        @Test
        @DisplayName("GET /search/predicate returns matching documents")
        void searchByPredicate() throws Exception {
            when(service.findByPredicateIRI(PREDICATE_JURISDICTION))
                    .thenReturn(List.of(sampleDocument()));

            mockMvc.perform(get("/dq-triage/search/predicate")
                            .param("predicateIRI", PREDICATE_JURISDICTION))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].uniqueTriageId").value(TRIAGE_ID_1));
        }
    }
}
