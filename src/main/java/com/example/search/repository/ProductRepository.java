package com.example.search.repository;

import com.example.search.document.ProductDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Data Elasticsearch repository for {@link ProductDocument}.
 *
 * ElasticsearchRepository provides save, saveAll, findById, findAll,
 * deleteById, count, and exists out of the box.
 *
 * Derived query methods (findByX) are auto-implemented from the method name.
 * Custom ES queries use @Query with raw JSON DSL.
 */
@Repository
public interface ProductRepository extends ElasticsearchRepository<ProductDocument, String> {

    // --- Derived queries (auto-implemented) ---

    List<ProductDocument> findByCategory(String category);

    List<ProductDocument> findByName(String name);

    List<ProductDocument> findByPriceBetween(BigDecimal min, BigDecimal max);

    List<ProductDocument> findByCategoryAndActiveTrue(String category);

    List<ProductDocument> findByTags(String tag);

    // --- Custom @Query (Elasticsearch JSON DSL) ---

    /**
     * Fuzzy multi-field full-text search.
     * name is boosted 3x over description.
     * ?0 is substituted with the first method argument.
     */
    @Query("""
        {
          "multi_match": {
            "query": "?0",
            "fields": ["name^3", "description"],
            "type": "best_fields",
            "fuzziness": "AUTO"
          }
        }
        """)
    List<ProductDocument> searchByText(String query);

    /**
     * Active products with price at or below the given cap.
     */
    @Query("""
        {
          "bool": {
            "must": [
              { "term":  { "active": true } },
              { "range": { "price": { "lte": "?0" } } }
            ]
          }
        }
        """)
    List<ProductDocument> findActiveProductsUnderPrice(BigDecimal maxPrice);
}
