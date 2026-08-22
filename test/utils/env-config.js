/**
 * Trình quản lý cấu hình trung tâm cho Playwright tests.
 *
 * Đọc credentials và base URLs từ `.env` (hoặc process.env có
 * mức ưu tiên cao hơn để CI / pipelines có thể inject values
 * mà không cần gửi file `.env`).
 *
 * Cách sử dụng:
 *   const { TEST_EMAIL, TEST_PASSWORD, API_BASE } = require('./env-config');
 */

const path = require('path');

// Load .env từ thư mục `test/` bất kể Playwright
// được gọi từ đâu.
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

function readEnv(key, fallback) {
  const value = process.env[key];
  if (value === undefined || value === '') return fallback;
  return value;
}

// Passwords được seed bởi backend/src/main/resources/schema.sql với
// crypt('11111111', gen_salt('bf', 10)) cho mỗi demo account. Giữ
// các giá trị này đồng bộ với DB seed để warmup + fixtures có thể
// đăng nhập thành công. Override qua env vars khi chạy với DB khác.
const TEST_EMAIL = readEnv('TEST_EMAIL', 'manager@osms.vn');
const TEST_PASSWORD = readEnv('TEST_PASSWORD', '11111111');
const API_BASE = readEnv('API_BASE', 'http://localhost:8080/api');
const FRONTEND_URL = readEnv('FRONTEND_URL', 'http://localhost:5174');
const ADMIN_EMAIL = readEnv('ADMIN_EMAIL', 'admin@osms.vn');
const ADMIN_PASSWORD = readEnv('ADMIN_PASSWORD', '11111111');

module.exports = {
  TEST_EMAIL,
  TEST_PASSWORD,
  API_BASE,
  FRONTEND_URL,
  ADMIN_EMAIL,
  ADMIN_PASSWORD,
};