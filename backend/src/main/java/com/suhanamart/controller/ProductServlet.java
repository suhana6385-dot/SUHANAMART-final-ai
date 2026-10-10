package com.suhanamart.controller;

import com.suhanamart.dao.ProductDAO;
import com.suhanamart.model.Product;
import com.suhanamart.model.Review;
import com.suhanamart.service.ReviewService;
import com.suhanamart.util.Flash;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Product browsing (list + search + category filter, and single
 * product detail). This is the one read path in the app that goes
 * through a dedicated controller rather than a JSP calling the DAO
 * directly - added during the project's layer-separation cleanup so
 * products.jsp/product-details.jsp become pure views that only read
 * request attributes this servlet sets.
 *
 * Mapped to two paths on one servlet: "/products" (list) and
 * "/product-details" (single item), distinguished by
 * request.getServletPath().
 *
 * The details page also shows the product's reviews and rating summary
 * (Review Module); those come from ReviewService, and a failure to load
 * them never hides the product itself.
 */
@WebServlet(urlPatterns = {"/products", "/product-details"})
public class ProductServlet extends HttpServlet {

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(ProductServlet.class);

    private final ProductDAO productDAO = new ProductDAO();
    private final ReviewService reviewService = new ReviewService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if ("/product-details".equals(request.getServletPath())) {
            showProductDetails(request, response);
        } else {
            showProductList(request, response);
        }
    }

    private void showProductList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String keyword = request.getParameter("q");
        String category = request.getParameter("category");

        List<Product> products = Collections.emptyList();
        List<String> categories = Collections.emptyList();
        boolean loadError = false;

        try {
            products = productDAO.searchProducts(keyword, category);
            categories = productDAO.listCategories();
        } catch (SQLException e) {
            logger.error("Failed to load products", e);
            loadError = true;
        }

        request.setAttribute("keyword", keyword);
        request.setAttribute("category", category);
        request.setAttribute("products", products);
        request.setAttribute("categories", categories);
        request.setAttribute("loadError", loadError);

        request.getRequestDispatcher("/public/products.jsp").forward(request, response);
    }

    private void showProductDetails(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String idParam = request.getParameter("id");
        Product product = null;
        boolean loadError = false;
        boolean invalidId = false;
        int productId = -1;

        try {
            productId = Integer.parseInt(idParam);
        } catch (Exception e) {
            invalidId = true;
        }

        if (!invalidId) {
            try {
                product = productDAO.findById(productId);
            } catch (SQLException e) {
                logger.error("Failed to load product {}", productId, e);
                loadError = true;
            }
        }

        Review.Summary reviewSummary = Review.Summary.empty();
        List<Review> reviews = Collections.emptyList();
        boolean reviewsLoadError = false;

        if (product != null) {
            try {
                reviewSummary = reviewService.getRatingSummary(productId);
                reviews = reviewService.getRecentReviews(productId);
            } catch (SQLException e) {
                logger.error("Failed to load reviews for product {}", productId, e);
                reviewsLoadError = true;
            }
        }

        // One-time message after a review is submitted (Post/Redirect/Get).
        Flash.consume(request);

        request.setAttribute("product", product);
        request.setAttribute("loadError", loadError);
        request.setAttribute("invalidId", invalidId);
        request.setAttribute("reviewSummary", reviewSummary);
        request.setAttribute("reviews", reviews);
        request.setAttribute("reviewsLoadError", reviewsLoadError);

        request.getRequestDispatcher("/public/product-details.jsp").forward(request, response);
    }
}
