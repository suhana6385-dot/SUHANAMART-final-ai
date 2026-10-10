package com.suhanamart.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Guards every page/servlet that requires a logged-in user. If there
 * is no session, or the session does not contain the "userId"
 * attribute that LoginServlet sets on a successful login, the request
 * is redirected to /public/login.jsp instead of being allowed through.
 *
 * Both the controller URLs (e.g. "/products") AND the underlying JSP
 * paths under /public/ are listed here. A @WebFilter's default
 * dispatcher type is REQUEST only, so a forward from a controller to
 * its JSP is not re-filtered - but a user typing a /public/*.jsp URL
 * directly into the browser IS a fresh REQUEST, so without listing
 * those paths too, direct access would bypass this filter entirely.
 */
@WebFilter(urlPatterns = {
        "/public/dashboard.jsp",
        "/public/cart.jsp",
        "/public/checkout.jsp",
        "/public/order-details.jsp",
        "/public/orders.jsp",
        "/public/profile.jsp",
        "/public/products.jsp",
        "/public/product-details.jsp",
        "/public/reviews.jsp",
        "/products",
        "/product-details",
        "/reviews",
        "/CartServlet",
        "/CheckoutServlet",
        "/chat"
})
public class AuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // Nothing to initialize.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        HttpSession session = httpRequest.getSession(false);
        boolean loggedIn = session != null && session.getAttribute("userId") != null;

        if (loggedIn) {
            chain.doFilter(request, response);
        } else {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/public/login.jsp");
        }
    }

    @Override
    public void destroy() {
        // Nothing to clean up.
    }
}
