package com.suhanamart.controller;

import com.suhanamart.model.Product;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.ProductService;
import com.suhanamart.service.ValidationException;
import com.suhanamart.util.Flash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seller product management.
 *
 *   GET  /seller/products             list MY products (+ inline stock update)
 *   GET  /seller/products/new         add-product form
 *   GET  /seller/products/edit?id=N   edit form (only if product N is mine)
 *   POST /seller/products/create      add a product
 *   POST /seller/products/update      edit a product
 *   POST /seller/products/stock       update just the stock quantity
 *   POST /seller/products/delete      delete a product
 *
 * The seller id always comes from the session. Product ids from the
 * browser are only ever used together with that seller id inside the
 * DAO queries, so another seller's product simply "isn't found".
 * Every POST also requires the session's CSRF token.
 */
@WebServlet("/seller/products/*")
public class SellerProductServlet extends SellerBaseServlet {

    private static final Logger logger = LogManager.getLogger(SellerProductServlet.class);

    private static final String LIST_URL = "/seller/products";
    private static final String GENERIC_ERROR = "Something went wrong. Please try again later.";

    private final ProductService productService = new ProductService();

    // ------------------------------------------------------------------ GET

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer sellerId = requireSeller(request, response);
        if (sellerId == null) {
            return;
        }

        String path = request.getPathInfo();

