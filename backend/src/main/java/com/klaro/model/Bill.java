package com.klaro.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A utility or recurring bill (electricity, water, internet, ...). Each
 * instance is one row in the "bill" table.
 */
@Entity
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The provider, e.g. the electric or water company.
    private String merchant;

    // Kind of bill, e.g. electricity, water, internet.
    private String type;

    // The period the bill covers; needed for month-by-month trends.
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;

    private LocalDate billingDueDate;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amountToPay;

    // ISO 4217 code such as PHP or USD.
    @Column(length = 3, nullable = false)
    private String currency = "PHP";

    // Consumption for the period (e.g. 245.5 kWh), used to explain cost spikes.
    @Column(precision = 12, scale = 3)
    private BigDecimal usageAmount;
    private String usageUnit;

    private boolean paid;

    // Uploaded file name and MIME type, used to show the original.
    private String fileName;
    private String contentType;

    // Full OCR output, used for keyword search.
    @Column(length = 10000)
    private String rawText;

    // Extraction confidence from 0.0 to 1.0, and whether a person should check it.
    private Double confidence;
    private boolean needsReview;

    // Exact moment the row was created (stored as timestamptz, in UTC).
    private Instant createdAt;

    // Runs automatically the first time the row is saved.
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getType() {
        return type;
    }

    public LocalDate getBillingPeriodStart() {
        return billingPeriodStart;
    }

    public LocalDate getBillingPeriodEnd() {
        return billingPeriodEnd;
    }

    public LocalDate getBillingDueDate() {
        return billingDueDate;
    }

    public BigDecimal getAmountToPay() {
        return amountToPay;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getUsageAmount() {
        return usageAmount;
    }

    public String getUsageUnit() {
        return usageUnit;
    }

    public boolean isPaid() {
        return paid;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Used by PUT /api/bills/{id} so an update targets an existing row.
    public void setId(Long id) {
        this.id = id;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setBillingPeriodStart(LocalDate billingPeriodStart) {
        this.billingPeriodStart = billingPeriodStart;
    }

    public void setBillingPeriodEnd(LocalDate billingPeriodEnd) {
        this.billingPeriodEnd = billingPeriodEnd;
    }

    public void setBillingDueDate(LocalDate billingDueDate) {
        this.billingDueDate = billingDueDate;
    }

    public void setAmountToPay(BigDecimal amountToPay) {
        this.amountToPay = amountToPay;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setUsageAmount(BigDecimal usageAmount) {
        this.usageAmount = usageAmount;
    }

    public void setUsageUnit(String usageUnit) {
        this.usageUnit = usageUnit;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
