package com.suhanamart.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The request was understood but is not acceptable (bad input, or a
 * business rule such as "an order can only move to its next status").
 * Every message is written to be shown directly to the seller - none
 * contain SQL or internal details.
 */
public class ValidationException extends Exception {

    private final List<String> messages;

    public ValidationException(String message) {
        super(message);
        this.messages = Collections.singletonList(message);
    }

    public ValidationException(List<String> messages) {
        super(messages.isEmpty() ? "Invalid input." : messages.get(0));
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
    }

    public List<String> getMessages() {
        return messages;
    }
}
