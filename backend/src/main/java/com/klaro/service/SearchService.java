package com.klaro.service;

import java.util.List;

import com.klaro.model.Receipt;

/**
 * Finds receipts matching a user's query.
 *
 * To implement the real version: create a new @Service class implementing this
 * interface, then delete SimpleSearchService.
 */
public interface SearchService {
    List<Receipt> search(String query);
}
