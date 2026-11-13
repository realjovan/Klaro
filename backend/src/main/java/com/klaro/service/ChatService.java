package com.klaro.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.klaro.dto.AnalyticsSummary;
import com.klaro.dto.BudgetStatus;
import com.klaro.dto.CategoryTotal;
import com.klaro.dto.ChatReply;
import com.klaro.dto.ChatRequest;
import com.klaro.dto.UpcomingBill;
import com.klaro.model.Bill;
import com.klaro.model.Receipt;
import com.klaro.repository.BillRepository;
import com.klaro.repository.ReceiptRepository;

/**
 * Answers chat questions with the Lumen AI Chat assistant.
 *
 * The assistant can't see Klaro's database, so every question is sent together
 * with a short summary of the user's data: the selected document, this month's
 * spending, budgets and unpaid bills. Lumen limits a message to 4000 characters,
 * so the summary is kept compact.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_MESSAGE = 4000;  // Lumen's limit per message
    private static final int MAX_DOCUMENT_TEXT = 1200;

    private final LumenChatClient lumen;
    private final ReceiptRepository receipts;
    private final BillRepository bills;
    private final AnalyticsService analytics;

    public ChatService(LumenChatClient lumen, ReceiptRepository receipts, BillRepository bills,
            AnalyticsService analytics) {
        this.lumen = lumen;
        this.receipts = receipts;
        this.bills = bills;
        this.analytics = analytics;
    }

    public ChatReply ask(ChatRequest request) {
        String message = buildMessage(request);
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? lumen.openSession("Klaro chat")
                : request.sessionId();
        try {
            return reply(sessionId, message);
        } catch (ChatException e) {
            // The old session can't be used any more (archived, expired, or from
            // another key). Start a fresh one and try once more.
            if (e.getStatus() == HttpStatus.CONFLICT && request.sessionId() != null) {
                log.info("Chat session {} unusable ({}), opening a new one", sessionId, e.getCode());
                return reply(lumen.openSession("Klaro chat"), message);
            }
            throw e;
        }
    }

    private ChatReply reply(String sessionId, String message) {
        LumenChatClient.Answer answer = lumen.send(sessionId, message);
        return new ChatReply(answer.text(), sessionId, answer.degraded());
    }

    // ------------------------------------------------------------ the message

    private String buildMessage(ChatRequest request) {
        boolean tagalog = "tl".equalsIgnoreCase(request.language());
        String instructions = """
                You are Klaro, a friendly assistant that helps a Filipino household understand \
                its receipts, utility bills and budget.
                Answer in plain, simple %s, in 2 to 5 short sentences.
                Use only the data below. If the answer is not in it, say so honestly.
                Amounts are in Philippine pesos. Do not give legal, medical or investment advice.
                """.formatted(tagalog ? "Tagalog (Filipino)" : "English");

        String question = "\nQuestion: " + request.question().trim();
        String data = "\n" + documentContext(request.documentId()) + "\n" + spendingContext();

        // Trim the data, never the instructions or the question, to fit Lumen's limit.
        int room = MAX_MESSAGE - instructions.length() - question.length();
        if (data.length() > room) data = data.substring(0, Math.max(0, room - 3)) + "...";
        return instructions + data + question;
    }

    // The document the user has open, e.g. "receipt-12" or "bill-3".
    private String documentContext(String documentId) {
        if (documentId == null) return "Selected document: none.";
        String[] parts = documentId.split("-", 2);
        Long id = parts.length == 2 ? parseId(parts[1]) : null;
        if (id != null && "receipt".equals(parts[0])) {
            return receipts.findById(id).map(this::describe).orElse("Selected document: not found.");
        }
        if (id != null && "bill".equals(parts[0])) {
            return bills.findById(id).map(this::describe).orElse("Selected document: not found.");
        }
        return "Selected document: none.";
    }

    private String describe(Receipt r) {
        StringBuilder s = new StringBuilder("Selected document: a receipt.\n");
        s.append("Merchant: ").append(r.getMerchant()).append('\n');
        s.append("Date: ").append(r.getReceiptDate()).append('\n');
        s.append("Amount paid: ").append(peso(r.getAmountPaid())).append('\n');
        if (r.getCategory() != null) s.append("Category: ").append(label(r.getCategory().name())).append('\n');
        if (r.getOrNumber() != null) s.append("OR number: ").append(r.getOrNumber()).append('\n');
        appendText(s, r.getRawText());
        return s.toString();
    }

    private String describe(Bill b) {
        StringBuilder s = new StringBuilder("Selected document: a utility bill.\n");
        s.append("Provider: ").append(b.getMerchant()).append('\n');
        if (b.getType() != null) s.append("Bill type: ").append(b.getType()).append('\n');
        s.append("Amount due: ").append(peso(b.getAmountToPay())).append('\n');
        s.append("Due date: ").append(b.getBillingDueDate()).append('\n');
        s.append("Status: ").append(b.isPaid() ? "paid" : "unpaid").append('\n');
        if (b.getBillingPeriodStart() != null || b.getBillingPeriodEnd() != null) {
            s.append("Billing period: ").append(b.getBillingPeriodStart()).append(" to ")
                    .append(b.getBillingPeriodEnd()).append('\n');
        }
        if (b.getUsageAmount() != null) {
            s.append("Consumption: ").append(b.getUsageAmount().stripTrailingZeros().toPlainString())
                    .append(' ').append(b.getUsageUnit() == null ? "" : b.getUsageUnit()).append('\n');
        }
        appendText(s, b.getRawText());
        return s.toString();
    }

    private void appendText(StringBuilder s, String rawText) {
        if (rawText == null || rawText.isBlank()) return;
        String text = rawText.length() > MAX_DOCUMENT_TEXT ? rawText.substring(0, MAX_DOCUMENT_TEXT) + "..." : rawText;
        s.append("Text read from the document:\n").append(text.trim()).append('\n');
    }

    // Headline numbers from the analytics, in short sentences the assistant can quote.
    private String spendingContext() {
        try {
            AnalyticsSummary sum = analytics.summary();
            StringBuilder s = new StringBuilder("Household spending, month ").append(sum.month()).append(":\n");
            s.append("- Spent so far: ").append(peso(sum.spentThisMonth()))
                    .append(" (receipts ").append(peso(sum.receiptsThisMonth()))
                    .append(", bills ").append(peso(sum.billsThisMonth())).append(")\n");
            s.append("- Last month: ").append(peso(sum.spentLastMonth())).append('\n');
            s.append("- On track to spend this month: ").append(peso(sum.projectedMonthTotal())).append('\n');
            if (sum.averageMonthlySpending() != null) {
                s.append("- Average month: ").append(peso(sum.averageMonthlySpending())).append('\n');
            }

            List<CategoryTotal> categories = analytics.spendingByCategory(null);
            if (!categories.isEmpty()) {
                s.append("- By category this month: ");
                categories.stream().limit(6).forEach(c -> s.append(label(c.category().name())).append(' ')
                        .append(peso(c.total())).append("; "));
                s.append('\n');
            }

            List<BudgetStatus> budgets = analytics.budgets(null);
            if (!budgets.isEmpty()) {
                s.append("- Budgets this month: ");
                budgets.forEach(b -> s.append(label(b.category().name())).append(' ').append(peso(b.spent()))
                        .append(" of ").append(peso(b.monthlyLimit())).append(" (").append(b.percentUsed())
                        .append("%, ").append(b.status().replace('_', ' ').toLowerCase(Locale.ROOT)).append("); "));
                s.append('\n');
            }

            List<UpcomingBill> upcoming = analytics.upcomingBills(30);
            if (!upcoming.isEmpty()) {
                s.append("- Unpaid bills: ");
                upcoming.stream().limit(6).forEach(u -> s.append(u.merchant()).append(' ').append(peso(u.amount()))
                        .append(u.overdue() ? ", overdue since " : ", due ").append(u.dueDate()).append("; "));
                s.append('\n');
            }
            return s.toString();
        } catch (RuntimeException e) {
            // The chat should still work if analytics fails for some reason.
            log.warn("Could not add spending data to the chat: {}", e.getMessage());
            return "";
        }
    }

    private static String peso(BigDecimal amount) {
        if (amount == null) return "unknown";
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "PHP " + format.format(amount);
    }

    // FOOD -> Food
    private static String label(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
