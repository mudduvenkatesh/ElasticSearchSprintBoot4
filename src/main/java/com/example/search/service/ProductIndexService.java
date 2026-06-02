package com.example.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.example.search.document.ProductDocument;
import com.example.search.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for indexing and querying ProductDocuments.
 *
 * Two indexing strategies:
 *
 *  1. repository.save()       — simple single-document upsert via Spring Data.
 *
 *  2. ElasticsearchClient     — bulk indexing via the 9.x Java API client.
 *                               One HTTP round-trip for an entire batch; far
 *                               more efficient than N individual save() calls.
 *
 * The ElasticsearchClient API is identical between 8.x and 9.x for the
 * operations used here (BulkRequest, IndexOperation, SearchResponse).
 * The only 9.x difference visible in this class: aggregation result fields
 * are now boxed (Double instead of double) to support null responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private static final String INDEX = "products";

    private final ProductRepository    productRepository;
    private final ElasticsearchClient  elasticsearchClient;

    // =========================================================================
    // INDEX
    // =========================================================================

    /**
     * Upsert a single product. Generates an id if none is set.
     */
    public ProductDocument indexProduct(ProductDocument product) {
        if (product.getId() == null || product.getId().isBlank()) {
            product.setId(UUID.randomUUID().toString());
        }
        if (product.getCreatedAt() == null) {
            product.setCreatedAt(Instant.now());
        }
        ProductDocument saved = productRepository.save(product);
        log.info("Indexed product id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Bulk-index a list of products using the 9.x Java API client.
     *
     * The BulkRequest / BulkOperation / IndexOperation builder API is
     * unchanged from 8.x — the same fluent lambda style works in 9.x.
     */
    public void bulkIndex(List<ProductDocument> products) throws IOException {
        if (products == null || products.isEmpty()) return;

        List<BulkOperation> operations = products.stream()
            .map(product -> {
                if (product.getId() == null || product.getId().isBlank()) {
                    product.setId(UUID.randomUUID().toString());
                }
                if (product.getCreatedAt() == null) {
                    product.setCreatedAt(Instant.now());
                }
                return BulkOperation.of(b -> b
                    .index(IndexOperation.of(i -> i
                        .index(INDEX)
                        .id(product.getId())
                        .document(product)
                    ))
                );
            })
            .toList();

        BulkRequest bulkRequest = BulkRequest.of(b -> b
            .index(INDEX)
            .operations(operations)
        );

        BulkResponse response = elasticsearchClient.bulk(bulkRequest);

        if (response.errors()) {
            response.items().stream()
                .filter(item -> item.error() != null)
                .forEach(item -> log.error(
                    "Bulk index error — id={} reason={}", item.id(), item.error().reason()));
        } else {
            // response.took() returns Long in 9.x (was long primitive in 8.x)
            log.info("Bulk indexed {} products in {}ms", products.size(), response.took());
        }
    }

    // =========================================================================
    // READ
    // =========================================================================

    public Optional<ProductDocument> findById(String id) {
        return productRepository.findById(id);
    }

    public List<ProductDocument> findAll() {
        return (List<ProductDocument>) productRepository.findAll();
    }

    public List<ProductDocument> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<ProductDocument> search(String query) {
        return productRepository.searchByText(query);
    }

    public List<ProductDocument> findByPriceRange(BigDecimal min, BigDecimal max) {
        return productRepository.findByPriceBetween(min, max);
    }

    public List<ProductDocument> findActiveUnderPrice(BigDecimal maxPrice) {
        return productRepository.findActiveProductsUnderPrice(maxPrice);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    public void deleteById(String id) {
        productRepository.deleteById(id);
        log.info("Deleted product id={}", id);
    }

    public void deleteAll() {
        productRepository.deleteAll();
        log.warn("Deleted all products from index");
    }
}
