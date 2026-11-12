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

import com.klaro.model.Bill;
import com.klaro.repository.BillRepository;

/**
 * Bill endpoints under /api/bills: list, get, create, update, delete
 * (same shape as /api/receipts).
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillRepository bills;

    public BillController(BillRepository bills) {
        this.bills = bills;
    }

    @GetMapping
    public List<Bill> list() {
        return bills.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Bill> get(@PathVariable Long id) {
        return bills.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Bill create(@RequestBody Bill bill) {
        return bills.save(bill);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Bill> update(@PathVariable Long id, @RequestBody Bill bill) {
        if (!bills.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bill.setId(id);
        return ResponseEntity.ok(bills.save(bill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!bills.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        bills.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
