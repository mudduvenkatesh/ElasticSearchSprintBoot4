package com.example.search.document;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Immutable result object returned after a bulk-index operation.
 */
@Value
@Builder
public class BulkIndexResult {

    /** Total documents submitted for indexing. */
    int totalRequested;

    /** Documents successfully indexed. */
    int successCount;

    /** Documents that failed (each entry holds the id and error reason). */
    List<FailedDocument> failures;

    /** Whether every document was indexed without error. */
    public boolean isFullSuccess() {
        return failures == null || failures.isEmpty();
    }

    /** Summary of a single failed document in a bulk operation. */
    @Value
    @Builder
    public static class FailedDocument {
        String documentId;
        String errorReason;
    }
}
