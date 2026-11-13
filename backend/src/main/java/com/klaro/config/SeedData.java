package com.klaro.config;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.klaro.model.Bill;
import com.klaro.model.Budget;
import com.klaro.model.Category;
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
            LocalDate today = LocalDate.now();
            // Amounts vary a little per month (index 0 = this month) so trends aren't flat.
            double[] factor = { 1.08, 0.94, 1.0 };
            if (receipts.count() == 0) {
                Object[][] items = {
                        { "SM Supermarket", 3, "1850.75", Category.FOOD },
                        { "Jollibee", 9, "389.00", Category.FOOD },
                        { "Shell", 12, "1500.00", Category.TRANSPORT },
                        { "Mercury Drug", 18, "620.50", Category.HEALTH },
                        { "Uniqlo", 22, "2490.00", Category.SHOPPING } };
                for (int m = 0; m < 3; m++) {
                    LocalDate base = today.minusMonths(m);
                    for (Object[] item : items) {
                        LocalDate date = base.withDayOfMonth(Math.min((int) item[1], base.lengthOfMonth()));
                        if (date.isAfter(today)) continue; // no receipts from the future
                        receipts.save(receipt((String) item[0], date, scaled((String) item[2], factor[m]),
                                (Category) item[3]));
                    }
                }
            }
            if (bills.count() == 0) {
                for (int m = 0; m < 3; m++) {
                    LocalDate base = today.minusMonths(m);
                    bills.save(bill("Meralco", "electricity", base, scaled("3250.00", factor[m]),
                            scaled("280", factor[m]), "kWh", m != 0));
                    bills.save(bill("Manila Water", "water", base, scaled("540.00", factor[m]),
                            scaled("18", factor[m]), "m3", m != 0));
                    bills.save(bill("PLDT", "internet", base, "1699.00", null, null, m != 0));
                }
            }
            if (budgets.count() == 0) {
                budgets.save(budget(Category.FOOD, "8000.00"));
                budgets.save(budget(Category.TRANSPORT, "3000.00"));
                budgets.save(budget(Category.HEALTH, "1500.00"));
                budgets.save(budget(Category.SHOPPING, "2000.00"));
            }
        };
    }

    private Receipt receipt(String merchant, LocalDate date, String total, Category category) {
        Receipt r = new Receipt();
        r.setMerchant(merchant);
        r.setReceiptDate(date);
        r.setAmountPaid(new BigDecimal(total));
        r.setCategory(category);
        r.setRawText(merchant.toUpperCase() + " TOTAL " + total);
        return r;
    }

    private Bill bill(String provider, String type, LocalDate base, String amount,
            String usage, String unit, boolean paid) {
        Bill b = new Bill();
        b.setMerchant(provider);
        b.setType(type);
        b.setBillingPeriodStart(base.withDayOfMonth(1));
        b.setBillingPeriodEnd(base.withDayOfMonth(base.lengthOfMonth()));
        b.setBillingDueDate(base.withDayOfMonth(Math.min(25, base.lengthOfMonth())));
        b.setAmountToPay(new BigDecimal(amount));
        if (usage != null) {
            b.setUsageAmount(new BigDecimal(usage));
            b.setUsageUnit(unit);
        }
        b.setPaid(paid);
        return b;
    }

    // "1850.75" x 1.08 -> "1998.81"
    private static String scaled(String amount, double factor) {
        return new BigDecimal(amount).multiply(BigDecimal.valueOf(factor))
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private Budget budget(Category category, String limit) {
        Budget b = new Budget();
        b.setCategory(category);
        b.setMonthlyLimit(new BigDecimal(limit));
        return b;
    }
}
