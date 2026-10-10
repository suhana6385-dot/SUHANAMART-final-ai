package com.suhanamart.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;

/**
 * One buyer review of a product they bought (a row of the "reviews"
 * table), plus the two small value types the Review Module passes
 * around:
 *
 *   Review.Summary        - average rating / count / per-star counts of one product
 *   Review.PurchasedItem  - the facts about one purchased order line that decide
 *                           whether (and how) the buyer may review it
 *
 * Immutable, like the other models. reviewerName is only filled when the
 * review is loaded for display (joined from users.name); it is never used
 * for authorization.
 */
public class Review {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    private final int reviewId;
    private final int userId;
    private final int productId;
    private final int orderId;
    private final int rating;
    private final String comment;
    private final Timestamp createdAt;
    private final Timestamp updatedAt;
    private final String reviewerName;

    public Review(int reviewId, int userId, int productId, int orderId, int rating, String comment,
                  Timestamp createdAt, Timestamp updatedAt, String reviewerName) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.productId = productId;
        this.orderId = orderId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.reviewerName = reviewerName;
    }

    public int getReviewId() { return reviewId; }
    public int getUserId() { return userId; }
    public int getProductId() { return productId; }
    public int getOrderId() { return orderId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public String getReviewerName() { return reviewerName; }

    // ------------------------------------------------------------------

    /**
     * Rating statistics for one product. The average is computed from the
     * exact per-star counts with BigDecimal arithmetic (no floating-point
     * drift) and rounded half-up to ONE decimal place, e.g. 4.3.
     * A product with no reviews has count 0 and average 0.0.
     */
    public static final class Summary {
        private final int[] countsByStar = new int[MAX_RATING + 1]; // index 1..5 used
        private final int total;
        private final BigDecimal average;

        /** @param starCounts number of reviews at each star, index 1..5 (index 0 is ignored) */
        public Summary(int[] starCounts) {
            int count = 0;
            long stars = 0;
            for (int star = MIN_RATING; star <= MAX_RATING; star++) {
                int n = star < starCounts.length ? Math.max(0, starCounts[star]) : 0;
                countsByStar[star] = n;
                count += n;
                stars += (long) n * star;
            }
            this.total = count;
            this.average = count == 0
                    ? BigDecimal.ZERO.setScale(1)
                    : BigDecimal.valueOf(stars).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        }

        public static Summary empty() {
            return new Summary(new int[MAX_RATING + 1]);
        }

        public int getReviewCount() { return total; }
        public boolean hasReviews() { return total > 0; }

        /** Average rating rounded to one decimal place (0.0 when there are no reviews). */
        public BigDecimal getAverageRating() { return average; }

        /** How many reviews gave exactly this many stars (0 for an out-of-range star). */
        public int getCount(int star) {
            return star >= MIN_RATING && star <= MAX_RATING ? countsByStar[star] : 0;
        }

        /** Share of reviews at this star, 0-100 (whole percent), for the distribution bars. */
        public int getPercent(int star) {
            return total == 0 ? 0 : Math.round(getCount(star) * 100f / total);
        }

        /** How much of a five-star row should be filled for the average, 0-100 (for the CSS star meter). */
        public int getAveragePercent() {
            return average.multiply(BigDecimal.valueOf(20)).intValue();
        }
    }

    // ------------------------------------------------------------------

    /**
     * One line of one of the buyer's orders, with everything the service
     * needs to decide whether it can be reviewed. Loaded by ReviewDAO with
     * the order's owner already checked in SQL, so an instance can only
     * ever describe the current buyer's own purchase.
     */
    public static final class PurchasedItem {
        private final int orderId;
        private final int productId;
        private final String productName;
        private final String imageUrl;
        private final String orderStatus;
        private final String itemStatus;
        private final Integer sellerId;
        private final boolean alreadyReviewed;

        public PurchasedItem(int orderId, int productId, String productName, String imageUrl,
                             String orderStatus, String itemStatus, Integer sellerId,
                             boolean alreadyReviewed) {
            this.orderId = orderId;
            this.productId = productId;
            this.productName = productName;
            this.imageUrl = imageUrl;
            this.orderStatus = orderStatus;
            this.itemStatus = itemStatus;
            this.sellerId = sellerId;
            this.alreadyReviewed = alreadyReviewed;
        }

        public int getOrderId() { return orderId; }
        public int getProductId() { return productId; }
        /** The name the product had when it was ordered (order_items.product_name). */
        public String getProductName() { return productName; }
        public String getImageUrl() { return imageUrl; }
        /** orders.status - the roll-up status of the whole order. */
        public String getOrderStatus() { return orderStatus; }
        /** order_items.status - this line's own status. */
        public String getItemStatus() { return itemStatus; }
        /** The product's owning seller (null for unowned/demo products). */
        public Integer getSellerId() { return sellerId; }
        public boolean isAlreadyReviewed() { return alreadyReviewed; }
    }
}
