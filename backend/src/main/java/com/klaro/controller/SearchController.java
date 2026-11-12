package com.klaro.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.klaro.model.Receipt;
import com.klaro.service.SearchService;

/** GET /api/search?q=grocery returns the receipts matching the query. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping
    public List<Receipt> search(@RequestParam("q") String query) {
        return search.search(query);
    }
}
