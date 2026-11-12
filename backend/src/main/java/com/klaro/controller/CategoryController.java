package com.klaro.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.klaro.model.Category;

/**
 * GET /api/categories returns every allowed category name, e.g.
 * ["FOOD","TRANSPORT",...,"OTHER"], so the front-end never hardcodes the list.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @GetMapping
    public List<String> list() {
        return Arrays.stream(Category.values()).map(Enum::name).toList();
    }
}
