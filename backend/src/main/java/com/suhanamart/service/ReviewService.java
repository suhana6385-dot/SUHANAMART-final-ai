package com.suhanamart.service;

import com.suhanamart.dao.ReviewDAO;
import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.Review;
import com.suhanamart.util.ValidationUtil;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Review Module business rules. ReviewServlet calls this; this calls
 * ReviewDAO. It is the only place that decides
 *
 *   - what a valid rating / comment is,
 *   - which purchases a buyer may review, and
 *   - what happens on a duplicate.
 *
 * Every check runs BEFORE anything is written. The DAO's INSERT repeats
 * the ownership/delivered checks as a last line of defence against races.
 */
public class ReviewService {

    public static final int MIN_COMMENT_LENGTH = 5;
    public static final int MAX_COMMENT_LENGTH = 1000;

    /** How many of a product's newest reviews the product page lists. */
    public static final int REVIEWS_SHOWN = 20;

    // Exactly one digit 1-5 (so "05", "1.5", "5; DROP", "-1", "" are all rejected).
    private static final Pattern RATING_PATTERN = Pattern.compile("^[1-5]$");
    // Control characters other than newline / tab (NUL, escape codes, ...).
    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");

    private static final int MYSQL_DUPLICATE_KEY = 1062;

    /** Where one purchased line stands with respect to reviewing. */
    public enum Eligibility {
        /** Delivered, not yet reviewed - show the "Write a review" action. */
        REVIEWABLE,
        /** The buyer already reviewed this product for this order. */
        ALREADY_REVIEWED,
        /** Order (or this line) has not been delivered yet. */
        NOT_DELIVERED,
        /** A seller's own product - sellers cannot review what they sell. */
        OWN_PRODUCT
    }

    private final ReviewDAO reviewDAO = new ReviewDAO();

    // ---------- Eligibility ----------

    /** The single rule that decides whether a purchased line can be reviewed. */
    public Eligibility evaluate(int userId, Review.PurchasedItem item) {
        if (item.getSellerId() != null && item.getSellerId() == userId) {
            return Eligibility.OWN_PRODUCT;
        }
        if (item.isAlreadyReviewed()) {
            return Eligibility.ALREADY_REVIEWED;
        }
        // Strict parse: only exactly DELIVERED counts (legacy/unknown values never do).
        boolean delivered = OrderStatus.parse(item.getOrderStatus()) == OrderStatus.DELIVERED
                && OrderStatus.parse(item.getItemStatus()) == OrderStatus.DELIVERED;
        return delivered ? Eligibility.REVIEWABLE : Eligibility.NOT_DELIVERED;
    }

    /**
     * The purchased line, if this buyer may review it right now.
     *
     * @throws NotFoundException   the order doesn't exist, isn't the buyer's, or doesn't
     *                             contain that product (all indistinguishable on purpose)
     * @throws ValidationException it exists but cannot be reviewed (not delivered yet,
     *                             already reviewed, or the buyer's own product)
     */
    public Review.PurchasedItem getReviewableItem(int userId, int orderId, int productId)
            throws SQLException, NotFoundException, ValidationException {

        Review.PurchasedItem item = reviewDAO.findPurchasedItem(userId, orderId, productId);
        if (item == null) {
            throw new NotFoundException("We couldn't find that item in your orders.");
        }

        switch (evaluate(userId, item)) {
            case OWN_PRODUCT:
                throw new ValidationException("You can't review your own product.");
            case ALREADY_REVIEWED:
                throw new ValidationException("You have already reviewed this product for this order.");
            case NOT_DELIVERED:
                throw new ValidationException("You can review a product once its order has been delivered.");
            default:
                return item;
        }
    }

    /**
     * Review state of every line of one of the buyer's orders, keyed by
     * product id, in order-line order. Empty if the order isn't theirs.
     * The buyer's order page uses this to decide which lines get a
     * "Write a review" action.
     */
    public Map<Integer, Eligibility> getEligibilityForOrder(int userId, int orderId) throws SQLException {
        Map<Integer, Eligibility> result = new LinkedHashMap<>();
        for (Review.PurchasedItem item : reviewDAO.findPurchasedItems(userId, orderId)) {
            result.put(item.getProductId(), evaluate(userId, item));
        }
        return result;
    }

    // ---------- Submitting ----------

    /**
     * Validates and stores a review. All arguments are the raw request
     * text - nothing is trusted.
     *
     * @return the new review's id
     * @throws ValidationException bad input (messages are safe to show), or the purchase
     *                             cannot be reviewed / was already reviewed
     * @throws NotFoundException   the order/product is not one of this buyer's purchases
     */
    public int submitReview(int userId, String orderIdText, String productIdText,
                            String ratingText, String commentText)
            throws SQLException, ValidationException, NotFoundException {

        // 1. Shape of the request - rejected before the database is touched.
        Integer orderId = ValidationUtil.parsePositiveInt(orderIdText);
        Integer productId = ValidationUtil.parsePositiveInt(productIdText);
        if (orderId == null || productId == null) {
            throw new ValidationException("That review request is not valid.");
        }

        List<String> errors = new ArrayList<>();

        int rating = 0;
        String ratingTrimmed = ratingText == null ? "" : ratingText.trim();
        if (!RATING_PATTERN.matcher(ratingTrimmed).matches()) {
            errors.add("Please choose a rating from " + Review.MIN_RATING + " to " + Review.MAX_RATING + " stars.");
        } else {
            rating = Integer.parseInt(ratingTrimmed);
        }

        String comment = normalizeComment(commentText);
        if (comment.isEmpty()) {
            errors.add("Please write a few words about the product.");
        } else if (FORBIDDEN_CHARS.matcher(comment).find()) {
            errors.add("Your review contains characters that aren't allowed.");
        } else {
            int length = comment.codePointCount(0, comment.length());
            if (length < MIN_COMMENT_LENGTH) {
                errors.add("Your review must be at least " + MIN_COMMENT_LENGTH + " characters long.");
            } else if (length > MAX_COMMENT_LENGTH) {
                errors.add("Your review must be at most " + MAX_COMMENT_LENGTH + " characters long.");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // 2. Business rules: the buyer's own delivered, not-yet-reviewed purchase.
        getReviewableItem(userId, orderId, productId);

        // 3. Write. The DAO re-checks ownership/delivery in the INSERT itself and the
        //    UNIQUE key rejects a duplicate even if two requests race each other.
        try {
            int reviewId = reviewDAO.insertIfDelivered(userId, orderId, productId, rating, comment);
            if (reviewId == 0) {
                throw new ValidationException(
                        "This item can't be reviewed right now. Please check your order and try again.");
            }
            return reviewId;

        } catch (SQLIntegrityConstraintViolationException e) {
            if (e.getErrorCode() == MYSQL_DUPLICATE_KEY) {
                throw new ValidationException("You have already reviewed this product for this order.");
            }
            throw e;
        }
    }

    // ---------- Reading ----------

    public Review.Summary getRatingSummary(int productId) throws SQLException {
        return reviewDAO.getSummary(productId);
    }

    public List<Review> getRecentReviews(int productId) throws SQLException {
        List<Review> reviews = reviewDAO.findByProduct(productId, REVIEWS_SHOWN);
        return Collections.unmodifiableList(reviews);
    }

    // ---------- helpers ----------

    /** Trims and converts Windows/Mac line breaks to \n so length limits are consistent. */
    private String normalizeComment(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }
}
