package com.example.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

/**
 * Top-level Customer document mapped to the "customers" index.
 *
 * -----------------------------------------------------------------------
 * Why FieldType.Nested for addresses and accounts?
 * -----------------------------------------------------------------------
 * Without Nested (plain Object), ES flattens all array values together:
 *
 *   addresses.type  → ["SHIPPING", "BILLING"]
 *   addresses.city  → ["New York", "Los Angeles"]
 *
 * A query for type=SHIPPING AND city=Los Angeles would INCORRECTLY match
 * a customer who has a BILLING address in LA and a SHIPPING address in NY,
 * because ES sees both values exist somewhere across the arrays.
 *
 * With FieldType.Nested, each element is a hidden sub-document. The bool/must
 * inside the nested query must be satisfied by the SAME element, eliminating
 * false positives.
 *
 * -----------------------------------------------------------------------
 * Trade-offs
 * -----------------------------------------------------------------------
 *   ✅ Accurate compound queries within a single array element
 *   ✅ Single document retrieval — no joins, low-latency reads
 *   ✅ Atomic updates — saving the customer saves all nested data
 *   ⚠️  Updating one nested item rewrites the entire parent document
 *   ⚠️  Nested queries are more expensive than flat object queries
 *
 * If addresses or accounts are updated at very high frequency independently,
 * consider storing them in a separate index with an application-side join.
 */
@Document(indexName = "customers", createIndex = true)
@Setting(settingPath = "/es-settings/customers-settings.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDocument {

    @Id
    private String id;

    /** External CRM / source-system identifier */
    @Field(type = FieldType.Keyword)
    private String externalId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String firstName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String lastName;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String phone;

    /** ACTIVE, INACTIVE, SUSPENDED */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    /**
     * One-to-many addresses stored as nested objects.
     * FieldType.Nested enables accurate per-element boolean queries.
     */
    @Field(type = FieldType.Nested)
    private List<Address> addresses;

    /**
     * One-to-many accounts stored as nested objects.
     * FieldType.Nested enables accurate per-element boolean queries.
     */
    @Field(type = FieldType.Nested)
    private List<Account> accounts;
}
