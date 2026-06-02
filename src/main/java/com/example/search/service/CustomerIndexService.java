package com.example.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.search.document.Account;
import com.example.search.document.Address;
import com.example.search.document.CustomerDocument;
import com.example.search.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for indexing and querying CustomerDocuments.
 *
 * -----------------------------------------------------------------------
 * Updating nested items
 * -----------------------------------------------------------------------
 * Elasticsearch has no "update element at index N" operation for nested
 * objects. The correct pattern is always:
 *
 *   1. Fetch the full parent document
 *   2. Mutate the nested list in Java
 *   3. Re-save the whole document (ES rewrites it internally)
 *
 * This is efficient for typical workloads. If nested items are updated
 * thousands of times per second per document, consider moving them to a
 * separate index and joining at the application layer.
 *
 * -----------------------------------------------------------------------
 * Two query approaches
 * -----------------------------------------------------------------------
 * 1. Repository @Query methods  — simple, readable, covers most cases.
 * 2. ElasticsearchClient directly — needed for features the @Query
 *    annotation can't express: inner_hits, aggregations on nested fields,
 *    geo-distance on nested geo_points.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerIndexService {

    private final CustomerRepository   customerRepository;
    private final ElasticsearchClient  elasticsearchClient;

    // =========================================================================
    // INDEX — create / update
    // =========================================================================

    /**
     * Save a new customer with their initial addresses and accounts.
     */
    public CustomerDocument save(CustomerDocument customer) {
        if (customer.getId() == null || customer.getId().isBlank()) {
            customer.setId(UUID.randomUUID().toString());
        }
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(Instant.now());
        }
        customer.setUpdatedAt(Instant.now());
        CustomerDocument saved = customerRepository.save(customer);
        log.info("Indexed customer id={} email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    // =========================================================================
    // PARTIAL UPDATES — fetch → mutate → re-save
    // =========================================================================

    /**
     * Appends a new Address to an existing customer.
     */
    public CustomerDocument addAddress(String customerId, Address address) {
        CustomerDocument customer = findByIdOrThrow(customerId);
        List<Address> addresses = new ArrayList<>(
            customer.getAddresses() != null ? customer.getAddresses() : List.of()
        );
        addresses.add(address);
        customer.setAddresses(addresses);
        customer.setUpdatedAt(Instant.now());
        return customerRepository.save(customer);
    }

    /**
     * Replaces all addresses of the given type with a new one.
     * Useful for "update my BILLING address" without touching SHIPPING.
     */
    public CustomerDocument replaceAddressByType(String customerId, Address updated) {
        CustomerDocument customer = findByIdOrThrow(customerId);
        List<Address> addresses = new ArrayList<>(
            customer.getAddresses() != null ? customer.getAddresses() : List.of()
        );
        addresses.replaceAll(a ->
            a.getType().equalsIgnoreCase(updated.getType()) ? updated : a
        );
        customer.setAddresses(addresses);
        customer.setUpdatedAt(Instant.now());
        return customerRepository.save(customer);
    }

    /**
     * Removes an address by type.
     */
    public CustomerDocument removeAddressByType(String customerId, String type) {
        CustomerDocument customer = findByIdOrThrow(customerId);
        List<Address> addresses = new ArrayList<>(
            customer.getAddresses() != null ? customer.getAddresses() : List.of()
        );
        addresses.removeIf(a -> a.getType().equalsIgnoreCase(type));
        customer.setAddresses(addresses);
        customer.setUpdatedAt(Instant.now());
        return customerRepository.save(customer);
    }

    /**
     * Appends a new Account to an existing customer.
     */
    public CustomerDocument addAccount(String customerId, Account account) {
        CustomerDocument customer = findByIdOrThrow(customerId);
        List<Account> accounts = new ArrayList<>(
            customer.getAccounts() != null ? customer.getAccounts() : List.of()
        );
        accounts.add(account);
        customer.setAccounts(accounts);
        customer.setUpdatedAt(Instant.now());
        return customerRepository.save(customer);
    }

    /**
     * Updates the status of a specific account by account number.
     */
    public CustomerDocument updateAccountStatus(String customerId,
                                                 String accountNumber,
                                                 String newStatus) {
        CustomerDocument customer = findByIdOrThrow(customerId);
        List<Account> accounts = new ArrayList<>(
            customer.getAccounts() != null ? customer.getAccounts() : List.of()
        );
        accounts.replaceAll(a ->
            a.getAccountNumber().equals(accountNumber)
                ? Account.builder()
                    .accountNumber(a.getAccountNumber())
                    .accountType(a.getAccountType())
                    .status(newStatus)
                    .balance(a.getBalance())
                    .currencyCode(a.getCurrencyCode())
                    .openedAt(a.getOpenedAt())
                    .closedAt("CLOSED".equals(newStatus) ? Instant.now() : a.getClosedAt())
                    .primary(a.isPrimary())
                    .build()
                : a
        );
        customer.setAccounts(accounts);
        customer.setUpdatedAt(Instant.now());
        return customerRepository.save(customer);
    }

    // =========================================================================
    // QUERY — via Spring Data repository
    // =========================================================================

    public Optional<CustomerDocument> findById(String id) {
        return customerRepository.findById(id);
    }

    public List<CustomerDocument> findByStatus(String status) {
        return customerRepository.findByStatus(status);
    }

    public List<CustomerDocument> findByAddressTypeAndCity(String type, String city) {
        return customerRepository.findByAddressTypeAndCity(type, city);
    }

    public List<CustomerDocument> findByPrimaryAddressCountry(String countryCode) {
        return customerRepository.findByPrimaryAddressCountry(countryCode);
    }

    public List<CustomerDocument> findByActiveAccountType(String accountType) {
        return customerRepository.findByActiveAccountType(accountType);
    }

    public List<CustomerDocument> findByAccountBalanceGte(double min) {
        return customerRepository.findByAccountBalanceGreaterThanEqual(min);
    }

    public List<CustomerDocument> findActiveWithShippingIn(String state) {
        return customerRepository.findActiveCustomersWithShippingAddressInState(state);
    }

    // =========================================================================
    // ADVANCED QUERY — via low-level ElasticsearchClient
    //
    // Use when you need inner_hits, aggregations on nested fields,
    // or geo-distance queries — features not supported by @Query.
    // =========================================================================

    /**
     * Finds customers with a SHIPPING address in the given state and uses
     * inner_hits to return WHICH specific addresses matched.
     *
     * inner_hits is not expressible via Spring Data @Query.
     */
    public List<CustomerDocument> findWithShippingInStateWithInnerHits(String state)
            throws IOException {

        SearchResponse<CustomerDocument> response = elasticsearchClient.search(s -> s
            .index("customers")
            .query(q -> q
                .nested(n -> n
                    .path("addresses")
                    .query(nq -> nq
                        .bool(b -> b
                            .must(m -> m.term(t -> t
                                .field("addresses.type").value("SHIPPING")))
                            .must(m -> m.term(t -> t
                                .field("addresses.state").value(state)))
                        )
                    )
                    .innerHits(ih -> ih
                        .name("matching_addresses")
                        .size(10)
                    )
                )
            ),
            CustomerDocument.class
        );

        return response.hits().hits().stream()
            .map(Hit::source)
            .toList();
    }

    /**
     * Customers with an account balance within the given range.
     */
    public List<CustomerDocument> findByAccountBalanceRange(double min, double max)
            throws IOException {

        SearchResponse<CustomerDocument> response = elasticsearchClient.search(s -> s
            .index("customers")
            .query(q -> q
                .nested(n -> n
                    .path("accounts")
                    .query(nq -> nq
                        .range(r -> r
                            .number(nr -> nr
                                .field("accounts.balance")
                                .gte((double) min)
                                .lte((double) max)
                            )
                        )
                    )
                )
            ),
            CustomerDocument.class
        );

        return response.hits().hits().stream()
            .map(Hit::source)
            .toList();
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    public void deleteById(String id) {
        customerRepository.deleteById(id);
        log.info("Deleted customer id={}", id);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private CustomerDocument findByIdOrThrow(String id) {
        return customerRepository.findById(id).orElseThrow(() ->
            new IllegalArgumentException("Customer not found: " + id));
    }
}
