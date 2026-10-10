package com.suhanamart.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Order {
    private final int orderId;
    private final BigDecimal totalAmount;
    private final String status;
    private final String paymentMethod;
    private final Timestamp createdAt;

    public Order(int orderId, BigDecimal totalAmount, String status,
                 String paymentMethod, Timestamp createdAt) {
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.createdAt = createdAt;
    }

    public int getOrderId() { return orderId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public Timestamp getCreatedAt() { return createdAt; }
}
