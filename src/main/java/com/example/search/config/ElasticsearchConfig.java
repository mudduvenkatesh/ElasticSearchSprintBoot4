package com.example.search.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import java.util.Base64;

/**
 * Elasticsearch client configuration for Spring Data Elasticsearch 6.x
 * and the Elasticsearch Java Client 9.x.
 *
 * -----------------------------------------------------------------------
 * 8.x → 9.x transport stack change
 * -----------------------------------------------------------------------
 *  8.x:  RestClient (HC4)  →  RestClientTransport  →  ElasticsearchClient
 *  9.x:  Rest5Client (HC5) →  Rest5ClientTransport →  ElasticsearchClient
 *
 * In Spring Data ES 6.x (ships with Spring Boot 4.x), the canonical way to
 * configure the client is to extend ElasticsearchConfiguration and override
 * clientConfiguration(). Spring Data then creates and registers all of the
 * following beans automatically:
 *
 *   Rest5Client            — low-level HC5 async HTTP client
 *   ElasticsearchClient    — high-level typed Java API client
 *   ElasticsearchOperations — template for ad-hoc operations
 *   JsonpMapper            — Jackson-based JSONP mapper
 *
 * -----------------------------------------------------------------------
 * Modes
 * -----------------------------------------------------------------------
 *  Cloud (ELASTIC_CLOUD_ID set)   — HTTPS to Elastic Cloud, API key or
 *                                   basic auth.
 *  Local (ELASTIC_CLOUD_ID blank) — HTTP to localhost:9200, no auth.
 */
@Configuration
@EnableConfigurationProperties(ElasticCloudProperties.class)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    private final ElasticCloudProperties props;

    public ElasticsearchConfig(ElasticCloudProperties props) {
        this.props = props;
    }

    /**
     * The single override point required by ElasticsearchConfiguration.
     *
     * ClientConfiguration is a Spring Data ES abstraction that internally
     * builds the Rest5Client (HC5) transport. No manual RestClient,
     * RestClientTransport, or ElasticsearchClient bean construction needed.
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        if (!props.hasCloudId()) {
            log.warn("ELASTIC_CLOUD_ID not set — connecting to localhost:9200 (local dev mode)");
            return ClientConfiguration.builder()
                    .connectedTo("localhost:9200")
                    .build();
        }

        // Decode Cloud ID to "host:port" string expected by ClientConfiguration
        log.debug("Calling decodeCloudId {}",props.cloudId());
        String hostAndPort = decodeCloudIdToHostPort(props.cloudId());
        log.info("Connecting to Elastic Cloud: https://{}", hostAndPort);

        var builder = ClientConfiguration.builder()
                .connectedTo(hostAndPort)
                .usingSsl()                          // Elastic Cloud is always HTTPS
                .withConnectTimeout(props.connectionTimeoutSeconds() * 1000L)
                .withSocketTimeout(props.socketTimeoutSeconds()    * 1000L);

        // ---- Authentication ------------------------------------------------
        if (props.hasApiKey()) {
            // API key: base64-encoded "id:api_key" string from Kibana
            log.info("Elastic Cloud auth: API key {}",props.apiKey());
            builder.withDefaultHeaders(
                    new org.springframework.data.elasticsearch.support.HttpHeaders() {{
                        add("Authorization", "ApiKey " + props.apiKey());
                    }}
            );
        } else if (!props.password().isBlank()) {
            // Basic auth fallback
            log.info("Elastic Cloud auth: basic (user={})", props.username());
            builder.withBasicAuth(props.username(), props.password());
        } else {
            throw new IllegalStateException(
                    "Elastic Cloud auth not configured. " +
                            "Set ELASTIC_API_KEY, or both ELASTIC_USERNAME and ELASTIC_PASSWORD.");
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Cloud ID decoder
    // -------------------------------------------------------------------------

    /**
     * Decodes an Elastic Cloud ID to a "host:port" string.
     *
     * Cloud ID format:  "label:<base64>"
     * After decoding:   "<parent-host>$<es-uuid>$<kibana-uuid>"
     * Result:           "<es-uuid>.<parent-host>:443"
     *
     * ClientConfiguration.builder().connectedTo() accepts "host:port" strings.
     */
    public static String decodeCloudIdToHostPort(String cloudId) {
        String[] top = cloudId.split(":", 2);
        log.debug("top={}",top);
        if (top.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid Cloud ID (missing colon separator): " + cloudId);
        }
        String decoded  = new String(Base64.getDecoder().decode(top[1]));
        String[] segs   = decoded.split("\\$");
        if (segs.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid Cloud ID (cannot parse segments): " + decoded);
        }

        String parentHost = segs[0];
        String esUuid     = segs[1];
        log.debug("parent Host={}, esUuid={}",parentHost,esUuid);

        // Parent host may embed a port: "us-east-1.aws.found.io:443"
        String[] hostParts = parentHost.split(":", 2);
        log.debug("hostParts={}",hostParts);
        String host = esUuid + "." + hostParts[0];
        String port = (hostParts.length > 1) ? hostParts[1] : "443";

        return host + ":" + port;
    }
}
