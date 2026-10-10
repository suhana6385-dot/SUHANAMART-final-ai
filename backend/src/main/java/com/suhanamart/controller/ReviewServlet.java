package com.suhanamart.controller;

import com.suhanamart.model.Review;
import com.suhanamart.service.NotFoundException;
import com.suhanamart.service.ReviewService;
import com.suhanamart.service.ValidationException;
import com.suhanamart.util.Flash;
import com.suhanamart.util.SecurityUtil;
import com.suhanamart.util.ValidationUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * Review Module controller (F8):
 *
 * <pre>
 *   JSP -> ReviewServlet -> ReviewService -> ReviewDAO -> database
 *
 *   GET  /reviews?orderId=N&amp;productId=M   the review form (reviews.jsp) - only for
 *                                         a delivered, not-yet-reviewed purchase
 *   POST /reviews                         submit the review (orderId, productId,
 *                                         rating, comment, csrfToken)
 * </pre>
 *
 * This class only reads the request, calls ReviewService and picks the
 * next page. There is no SQL and no business rule here. The buyer's id
 * always comes from the SESSION, never from a request parameter, and the
 * service/DAO check that the order really is that buyer's, so another
 * buyer's order or product can't be reviewed by guessing ids.
 *
 * AuthFilter already requires a login for /reviews; this class re-checks
 * it anyway. The POST additionally requires the session's CSRF token.
 * Every message shown to the user is a safe, prepared sentence - stack
 * traces and SQL errors only go to the server log.
 */
@WebServlet("/reviews")
public class ReviewServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(ReviewServlet.class);

    private static final String FORM_JSP = "/public/reviews.jsp";
    private static final String ORDERS_URL = "/public/orders.jsp";
    private static final String GENERIC_ERROR = "Something went wrong. Please try again later.";

    private final transient ReviewService reviewService = new ReviewService();

    // ------------------------------------------------------------------ GET

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer userId = SecurityUtil.currentUserId(request);
        if (userId == null) {
            redirect(request, response, "/public/login.jsp");
            return;
        }

        Integer orderId = ValidationUtil.parsePositiveInt(request.getParameter("orderId"));
        Integer productId = ValidationUtil.parsePositiveInt(request.getParameter("productId"));

        if (orderId == null || productId == null) {
            Flash.error(request, "Choose a delivered item from one of your orders to review it.");
            redirect(request, response, ORDERS_URL);
            return;
        }

        showForm(request, response, userId, orderId, productId, "", "", Collections.<String>emptyList());
    }

    // ----------------------------------------------------------------- POST

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Integer userId = SecurityUtil.currentUserId(request);
        if (userId == null) {
            redirect(request, response, "/public/login.jsp");
            return;
        }

        if (!SecurityUtil.hasValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your session token is missing or has expired. Please go back, reload the page and try again.");
            return;
        }

        String orderIdText = request.getParameter("orderId");
        String productIdText = request.getParameter("productId");
        String rating = request.getParameter("rating");
        String comment = request.getParameter("comment");

        try {
            reviewService.submitReview(userId, orderIdText, productIdText, rating, comment);

            Flash.success(request, "Thank you! Your review has been posted.");
            // The service accepted productIdText, so it is a valid positive int here.
            redirect(request, response, "/product-details?id=" + ValidationUtil.parsePositiveInt(productIdText)
                    + "#reviews");

        } catch (ValidationException e) {
            Integer orderId = ValidationUtil.parsePositiveInt(orderIdText);
            Integer productId = ValidationUtil.parsePositiveInt(productIdText);

            if (orderId == null || productId == null) {
                Flash.error(request, e.getMessage());
                redirect(request, response, ORDERS_URL);
                return;
            }
            // Put the buyer back on the form with what they typed and the reasons.
            showForm(request, response, userId, orderId, productId,
                    rating == null ? "" : rating.trim(), comment == null ? "" : comment, e.getMessages());

        } catch (NotFoundException e) {
            Flash.error(request, e.getMessage());
            redirect(request, response, ORDERS_URL);

        } catch (SQLException e) {
            logger.error("Failed to save a review (user {}, order {}, product {})",
                    userId, orderIdText, productIdText, e);
            Flash.error(request, GENERIC_ERROR);
            redirect(request, response, ORDERS_URL);
        }
    }

    // -------------------------------------------------------------- helpers

    /**
     * Renders the review form for a purchase the buyer may review. If the
     * purchase can't be reviewed (not theirs, not delivered, already
     * reviewed, ...) the buyer is sent to the right page with the reason.
     */
    private void showForm(HttpServletRequest request, HttpServletResponse response,
                          int userId, int orderId, int productId,
                          String formRating, String formComment, List<String> errors)
            throws ServletException, IOException {

        try {
            Review.PurchasedItem item = reviewService.getReviewableItem(userId, orderId, productId);

            request.setAttribute("reviewItem", item);
            request.setAttribute("formRating", formRating);
            request.setAttribute("formComment", formComment);
            request.setAttribute("errors", errors);
            request.setAttribute("commentMinLength", ReviewService.MIN_COMMENT_LENGTH);
            request.setAttribute("commentMaxLength", ReviewService.MAX_COMMENT_LENGTH);
            request.getRequestDispatcher(FORM_JSP).forward(request, response);

        } catch (NotFoundException e) {
            Flash.error(request, e.getMessage());
            redirect(request, response, ORDERS_URL);

        } catch (ValidationException e) {
            // e.g. "already reviewed" / "not delivered yet": the order page explains the state.
            Flash.error(request, e.getMessage());
            redirect(request, response, "/public/order-details.jsp?orderId=" + orderId);

        } catch (SQLException e) {
            logger.error("Failed to load the review form (user {}, order {}, product {})",
                    userId, orderId, productId, e);
            Flash.error(request, GENERIC_ERROR);
            redirect(request, response, ORDERS_URL);
        }
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }
}
