package com.suhanamart.service;

import com.suhanamart.dao.OrderDAO;
import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.SellerOrder;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Seller-side order handling: which orders a seller can see, and the
 * rules for changing their status. (The buyer's checkout/order-history
 * flow still talks to OrderDAO directly and is unchanged.)
 */
public class OrderService {

    private final OrderDAO orderDAO = new OrderDAO();

    /**
     * Orders containing this seller's products. statusFilter comes from
     * a query parameter: an unrecognised value is ignored (all orders
     * are shown) rather than causing an error.
     */
    public List<SellerOrder> listSellerOrders(int sellerId, String statusFilter) throws SQLException {
        return orderDAO.findSellerOrders(sellerId, OrderStatus.parse(statusFilter));
    }

    public List<SellerOrder> getRecentSellerOrders(int sellerId, int limit) throws SQLException {
        List<SellerOrder> all = orderDAO.findSellerOrders(sellerId, null);
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    /** The order as this seller sees it; NotFoundException if none of its lines are theirs. */
    public SellerOrder getSellerOrder(int sellerId, int orderId) throws SQLException, NotFoundException {
        SellerOrder order = orderDAO.findSellerOrder(orderId, sellerId);
        if (order == null) {
            throw new NotFoundException("Order not found.");
        }
        return order;
    }

    public Map<OrderStatus, Integer> getSellerOrderCounts(int sellerId) throws SQLException {
        return orderDAO.countSellerOrdersByStatus(sellerId);
    }

    /**
     * Advances this seller's part of an order to the next status.
     *
     * Rules enforced here, on the server, regardless of what the
     * browser sent:
     *  - the requested status must be one of the four real statuses;
     *  - the order must contain at least one of THIS seller's products;
     *  - the only allowed move is exactly one step forward
     *    (PENDING -> CONFIRMED -> SHIPPED -> DELIVERED): no skipping,
     *    no going back, nothing after DELIVERED.
     *
     * @return the status the order was moved to
     */
    public OrderStatus advanceStatus(int sellerId, int orderId, String requestedStatus)
            throws SQLException, ValidationException, NotFoundException {

        OrderStatus target = OrderStatus.parse(requestedStatus);
        if (target == null) {
            throw new ValidationException("Please choose a valid order status.");
        }

        SellerOrder order = getSellerOrder(sellerId, orderId);
        OrderStatus current = order.getStatus();

        if (current.isTerminal()) {
            throw new ValidationException("Order #" + orderId + " has already been delivered - "
                    + "its status can't be changed any more.");
        }
        if (current.next() != target) {
            throw new ValidationException("Order #" + orderId + " is " + current.getLabel()
                    + ", so it can only be moved to " + current.next().getLabel() + " next.");
        }

        int changed = orderDAO.updateSellerItemsStatus(orderId, sellerId, current, target);
        if (changed == 0) {
            // Compare-and-set failed: the status changed after this page was loaded.
            throw new ValidationException("Order #" + orderId + " was updated in the meantime. "
                    + "Please review its current status and try again.");
        }
        return target;
    }
}
