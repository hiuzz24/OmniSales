package fu.osms.catalog.enums;

/**
 * Loại hành động ghi nhận trong product_logs.
 * Tương ứng với PostgreSQL enum: product_log_action
 */
public enum ProductLogAction {
    CREATE,
    UPDATE,
    DELETE,
    SYNC,
    MAPPING
}
