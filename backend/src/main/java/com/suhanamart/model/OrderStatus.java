package com.suhanamart.model;

import java.util.Collection;

/**
 * Application-level fulfilment status (no courier / GPS tracking).
 *
 * <pre>
 *   PENDING -> CONFIRMED -> SHIPPED -> DELIVERED
 * </pre>
 *
 * The enum order IS the flow order, so ordinal() comparisons mean
 * "further along". Values are stored in the database by name()
 * (e.g. "CONFIRMED") and shown to people via getLabel().
 */
public enum OrderStatus {

    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Lower-case name, used to build CSS class names such as "badge-shipped". */
    public String getCssClass() {
        return name().toLowerCase();
    }

    /** The only status a seller may move to from this one; null once DELIVERED. */
    public OrderStatus next() {
        int nextOrdinal = ordinal() + 1;
        OrderStatus[] all = values();
        return nextOrdinal < all.length ? all[nextOrdinal] : null;
    }

    public boolean isTerminal() {
        return next() == null;
    }

    /**
     * STRICT parse for values that come from the browser: returns null
     * unless the text is exactly one of the four statuses (case and
     * surrounding spaces ignored). Anything else - including the legacy
     * "PLACED" - is rejected, so a tampered request can never introduce
     * an arbitrary status.
     */
    public static OrderStatus parse(String value) {
        if (value == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }

    /**
     * TOLERANT read for values that come from the database. Rows created
     * before the seller module were labelled "PLACED"; those (and any
     * null/unknown value) are shown as PENDING.
     */
    public static OrderStatus fromDb(String value) {
        OrderStatus parsed = parse(value);
        return parsed != null ? parsed : PENDING;
    }

    /** The least-advanced status in the collection (PENDING if empty). */
    public static OrderStatus leastAdvanced(Collection<OrderStatus> statuses) {
        OrderStatus least = null;
        for (OrderStatus status : statuses) {
            if (least == null || status.ordinal() < least.ordinal()) {
                least = status;
            }
        }
        return least != null ? least : PENDING;
    }
}
