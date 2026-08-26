/**
 * Thiết lập toàn cục - chạy một lần trước tất cả tests trong một lần chạy Playwright.
 *
 * Kiểm tra backend và frontend có thể truy cập được. Dừng ngay với
 * thông báo rõ ràng khi người dùng quên khởi động dev servers.
 *
 * Sử dụng module `http` có sẵn của Node để không phụ thuộc
 * vào Playwright APIs (globalSetup chạy bên ngoài test context).
 */

const http = require('node:http');

function checkUrl(url, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const req = http.get(url, { timeout: timeoutMs }, (res) => {
      res.resume(); // drain to free memory
      resolve({ url, status: res.statusCode });
    });
    req.on('timeout', () => {
      req.destroy(new Error(`Timeout after ${timeoutMs}ms: ${url}`));
    });
    req.on('error', (err) => reject(err));
  });
}

module.exports = async () => {
  const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5174';
  const API_BASE = process.env.API_BASE || 'http://localhost:8080/api';
  // Backend không có endpoint /health riêng; sử dụng
  // endpoint công khai /address/countries như proxy kiểm tra sức khỏe.
  const BACKEND_HEALTH = `${API_BASE}/address/countries`;

  console.log('[setup] verifying frontend:', FRONTEND_URL);
  try {
    const fe = await checkUrl(FRONTEND_URL);
    if (fe.status >= 500) {
      throw new Error(`Frontend unhealthy (status ${fe.status}): ${FRONTEND_URL}`);
    }
    console.log(`[setup] frontend OK (status ${fe.status})`);
  } catch (err) {
    throw new Error(
      `Không thể kết nối frontend tại ${FRONTEND_URL}. ` +
      `Khởi động Vite dev server (npm run dev) trước khi chạy tests. ` +
      `Lỗi gốc: ${err.message}`,
    );
  }

  console.log('[setup] verifying backend via', BACKEND_HEALTH);
  try {
    const be = await checkUrl(BACKEND_HEALTH);
    if (be.status >= 500) {
      throw new Error(`Backend unhealthy (status ${be.status}): ${BACKEND_HEALTH}`);
    }
    console.log(`[setup] backend OK (status ${be.status})`);
  } catch (err) {
    throw new Error(
      `Không thể kết nối backend tại ${BACKEND_HEALTH}. ` +
      `Khởi động Spring Boot app trước khi chạy tests. ` +
      `Lỗi gốc: ${err.message}`,
    );
  }

  console.log('[setup] all systems ready.');
};