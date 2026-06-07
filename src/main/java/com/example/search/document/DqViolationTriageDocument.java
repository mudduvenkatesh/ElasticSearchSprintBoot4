package com.example.search.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level Elasticsearch document for a DQ violation triage report.
 *
 * <p>Maps to the JSON payload produced by the DQ scorecard execution pipeline.
 * The document is stored in the {@code dq-violation-triage} index (configurable
 * via {@code app.elasticsearch.index-name}).
 *
 * <p><b>Index mapping highlights:</b>
 * <ul>
 *   <li>{@code trackingId} / {@code uniqueTriageId} — keyword, used as the ES {@code _id}</li>
 *   <li>{@code scoreCardSubject} — keyword IRI</li>
 *   <li>{@code extractDateTime} — date field for time-range queries</li>
 *   <li>{@code violatedRules} — object array; each element has a {@code nested}
 *       {@code offendingRecords} list to support inner-document filtering</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "#{@elasticsearchProperties.indexName}",
          createIndex = true)
@Setting(shards = 1, replicas = 1)
@Mapping(mappingPath = "es-settings/dq-violation-triage-mapping.json")
public class DqViolationTriageDocument {

    // -----------------------------------------------------------------------
    // Identity
    // -----------------------------------------------------------------------

    /**
     * Elasticsearch document ID — mapped from {@code uniqueTriageId}.
     * Using the uniqueTriageId as _id ensures idempotent upserts.
     */
    @Id
    @Field(name = "uniqueTriageId", type = FieldType.Keyword)
    private String uniqueTriageId;

    /**
     * A separate tracking identifier (may differ from uniqueTriageId in some flows).
     */
    @Field(name = "trackingId", type = FieldType.Keyword)
    private String trackingId;

    // -----------------------------------------------------------------------
    // Scorecard / context
    // -----------------------------------------------------------------------

    /**
     * IRI of the DQ scorecard that produced this triage document.
     * Example: {@code http://metadata.pmog.mymac.net/dqscorecard#fivfibedax3p0enbeosffg}
     */
    @Field(name = "scoreCardSubject", type = FieldType.Keyword)
    private String scoreCardSubject;

    /**
     * Timestamp when the violations were extracted from the scorecard execution.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field(name = "extractDateTime", type = FieldType.Date,
           format = DateFormat.date_hour_minute_second)
    private LocalDateTime extractDateTime;

    /**
     * Human-readable reason / description of why this triage was triggered.
     */
    @Field(name = "businessReason", type = FieldType.Text)
    private String businessReason;

    // -----------------------------------------------------------------------
    // Workflow tracking (optional — null when not in an active workflow)
    // -----------------------------------------------------------------------

    @Field(name = "workflowId", type = FieldType.Keyword)
    private String workflowId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field(name = "workflowStartTime", type = FieldType.Date,
           format = DateFormat.date_hour_minute_second)
    private LocalDateTime workflowStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field(name = "workflowEndTime", type = FieldType.Date,
           format = DateFormat.date_hour_minute_second)
    private LocalDateTime workflowEndTime;

    // -----------------------------------------------------------------------
    // Data stewardship
    // -----------------------------------------------------------------------

    /** Email / IRI of the data steward responsible for resolving these violations. */
    @Field(name = "dataSteward", type = FieldType.Keyword)
    private String dataSteward;

    /** Email / IRI of the data custodian. */
    @Field(name = "dataCustodian", type = FieldType.Keyword)
    private String dataCustodian;

    /** Email / IRI of the data owner. */
    @Field(name = "dataOwner", type = FieldType.Keyword)
    private String dataOwner;

    // -----------------------------------------------------------------------
    // Violation payload
    // -----------------------------------------------------------------------

    /**
     * List of DQ rules that were violated in this scorecard run.
     * Each entry holds the rule metadata and the full list of offending records.
     */
    @Builder.Default
    @Field(name = "violatedRules", type = FieldType.Object)
    private List<ViolatedRule> violatedRules = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Derived / computed fields (populated by the service layer before indexing)
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Audit
    // -----------------------------------------------------------------------

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Field(name = "indexedAt", type = FieldType.Date,
           format = DateFormat.date_hour_minute_second)
    private LocalDateTime indexedAt;
}