        if (path == null || "/".equals(path)) {
            showList(request, response, sellerId);
        } else if ("/new".equals(path)) {
            showAddForm(request, response);
        } else if ("/edit".equals(path)) {
            showEditForm(request, response, sellerId);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void showList(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws ServletException, IOException {

        Flash.consume(request);

        List<Product> products = Collections.emptyList();
        boolean loadError = false;

        try {
            products = productService.listForSeller(sellerId);
        } catch (SQLException e) {
            logger.error("Failed to load products for seller {}", sellerId, e);
            loadError = true;
        }

        request.setAttribute("products", products);
        request.setAttribute("loadError", loadError);
        request.setAttribute("lowStockThreshold", ProductService.LOW_STOCK_THRESHOLD);
        request.getRequestDispatcher("/public/seller/products.jsp").forward(request, response);
    }

    private void showAddForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("formValues", blankForm());
        loadCategories(request);
        request.getRequestDispatcher("/public/seller/add-product.jsp").forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws ServletException, IOException {

        int productId = parseId(request.getParameter("id"));

        try {
            Product product = productService.getOwnedProduct(sellerId, productId);

            request.setAttribute("product", product);
            request.setAttribute("formValues", formFrom(product));
            loadCategories(request);
            request.getRequestDispatcher("/public/seller/edit-product.jsp").forward(request, response);

        } catch (NotFoundException e) {
            Flash.error(request, "That product could not be found in your products.");
            redirect(request, response, LIST_URL);
        } catch (SQLException e) {
            logger.error("Failed to load product {} for seller {}", productId, sellerId, e);
            Flash.error(request, GENERIC_ERROR);
            redirect(request, response, LIST_URL);
        }
    }

    // ----------------------------------------------------------------- POST

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer sellerId = requireSeller(request, response);
        if (sellerId == null) {
            return;
        }
        if (!requireCsrf(request, response)) {
            return;
        }

        String path = request.getPathInfo();
        if (path == null) {
            path = "";
        }

        switch (path) {
            case "/create":
                createProduct(request, response, sellerId);
                break;
            case "/update":
                updateProduct(request, response, sellerId);
                break;
            case "/stock":
                updateStock(request, response, sellerId);
                break;
            case "/delete":
                deleteProduct(request, response, sellerId);
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void createProduct(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws ServletException, IOException {

        try {
            productService.createProduct(sellerId,
                    request.getParameter("name"),
                    request.getParameter("description"),
                    request.getParameter("price"),
                    request.getParameter("category"),
                    request.getParameter("imageUrl"),
                    request.getParameter("stock"));

            Flash.success(request, "Product added.");
            redirect(request, response, LIST_URL);

        } catch (ValidationException e) {
            request.setAttribute("errors", e.getMessages());
            request.setAttribute("formValues", formFrom(request));
            loadCategories(request);
            request.getRequestDispatcher("/public/seller/add-product.jsp").forward(request, response);

        } catch (SQLException e) {
            logger.error("Failed to add product for seller {}", sellerId, e);
            request.setAttribute("errors", Collections.singletonList(GENERIC_ERROR));
            request.setAttribute("formValues", formFrom(request));
            loadCategories(request);
            request.getRequestDispatcher("/public/seller/add-product.jsp").forward(request, response);
        }
    }

    private void updateProduct(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws ServletException, IOException {

        int productId = parseId(request.getParameter("productId"));

        try {
            productService.updateProduct(sellerId, productId,
                    request.getParameter("name"),
                    request.getParameter("description"),
                    request.getParameter("price"),
                    request.getParameter("category"),
                    request.getParameter("imageUrl"),
                    request.getParameter("stock"));

            Flash.success(request, "Product updated.");
            redirect(request, response, LIST_URL);

        } catch (ValidationException e) {
            redisplayEditForm(request, response, sellerId, productId, e.getMessages());

        } catch (NotFoundException e) {
            Flash.error(request, "That product could not be found in your products.");
            redirect(request, response, LIST_URL);

        } catch (SQLException e) {
            logger.error("Failed to update product {} for seller {}", productId, sellerId, e);
            redisplayEditForm(request, response, sellerId, productId, Collections.singletonList(GENERIC_ERROR));
        }
    }

    /** Shows the edit form again with the seller's typed values and the error messages. */
    private void redisplayEditForm(HttpServletRequest request, HttpServletResponse response,
                                   int sellerId, int productId, List<String> errors)
            throws ServletException, IOException {

        try {
            Product product = productService.getOwnedProduct(sellerId, productId);
            request.setAttribute("product", product);
            request.setAttribute("errors", errors);
            request.setAttribute("formValues", formFrom(request));
            loadCategories(request);
            request.getRequestDispatcher("/public/seller/edit-product.jsp").forward(request, response);

        } catch (NotFoundException e) {
            Flash.error(request, "That product could not be found in your products.");
            redirect(request, response, LIST_URL);
        } catch (SQLException e) {
            logger.error("Failed to reload product {} for seller {}", productId, sellerId, e);
            Flash.error(request, GENERIC_ERROR);
            redirect(request, response, LIST_URL);
        }
    }

    private void updateStock(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws IOException {

        int productId = parseId(request.getParameter("productId"));

        try {
            productService.updateStock(sellerId, productId, request.getParameter("stock"));
            Flash.success(request, "Stock updated.");

        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
        } catch (NotFoundException e) {
            Flash.error(request, "That product could not be found in your products.");
        } catch (SQLException e) {
            logger.error("Failed to update stock of product {} for seller {}", productId, sellerId, e);
            Flash.error(request, GENERIC_ERROR);
        }
        redirect(request, response, LIST_URL);
    }

    private void deleteProduct(HttpServletRequest request, HttpServletResponse response, int sellerId)
            throws IOException {

        int productId = parseId(request.getParameter("productId"));

        try {
            String deletedName = productService.deleteProduct(sellerId, productId);
            Flash.success(request, "\"" + deletedName + "\" was deleted.");

        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
        } catch (NotFoundException e) {
            Flash.error(request, "That product could not be found in your products.");
        } catch (SQLException e) {
            logger.error("Failed to delete product {} for seller {}", productId, sellerId, e);
            Flash.error(request, GENERIC_ERROR);
        }
        redirect(request, response, LIST_URL);
    }

    // -------------------------------------------------------------- helpers

    private void loadCategories(HttpServletRequest request) {
        try {
            request.setAttribute("categories", productService.listCategories());
        } catch (SQLException e) {
            // Categories are only autocomplete suggestions - the form still works without them.
            logger.warn("Could not load category suggestions", e);
            request.setAttribute("categories", Collections.emptyList());
        }
    }

    private Map<String, String> blankForm() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : new String[] {"name", "description", "price", "category", "imageUrl", "stock"}) {
            values.put(field, "");
        }
        return values;
    }

    /** Echoes back exactly what the seller typed, so a rejected form isn't lost. */
    private Map<String, String> formFrom(HttpServletRequest request) {
        Map<String, String> values = blankForm();
        for (String field : values.keySet()) {
            String submitted = request.getParameter(field);
            values.put(field, submitted != null ? submitted : "");
        }
        return values;
    }

    private Map<String, String> formFrom(Product product) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", nullToEmpty(product.getName()));
        values.put("description", nullToEmpty(product.getDescription()));
        values.put("price", product.getPrice() != null ? product.getPrice().toPlainString() : "");
        values.put("category", nullToEmpty(product.getCategory()));
        values.put("imageUrl", nullToEmpty(product.getImageUrl()));
        values.put("stock", String.valueOf(product.getStock()));
        return values;
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
