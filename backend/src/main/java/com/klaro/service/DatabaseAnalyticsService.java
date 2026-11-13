package com.klaro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.klaro.dto.AnalyticsOverview;
import com.klaro.dto.AnalyticsSummary;
import com.klaro.dto.BudgetStatus;
import com.klaro.dto.CategoryTotal;
import com.klaro.dto.MerchantTotal;
import com.klaro.dto.MonthlySpending;
import com.klaro.dto.MonthlyTotal;
import com.klaro.dto.UpcomingBill;
import com.klaro.dto.UtilityStats;
import com.klaro.model.Bill;
import com.klaro.model.Budget;
import com.klaro.model.Category;
import com.klaro.model.Receipt;
import com.klaro.repository.BillRepository;
import com.klaro.repository.BudgetRepository;
import com.klaro.repository.ReceiptRepository;

/**
 * Computes analytics in Java from all saved receipts, bills and budgets.
 * Loading everything is fine at hackathon scale; switch to database queries if
 * the tables grow to many thousands of rows.
 *
 * Which month a record counts in:
 *   receipt                     -> its receipt date
 *   bill, for spending          -> its due date (when the money is paid)
 *   bill, for utility trends    -> the end of its billing period (when it was used),
 *                                  or the due date if no period was entered
 * Records missing a date fall back to the day they were saved.
 */
@Service
public class DatabaseAnalyticsService implements AnalyticsService {

    private static final int AVERAGE_MONTHS = 6;          // how many complete months the average uses
    private static final double NEAR_LIMIT_PERCENT = 80;  // budget "near limit" from this share used
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReceiptRepository receiptRepository;
    private final BillRepository billRepository;
    private final BudgetRepository budgetRepository;
    private final ZoneId zone;

    public DatabaseAnalyticsService(ReceiptRepository receiptRepository, BillRepository billRepository,
            BudgetRepository budgetRepository, @Value("${klaro.timezone:Asia/Manila}") String timezone) {
        this.receiptRepository = receiptRepository;
        this.billRepository = billRepository;
        this.budgetRepository = budgetRepository;
        this.zone = ZoneId.of(timezone);
    }

    // Everything is computed from one snapshot of the data per request.
    private record Data(List<Receipt> receipts, List<Bill> bills, List<Budget> budgets) {
    }

    private Data load() {
        return new Data(receiptRepository.findAll(), billRepository.findAll(), budgetRepository.findAll());
    }

    // ---------------------------------------------------------------- summary

    @Override
    public AnalyticsSummary summary() {
        Data data = load();
        LocalDate today = today();
        YearMonth now = YearMonth.from(today);

        BigDecimal receiptsNow = receiptTotal(data.receipts(), now);
        BigDecimal billsNow = billTotal(data.bills(), now);
        BigDecimal spentNow = receiptsNow.add(billsNow);
        BigDecimal spentLast = monthTotal(data, now.minusMonths(1));

        // Daily rate from receipts only: bills are one-off and would inflate it.
        BigDecimal dailyRate = receiptsNow.divide(BigDecimal.valueOf(today.getDayOfMonth()), 6, RoundingMode.HALF_UP);
        BigDecimal projected = money(dailyRate.multiply(BigDecimal.valueOf(now.lengthOfMonth())).add(billsNow));

        List<YearMonth> pastMonths = completeMonths(data, now);
        BigDecimal averageMonthly = pastMonths.isEmpty() ? null
                : average(pastMonths.stream().map(m -> monthTotal(data, m)).toList(), 2);

        List<CategoryTotal> categories = categoryTotals(data, now);

        List<Bill> unpaid = data.bills().stream().filter(b -> !b.isPaid()).toList();
        int overdue = (int) unpaid.stream().filter(b -> dueDate(b).isBefore(today)).count();
        UpcomingBill nextDue = unpaid.stream()
                .filter(b -> !dueDate(b).isBefore(today))
                .min(Comparator.comparing(this::dueDate))
                .map(b -> upcoming(b, today))
                .orElse(null);

        List<BudgetStatus> budgetStatuses = budgetStatuses(data, categories);

        return new AnalyticsSummary(
                now.toString(),
                money(spentNow), money(receiptsNow), money(billsNow), money(spentLast),
                money(dailyRate), projected, changePercent(projected, spentLast),
                averageMonthly, pastMonths.size(),
                categories.isEmpty() ? null : categories.get(0),
                unpaid.size(), sum(unpaid.stream().map(Bill::getAmountToPay)), overdue, nextDue,
                budgetStatuses.size(),
                (int) budgetStatuses.stream().filter(s -> "OVER".equals(s.status())).count(),
                (int) budgetStatuses.stream().filter(s -> "NEAR_LIMIT".equals(s.status())).count());
    }

