package com.suhanamart.model;

import java.math.BigDecimal;

/** A single line item within an order - a price/quantity snapshot
 *  taken at the moment the order was placed, independent of any
 *  later change to the product's current price. */
public class OrderItem {
    private final int orderItemId;
    private final int productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;
    /** Fulfilment status of this line (OrderStatus name, e.g. "SHIPPED"); may be null for legacy callers. */
    private final String status;

    public OrderItem(String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        this(0, 0, productName, quantity, unitPrice, subtotal, null);
    }

    public OrderItem(int orderItemId, int productId, String productName, int quantity,
                     BigDecimal unitPrice, BigDecimal subtotal, String status) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
        this.status = status;
    }

    public int getOrderItemId() { return orderItemId; }
    public int getProductId() { return productId; }
    public String getStatus() { return status; }

    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
}
