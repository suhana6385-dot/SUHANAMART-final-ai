package com.suhanamart.service;

import com.suhanamart.dao.OrderDAO;
import com.suhanamart.dao.ProductDAO;
import com.suhanamart.model.Order;
import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.Product;
import com.suhanamart.model.Review;
import com.suhanamart.util.AiConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The AI chatbot's business logic:
 *
 *   ChatServlet -> ChatService.answer() -> (this class) -> an AI provider, with a
 *   safe, database-grounded fallback if the provider is unavailable or unconfigured
 *
 * Scope, deliberately narrow:
 *   - PRODUCTS: name, description, category, price, stock - looked up by
 *     keyword from the user's own message (ProductDAO.searchProducts),
 *     never a raw user-supplied query string run as SQL.
 *   - REVIEWS: a matched product's average rating and review count
 *     (ReviewService.getRatingSummary) - no individual reviewer names.
 *   - ORDERS: ONLY the signed-in caller's own recent orders (order id,
 *     date, total, status), and only when the message looks like it's
 *     asking about orders - OrderDAO.getOrdersForUser is already scoped
 *     to one user_id, so this can never surface anyone else's order.
 *
 * This is intentionally not a general database interface: every query
 * runs with parameters the server computes (keywords extracted from the
 * message, the caller's own id), never a value an attacker could turn
 * into an arbitrary lookup, and the only tables touched are the three
 * above.
 */
public class ChatService {

    private static final Logger logger = LogManager.getLogger(ChatService.class);

    public static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MIN_MESSAGE_LENGTH = 1;

    /** Requests per user within the rolling window below. */
    public static final int RATE_LIMIT_COUNT = 10;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private static final int MAX_PRODUCT_MATCHES = 5;
    private static final int MAX_ORDERS_SHOWN = 5;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]{3,}");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "you", "your", "about", "that", "this", "with", "have",
            "what", "does", "any", "can", "how", "much", "price", "cost", "there", "their",
            "show", "tell", "me", "please", "hello", "hey", "hii");
    private static final Set<String> ORDER_INTENT_WORDS = Set.of(
            "order", "orders", "delivery", "deliver", "delivered", "status", "track", "tracking",
            "purchase", "purchased", "bought", "shipment", "shipped");

    /** One entry per user id: the timestamps of their requests in the current window. */
    private static final Map<Integer, Deque<Instant>> REQUEST_LOG = new ConcurrentHashMap<>();

    private final ProductDAO productDAO = new ProductDAO();
    private final ReviewService reviewService = new ReviewService();
    private final OrderDAO orderDAO = new OrderDAO();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    // ---------------------------------------------------------------
    // Rate limiting
    // ---------------------------------------------------------------

    /** True if this user may send another message right now; also records the attempt if so. */
    public boolean allowRequest(int userId) {
        Deque<Instant> timestamps = REQUEST_LOG.computeIfAbsent(userId, id -> new ArrayDeque<>());
        Instant now = Instant.now();

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(RATE_LIMIT_WINDOW))) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= RATE_LIMIT_COUNT) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    // ---------------------------------------------------------------
    // Answering
    // ---------------------------------------------------------------

    /**
     * Validates the message, gathers only the context it actually needs, and answers -
     * through the AI provider if one is configured and responds in time, otherwise through
     * a direct, templated answer built from the very same context.
     *
     * @param userId null for an anonymous caller (order context is then never gathered)
     * @throws ValidationException the message is empty, too long, or contains control characters
     */
    public String answer(Integer userId, String rawMessage) throws ValidationException, SQLException {
        String message = rawMessage == null ? "" : rawMessage.replace("\r\n", "\n").strip();

        if (message.codePointCount(0, message.length()) < MIN_MESSAGE_LENGTH) {
            throw new ValidationException("Please type a question first.");
        }
        if (message.codePointCount(0, message.length()) > MAX_MESSAGE_LENGTH) {
            throw new ValidationException("That message is too long (max " + MAX_MESSAGE_LENGTH + " characters).");
        }
        if (FORBIDDEN_CHARS.matcher(message).find()) {
            throw new ValidationException("Your message contains characters that aren't allowed.");
        }

        Context context = gatherContext(userId, message);

        if (AiConfig.isConfigured()) {
            String aiReply = tryAiProvider(message, context);
            if (aiReply != null) {
                return aiReply;
            }
            // Falls through to the deterministic fallback below - the caller never sees an error.
        }
        return fallbackAnswer(context);
    }

    // ---------------------------------------------------------------
    // Context gathering - only what the message actually asks about
    // ---------------------------------------------------------------

    private static final class Context {
        List<Product> products = List.of();
        List<Order> recentOrders = List.of();
        Map<Integer, Review.Summary> ratingByProduct = new java.util.HashMap<>();
    }

    private Context gatherContext(Integer userId, String message) throws SQLException {
        Context context = new Context();

        Set<String> keywords = extractKeywords(message);
        if (!keywords.isEmpty()) {
            List<Product> matches = new ArrayList<>();
            Set<Integer> seenIds = new LinkedHashSet<>();
            for (String keyword : keywords) {
                for (Product product : productDAO.searchProducts(keyword, null)) {
                    if (seenIds.add(product.getProductId())) {
                        matches.add(product);
                    }
                    if (matches.size() >= MAX_PRODUCT_MATCHES) {
                        break;
                    }
                }
                if (matches.size() >= MAX_PRODUCT_MATCHES) {
                    break;
                }
            }
            context.products = matches;
            for (Product product : matches) {
                context.ratingByProduct.put(product.getProductId(), reviewService.getRatingSummary(product.getProductId()));
            }
        }

        if (userId != null && looksLikeOrderQuestion(message)) {
            List<Order> orders = orderDAO.getOrdersForUser(userId);
            context.recentOrders = orders.size() > MAX_ORDERS_SHOWN ? orders.subList(0, MAX_ORDERS_SHOWN) : orders;
        }

        return context;
    }

    private Set<String> extractKeywords(String message) {
        Set<String> keywords = new LinkedHashSet<>();
        java.util.regex.Matcher matcher = WORD.matcher(message.toLowerCase());
        while (matcher.find() && keywords.size() < 6) {
            String word = matcher.group();
            if (!STOPWORDS.contains(word)) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private boolean looksLikeOrderQuestion(String message) {
        String lower = message.toLowerCase();
        for (String word : ORDER_INTENT_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // AI provider (Anthropic Messages API) - optional, with a timeout
    // and a hand-rolled, narrowly-scoped JSON encode/decode (no JSON
    // library dependency was added - see docs/architecture.md)
    // ---------------------------------------------------------------

    /** Null (never an exception) if the provider is unavailable, slow, or returns something unexpected. */
    private String tryAiProvider(String message, Context context) {
        try {
            String body = buildRequestBody(message, context);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AiConfig.apiUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("content-type", "application/json")
                    .header("x-api-key", AiConfig.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("AI provider returned HTTP {} (body omitted - may contain account details)",
                        response.statusCode());
                return null;
            }

            String text = extractText(response.body());
            return text != null && !text.isBlank() ? text.trim() : null;

        } catch (HttpTimeoutException e) {
            logger.warn("AI provider timed out", e);
            return null;
        } catch (IOException | InterruptedException e) {
            logger.warn("AI provider call failed", e);
            return null;
        } catch (RuntimeException e) {
            // Defensive: a provider response we didn't anticipate must never surface as a 500.
            logger.error("Unexpected error calling the AI provider", e);
            return null;
        }
    }

    private String buildRequestBody(String message, Context context) {
        String system = "You are the SUHANAMART shopping assistant. Answer ONLY using the CONTEXT "
                + "below (SUHANAMART product, review and the current buyer's own order data). "
                + "If the question is outside SUHANAMART's products, reviews or the buyer's own "
                + "orders, politely say you can only help with SUHANAMART shopping questions. "
                + "Never invent a product, price, or order that is not listed in the context. "
                + "Keep the answer under 80 words.\n\nCONTEXT:\n" + renderContextForAi(context);

        String json = "{"
                + "\"model\":" + jsonString(AiConfig.model()) + ","
                + "\"max_tokens\":300,"
                + "\"system\":" + jsonString(system) + ","
                + "\"messages\":[{\"role\":\"user\",\"content\":" + jsonString(message) + "}]"
                + "}";
        return json;
    }

    private String renderContextForAi(Context context) {
        StringBuilder sb = new StringBuilder();
        if (context.products.isEmpty()) {
            sb.append("No matching products found.\n");
        } else {
            sb.append("Matching products:\n");
            for (Product p : context.products) {
                Review.Summary summary = context.ratingByProduct.get(p.getProductId());
                sb.append("- ").append(p.getName())
                        .append(" | category: ").append(p.getCategory())
                        .append(" | price: Rs.").append(p.getPrice())
                        .append(" | stock: ").append(p.getStock() > 0 ? p.getStock() + " available" : "out of stock")
                        .append(" | rating: ")
                        .append(summary != null && summary.hasReviews()
                                ? summary.getAverageRating() + "/5 (" + summary.getReviewCount() + " reviews)"
                                : "no reviews yet")
                        .append('\n');
            }
        }
        if (!context.recentOrders.isEmpty()) {
            sb.append("The current buyer's recent orders:\n");
            for (Order o : context.recentOrders) {
                sb.append("- Order #").append(o.getOrderId())
                        .append(" | total: Rs.").append(o.getTotalAmount())
                        .append(" | status: ").append(OrderStatus.fromDb(o.getStatus()).getLabel())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Extracts ONLY the first content block's "text" value from an Anthropic Messages API
     * response: {"content":[{"type":"text","text":"..."}], ...}. This is not a general JSON
     * parser - it looks for exactly that one field and nothing else, and returns null rather
     * than guess at anything the shape doesn't match.
     */
    static String extractText(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        int textKey = responseBody.indexOf("\"text\"");
        if (textKey < 0) {
            return null;
        }
        int colon = responseBody.indexOf(':', textKey + 6);
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        int len = responseBody.length();
        while (i < len && Character.isWhitespace(responseBody.charAt(i))) {
            i++;
        }
        if (i >= len || responseBody.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder out = new StringBuilder();
        while (i < len) {
            char c = responseBody.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\' && i + 1 < len) {
                char next = responseBody.charAt(i + 1);
                switch (next) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case 'r': out.append('\r'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'u':
                        if (i + 5 < len) {
                            try {
                                out.append((char) Integer.parseInt(responseBody.substring(i + 2, i + 6), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                // Malformed escape - stop rather than guess.
                                return out.toString();
                            }
                        }
                        break;
                    default: out.append(next);
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return null; // Ran off the end without a closing quote - not the shape we expect.
    }

    /** Minimal JSON string escaping for the handful of fields this class sends. */
    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    // ---------------------------------------------------------------
    // Fallback (no AI provider configured, or it failed/timed out) -
    // still answers directly from the SAME context, never a raw error
    // ---------------------------------------------------------------

    private String fallbackAnswer(Context context) {
        StringBuilder sb = new StringBuilder();

        if (!context.products.isEmpty()) {
            sb.append("Here's what I found:\n");
            for (Product p : context.products) {
                Review.Summary summary = context.ratingByProduct.get(p.getProductId());
                sb.append("\n- ").append(p.getName()).append(" (").append(p.getCategory()).append(") - Rs.")
                        .append(p.getPrice()).append(", ")
                        .append(p.getStock() > 0 ? p.getStock() + " in stock" : "out of stock");
                if (summary != null && summary.hasReviews()) {
                    sb.append(", rated ").append(summary.getAverageRating()).append("/5 (")
                            .append(summary.getReviewCount()).append(" reviews)");
                }
            }
        }

        if (!context.recentOrders.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("Your recent orders:\n");
            for (Order o : context.recentOrders) {
                sb.append("\n- Order #").append(o.getOrderId()).append(": Rs.").append(o.getTotalAmount())
                        .append(" - ").append(OrderStatus.fromDb(o.getStatus()).getLabel());
            }
        }

        if (sb.length() == 0) {
            return "I can help with SUHANAMART products, reviews, and your own orders - try asking "
                    + "about a product by name, or about your recent orders.";
        }
        return sb.toString();
    }
}
