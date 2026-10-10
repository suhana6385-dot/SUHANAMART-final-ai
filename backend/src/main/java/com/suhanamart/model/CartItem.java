package com.suhanamart.model;

import java.math.BigDecimal;

public class CartItem {
    private final int cartItemId;
    private final int productId;
    private final String productName;
    private final String imageUrl;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final int stock;

    public CartItem(int cartItemId, int productId, String productName, String imageUrl,
                     BigDecimal unitPrice, int quantity, int stock) {
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.stock = stock;
    }

    public int getCartItemId() { return cartItemId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public int getStock() { return stock; }
    public BigDecimal getSubtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
