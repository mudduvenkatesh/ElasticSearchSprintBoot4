package com.example.search;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

/**
 * Singleton Testcontainers base for integration tests that need Elasticsearch.
 *
 * Uses GenericContainer with ES 9.4.2 (matching the ES Java client 9.x) so
 * that Testcontainers' ElasticsearchContainer SSL wrapper (designed for 8.x)
 * is bypassed entirely.  Container starts once per JVM; all subclasses share
 * it via Spring context caching.
 *
 * Each subclass must declare its own @DynamicPropertySource — Spring Framework
 * 7 does not inherit the annotation from superclasses.
 */
public abstract class ElasticsearchContainerBase {

    protected static final GenericContainer<?> ES_CONTAINER =
        new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:9.4.2")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(
                Wait.forHttp("/_cluster/health")
                    .forPort(9200)
                    .forStatusCode(200)
                    .withReadTimeout(Duration.ofSeconds(90))
            );

    static {
        ES_CONTAINER.start();
    }
}
