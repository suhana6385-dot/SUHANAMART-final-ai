package com.suhanamart.service;

import com.suhanamart.dao.ProductDAO;
import com.suhanamart.model.Product;
import com.suhanamart.model.ProductStats;
import com.suhanamart.util.ValidationUtil;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Seller product management: input validation + business rules, on top
 * of ProductDAO. Controllers call this; it is the only layer that
 * decides what a valid product is, and every operation is scoped to the
 * seller id passed in (which controllers take from the session).
 */
public class ProductService {

    /** A product with this much stock or less (but more than 0) counts as "low stock". */
    public static final int LOW_STOCK_THRESHOLD = 5;

    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_CATEGORY_LENGTH = 80;
    public static final int MAX_IMAGE_URL_LENGTH = 500;
    public static final int MAX_STOCK = 1_000_000;

    // products.price is DECIMAL(10,2): up to 8 digits before the point, 2 after.
    private static final Pattern PRICE_PATTERN = Pattern.compile("^\\d{1,8}(\\.\\d{1,2})?$");
    private static final Pattern STOCK_PATTERN = Pattern.compile("^\\d{1,7}$");

    private final ProductDAO productDAO = new ProductDAO();

    // ---------- Reads ----------

    public List<Product> listForSeller(int sellerId) throws SQLException {
        return productDAO.findBySeller(sellerId);
    }

    /** The product if this seller owns it; otherwise NotFoundException. */
    public Product getOwnedProduct(int sellerId, int productId) throws SQLException, NotFoundException {
        Product product = productDAO.findByIdAndSeller(productId, sellerId);
        if (product == null) {
            throw new NotFoundException("Product not found.");
        }
        return product;
    }

    public ProductStats getStats(int sellerId) throws SQLException {
        return productDAO.getStatsForSeller(sellerId, LOW_STOCK_THRESHOLD);
    }

    public List<Product> getLowStockProducts(int sellerId, int limit) throws SQLException {
        return productDAO.findLowStockBySeller(sellerId, LOW_STOCK_THRESHOLD, limit);
    }

    public List<String> listCategories() throws SQLException {
        return productDAO.listCategories();
    }

    // ---------- Writes ----------

    public int createProduct(int sellerId, String name, String description, String price,
                             String category, String imageUrl, String stock)
            throws SQLException, ValidationException {

        ProductInput input = validate(name, description, price, category, imageUrl, stock);

        return productDAO.insertForSeller(sellerId, input.name, input.description, input.price,
                input.category, input.imageUrl, input.stock);
    }

    public void updateProduct(int sellerId, int productId, String name, String description, String price,
                              String category, String imageUrl, String stock)
            throws SQLException, ValidationException, NotFoundException {

        ProductInput input = validate(name, description, price, category, imageUrl, stock);

        boolean updated = productDAO.updateOwned(productId, sellerId, input.name, input.description,
                input.price, input.category, input.imageUrl, input.stock);

        if (!updated) {
            // Either the product doesn't exist or it isn't this seller's. (An UPDATE that
            // changes nothing still reports the row as matched, so 0 really means "not yours".)
            throw new NotFoundException("Product not found.");
        }
    }

    public void updateStock(int sellerId, int productId, String stock)
            throws SQLException, ValidationException, NotFoundException {

        Integer parsedStock = parseStock(stock);
        if (parsedStock == null) {
            throw new ValidationException("Stock must be a whole number between 0 and " + MAX_STOCK + ".");
        }

        if (!productDAO.updateStockOwned(productId, sellerId, parsedStock)) {
            throw new NotFoundException("Product not found.");
        }
    }

    /** @return the deleted product's name, for a confirmation message */
    public String deleteProduct(int sellerId, int productId)
            throws SQLException, ValidationException, NotFoundException {

        Product product = getOwnedProduct(sellerId, productId);

        if (productDAO.hasOrderItems(productId)) {
            throw new ValidationException("\"" + product.getName() + "\" can't be deleted because customers "
                    + "have already ordered it. Set its stock to 0 to stop selling it instead.");
        }

        if (!productDAO.deleteOwned(productId, sellerId)) {
            // Lost a race (an order was placed, or the row vanished, between the check and the delete).
            throw new ValidationException("\"" + product.getName() + "\" could not be deleted. "
                    + "It may have just been ordered - set its stock to 0 instead.");
        }
        return product.getName();
    }

    // ---------- Validation ----------

    private static final class ProductInput {
        String name;
        String description;
        BigDecimal price;
        String category;
        String imageUrl;
        int stock;
    }

    private ProductInput validate(String name, String description, String price,
                                  String category, String imageUrl, String stock)
            throws ValidationException {

        List<String> errors = new ArrayList<>();
        ProductInput input = new ProductInput();

        input.name = trimToNull(name);
        if (input.name == null) {
            errors.add("Product name is required.");
        } else if (input.name.length() > MAX_NAME_LENGTH) {
            errors.add("Product name must be at most " + MAX_NAME_LENGTH + " characters.");
        }

        input.description = trimToNull(description);
        if (input.description != null && input.description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }

        String priceText = price == null ? "" : price.trim();
        if (priceText.isEmpty()) {
            errors.add("Price is required.");
        } else if (!PRICE_PATTERN.matcher(priceText).matches()) {
            errors.add("Price must be a number with at most 2 decimal places, e.g. 499 or 499.50.");
        } else {
            input.price = new BigDecimal(priceText);
            if (input.price.signum() <= 0) {
                errors.add("Price must be greater than 0.");
            }
        }

        input.category = trimToNull(category);
        if (input.category == null) {
            errors.add("Category is required.");
        } else if (input.category.length() > MAX_CATEGORY_LENGTH) {
            errors.add("Category must be at most " + MAX_CATEGORY_LENGTH + " characters.");
        }

        input.imageUrl = trimToNull(imageUrl);
        if (input.imageUrl != null) {
            if (input.imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
                errors.add("Image URL must be at most " + MAX_IMAGE_URL_LENGTH + " characters.");
            } else if (!ValidationUtil.isValidHttpUrl(input.imageUrl)) {
                errors.add("Image URL must be a full web address starting with http:// or https://.");
            }
        }

        Integer parsedStock = parseStock(stock);
        if (parsedStock == null) {
            errors.add("Stock must be a whole number between 0 and " + MAX_STOCK + ".");
        } else {
            input.stock = parsedStock;
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return input;
    }

    /** Returns the stock if it is a whole number 0..MAX_STOCK, otherwise null. */
    private Integer parseStock(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (!STOCK_PATTERN.matcher(text).matches()) {
            return null;
        }
        int parsed = Integer.parseInt(text);
        return parsed <= MAX_STOCK ? parsed : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
