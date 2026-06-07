package com.example.search.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single DQ rule that was violated within a scorecard execution.
 * <p>
 * One {@link DqViolationTriageDocument} may contain multiple {@code ViolatedRule}
 * entries — one per distinct rule that produced offending records.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViolatedRule {

    /**
     * IRI of the specific rule-execution event (the concrete run, not the rule definition).
     * Example: {@code http://metadata.pmog.mymac.net/dqruleexecution#q99utilq3wun0rewlzhw6h}
     */
    @Field(name = "ruleExecutionSubject", type = FieldType.Keyword)
    private String ruleExecutionSubject;

    /**
     * IRI of the DQ rule definition (the ruleset entry that was violated).
     * Example: {@code http://metadata.pmog.mymac.net/dqruleset#u8ytnp6ajlto6tdgdvgvyi}
     */
    @Field(name = "offendingRuleSubject", type = FieldType.Keyword)
    private String offendingRuleSubject;

    /**
     * Human-readable label of the violated rule.
     * Example: {@code "ValueRequiredConstraintRule-Fund Jurisdiction"}
     */
    @Field(name = "offendingRuleLabel", type = FieldType.Text,
           analyzer = "standard", searchAnalyzer = "standard")
    private String offendingRuleLabel;

    /**
     * Keyword copy of the rule label for exact-match / aggregation queries.
     */
    @Field(name = "offendingRuleLabelKeyword", type = FieldType.Keyword)
    private String offendingRuleLabelKeyword;

    /**
     * List of data records that violated this rule.
     * Stored as a nested type to allow independent querying per record.
     */
    @Builder.Default
    @Field(name = "offendingRecords", type = FieldType.Nested)
    private List<OffendingRecord> offendingRecords = new ArrayList<>();

    /**
     * Pre-computed count of offending records.
     * May differ from {@code offendingRecords.size()} if the list was paginated
     * before indexing; treat as a summary metric.
     */
//

}
