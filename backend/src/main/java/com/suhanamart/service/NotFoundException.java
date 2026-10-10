package com.suhanamart.service;

/**
 * The product/order does not exist OR does not belong to the current
 * seller. The two cases are deliberately indistinguishable, so a seller
 * can't probe for the existence of other sellers' products or orders by
 * guessing ids.
 */
public class NotFoundException extends Exception {

    public NotFoundException(String message) {
        super(message);
    }
}
