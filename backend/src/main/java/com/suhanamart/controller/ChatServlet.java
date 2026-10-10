package com.suhanamart.controller;

import com.suhanamart.service.ChatService;
import com.suhanamart.service.ValidationException;
import com.suhanamart.util.SecurityUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * The AI chatbot's one endpoint: POST /chat, called by the floating
 * widget in main.js. Requires a logged-in session (AuthFilter already
 * enforces this; checked again here) and a valid CSRF token, same as
 * every other state-changing POST in the app.
 *
 * This servlet only does HTTP plumbing - reads "message", checks the
 * rate limit, calls ChatService.answer(), and writes back a small,
 * hand-built JSON object. All of the actual logic (what counts as a
 * valid message, what context to gather, calling the AI provider,
 * falling back if it's unavailable) lives in ChatService.
 *
 * The response is always one of: 200 with a reply, 400 for a message
 * ChatService rejected, 403 for a missing/bad CSRF token, 429 for the
 * rate limit, or 500 with a generic message - never a stack trace,
 * and the body is always valid JSON with an "error" boolean field, so
 * main.js never needs to guess what came back.
 */
@WebServlet("/chat")
public class ChatServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(ChatServlet.class);

    private final transient ChatService chatService = new ChatService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        Integer userId = SecurityUtil.currentUserId(request);
        if (userId == null) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, true,
                    "Please log in to use the chat assistant.");
            return;
        }

        if (!SecurityUtil.hasValidCsrfToken(request)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, true,
                    "Your session token is missing or has expired. Please reload the page and try again.");
            return;
        }

        if (!chatService.allowRequest(userId)) {
            writeJson(response, 429, true,
                    "You're sending messages too quickly. Please wait a moment and try again.");
            return;
        }

        String message = request.getParameter("message");

        try {
            String reply = chatService.answer(userId, message);
            writeJson(response, HttpServletResponse.SC_OK, false, reply);

        } catch (ValidationException e) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST, true, e.getMessage());

        } catch (SQLException e) {
            logger.error("Chat request failed for user {}", userId, e);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, true,
                    "Something went wrong. Please try again later.");
        }
    }

    /** Every field here is server-controlled text, so hand-building the JSON needs no library. */
    private void writeJson(HttpServletResponse response, int status, boolean error, String reply)
            throws IOException {

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder("{\"error\":").append(error).append(",\"reply\":\"");
        for (int i = 0; i < reply.length(); i++) {
            char c = reply.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
            }
        }
        json.append("\"}");

        try (PrintWriter writer = response.getWriter()) {
            writer.write(json.toString());
        }
    }
}
