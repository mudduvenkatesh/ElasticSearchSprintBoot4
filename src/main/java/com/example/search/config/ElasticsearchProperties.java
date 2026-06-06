package com.example.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    /**
     * Index name for DQ violation triage documents.
     */
    private String indexName = "dq-violation-triage";

    /**
     * Number of primary shards.
     */
    private int numberOfShards = 1;

    /**
     * Number of replica shards.
     */
    private int numberOfReplicas = 1;

    /**
     * Bulk operation settings.
     */
    private Bulk bulk = new Bulk();

    @Data
    public static class Bulk {
        /**
         * Maximum documents per bulk request.
         */
        private int batchSize = 100;
        /**
         * Elasticsearch refresh policy after a bulk write.
         * Values: IMMEDIATE, WAIT_UNTIL, NONE.
         */
        private String refreshPolicy = "WAIT_UNTIL";
    }
}