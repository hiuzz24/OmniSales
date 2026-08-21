-- Cho phép ghi audit khi Sales/Owner xác nhận thủ công đơn đã có hàng.
ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS chk_audit_action,
    DROP CONSTRAINT IF EXISTS audit_logs_action_check;

ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_action_check CHECK (action IN (
        'CREATE',
        'UPDATE',
        'DELETE',
        'LOGIN',
        'LOGOUT',
        'EXPORT',
        'CONNECT',
        'DISCONNECT',
        'STATUS_CHANGE',
        'ORDER_CANCEL',
        'PAYMENT_STATUS_CHANGE',
        'STOCK_CONFIRMED_MANUALLY'
    ));
