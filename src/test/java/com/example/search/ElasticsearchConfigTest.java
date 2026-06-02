package com.example.search;

import com.example.search.config.ElasticsearchConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElasticsearchConfigTest {

    /**
     * Verifies Cloud ID decoding produces the correct "host:port" string.
     * Uses a synthetic Cloud ID with a known structure.
     */
    @Test
    void decodeCloudId_parsesHostAndPort() {
        // "parent$esUuid$kibanaUuid" base64-encoded
        // parent = "us-east-1.aws.found.io"
        // esUuid  = "abc123"
        String raw     = "us-east-1.aws.found.io$abc123$kib456";
        String encoded = java.util.Base64.getEncoder().encodeToString(raw.getBytes());
        String cloudId = "my-cluster:" + encoded;

        String hostPort = ElasticsearchConfig.decodeCloudIdToHostPort(cloudId);

        assertEquals("abc123.us-east-1.aws.found.io:443", hostPort);
    }

    @Test
    void decodeCloudId_respectsPortInParentHost() {
        String raw     = "us-east-1.aws.found.io:9243$abc123$kib456";
        String encoded = java.util.Base64.getEncoder().encodeToString(raw.getBytes());
        String cloudId = "my-cluster:" + encoded;

        String hostPort = ElasticsearchConfig.decodeCloudIdToHostPort(cloudId);

        assertEquals("abc123.us-east-1.aws.found.io:9243", hostPort);
    }

    @Test
    void decodeCloudId_throwsOnInvalidFormat() {
        assertThrows(IllegalArgumentException.class,
            () -> ElasticsearchConfig.decodeCloudIdToHostPort("no-colon-here"));
    }
}
