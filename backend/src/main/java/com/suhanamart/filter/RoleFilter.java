package com.suhanamart.filter;

import com.suhanamart.util.SecurityUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Role-gated access control for the Seller module AND the Admin module
 * (the two areas of the site that require a specific role beyond just
 * being logged in).
 *
 * Protects the seller controllers/JSPs (/seller/*, /public/seller/*)
 * and the admin controllers/JSPs (/admin/*, /public/admin/*):
 *
 *   - not logged in                         -> redirect to the login page
 *   - logged in, but wrong role for the area -> 403 Forbidden
 *   - correct role requesting a JSP directly -> redirected to the
 *     matching controller URL. Those JSPs only make sense when a
 *     controller has loaded their data first, and controller-to-JSP
 *     forwards are not passed through this filter (default REQUEST
 *     dispatch only), so this blocks nothing legitimate.
 *
 * The role is read from the server-side session set by LoginServlet -
 * never from a request parameter, cookie, or anything the browser can
 * edit. Controllers repeat the check independently
 * (SecurityUtil.currentSellerId / currentAdminId), and every database
 * query for a seller's own data is scoped by seller_id, so this filter
 * is one layer of several, not the only one. Buyers and sellers can
 * never reach the admin controllers through this filter, and an admin
 * (who is not a seller) is likewise kept out of the seller area.
 */
@WebFilter(urlPatterns = {"/seller/*", "/public/seller/*", "/admin/*", "/public/admin/*"})
public class RoleFilter implements Filter {

    private static final String SELLER_JSP_PREFIX = "/public/seller/";
    private static final String ADMIN_JSP_PREFIX = "/public/admin/";

    @Override
    public void init(FilterConfig filterConfig) {
        // Nothing to initialize.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String contextPath = httpRequest.getContextPath();
        String path = httpRequest.getServletPath();
        boolean adminArea = path != null && (path.startsWith("/admin") || path.startsWith(ADMIN_JSP_PREFIX));

        if (!SecurityUtil.isLoggedIn(httpRequest)) {
            httpResponse.sendRedirect(contextPath + "/public/login.jsp");
            return;
        }

        if (adminArea) {
            if (!SecurityUtil.isAdmin(httpRequest)) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "This area is only available to admin accounts.");
                return;
            }
            if (path.startsWith(ADMIN_JSP_PREFIX)) {
                httpResponse.sendRedirect(contextPath + adminControllerUrlFor(path.substring(ADMIN_JSP_PREFIX.length())));
                return;
            }
        } else {
            if (!SecurityUtil.isSeller(httpRequest)) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "This area is only available to seller accounts.");
                return;
            }
            if (path.startsWith(SELLER_JSP_PREFIX)) {
                httpResponse.sendRedirect(contextPath + sellerControllerUrlFor(path.substring(SELLER_JSP_PREFIX.length())));
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** Maps a seller JSP file name to the controller URL that renders it. */
    private String sellerControllerUrlFor(String jspName) {
        switch (jspName) {
            case "products.jsp":
                return "/seller/products";
            case "add-product.jsp":
                return "/seller/products/new";
            case "orders.jsp":
            case "order-details.jsp":
                return "/seller/orders";
            case "dashboard.jsp":
            default:
                return "/seller/dashboard";
        }
    }

    /** Maps an admin JSP file name to the controller URL that renders it. */
    private String adminControllerUrlFor(String jspName) {
        switch (jspName) {
            case "users.jsp":
                return "/admin/users";
            case "products.jsp":
                return "/admin/products";
            case "orders.jsp":
                return "/admin/orders";
            case "dashboard.jsp":
            default:
                return "/admin/dashboard";
        }
    }

    @Override
    public void destroy() {
        // Nothing to clean up.
    }
}
