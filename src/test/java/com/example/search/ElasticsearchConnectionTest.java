package com.example.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

@SpringBootTest
@ActiveProfiles("test")
public class ElasticsearchConnectionTest {
    @Autowired
    private ElasticsearchClient esClient;

    @Test
    void pingElasticsearch() throws IOException {
        boolean ping = esClient.ping().value();
        Assertions.assertTrue(ping, "Should connect to local ES");
    }
}
