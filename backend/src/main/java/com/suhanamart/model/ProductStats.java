package com.suhanamart.model;

/**
 * Counts shown on the seller dashboard. All four numbers are computed
 * straight from a seller's products.stock values - nothing is estimated.
 *
 *   active     = products currently in stock (stock &gt; 0)
 *   lowStock   = in stock, but at or below the low-stock threshold
 *   outOfStock = stock is 0
 */
public class ProductStats {
    private final int total;
    private final int active;
    private final int lowStock;
    private final int outOfStock;

    public ProductStats(int total, int active, int lowStock, int outOfStock) {
        this.total = total;
        this.active = active;
        this.lowStock = lowStock;
        this.outOfStock = outOfStock;
    }

    public int getTotal() { return total; }
    public int getActive() { return active; }
    public int getLowStock() { return lowStock; }
    public int getOutOfStock() { return outOfStock; }
}
