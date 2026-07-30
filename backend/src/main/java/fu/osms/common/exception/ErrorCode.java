package fu.osms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    ORDER_REFUND_SYSTEM_MANAGED(HttpStatus.BAD_REQUEST, "Refunded payment status is managed by the return workflow"),
    ORDER_RETURN_NOT_FOUND(HttpStatus.NOT_FOUND, "Order return was not found"),
    ORDER_RETURN_INVALID_STATE(HttpStatus.CONFLICT, "Order return is not in a valid state for this action"),
    ORDER_RETURN_ACTION_IN_PROGRESS(HttpStatus.CONFLICT, "Another return action is already in progress"),
    ORDER_RETURN_ACTION_NOT_RETRYABLE(HttpStatus.CONFLICT, "The last return action cannot be retried"),
    ORDER_RETURN_ITEM_IDENTITY_MISSING(HttpStatus.BAD_REQUEST, "Return item identity is missing"),
    ORDER_RETURN_QUANTITY_INVALID(HttpStatus.BAD_REQUEST, "Return quantities are invalid"),
    ORDER_RETURN_STOCK_PENDING(HttpStatus.CONFLICT, "Return stock posting is still pending"),

    ORDER_STOCK_DELIVERY_REQUIRED(HttpStatus.CONFLICT, "Đơn hàng chưa có phiếu xuất kho đang hoạt động"),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi máy chủ nội bộ, vui lòng thử lại sau"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Chưa xác thực"),
    CONFLICT(HttpStatus.CONFLICT, "Xung đột dữ liệu"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không hợp lệ"),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Tài khoản đã bị khóa"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Tài khoản chưa được kích hoạt"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Token đã bị thu hồi"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã được sử dụng"),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    USER_ALREADY_HAS_ROLE(HttpStatus.CONFLICT, "Người dùng đã có vai trò này"),

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"),

    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kênh bán hàng"),
    CHANNEL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Kênh bán hàng đã tồn tại"),
    CHANNEL_ALREADY_DISCONNECTED(HttpStatus.CONFLICT, "Kênh bán hàng đã bị ngắt kết nối"),
    CHANNEL_IDENTITY_CONFLICT(HttpStatus.CONFLICT, "Có nhiều kênh trùng danh tính seller/shop"),
    CHANNEL_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "Kênh bán hàng chưa được kết nối"),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"),
    VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"),
    PRODUCT_SKU_CONFLICT(HttpStatus.CONFLICT, "SKU sản phẩm đã tồn tại"),
    PRODUCT_NAME_CONFLICT(HttpStatus.CONFLICT, "Tên sản phẩm đã tồn tại"),
    VARIANT_SKU_CONFLICT(HttpStatus.CONFLICT, "SKU biến thể đã tồn tại"),
    VARIANT_BARCODE_CONFLICT(HttpStatus.CONFLICT, "Barcode biến thể đã tồn tại"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Dữ liệu đã bị thay đổi bởi người khác. Vui lòng tải lại trang."),
    PRODUCT_HAS_ORDERS(HttpStatus.BAD_REQUEST, "Không thể thay đổi SKU vì sản phẩm đã phát sinh đơn hàng"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),

    EXCEL_IMPORT_INVALID_FILE(HttpStatus.BAD_REQUEST, "File Excel không hợp lệ"),
    EXCEL_IMPORT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu Excel không hợp lệ"),
    EXCEL_IMPORT_EMPTY(HttpStatus.BAD_REQUEST, "File Excel không có dữ liệu"),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"),
    ORDER_STATUS_INVALID_TRANSITION(HttpStatus.BAD_REQUEST, "Không thể chuyển đổi trạng thái đơn hàng này"),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Đơn hàng đã bị hủy trước đó"),
    ORDER_CANCEL_ENDPOINT_REQUIRED(HttpStatus.BAD_REQUEST, "Hãy sử dụng chức năng Hủy đơn để hủy đơn hàng"),

    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kho hàng"),
    INVENTORY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm trong kho"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Không đủ tồn kho để thực hiện thao tác này"),
    NEGATIVE_STOCK_NOT_ALLOWED(HttpStatus.CONFLICT, "Không được phép tồn kho âm"),
    SUPPLIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy nhà cung cấp"),
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu nhập kho"),
    RECEIPT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Phiếu nhập kho đã được xác nhận và không thể thay đổi"),
    RECEIPT_CODE_CONFLICT(HttpStatus.CONFLICT, "Mã phiếu nhập kho đã tồn tại"),
    ISSUE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu xuất kho"),
    ISSUE_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Phiếu xuất kho đã được xác nhận và không thể thay đổi"),

    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu chuyển kho"),
    STOCKTAKE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên kiểm kê"),
    SAME_WAREHOUSE_TRANSFER(HttpStatus.BAD_REQUEST, "Kho xuất và kho nhập phải khác nhau"),

    SYNC_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy nhật ký đồng bộ"),
    SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "Đang có tiến trình đồng bộ chạy cho kênh này"),
    PASSWORD_EXPIRED(HttpStatus.FORBIDDEN, "Mật khẩu tạm thời đã hết hạn, vui lòng đổi mật khẩu mới."),
    REPORT_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cấu hình báo cáo");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
