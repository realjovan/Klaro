package com.klaro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.klaro.dto.ExtractedDocument;
import com.klaro.model.Category;
import com.klaro.service.LumenOcrClient.Line;
import com.klaro.service.LumenOcrClient.Page;

/**
 * Turns Lumen's OCR lines into receipt fields using simple rules:
 *   merchant = first text row near the top, date = first valid date,
 *   total    = the amount next to a TOTAL / AMOUNT DUE label,
 *   category = keyword match on the merchant and text.
 * It will not always be right, so the user always checks the result in the
 * entry form before saving.
 */
public final class ReceiptTextParser {

    private ReceiptTextParser() {
    }

    // Amounts must have two decimals ("1,850.75") so dates and quantities aren't mistaken for money.
    private static final Pattern MONEY =
            Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+|\\d+)\\.(\\d{2})(?!\\d|\\.\\d)");

    // Labels for the total, most specific first. Rows matching NOT_TOTAL are skipped.
    private static final List<String> TOTAL_LABELS = List.of(
            "grand total", "total amount due", "amount due", "total due", "total amount",
            "amount payable", "total sales", "total");
    private static final Pattern NOT_TOTAL = Pattern.compile(
            "sub\\s*-?\\s*total|total\\s+(items?|qty|quantity|discount|vat|savings)|vatable|change|cash|tendered");

    private static final String MONTHS = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern DATE_ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final Pattern DATE_NUMERIC =
            Pattern.compile("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern DATE_MONTH_FIRST =
            Pattern.compile("(?<![a-z])" + MONTHS + "\\s+(\\d{1,2}),?\\s+(\\d{4})(?!\\d)");
    private static final Pattern DATE_DAY_FIRST =
            Pattern.compile("(?<!\\d)(\\d{1,2})\\s+" + MONTHS + ",?\\s+(\\d{4})(?!\\d)");

    // Words that suggest a utility bill rather than a store receipt.
    private static final List<String> BILL_WORDS = List.of(
            "due date", "billing period", "amount due", "account number", "account no",
            "statement of account", "service period", "kwh", "meter reading");

    // Rows near the top that are not the merchant name.
    private static final Pattern NOT_MERCHANT = Pattern.compile(
            "official receipt|sales invoice|invoice|receipt|\\btin\\b|vat reg|tel\\.?|phone|\\bdate\\b|cashier");

    // Category keywords, checked in this order. Lower-case.
    private static final Map<Category, List<String>> CATEGORY_WORDS = new LinkedHashMap<>();
    static {
        CATEGORY_WORDS.put(Category.UTILITIES, List.of("meralco", "maynilad", "manila water", "pldt", "globe telecom",
                "converge", "sky cable", "electric", "water district", "kwh"));
        CATEGORY_WORDS.put(Category.TRANSPORT, List.of("shell", "petron", "caltex", "seaoil", "grab", "angkas",
                "joyride", "lrt", "mrt", "toll", "parking", "gasoline", "diesel", "fuel"));
        CATEGORY_WORDS.put(Category.HEALTH, List.of("drug", "pharmacy", "mercury", "watsons", "southstar", "clinic",
                "hospital", "medical", "dental", "laboratory"));
        CATEGORY_WORDS.put(Category.FOOD, List.of("supermarket", "grocery", "groceries", "puregold", "savemore",
                "7-eleven", "alfamart", "ministop", "jollibee", "mcdonald", "mcdo", "chowking", "greenwich",
                "mang inasal", "kfc", "starbucks", "restaurant", "cafe", "coffee", "bakery", "food", "eatery", "pizza"));
        CATEGORY_WORDS.put(Category.EDUCATION, List.of("school", "university", "college", "tuition", "book store",
                "bookstore", "fully booked"));
        CATEGORY_WORDS.put(Category.ENTERTAINMENT, List.of("cinema", "movie", "netflix", "spotify", "timezone",
                "concert", "ticket"));
        CATEGORY_WORDS.put(Category.TRAVEL, List.of("hotel", "resort", "airbnb", "cebu pacific", "philippine airlines",
                "airasia", "airport", "travel"));
        CATEGORY_WORDS.put(Category.PERSONAL, List.of("salon", "barber", "spa", "laundry", "cosmetics"));
        CATEGORY_WORDS.put(Category.SHOPPING, List.of("uniqlo", "h&m", "zara", "department store", "sm store",
                "shopee", "lazada", "hardware"));
    }

    /** Reads the fields out of the OCR pages. */
    public static ExtractedDocument parse(List<Page> pages) {
        List<String> rows = toRows(pages);
        if (rows.isEmpty()) {
            throw new ExtractionException("No text was found in this document.");
        }
        String allText = String.join("\n", rows);
        String lower = allText.toLowerCase(Locale.ROOT);

        boolean isBill = BILL_WORDS.stream().anyMatch(lower::contains);
        String merchant = findMerchant(rows);
        LocalDate date = findDate(rows, isBill);
        BigDecimal total = findTotal(rows);
        Category category = findCategory(merchant, lower, isBill);
        String currency = lower.contains("usd") || allText.contains("US$") ? "USD" : "PHP";

        int found = (merchant != null ? 1 : 0) + (date != null ? 1 : 0) + (total != null ? 1 : 0);
        double confidence = round2(averageConfidence(pages) * found / 3.0);

        return new ExtractedDocument(isBill ? "BILL" : "RECEIPT", merchant, date, total, currency,
                category, allText, confidence);
    }

    /**
     * Lumen returns each separately printed piece as its own line, so "TOTAL" and
     * "950.75" arrive apart. Lines whose vertical centres line up are joined back
     * into one row, left to right.
     */
    static List<String> toRows(List<Page> pages) {
        List<String> rows = new ArrayList<>();
        for (Page page : pages) {
            if (page.lines() == null) continue;
            List<Line> placed = new ArrayList<>();
            for (Line line : page.lines()) {
                if (line.text() == null || line.text().isBlank()) continue;
                if (line.box() == null) rows.add(line.text().trim()); // no position: keep as its own row
                else placed.add(line);
            }
            placed.sort(Comparator.comparingDouble(ReceiptTextParser::centreY));

            List<List<Line>> grouped = new ArrayList<>();
            double rowCentre = 0;
            double rowHeight = 0;
            for (Line line : placed) {
                double centre = centreY(line);
                boolean sameRow = !grouped.isEmpty()
                        && Math.abs(centre - rowCentre) < Math.max(rowHeight, line.box().h()) * 0.6;
                if (sameRow) {
                    grouped.get(grouped.size() - 1).add(line);
                } else {
                    grouped.add(new ArrayList<>(List.of(line)));
                    rowCentre = centre;
                    rowHeight = line.box().h();
                }
            }
            for (List<Line> row : grouped) {
                row.sort(Comparator.comparingDouble(l -> l.box().x()));
                rows.add(row.stream().map(l -> l.text().trim()).collect(Collectors.joining("  ")));
            }
        }
        return rows;
    }

    private static double centreY(Line line) {
        return line.box().y() + line.box().h() / 2;
    }

    private static String findMerchant(List<String> rows) {
        for (String row : rows.subList(0, Math.min(5, rows.size()))) {
            String lower = row.toLowerCase(Locale.ROOT);
            long letters = row.chars().filter(Character::isLetter).count();
            if (letters >= 3 && !MONEY.matcher(row).find() && findDateIn(lower) == null
                    && !NOT_MERCHANT.matcher(lower).find()) {
                String name = row.replaceAll("\\s{2,}", " ").replaceAll("[\\s,.:;*-]+$", "").trim();
                return name.length() > 120 ? name.substring(0, 120) : name;
            }
        }
        return null;
    }

    private static BigDecimal findTotal(List<String> rows) {
        for (String label : TOTAL_LABELS) {
            for (int i = 0; i < rows.size(); i++) {
                String lower = rows.get(i).toLowerCase(Locale.ROOT);
                if (!lower.contains(label) || NOT_TOTAL.matcher(lower).find()) continue;
                BigDecimal amount = lastAmount(rows.get(i));
                if (amount == null && i + 1 < rows.size()) amount = lastAmount(rows.get(i + 1));
                if (amount != null) return amount;
            }
        }
        return null;
    }

    private static BigDecimal lastAmount(String row) {
        Matcher m = MONEY.matcher(row);
        BigDecimal last = null;
        while (m.find()) {
            last = new BigDecimal(m.group(1).replace(",", "") + "." + m.group(2));
        }
        return last;
    }

    // For bills, prefer a date on a row that mentions "due".
    private static LocalDate findDate(List<String> rows, boolean preferDue) {
        if (preferDue) {
            for (String row : rows) {
                String lower = row.toLowerCase(Locale.ROOT);
                if (lower.contains("due")) {
                    LocalDate date = findDateIn(lower);
                    if (date != null) return date;
                }
            }
        }
        for (String row : rows) {
            LocalDate date = findDateIn(row.toLowerCase(Locale.ROOT));
            if (date != null) return date;
        }
        return null;
    }

    private static LocalDate findDateIn(String lower) {
        Matcher m = DATE_ISO.matcher(lower);
        if (m.find()) {
            LocalDate d = date(m.group(1), m.group(2), m.group(3));
            if (d != null) return d;
        }
        m = DATE_NUMERIC.matcher(lower);
        while (m.find()) {
            // Philippine receipts usually print MM/DD/YYYY; switch if the first number can't be a month.
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            String year = m.group(3).length() == 2 ? "20" + m.group(3) : m.group(3);
            LocalDate d = a > 12 ? date(year, m.group(2), m.group(1)) : date(year, m.group(1), m.group(2));
            if (d == null && b <= 12) d = date(year, m.group(2), m.group(1));
            if (d != null) return d;
        }
        m = DATE_MONTH_FIRST.matcher(lower);
        if (m.find()) {
            LocalDate d = date(m.group(3), monthNumber(m.group(1)), m.group(2));
            if (d != null) return d;
        }
        m = DATE_DAY_FIRST.matcher(lower);
        if (m.find()) {
            return date(m.group(3), monthNumber(m.group(2)), m.group(1));
        }
        return null;
    }

    // Builds a date, or returns null if it is invalid or implausible for a receipt.
    private static LocalDate date(String year, String month, String day) {
        try {
            LocalDate d = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            return d.getYear() >= 2000 && !d.isAfter(LocalDate.now().plusYears(1)) ? d : null;
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static String monthNumber(String month) {
        int index = "janfebmaraprmayjunjulaugsepoctnovdec".indexOf(month.substring(0, 3));
        return String.valueOf(index / 3 + 1);
    }

    private static Category findCategory(String merchant, String lowerText, boolean isBill) {
        if (merchant != null) {
            Category byMerchant = matchCategory(merchant.toLowerCase(Locale.ROOT));
            if (byMerchant != null) return byMerchant;
        }
        Category byText = matchCategory(lowerText);
        if (byText != null) return byText;
        return isBill ? Category.UTILITIES : Category.OTHER;
    }

    private static Category matchCategory(String lower) {
        for (Map.Entry<Category, List<String>> entry : CATEGORY_WORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lower::contains)) return entry.getKey();
        }
        return null;
    }

    private static double averageConfidence(List<Page> pages) {
        return pages.stream()
                .map(p -> p.confidence() == null ? null : p.confidence().value())
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.5);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
