/**
 * Shared DELETE statements used by both delete-test-data.js and the
 * validate script. Keep the SQL out of any one file so it can be
 * unit-tested without re-declaring the queries.
 *
 * Pattern rules (keep these in sync with backend fixtures to avoid catching
 * real data):
 *   • products: name LIKE 'Test Product %' / 'API Test %' / 'Updated Product %' / 'First Product %'
 *               OR sku LIKE 'TEST-%' / 'SKU-TEST-%' / 'API-%' / 'VAR-%' / 'DUP-%' / 'NONAME-%' / 'UNAUTH-%'
 *   • product_variants: sku LIKE 'TEST-V-%' / 'SKU-TEST-%' / 'TEST-%' / 'APIV-%'
 *   • customers: full_name LIKE 'Test Customer %' / 'Updated Customer %'
 *                OR email LIKE 'test%@example.com' / 'noauth%@example.com' / ...
 *   • users: email LIKE 'testuser_%@test.com' / 'newuser_%@test.com' / 'dup_%@test.com' / ...
 *            (EXCEPT admin@osms.vn + manager@osms.vn)
 *   • channels: display_name LIKE 'TestMC_%' / 'BadCommission %' / 'Updated Manual Channel %' / 'ToDelete_%' / 'DupCh_%'
 *   • stocktake_sessions: session_code LIKE 'KK-%'
 *   • stock_transfers:    transfer_code LIKE 'CK-%'
 *   • suppliers: name LIKE 'TestSup%' / 'ToUpdate%' / 'StatusTest%' / 'DuplicateTest%'
 *                OR email LIKE 'supplier%@example.com'
 *   • categories: name LIKE 'Test Category %' / 'API Test %' OR slug LIKE 'test-category-%'
 *   • orders: note LIKE 'Test order note %' / 'Test Item%'
 *   • order_items: sku LIKE 'TEST-ORD-%'
 *   • inventory_transactions: note LIKE 'Test transaction %'
 *
 * The variant_id DELETE references below also use the same TEST-%
 * pattern (because some test specs create variants with sku='TEST-…'
 * directly, not 'TEST-V-…').
 */

