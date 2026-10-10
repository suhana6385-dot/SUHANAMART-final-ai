package com.suhanamart.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

/**
 * One order as a single SELLER sees it: the order header, who placed it,
 * and ONLY the line items that belong to that seller's products. In an
 * order that mixes several sellers' products, the other sellers' lines
 * (and their totals) are deliberately absent from this object.
 *
 * status is the least-advanced status among this seller's own lines, so
 * it reflects this seller's part of the order, not the whole order.
 */
public class SellerOrder {
    private final int orderId;
    private final Timestamp createdAt;
    private final String paymentMethod;
    private final String customerName;
    private final String customerEmail;
    private final List<OrderItem> items;

    public SellerOrder(int orderId, Timestamp createdAt, String paymentMethod,
                       String customerName, String customerEmail, List<OrderItem> items) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.paymentMethod = paymentMethod;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.items = Collections.unmodifiableList(items);
    }

    public int getOrderId() { return orderId; }
    public Timestamp getCreatedAt() { return createdAt; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public List<OrderItem> getItems() { return items; }

    /** Sum of this seller's line subtotals only. */
    public BigDecimal getSellerTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    public int getTotalQuantity() {
        int quantity = 0;
        for (OrderItem item : items) {
            quantity += item.getQuantity();
        }
        return quantity;
    }

    public OrderStatus getStatus() {
        java.util.List<OrderStatus> statuses = new java.util.ArrayList<>();
        for (OrderItem item : items) {
            statuses.add(OrderStatus.fromDb(item.getStatus()));
        }
        return OrderStatus.leastAdvanced(statuses);
    }
}
