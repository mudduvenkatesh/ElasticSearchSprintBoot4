package com.example.search.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an Elasticsearch operation fails unexpectedly. */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ElasticsearchOperationException extends RuntimeException {

    public ElasticsearchOperationException(String message) {
        super(message);
    }

    public ElasticsearchOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