    // The complete months (before the current one) used for the average: at
    // most the last six, and none before the first month that has any record,
    // so a new user's average isn't pulled down by empty months.
    private List<YearMonth> completeMonths(Data data, YearMonth now) {
        YearMonth first = Stream.concat(
                        data.receipts().stream().map(r -> YearMonth.from(receiptDate(r))),
                        data.bills().stream().map(b -> YearMonth.from(dueDate(b))))
                .min(Comparator.naturalOrder())
                .orElse(null);
        List<YearMonth> months = new ArrayList<>();
        if (first == null) return months;
        YearMonth start = first.isAfter(now.minusMonths(AVERAGE_MONTHS)) ? first : now.minusMonths(AVERAGE_MONTHS);
        for (YearMonth m = start; m.isBefore(now); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    // ------------------------------------------------------ spending & budgets

    @Override
    public List<CategoryTotal> spendingByCategory(YearMonth month) {
        return categoryTotals(load(), month == null ? currentMonth() : month);
    }

    private List<CategoryTotal> categoryTotals(Data data, YearMonth month) {
        return categoryTotals(data, month, month);
    }

    // Spending per category for the months from..to (inclusive).
    private List<CategoryTotal> categoryTotals(Data data, YearMonth from, YearMonth to) {
        Map<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Receipt r : data.receipts()) {
            if (!within(YearMonth.from(receiptDate(r)), from, to)) continue;
            Category category = r.getCategory() == null ? Category.OTHER : r.getCategory();
            totals.merge(category, amount(r.getAmountPaid()), BigDecimal::add);
            counts.merge(category, 1, Integer::sum);
        }
        for (Bill b : data.bills()) {
            if (!within(YearMonth.from(dueDate(b)), from, to)) continue;
            totals.merge(Category.UTILITIES, amount(b.getAmountToPay()), BigDecimal::add);
            counts.merge(Category.UTILITIES, 1, Integer::sum);
        }
        BigDecimal grandTotal = sum(totals.values().stream());
        return totals.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), money(e.getValue()),
                        sharePercent(e.getValue(), grandTotal), counts.get(e.getKey())))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();
    }

    @Override
    public List<MonthlySpending> spendingByMonth(int months) {
        return monthlySpending(load(), window(months));
    }

    private List<MonthlySpending> monthlySpending(Data data, List<YearMonth> months) {
        return months.stream()
                .map(m -> {
                    BigDecimal receipts = receiptTotal(data.receipts(), m);
                    BigDecimal bills = billTotal(data.bills(), m);
                    return new MonthlySpending(m.toString(), money(receipts), money(bills), money(receipts.add(bills)));
                })
                .toList();
    }

    // ------------------------------------------------- overview for a period

    @Override
    public AnalyticsOverview overview(int months) {
        Data data = load();
        List<YearMonth> window = window(months);
        YearMonth from = window.get(0);
        YearMonth to = window.get(window.size() - 1);

        List<MonthlySpending> byMonth = monthlySpending(data, window);
        BigDecimal receiptsTotal = sum(byMonth.stream().map(MonthlySpending::receipts));
        BigDecimal billsTotal = sum(byMonth.stream().map(MonthlySpending::bills));
        BigDecimal total = receiptsTotal.add(billsTotal);
        int recordCount = (int) (data.receipts().stream().filter(r -> within(YearMonth.from(receiptDate(r)), from, to)).count()
                + data.bills().stream().filter(b -> within(YearMonth.from(dueDate(b)), from, to)).count());

        // Average over the period, but not over months before the user's first
        // record, so someone who started last month isn't averaged over 6 months.
        YearMonth first = firstRecordMonth(data);
        int monthsCounted = first == null ? 0
                : (int) window.stream().filter(m -> !m.isBefore(first)).count();
        BigDecimal average = monthsCounted == 0 || recordCount == 0 ? null
                : total.divide(BigDecimal.valueOf(monthsCounted), 2, RoundingMode.HALF_UP);

        List<MonthlySpending> withSpending = byMonth.stream().filter(m -> m.total().signum() > 0).toList();
        MonthlyTotal highest = withSpending.stream()
                .max(Comparator.comparing(MonthlySpending::total))
                .map(m -> new MonthlyTotal(m.month(), m.total())).orElse(null);
        MonthlyTotal lowest = withSpending.stream()
                .min(Comparator.comparing(MonthlySpending::total))
                .map(m -> new MonthlyTotal(m.month(), m.total())).orElse(null);

        return new AnalyticsOverview(window.size(), from.toString(), to.toString(),
                money(total), receiptsTotal, billsTotal, recordCount, monthsCounted, average,
                highest, lowest, byMonth, categoryTotals(data, from, to), utilities(data, window));
    }

    private YearMonth firstRecordMonth(Data data) {
        return Stream.concat(
                        data.receipts().stream().map(r -> YearMonth.from(receiptDate(r))),
                        data.bills().stream().map(b -> YearMonth.from(dueDate(b))))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean within(YearMonth month, YearMonth from, YearMonth to) {
        return !month.isBefore(from) && !month.isAfter(to);
    }

    @Override
    public List<BudgetStatus> budgets(YearMonth month) {
        Data data = load();
        return budgetStatuses(data, categoryTotals(data, month == null ? currentMonth() : month));
    }

    private List<BudgetStatus> budgetStatuses(Data data, List<CategoryTotal> categories) {
        Map<Category, BigDecimal> spentByCategory = categories.stream()
                .collect(Collectors.toMap(CategoryTotal::category, CategoryTotal::total));
        return data.budgets().stream()
                .filter(b -> b.getCategory() != null && b.getMonthlyLimit() != null)
                .map(b -> {
                    BigDecimal limit = money(b.getMonthlyLimit());
                    BigDecimal spent = spentByCategory.getOrDefault(b.getCategory(), money(BigDecimal.ZERO));
                    double used = limit.signum() > 0 ? round1(spent.multiply(HUNDRED)
                            .divide(limit, 4, RoundingMode.HALF_UP).doubleValue()) : 0;
                    String status = used > 100 ? "OVER" : used >= NEAR_LIMIT_PERCENT ? "NEAR_LIMIT" : "OK";
                    return new BudgetStatus(b.getCategory(), limit, spent, money(limit.subtract(spent)), used, status);
                })
                .sorted(Comparator.comparingDouble(BudgetStatus::percentUsed).reversed())
                .toList();
    }

    // -------------------------------------------------------------- utilities

    @Override
    public List<UtilityStats> utilities(int months) {
        return utilities(load(), window(months));
    }

    private List<UtilityStats> utilities(Data data, List<YearMonth> window) {
        YearMonth from = window.get(0);
        YearMonth to = window.get(window.size() - 1);

        // Bills in the window, grouped by type ("electricity", "water", ...).
        Map<String, List<Bill>> byType = data.bills().stream()
                .filter(b -> within(YearMonth.from(usageDate(b)), from, to))
                .collect(Collectors.groupingBy(this::typeOf, TreeMap::new, Collectors.toList()));

        List<UtilityStats> result = new ArrayList<>();
        byType.forEach((type, typeBills) -> result.add(utilityStats(type, typeBills, window)));
        result.sort(Comparator.comparing(UtilityStats::averageMonthlyCost).reversed());
        return result;
    }

    private UtilityStats utilityStats(String type, List<Bill> typeBills, List<YearMonth> window) {
        // Use the unit most bills of this type have; bills in another unit don't count toward consumption.
        String unit = typeBills.stream()
                .map(Bill::getUsageUnit)
                .filter(u -> u != null && !u.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        TreeMap<YearMonth, BigDecimal> costByMonth = new TreeMap<>();
        TreeMap<YearMonth, BigDecimal> usageByMonth = new TreeMap<>();
        BigDecimal costWithUsage = BigDecimal.ZERO;
        BigDecimal totalUsage = BigDecimal.ZERO;
        for (Bill b : typeBills) {
            YearMonth month = YearMonth.from(usageDate(b));
            BigDecimal cost = amount(b.getAmountToPay());
            costByMonth.merge(month, cost, BigDecimal::add);
            if (unit != null && unit.equals(b.getUsageUnit()) && b.getUsageAmount() != null) {
                usageByMonth.merge(month, b.getUsageAmount(), BigDecimal::add);
                costWithUsage = costWithUsage.add(cost);
                totalUsage = totalUsage.add(b.getUsageAmount());
            }
        }

        YearMonth latest = costByMonth.lastKey();
        BigDecimal latestCost = costByMonth.get(latest);
        Map.Entry<YearMonth, BigDecimal> previousCost = costByMonth.lowerEntry(latest);

        BigDecimal latestUsage = usageByMonth.get(latest);
        Map.Entry<YearMonth, BigDecimal> previousUsage = usageByMonth.lowerEntry(latest);

        List<MonthlyTotal> series = window.stream()
                .map(m -> new MonthlyTotal(m.toString(), money(costByMonth.getOrDefault(m, BigDecimal.ZERO))))
                .toList();

        return new UtilityStats(
                type,
                typeBills.size(),
                costByMonth.size(),
                latest.toString(),
                money(latestCost),
                average(costByMonth.values(), 2),
                previousCost == null ? null : changePercent(latestCost, previousCost.getValue()),
                latestUsage == null ? null : latestUsage.setScale(2, RoundingMode.HALF_UP),
                usageByMonth.isEmpty() ? null : average(usageByMonth.values(), 2),
                latestUsage == null || previousUsage == null ? null : changePercent(latestUsage, previousUsage.getValue()),
                usageByMonth.isEmpty() ? null : totalUsage.setScale(2, RoundingMode.HALF_UP),
                usageByMonth.size(),
                usageByMonth.isEmpty() ? null : unit,
                totalUsage.signum() > 0 ? costWithUsage.divide(totalUsage, 2, RoundingMode.HALF_UP) : null,
                series);
    }

    @Override
    public List<MonthlyTotal> utilitiesByMonth(int months) {
        List<Bill> bills = load().bills();
        return window(months).stream()
                .map(m -> new MonthlyTotal(m.toString(), sum(bills.stream()
                        .filter(b -> YearMonth.from(usageDate(b)).equals(m))
                        .map(Bill::getAmountToPay))))
                .toList();
    }

    // -------------------------------------------------------- merchants, bills

    @Override
    public List<MerchantTotal> topMerchants(int months, int limit) {
        YearMonth from = window(months).get(0);
        YearMonth now = currentMonth();

        // Group case-insensitively ("Jollibee" and "JOLLIBEE" are the same place);
        // show the name as written on the most recent receipt.
        Map<String, List<Receipt>> byMerchant = new LinkedHashMap<>();
        load().receipts().stream()
                .filter(r -> r.getMerchant() != null && !r.getMerchant().isBlank())
                .filter(r -> {
                    YearMonth m = YearMonth.from(receiptDate(r));
                    return !m.isBefore(from) && !m.isAfter(now);
                })
                .sorted(Comparator.comparing(this::receiptDate).reversed())
                .forEach(r -> byMerchant.computeIfAbsent(
                        r.getMerchant().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
                        k -> new ArrayList<>()).add(r));

        return byMerchant.values().stream()
                .map(list -> new MerchantTotal(list.get(0).getMerchant().trim(),
                        sum(list.stream().map(Receipt::getAmountPaid)), list.size()))
                .sorted(Comparator.comparing(MerchantTotal::total).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<UpcomingBill> upcomingBills(int days) {
        LocalDate today = today();
        LocalDate until = today.plusDays(days);
        return load().bills().stream()
                .filter(b -> !b.isPaid())
                .filter(b -> !dueDate(b).isAfter(until))
                .sorted(Comparator.comparing(this::dueDate))
                .map(b -> upcoming(b, today))
                .toList();
    }

    private UpcomingBill upcoming(Bill b, LocalDate today) {
        LocalDate due = dueDate(b);
        return new UpcomingBill(b.getId(), b.getMerchant(), typeOf(b), due, money(amount(b.getAmountToPay())),
                ChronoUnit.DAYS.between(today, due), due.isBefore(today));
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate today() {
        return LocalDate.now(zone);
    }

    private YearMonth currentMonth() {
        return YearMonth.from(today());
    }

    // The given number of months ending with the current month, oldest first.
    private List<YearMonth> window(int months) {
        YearMonth now = currentMonth();
        List<YearMonth> result = new ArrayList<>();
        for (int i = Math.max(1, months) - 1; i >= 0; i--) {
            result.add(now.minusMonths(i));
        }
        return result;
    }

    private LocalDate receiptDate(Receipt r) {
        return r.getReceiptDate() != null ? r.getReceiptDate() : savedDate(r.getCreatedAt());
    }

    private LocalDate dueDate(Bill b) {
        if (b.getBillingDueDate() != null) return b.getBillingDueDate();
        if (b.getBillingPeriodEnd() != null) return b.getBillingPeriodEnd();
        return savedDate(b.getCreatedAt());
    }

    private LocalDate usageDate(Bill b) {
        if (b.getBillingPeriodEnd() != null) return b.getBillingPeriodEnd();
        if (b.getBillingDueDate() != null) return b.getBillingDueDate();
        return savedDate(b.getCreatedAt());
    }

    private LocalDate savedDate(Instant createdAt) {
        return createdAt == null ? today() : LocalDate.ofInstant(createdAt, zone);
    }

    private String typeOf(Bill b) {
        return b.getType() == null || b.getType().isBlank() ? "other" : b.getType().trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal receiptTotal(List<Receipt> receipts, YearMonth month) {
        return sum(receipts.stream().filter(r -> YearMonth.from(receiptDate(r)).equals(month)).map(Receipt::getAmountPaid));
    }

    private BigDecimal billTotal(List<Bill> bills, YearMonth month) {
        return sum(bills.stream().filter(b -> YearMonth.from(dueDate(b)).equals(month)).map(Bill::getAmountToPay));
    }

    private BigDecimal monthTotal(Data data, YearMonth month) {
        return receiptTotal(data.receipts(), month).add(billTotal(data.bills(), month));
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return amount(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(Stream<BigDecimal> values) {
        return money(values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal average(Collection<BigDecimal> values, int scale) {
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), scale, RoundingMode.HALF_UP);
    }

    // Share of a whole, e.g. 32.5 (%). 0 when the whole is 0.
    private static double sharePercent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) return 0;
        return round1(part.multiply(HUNDRED).divide(whole, 4, RoundingMode.HALF_UP).doubleValue());
    }

    // Change from before to now, e.g. 12.5 (% more) or -8.0 (% less). null when before is 0.
    private static Double changePercent(BigDecimal now, BigDecimal before) {
        if (before == null || before.signum() == 0) return null;
        return round1(now.subtract(before).multiply(HUNDRED).divide(before, 4, RoundingMode.HALF_UP).doubleValue());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
