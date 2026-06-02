package com.example.search.repository;

import com.example.search.document.CustomerDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for CustomerDocument.
 *
 * -----------------------------------------------------------------------
 * Why @Query for nested fields?
 * -----------------------------------------------------------------------
 * Derived method names (findByAddressesCity) cannot express nested queries
 * — Spring Data would generate a plain term query on a flattened field,
 * losing the "same element" guarantee. All queries on nested fields must
 * use @Query with the ES nested query DSL.
 *
 * Nested query pattern:
 *   {
 *     "nested": {
 *       "path":  "<field-name>",     ← must match the @Field name exactly
 *       "query": { <inner query> },
 *       "score_mode": "max"
 *     }
 *   }
 *
 * The inner query's field names must be prefixed with the path, e.g.
 * "addresses.city" not just "city".
 */
@Repository
public interface CustomerRepository extends ElasticsearchRepository<CustomerDocument, String> {

    // -------------------------------------------------------------------------
    // Flat-field queries — derived methods work fine for non-nested fields
    // -------------------------------------------------------------------------

    List<CustomerDocument> findByStatus(String status);

    List<CustomerDocument> findByEmail(String email);

    List<CustomerDocument> findByFirstNameAndLastName(String firstName, String lastName);

    // -------------------------------------------------------------------------
    // Nested: Address queries
    // -------------------------------------------------------------------------

    /**
     * Customers with any address in the given city.
     * Single condition — a plain nested term query is sufficient.
     */
    @Query("""
        {
          "nested": {
            "path": "addresses",
            "query": {
              "term": { "addresses.city": "?0" }
            }
          }
        }
        """)
    List<CustomerDocument> findByAddressCity(String city);

    /**
     * Customers with a specific address TYPE in a specific CITY.
     *
     * The bool/must is INSIDE the nested query — both conditions must be
     * satisfied by the SAME address element, not across different elements.
     */
    @Query("""
        {
          "nested": {
            "path": "addresses",
            "query": {
              "bool": {
                "must": [
                  { "term": { "addresses.type":  "?0" } },
                  { "term": { "addresses.city":  "?1" } }
                ]
              }
            }
          }
        }
        """)
    List<CustomerDocument> findByAddressTypeAndCity(String addressType, String city);

    /**
     * Customers whose primary address is in the given country.
     */
    @Query("""
        {
          "nested": {
            "path": "addresses",
            "query": {
              "bool": {
                "must": [
                  { "term": { "addresses.primary":     true } },
                  { "term": { "addresses.countryCode": "?0" } }
                ]
              }
            }
          }
        }
        """)
    List<CustomerDocument> findByPrimaryAddressCountry(String countryCode);

    // -------------------------------------------------------------------------
    // Nested: Account queries
    // -------------------------------------------------------------------------

    /**
     * Customers with an ACTIVE account of the given type.
     */
    @Query("""
        {
          "nested": {
            "path": "accounts",
            "query": {
              "bool": {
                "must": [
                  { "term": { "accounts.accountType": "?0"    } },
                  { "term": { "accounts.status":      "ACTIVE" } }
                ]
              }
            }
          }
        }
        """)
    List<CustomerDocument> findByActiveAccountType(String accountType);

    /**
     * Customers with any account balance at or above the given threshold.
     */
    @Query("""
        {
          "nested": {
            "path": "accounts",
            "query": {
              "range": { "accounts.balance": { "gte": ?0 } }
            }
          }
        }
        """)
    List<CustomerDocument> findByAccountBalanceGreaterThanEqual(double minBalance);

    // -------------------------------------------------------------------------
    // Combined: address + account in a single query
    // -------------------------------------------------------------------------

    /**
     * Customers who have BOTH:
     *   - A SHIPPING address in the given state
     *   - At least one ACTIVE account
     *
     * Two independent nested queries wrapped in a top-level bool/must.
     * Each nested clause is evaluated on its own path independently.
     */
    @Query("""
        {
          "bool": {
            "must": [
              {
                "nested": {
                  "path": "addresses",
                  "query": {
                    "bool": {
                      "must": [
                        { "term": { "addresses.type":  "SHIPPING" } },
                        { "term": { "addresses.state": "?0"       } }
                      ]
                    }
                  }
                }
              },
              {
                "nested": {
                  "path": "accounts",
                  "query": {
                    "term": { "accounts.status": "ACTIVE" }
                  }
                }
              }
            ]
          }
        }
        """)
    List<CustomerDocument> findActiveCustomersWithShippingAddressInState(String state);
}
