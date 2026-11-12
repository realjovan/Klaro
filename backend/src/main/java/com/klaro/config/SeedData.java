package com.klaro.config;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.klaro.model.Bill;
import com.klaro.model.Budget;
import com.klaro.model.Receipt;
import com.klaro.repository.BillRepository;
import com.klaro.repository.BudgetRepository;
import com.klaro.repository.ReceiptRepository;

/**
 * Loads sample receipts, bills and budgets on startup so everyone has data to
 * work with. Each table is only filled when it is empty, so real data is
 * never duplicated. Dates are relative to today, covering the last 3 months.
 *
 * To reset: stop the app and delete the backend/data folder.
 */
@Configuration
public class SeedData {

    @Bean
    CommandLineRunner seed(ReceiptRepository receipts, BillRepository bills, BudgetRepository budgets) {
        return args -> {
            if (receipts.count() == 0) {
                for (int m = 0; m < 3; m++) {
                    LocalDate base = LocalDate.now().minusMonths(m);
                    receipts.save(receipt("SM Supermarket", base.withDayOfMonth(3), "1850.75", "Food"));
                    receipts.save(receipt("Jollibee", base.withDayOfMonth(9), "389.00", "Food"));
                    receipts.save(receipt("Shell", base.withDayOfMonth(12), "1500.00", "Transport"));
                    receipts.save(receipt("Mercury Drug", base.withDayOfMonth(18), "620.50", "Health"));
                    receipts.save(receipt("Uniqlo", base.withDayOfMonth(22), "2490.00", "Shopping"));
                }
            }
            if (bills.count() == 0) {
                for (int m = 0; m < 3; m++) {
                    LocalDate base = LocalDate.now().minusMonths(m);
                    bills.save(bill("Meralco", "electricity", base, "3250.00", "280", "kWh", m != 0));
                    bills.save(bill("Manila Water", "water", base, "540.00", "18", "m3", m != 0));
                    bills.save(bill("PLDT", "internet", base, "1699.00", null, null, m != 0));
                }
            }
            if (budgets.count() == 0) {
                budgets.save(budget("Food", "8000.00"));
                budgets.save(budget("Transport", "3000.00"));
                budgets.save(budget("Health", "1500.00"));
                budgets.save(budget("Shopping", "2000.00"));
            }
        };
    }

    private Receipt receipt(String merchant, LocalDate date, String total, String category) {
        Receipt r = new Receipt();
        r.setMerchant(merchant);
        r.setReceiptDate(date);
        r.setTotal(new BigDecimal(total));
        r.setCategory(category);
        r.setRawText(merchant.toUpperCase() + " TOTAL " + total);
        return r;
    }

    private Bill bill(String provider, String type, LocalDate base, String amount,
            String usage, String unit, boolean paid) {
        Bill b = new Bill();
        b.setEstablishment(provider);
        b.setType(type);
        b.setBillingPeriodStart(base.withDayOfMonth(1));
        b.setBillingPeriodEnd(base.withDayOfMonth(base.lengthOfMonth()));
        b.setDueDate(base.withDayOfMonth(Math.min(25, base.lengthOfMonth())));
        b.setDueAmount(new BigDecimal(amount));
        if (usage != null) {
            b.setUsageAmount(new BigDecimal(usage));
            b.setUsageUnit(unit);
        }
        b.setPaid(paid);
        return b;
    }

    private Budget budget(String category, String limit) {
        Budget b = new Budget();
        b.setCategory(category);
        b.setMonthlyLimit(new BigDecimal(limit));
        return b;
    }
}
