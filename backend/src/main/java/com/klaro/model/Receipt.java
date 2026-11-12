package com.klaro.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A purchase receipt uploaded by the user. Each instance is one row in the
 * "receipt" table.
 */
@Entity
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String merchant;

    // Named receiptDate instead of "date" because DATE is a reserved word in SQL.
    private LocalDate receiptDate;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount_paid;

    // ISO 4217 code such as PHP or USD.
    @Column(length = 3, nullable = false)
    private String currency = "PHP";

    private String category;

    // Uploaded file name and MIME type (e.g. image/jpeg), used to show the original.
    private String fileName;
    private String contentType;

    // Full OCR output, used for keyword search and for showing what was read.
    @Column(length = 10000)
    private String rawText;

    // Extraction confidence from 0.0 to 1.0, and whether a person should check it.
    private Double confidence;
    private boolean needsReview;

    private LocalDateTime createdAt;

    // Runs automatically the first time the row is saved.
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getMerchant() {
        return merchant;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public BigDecimal getAmountPaid() {
        return amount_paid;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCategory() {
        return category;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getRawText() {
        return rawText;
    }

    public Double getConfidence() {
        return confidence;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Used by PUT /api/receipts/{id} so an update targets an existing row.
    public void setId(Long id) {
        this.id = id;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amount_paid = amountPaid;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
