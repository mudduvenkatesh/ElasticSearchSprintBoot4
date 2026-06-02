package com.example.search.controller;

import com.example.search.document.ProductDocument;
import com.example.search.service.ProductIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * REST API for the products index.
 *
 * Endpoints:
 *   POST   /api/products          — index one product
 *   POST   /api/products/bulk     — bulk index
 *   GET    /api/products/{id}     — get by id
 *   GET    /api/products?q=...    — full-text search
 *   GET    /api/products?category=... — filter by category
 *   GET    /api/products/price-range?min=&max=
 *   DELETE /api/products/{id}     — delete one
 *   DELETE /api/products          — delete all
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductIndexService service;

    @PostMapping
    public ResponseEntity<ProductDocument> index(@RequestBody ProductDocument product) {
        return ResponseEntity.ok(service.indexProduct(product));
    }

    @PostMapping("/bulk")
    public ResponseEntity<String> bulkIndex(@RequestBody List<ProductDocument> products)
            throws IOException {
        service.bulkIndex(products);
        return ResponseEntity.ok("Indexed " + products.size() + " products");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDocument> getById(@PathVariable String id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<ProductDocument> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        if (q != null && !q.isBlank())           return service.search(q);
        if (category != null && !category.isBlank()) return service.findByCategory(category);
        return service.findAll();
    }

    @GetMapping("/price-range")
    public List<ProductDocument> byPriceRange(@RequestParam BigDecimal min,
                                               @RequestParam BigDecimal max) {
        return service.findByPriceRange(min, max);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        service.deleteAll();
        return ResponseEntity.noContent().build();
    }
}
