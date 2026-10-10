package com.suhanamart.model;

import java.math.BigDecimal;

public class Product {
    private final int productId;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String category;
    private final String imageUrl;
    private final int stock;
    /** Owning seller's user id; null for products that have no owner (e.g. the demo seed data). */
    private final Integer sellerId;
    /** Owning seller's display name; null unless loaded by AdminDAO.listProducts() (the one place that needs it for the admin product list). */
    private final String sellerName;

    public Product(int productId, String name, String description, BigDecimal price,
                    String category, String imageUrl, int stock) {
        this(productId, name, description, price, category, imageUrl, stock, null, null);
    }

    public Product(int productId, String name, String description, BigDecimal price,
                    String category, String imageUrl, int stock, Integer sellerId) {
        this(productId, name, description, price, category, imageUrl, stock, sellerId, null);
    }

    public Product(int productId, String name, String description, BigDecimal price,
                    String category, String imageUrl, int stock, Integer sellerId, String sellerName) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.imageUrl = imageUrl;
        this.stock = stock;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
    }

    public int getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public int getStock() { return stock; }
    public Integer getSellerId() { return sellerId; }
    /** Null unless loaded by AdminDAO.listProducts(). */
    public String getSellerName() { return sellerName; }
}
