package com.suhanamart.service;

import com.suhanamart.dao.CartDAO;
import com.suhanamart.dao.ProductDAO;
import com.suhanamart.model.Product;
import com.suhanamart.util.ValidationUtil;

import java.sql.SQLException;

/**
 * Buyer cart rules: validates what the browser sent (ids, quantities)
 * BEFORE anything reaches CartDAO. CartServlet calls this instead of the
 * DAO. Every operation is for the user id passed in, which the controller
 * takes from the session - never from the request.
 *
 * Behaviour is the same as before the service existed (adding an existing
 * product increases its quantity, quantities are limited by stock, a
 * quantity of 0 or less removes the line); the additions are that
 * malformed ids/quantities are rejected instead of being passed on, and
 * that quantities are bounded so they can never overflow.
 */
public class CartService {

    /** Largest quantity accepted in one request (stock limits it further). */
    public static final int MAX_QUANTITY = 1000;

    private final CartDAO cartDAO = new CartDAO();
    private final ProductDAO productDAO = new ProductDAO();

    /** Adds a product (default quantity 1); the quantity is limited to the product's current stock. */
    public void addToCart(int userId, String productIdText, String quantityText)
            throws SQLException, ValidationException, NotFoundException {

        Integer productId = ValidationUtil.parsePositiveInt(productIdText);
        if (productId == null) {
            throw new ValidationException("Invalid product.");
        }

        int quantity = 1;
        if (!ValidationUtil.isBlank(quantityText)) {
            Integer parsed = ValidationUtil.parsePositiveInt(quantityText);
            if (parsed == null) {
                throw new ValidationException("Quantity must be a whole number of at least 1.");
            }
            quantity = Math.min(parsed, MAX_QUANTITY);
        }

        Product product = productDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product not found.");
        }
        if (product.getStock() <= 0) {
            throw new ValidationException("\"" + product.getName() + "\" is out of stock.");
        }

        cartDAO.addToCart(userId, productId, Math.min(quantity, product.getStock()));
    }

    /** Sets a line's quantity; 0 or less removes it. The DAO limits it to the product's stock. */
    public void updateQuantity(int userId, String cartItemIdText, String quantityText)
            throws SQLException, ValidationException {

        Integer cartItemId = ValidationUtil.parsePositiveInt(cartItemIdText);
        if (cartItemId == null) {
            throw new ValidationException("Invalid cart item.");
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityText == null ? "" : quantityText.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Quantity must be a whole number.");
        }

        cartDAO.updateQuantity(userId, cartItemId, Math.min(quantity, MAX_QUANTITY));
    }

    public void removeItem(int userId, String cartItemIdText) throws SQLException, ValidationException {
        Integer cartItemId = ValidationUtil.parsePositiveInt(cartItemIdText);
        if (cartItemId == null) {
            throw new ValidationException("Invalid cart item.");
        }
        cartDAO.removeItem(userId, cartItemId);
    }

    public void clearCart(int userId) throws SQLException {
        cartDAO.clearCart(userId);
    }
}
