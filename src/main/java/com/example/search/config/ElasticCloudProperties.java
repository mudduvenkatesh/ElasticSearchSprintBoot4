package com.example.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the elastic.cloud.* properties in application.yml.
 *
 * IDE autocompletion works automatically once spring-boot-configuration-processor
 * is on the classpath (declared optional in pom.xml).
 */
@ConfigurationProperties(prefix = "elastic.cloud")
public record ElasticCloudProperties(
    String cloudId,
    String apiKey,
    String username,
    String password,
    int connectionTimeoutSeconds,
    int socketTimeoutSeconds,
    String indexName,
    String localUri
) {
    public ElasticCloudProperties {
        username = (username == null) ? "elastic" : username;
        password = (password == null) ? "" : password;
        localUri  = (localUri  == null || localUri.isBlank()) ? "localhost:9200" : localUri;
        if (connectionTimeoutSeconds <= 0) connectionTimeoutSeconds = 10;
        if (socketTimeoutSeconds <= 0) socketTimeoutSeconds = 60;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns true when a real Cloud ID (not empty local placeholder) is set. */
    public boolean hasCloudId() {
        return cloudId != null && !cloudId.isBlank() && cloudId.contains(":");
    }
}
