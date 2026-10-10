package com.suhanamart.filter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

import java.io.IOException;

/**
 * Makes the container read submitted form data as UTF-8. Without this,
 * Tomcat 9 decodes POST bodies as ISO-8859-1, so a product name with
 * non-ASCII characters (Tamil, accented letters, ...) would be stored
 * garbled.
 *
 * Applied to every request. It only sets the encoding when the browser did
 * not declare one, and it must run before the first getParameter() call,
 * which is why it covers the whole application (registration names and
 * review comments in Tamil or accented letters need it just like seller
 * product names do).
 */
@WebFilter(urlPatterns = {"/*"})
public class EncodingFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // Nothing to initialize.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding("UTF-8");
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Nothing to clean up.
    }
}
