package com.example.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ElasticsearchConnectionIT extends ElasticsearchContainerBase {

    @Autowired
    private ElasticsearchClient esClient;

    @Test
    void pingElasticsearch() throws IOException {
        assertTrue(esClient.ping().value(), "Should connect to Testcontainers Elasticsearch");
    }
}
