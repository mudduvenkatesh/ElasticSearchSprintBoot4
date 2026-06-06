package com.example.search.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Captures the specific property that violated a DQ rule:
 * which predicate (IRI), what value was present (or null),
 * whether it is obfuscated, and a human-readable error description.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DqErrorPropertyValue {

    /**
     * The full IRI of the data property that was checked.
     * E.g. {@code http://spec.pmog.mymac.net/dataproperty#fundJurisdictionDataProperty}
     */
    @Field(name = "predicateIRI", type = FieldType.Keyword)
    private String predicateIRI;

    /**
     * The actual (string) value of the property at the time of validation,
     * or {@code null} if missing / empty.
     */
    @Field(name = "stringValue", type = FieldType.Keyword)
    private String stringValue;

    /**
     * {@code true} if the value was obfuscated (e.g. PII masking) before storage.
     */
    @Field(name = "obfuscated", type = FieldType.Boolean)
    private boolean obfuscated;

    /**
     * Human-readable description of the validation error.
     */
    @Field(name = "errorDescription", type = FieldType.Text)
    private String errorDescription;
}
