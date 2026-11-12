package com.klaro.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.klaro.model.Bill;

/**
 * Database access for utility bills. Spring generates the implementation at
 * startup, giving save, findById, findAll, deleteById and more for free.
 * Add custom queries here (e.g. by type or billing period) as needed.
 */
public interface BillRepository extends JpaRepository<Bill, Long> {
}
