package com.klaro.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.klaro.model.Budget;

/**
 * Database access for budgets. Spring generates the implementation at
 * startup, giving save, findById, findAll, deleteById and more for free.
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {
}
