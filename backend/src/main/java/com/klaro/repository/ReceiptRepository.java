package com.klaro.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.klaro.model.Receipt;

/**
 * Database access for receipts. Spring generates the implementation at
 * startup, giving save, findById, findAll, deleteById and more for free.
 * Add custom queries here (e.g. by date range or category) as needed.
 */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
}
