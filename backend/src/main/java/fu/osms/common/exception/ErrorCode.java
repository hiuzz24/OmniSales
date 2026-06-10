package fu.osms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error, please try again later"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid input data"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    CONFLICT(HttpStatus.CONFLICT, "Data conflict"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account has been locked"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Account has not been activated"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid token"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Token has been revoked"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already in use"),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Password reset token is invalid or has expired"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_ALREADY_HAS_ROLE(HttpStatus.CONFLICT, "User already has this role"),

    SHOP_NOT_FOUND(HttpStatus.NOT_FOUND, "Shop not found"),
    SHOP_SLUG_CONFLICT(HttpStatus.CONFLICT, "Shop slug already exists"),

    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "Sales channel not found"),
    CHANNEL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Sales channel already exists"),
    CHANNEL_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "Sales channel is not connected"),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product variant not found"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),
    PRODUCT_SKU_CONFLICT(HttpStatus.CONFLICT, "Product SKU already exists in this shop"),
    VARIANT_SKU_CONFLICT(HttpStatus.CONFLICT, "Variant SKU already exists in this shop"),
    VARIANT_BARCODE_CONFLICT(HttpStatus.CONFLICT, "Variant barcode already exists in this shop"),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_STATUS_INVALID_TRANSITION(HttpStatus.BAD_REQUEST, "Invalid order status transition"),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Order has already been cancelled"),

    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Warehouse not found"),
    INVENTORY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Inventory item not found"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock to complete this operation"),
    NEGATIVE_STOCK_NOT_ALLOWED(HttpStatus.CONFLICT, "Negative stock is not allowed for this shop"),
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Supplier not found"),
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Inventory receipt not found"),
    RECEIPT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Inventory receipt has already been confirmed and cannot be changed"),
    RECEIPT_CODE_CONFLICT(HttpStatus.CONFLICT, "Inventory receipt code already exists"),
    ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "Inventory issue not found"),
    ISSUE_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Inventory issue has already been confirmed and cannot be changed"),
    BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Inventory batch not found"),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Stock transfer not found"),
    STOCKTAKE_NOT_FOUND(HttpStatus.NOT_FOUND, "Stocktake session not found"),
    SAME_WAREHOUSE_TRANSFER(HttpStatus.BAD_REQUEST, "Source and destination warehouses must be different"),

    SYNC_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Sync log not found"),
    SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "A sync task is already running for this channel"),

    REPORT_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "Report configuration not found"),

    //register-error-codes
    INVALID_DATA(HttpStatus.BAD_REQUEST, "Invalid data"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Phone number already exists in this shop");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
