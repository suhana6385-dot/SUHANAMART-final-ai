package com.suhanamart.service;

import com.suhanamart.dao.AdminDAO;
import com.suhanamart.dao.OrderDAO;
import com.suhanamart.dao.ProductDAO;
import com.suhanamart.model.AccountStatus;
import com.suhanamart.model.OrderStatus;
import com.suhanamart.model.Product;
import com.suhanamart.model.Role;
import com.suhanamart.model.SellerOrder;
import com.suhanamart.model.User;
import com.suhanamart.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Admin module business rules, between the Admin controllers and
 * AdminDAO (plus the existing OrderDAO for order monitoring - see the
 * note on OrderDAO.findAllOrders). This is the only place admin rules
 * live: an admin account can never be disabled (not even its own), and
 * a product can only be removed if it has no order history.
 */
public class AdminService {

    /** Totals shown on the admin dashboard. */
    public static final class DashboardStats {
        private final Map<Role, Integer> usersByRole;
        private final int productCount;
        private final int orderCount;
        private final Map<OrderStatus, Integer> ordersByStatus;

        public DashboardStats(Map<Role, Integer> usersByRole, int productCount, int orderCount,
                              Map<OrderStatus, Integer> ordersByStatus) {
            this.usersByRole = usersByRole;
            this.productCount = productCount;
            this.orderCount = orderCount;
            this.ordersByStatus = ordersByStatus;
        }

        public int getBuyerCount() { return usersByRole.getOrDefault(Role.BUYER, 0); }
        public int getSellerCount() { return usersByRole.getOrDefault(Role.SELLER, 0); }
        public int getAdminCount() { return usersByRole.getOrDefault(Role.ADMIN, 0); }
        public int getTotalUsers() { return getBuyerCount() + getSellerCount() + getAdminCount(); }
        public int getProductCount() { return productCount; }
        public int getOrderCount() { return orderCount; }
        public Map<OrderStatus, Integer> getOrdersByStatus() { return ordersByStatus; }
    }

    private final AdminDAO adminDAO = new AdminDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public DashboardStats getDashboardStats() throws SQLException {
        return new DashboardStats(
                adminDAO.countUsersByRole(),
                adminDAO.countProducts(),
                adminDAO.countOrders(),
                orderDAO.countAllOrdersByStatus());
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    /** @param roleFilterText "BUYER" / "SELLER" / "ADMIN", or blank/unrecognised for every user */
    public List<User> listUsers(String roleFilterText) throws SQLException {
        Role roleFilter = null;
        if (!ValidationUtil.isBlank(roleFilterText)) {
            for (Role role : Role.values()) {
                if (role.name().equalsIgnoreCase(roleFilterText.trim())) {
                    roleFilter = role;
                }
            }
        }
        return adminDAO.listUsers(roleFilter);
    }

    /**
     * Enables or disables a user's account.
     *
     * @throws ValidationException the target is an admin account, or is the acting admin's own
     *                             account - admin accounts can never be disabled through this,
     *                             even by another admin, so the system can never be left with no
     *                             way to log in and re-enable everyone else
     */
    public void setAccountStatus(int targetUserId, boolean enable, int actingAdminId)
            throws SQLException, ValidationException, NotFoundException {

        if (targetUserId == actingAdminId) {
            throw new ValidationException("You can't disable your own account.");
        }

        AccountStatus status = enable ? AccountStatus.ACTIVE : AccountStatus.DISABLED;
        boolean changed = adminDAO.setAccountStatus(targetUserId, status, actingAdminId);

        if (!changed) {
            // Either the user doesn't exist, or the database-level guard refused it
            // (it's an admin account) - both are reported the same way.
            throw new ValidationException("That account can't be changed. It may be an admin account, "
                    + "or it may no longer exist.");
        }
    }

    // ---------------------------------------------------------------
    // Products
    // ---------------------------------------------------------------

    public List<Product> listProducts() throws SQLException {
        return adminDAO.listProducts();
    }

    /**
     * Removes a listing.
     *
     * @throws ValidationException the product has order history and can't be safely removed
     * @throws NotFoundException   no such product
     */
    public void removeListing(int productId) throws SQLException, ValidationException, NotFoundException {
        if (productDAO.findById(productId) == null) {
            throw new NotFoundException("We couldn't find that product.");
        }
        boolean removed = adminDAO.removeListing(productId);
        if (!removed) {
            throw new ValidationException("This product can't be removed because it has order history. "
                    + "Removing it would corrupt past orders and reviews.");
        }
    }

    // ---------------------------------------------------------------
    // Orders (read-only monitoring - reuses the existing OrderDAO)
    // ---------------------------------------------------------------

    public List<SellerOrder> listOrders(OrderStatus statusFilter) throws SQLException {
        return orderDAO.findAllOrders(statusFilter);
    }

    public SellerOrder getOrder(int orderId) throws SQLException, NotFoundException {
        SellerOrder order = orderDAO.findAnyOrder(orderId);
        if (order == null) {
            throw new NotFoundException("We couldn't find that order.");
        }
        return order;
    }
}
