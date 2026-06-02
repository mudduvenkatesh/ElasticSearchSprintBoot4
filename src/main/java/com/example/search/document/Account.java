package com.example.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Nested account — NOT a top-level @Document.
 *
 * Stored as FieldType.Nested inside CustomerDocument so queries like
 * "SAVINGS account with balance > $1000" match only customers where
 * a single account satisfies BOTH conditions — not customers who have
 * a SAVINGS account and separately have any account over $1000.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Field(type = FieldType.Keyword)
    private String accountNumber;

    /** CHECKING, SAVINGS, CREDIT, LOAN */
    @Field(type = FieldType.Keyword)
    private String accountType;

    /** ACTIVE, CLOSED, SUSPENDED */
    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Double)
    private BigDecimal balance;

    /** ISO-4217 currency code, e.g. "USD" */
    @Field(type = FieldType.Keyword)
    private String currencyCode;

    @Field(type = FieldType.Date)
    private Instant openedAt;

    /** null if still open */
    @Field(type = FieldType.Date)
    private Instant closedAt;

    @Field(type = FieldType.Boolean)
    private boolean primary;
}
