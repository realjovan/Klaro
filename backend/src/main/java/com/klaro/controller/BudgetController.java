package com.klaro.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.klaro.model.Budget;
import com.klaro.repository.BudgetRepository;

/**
 * Budget endpoints under /api/budgets: list, get, create, update, delete
 * (same shape as /api/receipts).
 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetRepository budgets;

    public BudgetController(BudgetRepository budgets) {
        this.budgets = budgets;
    }

    @GetMapping
    public List<Budget> list() {
        return budgets.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Budget> get(@PathVariable Long id) {
        return budgets.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Budget create(@RequestBody Budget budget) {
        return budgets.save(budget);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Budget> update(@PathVariable Long id, @RequestBody Budget budget) {
        if (!budgets.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        budget.setId(id);
        return ResponseEntity.ok(budgets.save(budget));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!budgets.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        budgets.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
