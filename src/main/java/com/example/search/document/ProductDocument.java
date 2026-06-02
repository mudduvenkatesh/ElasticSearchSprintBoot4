package com.example.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Elasticsearch document mapped to the "products" index.
 *
 * @Document  — declares the index name; createIndex = true auto-creates it on startup.
 * @Setting   — points to the JSON file with custom analyzer and shard settings.
 * @Field     — controls the ES field mapping (type, analyzer, etc.).
 */
@Document(indexName = "products", createIndex = true)
@Setting(settingPath = "/es-settings/products-settings.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "english")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer stockQuantity;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Boolean)
    private boolean active;
}
