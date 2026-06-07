package com.example.search.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Represents a single data record (identified by IRI) that violated a DQ rule.
 * Each offending record points to both the data record and the rule that was violated,
 * along with the specific property error detail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OffendingRecord {

    /**
     * IRI of the data record (e.g. a Fund individual in the knowledge graph).
     * Example: {@code http://data.pmog.mymac.net/fund#qnhf35k7ttqshoh4uj5quv}
     */
    @Field(name = "dataRecordSubject", type = FieldType.Keyword)
    private String dataRecordSubject;

    /**
     * IRI of the DQ rule that this record violated.
     *
     */
    @Field(name = "dqRuleSubject", type = FieldType.Keyword)
    private String dqRuleSubject;

    /**
     * Optional business/legacy identifier for the data record.
     * May be {@code null} when only the IRI is available.
     */
    @Field(name = "dataRecordId", type = FieldType.Keyword)
    private String dataRecordId;

    /**
     * Details about which property failed and why.
     */
    @Field(name = "dqErrorPropertyValue", type = FieldType.Object)
    private DqErrorPropertyValue dqErrorPropertyValue;
}
