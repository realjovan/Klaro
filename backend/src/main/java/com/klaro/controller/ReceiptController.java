package com.klaro.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.klaro.dto.ExtractedDocument;
import com.klaro.model.Receipt;
import com.klaro.repository.ReceiptRepository;
import com.klaro.service.ExtractionException;
import com.klaro.service.ExtractionService;

/**
 * Receipt endpoints under /api/receipts:
 *   GET    /api/receipts           list all
 *   GET    /api/receipts/{id}      get one
 *   POST   /api/receipts           create from JSON
 *   PUT    /api/receipts/{id}      update from JSON
 *   DELETE /api/receipts/{id}      delete
 *   POST   /api/receipts/extract   upload a file (form field "file"), returns
 *                                  extracted fields without saving anything
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptRepository receipts;
    private final ExtractionService extraction;

    public ReceiptController(ReceiptRepository receipts, ExtractionService extraction) {
        this.receipts = receipts;
        this.extraction = extraction;
    }

    @GetMapping
    public List<Receipt> list() {
        return receipts.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Receipt> get(@PathVariable Long id) {
        return receipts.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Receipt create(@Valid @RequestBody Receipt receipt) {
        return receipts.save(receipt);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Receipt> update(@PathVariable Long id, @Valid @RequestBody Receipt receipt) {
        if (!receipts.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        receipt.setId(id);
        return ResponseEntity.ok(receipts.save(receipt));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!receipts.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        receipts.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Reads the file with OCR and returns the fields for the user to check.
    // Nothing is saved here; the front-end saves after the user confirms.
    @PostMapping("/extract")
    public ExtractedDocument extract(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ExtractionException("The uploaded file is empty.");
        }
        return extraction.extract(file.getBytes(), file.getContentType(), file.getOriginalFilename());
    }
}