const STATEMENTS = [
  // ── Phase A: disable immutability triggers so we can delete ledger rows.
  `ALTER TABLE inventory_transactions DISABLE TRIGGER trg_inventory_transactions_immutable`,
  `ALTER TABLE inventory_receipts    DISABLE TRIGGER trg_receipt_immutable`,
  `ALTER TABLE inventory_issues      DISABLE TRIGGER trg_issue_immutable`,
  `ALTER TABLE audit_logs            DISABLE TRIGGER trg_audit_logs_immutable`,
  `ALTER TABLE orders                DISABLE TRIGGER trg_orders_before_update`,

  // 1. Order items + orders first (FK to orders / products / customers)
  `DELETE FROM order_items WHERE sku LIKE 'TEST-ORD-%'`,
  `DELETE FROM orders WHERE note LIKE 'Test order note %' OR note LIKE 'Test Item%'`,

  // 2. Inventory transactions tied to test notes
  `DELETE FROM inventory_transactions WHERE note LIKE 'Test transaction %'
                                          OR variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,

  // 3. Stocktake + transfer (drop FK rows referencing test variants too)
  `DELETE FROM stocktake_items WHERE session_id IN (SELECT id FROM stocktake_sessions WHERE session_code LIKE 'KK-%')
                                  OR variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,
  `DELETE FROM stocktake_sessions WHERE session_code LIKE 'KK-%'`,
  `DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE transfer_code LIKE 'CK-%')
                                      OR variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,
  `DELETE FROM stock_transfers WHERE transfer_code LIKE 'CK-%'`,

  // 4. Receipts/issues — keep narrow markers; also drop lines for test variants
  `DELETE FROM inventory_receipt_items WHERE receipt_id IN (SELECT id FROM inventory_receipts WHERE notes = 'Updated by test')
                                         OR variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,
  `DELETE FROM inventory_receipts WHERE notes = 'Updated by test'`,
  `DELETE FROM inventory_issue_items WHERE issue_id IN (SELECT id FROM inventory_issues WHERE notes = 'Updated note')
                                       OR variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,
  `DELETE FROM inventory_issues WHERE notes = 'Updated note'`,
  `DELETE FROM inventory_items WHERE variant_id IN (SELECT id FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%')`,

  // 5. Channels (channel_products + channel_product_variants FK must come first)
  `DELETE FROM channel_product_variants WHERE channel_product_id IN (SELECT id FROM channel_products WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%'))`,
  `DELETE FROM channel_products WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
  `DELETE FROM channel_credentials WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
  `DELETE FROM channel_connection_logs WHERE channel_id IN (SELECT id FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%')`,
  `DELETE FROM channels WHERE display_name LIKE 'TestMC_%' OR display_name LIKE 'BadCommission %' OR display_name LIKE 'Updated Manual Channel %' OR display_name LIKE 'ToDelete_%' OR display_name LIKE 'DupCh_%'`,

  // 6. Product variants + products
  `DELETE FROM product_variants WHERE sku LIKE 'TEST-V-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'TEST-%' OR sku LIKE 'APIV-%'`,
  `DELETE FROM product_images WHERE product_id IN (SELECT id FROM products WHERE name LIKE 'Test Product %' OR name LIKE 'API Test %' OR name LIKE 'Updated Product %' OR name LIKE 'First Product %' OR sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%' OR sku LIKE 'NONAME-%' OR sku LIKE 'UNAUTH-%')`,
  `DELETE FROM product_logs WHERE product_id IN (SELECT id FROM products WHERE name LIKE 'Test Product %' OR name LIKE 'API Test %' OR name LIKE 'Updated Product %' OR name LIKE 'First Product %' OR sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%' OR sku LIKE 'NONAME-%' OR sku LIKE 'UNAUTH-%')`,
  `DELETE FROM channel_products WHERE product_id IN (SELECT id FROM products WHERE name LIKE 'Test Product %' OR name LIKE 'API Test %' OR name LIKE 'Updated Product %' OR name LIKE 'First Product %' OR sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%' OR sku LIKE 'NONAME-%' OR sku LIKE 'UNAUTH-%')`,
  `DELETE FROM products WHERE name LIKE 'Test Product %' OR name LIKE 'API Test %' OR name LIKE 'Updated Product %' OR name LIKE 'First Product %' OR sku LIKE 'TEST-%' OR sku LIKE 'SKU-TEST-%' OR sku LIKE 'API-%' OR sku LIKE 'VAR-%' OR sku LIKE 'DUP-%' OR sku LIKE 'NONAME-%' OR sku LIKE 'UNAUTH-%'`,

  // 7. Customers (order_items.order_id has FK, but we already deleted orders first)
  `DELETE FROM customer_platform_ids WHERE customer_id IN (SELECT id FROM customers WHERE full_name LIKE 'Test Customer %' OR full_name LIKE 'Updated Customer %' OR email LIKE 'test%@example.com' OR email LIKE 'noauth%@example.com' OR email LIKE 'noname%@example.com' OR email LIKE 'updated%@example.com')`,
  `DELETE FROM customers WHERE full_name LIKE 'Test Customer %' OR full_name LIKE 'Updated Customer %' OR email LIKE 'test%@example.com' OR email LIKE 'noauth%@example.com' OR email LIKE 'noname%@example.com' OR email LIKE 'updated%@example.com'`,

  // 8. Categories (after products since products has FK to categories)
  // First: reassign children of test categories to NULL so we don't block the FK
  `UPDATE categories SET parent_id = NULL WHERE parent_id IN (SELECT id FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%')`,
  `DELETE FROM categories WHERE name LIKE 'Test Category %' OR name LIKE 'API Test %' OR slug LIKE 'test-category-%'`,

  // 9. Suppliers
  `DELETE FROM suppliers WHERE name LIKE 'TestSup%' OR name LIKE 'ToUpdate%' OR name LIKE 'StatusTest%' OR name LIKE 'DuplicateTest%' OR email LIKE 'supplier%@example.com'`,

  // 10. User invite tokens (must come before user delete)
  `DELETE FROM user_invite_tokens WHERE email LIKE 'invitee+%@osms-test.vn'`,

  // 11. Notifications + audit_logs tied to test users (before deleting users)
  `DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%@test.com')`,
  `DELETE FROM audit_logs WHERE actor_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%@test.com')`,

  // 12. Refresh tokens (FK to users)
  `DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%@test.com')`,

  // 13. User roles (FK to users + roles)
  `DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'testuser_%@test.com')`,

  // 14. Users (last — protected: keep admin@osms.vn + manager@osms.vn)
  `DELETE FROM users WHERE (email LIKE 'testuser_%@test.com' OR email LIKE 'newuser_%@test.com' OR email LIKE 'dup_%@test.com' OR email LIKE 'delete_%@test.com' OR email LIKE 'update_%@test.com' OR email LIKE 'getbyid_%@test.com' OR email LIKE 'auth_test_%@test.com' OR email LIKE 'e2e_%@test.com' OR email LIKE 'invitee+%@osms-test.vn') AND email NOT IN ('admin@osms.vn', 'manager@osms.vn')`,

  // ── Phase Z: re-enable the immutability triggers.
  `ALTER TABLE inventory_transactions ENABLE TRIGGER trg_inventory_transactions_immutable`,
  `ALTER TABLE inventory_receipts    ENABLE TRIGGER trg_receipt_immutable`,
  `ALTER TABLE inventory_issues      ENABLE TRIGGER trg_issue_immutable`,
  `ALTER TABLE audit_logs            ENABLE TRIGGER trg_audit_logs_immutable`,
  `ALTER TABLE orders                ENABLE TRIGGER trg_orders_before_update`,
];

module.exports = { STATEMENTS };