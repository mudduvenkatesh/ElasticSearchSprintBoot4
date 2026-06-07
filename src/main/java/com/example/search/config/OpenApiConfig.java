package com.example.search.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures SpringDoc / OpenAPI 3 metadata exposed at
 * {@code /api/swagger-ui.html} and {@code /api/v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dqViolationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DQ Violation Elasticsearch API")
                        .description("""
                                REST API for indexing, searching and managing DQ (Data Quality)
                                violation triage events in Elastic Cloud (Elasticsearch 9.x).

                                **Key capabilities**
                                - Single-document CRUD (upsert by uniqueTriageId)
                                - Multi-criteria search with free-text, keyword filters, date ranges
                                - Nested queries on violated rules and offending records
                                - Bulk upsert / partial-update / delete with automatic chunking
                                - Index lifecycle management (ensure, refresh, stats)
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ontollogix – Data Platform Team")
                                .email("data-platform@ontollogix.com")))
                .servers(List.of(
                        new Server().description("Default server")));
    }
}
