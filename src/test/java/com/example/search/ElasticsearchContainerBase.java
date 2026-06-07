package com.example.search;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Testcontainers base for integration tests that need Elasticsearch.
 *
 * Extend this class instead of hard-coding localhost:9200.  The container
 * starts once per JVM and is shared across all subclasses via Spring context
 * caching — Docker startup cost is paid only once per test run.
 */
public abstract class ElasticsearchContainerBase {

    private static final ElasticsearchContainer ES_CONTAINER =
        new ElasticsearchContainer(
            DockerImageName
                .parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.0")
                .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
        )
        .withEnv("discovery.type", "single-node")
        .withEnv("xpack.security.enabled", "false");

    static {
        ES_CONTAINER.start();
    }

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
            () -> "http://" + ES_CONTAINER.getHttpHostAddress());
        registry.add("elastic.cloud.cloud-id", () -> "");
        registry.add("elastic.cloud.api-key",  () -> "");
    }
}