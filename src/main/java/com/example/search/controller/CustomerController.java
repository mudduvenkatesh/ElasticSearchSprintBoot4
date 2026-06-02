package com.example.search.controller;

import com.example.search.document.Account;
import com.example.search.document.Address;
import com.example.search.document.CustomerDocument;
import com.example.search.service.CustomerIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * REST API for the customers index.
 *
 * Endpoints:
 *   POST   /api/customers                          — create customer
 *   GET    /api/customers/{id}                     — get by id
 *   GET    /api/customers?status=ACTIVE            — filter by status
 *   POST   /api/customers/{id}/addresses           — add address
 *   PUT    /api/customers/{id}/addresses           — replace address by type
 *   DELETE /api/customers/{id}/addresses/{type}    — remove address by type
 *   POST   /api/customers/{id}/accounts            — add account
 *   PATCH  /api/customers/{id}/accounts/{number}/status — update account status
 *   GET    /api/customers/search/address?type=&city=
 *   GET    /api/customers/search/account?type=SAVINGS
 *   GET    /api/customers/search/shipping-state?state=NY
 *   DELETE /api/customers/{id}                     — delete
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerIndexService service;

    @PostMapping
    public ResponseEntity<CustomerDocument> create(@RequestBody CustomerDocument customer) {
        return ResponseEntity.ok(service.save(customer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerDocument> getById(@PathVariable String id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<CustomerDocument> findByStatus(@RequestParam(defaultValue = "ACTIVE") String status) {
        return service.findByStatus(status);
    }

    // --- Address sub-resource ---

    @PostMapping("/{id}/addresses")
    public ResponseEntity<CustomerDocument> addAddress(@PathVariable String id,
                                                        @RequestBody Address address) {
        return ResponseEntity.ok(service.addAddress(id, address));
    }

    @PutMapping("/{id}/addresses")
    public ResponseEntity<CustomerDocument> replaceAddress(@PathVariable String id,
                                                            @RequestBody Address address) {
        return ResponseEntity.ok(service.replaceAddressByType(id, address));
    }

    @DeleteMapping("/{id}/addresses/{type}")
    public ResponseEntity<CustomerDocument> removeAddress(@PathVariable String id,
                                                           @PathVariable String type) {
        return ResponseEntity.ok(service.removeAddressByType(id, type));
    }

    // --- Account sub-resource ---

    @PostMapping("/{id}/accounts")
    public ResponseEntity<CustomerDocument> addAccount(@PathVariable String id,
                                                        @RequestBody Account account) {
        return ResponseEntity.ok(service.addAccount(id, account));
    }

    @PatchMapping("/{id}/accounts/{number}/status")
    public ResponseEntity<CustomerDocument> updateAccountStatus(
            @PathVariable String id,
            @PathVariable String number,
            @RequestParam String status) {
        return ResponseEntity.ok(service.updateAccountStatus(id, number, status));
    }

    // --- Search ---

    @GetMapping("/search/address")
    public List<CustomerDocument> searchByAddress(@RequestParam String type,
                                                   @RequestParam String city) {
        return service.findByAddressTypeAndCity(type, city);
    }

    @GetMapping("/search/account")
    public List<CustomerDocument> searchByAccountType(@RequestParam String type) {
        return service.findByActiveAccountType(type);
    }

    @GetMapping("/search/shipping-state")
    public List<CustomerDocument> searchByShippingState(@RequestParam String state)
            throws IOException {
        return service.findWithShippingInStateWithInnerHits(state);
    }

    // --- Delete ---

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
