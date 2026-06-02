package com.example.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * Nested address — NOT a top-level @Document.
 *
 * Stored as FieldType.Nested inside CustomerDocument.
 * ES indexes each Address as a hidden sub-document so compound queries
 * like "SHIPPING address in NEW YORK" work accurately without
 * false positives from mixing fields across different array elements.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    /** SHIPPING, BILLING, HOME, WORK */
    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String street;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Keyword)
    private String state;

    @Field(type = FieldType.Keyword)
    private String postalCode;

    /** ISO-3166 alpha-2, e.g. "US" */
    @Field(type = FieldType.Keyword)
    private String countryCode;

    @Field(type = FieldType.Boolean)
    private boolean primary;

    /**
     * Geo-point for distance queries ("customers within 50km of NYC").
     * Format: "lat,lon" string — e.g. "40.7128,-74.0060"
     */
    @Field(type = FieldType.Text)
    private String location;
}
