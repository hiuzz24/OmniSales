/**
 * Mock API setup dùng axios-mock-adapter
 *
 * Cách dùng:
 *   - Chỉ chạy khi VITE_USE_MOCK=true trong .env
 *   - Hoặc khi backend không phản hồi (fallback tự động)
 *
 * Các endpoint được mock:
 *   POST /api/auth/login
 *   POST /api/auth/logout
 *   POST /api/auth/refresh
 *   GET  /api/auth/me
 *   POST /api/auth/forgot-password
 *   POST /api/auth/reset-password
 *   POST /api/auth/change-password
 */

import MockAdapter from 'axios-mock-adapter';
import axiosClient from '../api/axiosClient';
import {
  MOCK_USERS,
  MOCK_CHANNELS,
  MOCK_ORDERS,
  MOCK_PRODUCTS,
  MOCK_WAREHOUSES,
  MOCK_STATS,
  MOCK_AUDIT_LOGS,
} from './data';

const MOCK_TOKEN = 'mock-jwt-token-for-development-only';
const mock = new MockAdapter(axiosClient, { delayResponse: 400 });

//  Helper 

const mockResponse = (data, status = 200) => [status, { data }];
const errorResponse = (message, status) => [{ response: { data: { message }, status } }];

const findUser = (email, password) =>
  MOCK_USERS.find((u) => u.email === email && u.password === password);

//  Auth Mock 

mock.onPost('/auth/login').reply((config) => {
  const { email, password } = JSON.parse(config.data);
  const user = findUser(email, password);

  if (!user) {
    return [401, { message: 'Incorrect email or password.' }];
  }

  return [
    200,
    {
      data: {
        user: {
          id: user.id,
          email: user.email,
          name: user.name,
          role: user.role,
          avatar: user.avatar,
          isActive: user.isActive,
        },
        accessToken: MOCK_TOKEN,
      },
    },
  ];
});

mock.onPost('/auth/logout').reply(200, { data: { success: true } });

mock.onPost('/auth/refresh').reply(200, {
  data: { accessToken: MOCK_TOKEN },
});

mock.onGet('/auth/me').reply((config) => {
  const token = config.headers.Authorization?.replace('Bearer ', '');
  if (!token || token !== MOCK_TOKEN) {
    return [401, { message: 'Unauthorized' }];
  }
  // Lấy user đầu tiên làm mặc định
  return [200, { data: MOCK_USERS[1] }];
});

mock.onPost('/auth/forgot-password').reply(200, {
  data: { message: 'Reset link sent to your email.' },
});

mock.onPost('/auth/reset-password').reply(200, {
  data: { message: 'Password reset successfully.' },
});

mock.onPost('/auth/change-password').reply(200, {
  data: { message: 'Password changed successfully.' },
});

//  Channels Mock 

mock.onGet('/channels').reply(200, { data: MOCK_CHANNELS });

mock.onGet(/\/channels\/\d+/).reply((config) => {
  const id = parseInt(config.url.split('/').pop());
  const channel = MOCK_CHANNELS.find((s) => s.id === id);
  return channel ? [200, { data: channel }] : [404, { message: 'Channel not found' }];
});

//  Orders Mock 

mock.onGet('/orders').reply(200, { data: MOCK_ORDERS });

mock.onGet(/\/orders\/\d+/).reply((config) => {
  const id = parseInt(config.url.split('/').pop());
  const order = MOCK_ORDERS.find((o) => o.id === id);
  return order ? [200, { data: order }] : [404, { message: 'Order not found' }];
});

//  Products Mock 

mock.onGet('/products').reply(200, { data: MOCK_PRODUCTS });

mock.onGet(/\/products\/\d+/).reply((config) => {
  const id = parseInt(config.url.split('/').pop());
  const product = MOCK_PRODUCTS.find((p) => p.id === id);
  return product ? [200, { data: product }] : [404, { message: 'Product not found' }];
});

//  Warehouse Mock 

mock.onGet('/warehouses').reply((config) => {
  const { keyword = '', status = 'ALL' } = config.params || {};
  let filtered = [...MOCK_WAREHOUSES];

  if (keyword) {
    const kw = keyword.toLowerCase();
    filtered = filtered.filter(
      (w) =>
        (w.name || '').toLowerCase().includes(kw) ||
        (w.code || '').toLowerCase().includes(kw) ||
        (w.address || '').toLowerCase().includes(kw)
    );
  }

  if (status && status !== 'ALL') {
    const isActive = status === 'ACTIVE';
    filtered = filtered.filter((w) => w.isActive === isActive);
  }

  return [200, { data: filtered }];
});

mock.onGet(/\/warehouses\/\d+/).reply((config) => {
  const id = parseInt(config.url.split('/').pop());
  const warehouse = MOCK_WAREHOUSES.find((w) => w.id === id);
  return warehouse ? [200, { data: warehouse }] : [404, { message: 'Warehouse not found' }];
});

mock.onPost('/warehouses').reply((config) => {
  const data = JSON.parse(config.data);
  const newId = MOCK_WAREHOUSES.length > 0 ? Math.max(...MOCK_WAREHOUSES.map((w) => w.id)) + 1 : 1;
  const newWarehouse = {
    id: newId,
    code: data.code || `WH${newId}`,
    name: data.name,
    address: data.address,
    stock: 0,
    capacity: Number(data.capacity || 5000),
    isActive: data.isActive !== undefined ? data.isActive : true,
  };
  MOCK_WAREHOUSES.push(newWarehouse);
  return [200, { data: newWarehouse, success: true }];
});

mock.onPut(/\/warehouses\/\d+/).reply((config) => {
  const id = parseInt(config.url.split('/').pop());
  const data = JSON.parse(config.data);
  const index = MOCK_WAREHOUSES.findIndex((w) => w.id === id);
  if (index !== -1) {
    MOCK_WAREHOUSES[index] = { ...MOCK_WAREHOUSES[index], ...data };
    return [200, { data: MOCK_WAREHOUSES[index], success: true }];
  }
  return [404, { message: 'Warehouse not found' }];
});

mock.onPatch(/\/warehouses\/\d+\/status/).reply((config) => {
  const urlParts = config.url.split('/');
  const id = parseInt(urlParts[urlParts.length - 2]);
  const data = JSON.parse(config.data);
  const index = MOCK_WAREHOUSES.findIndex((w) => w.id === id);
  if (index !== -1) {
    MOCK_WAREHOUSES[index].isActive = data.isActive;
    return [200, { data: MOCK_WAREHOUSES[index], success: true }];
  }
  return [404, { message: 'Warehouse not found' }];
});

//  Reports / Dashboard Mock 

mock.onGet('/reports/dashboard').reply(200, { data: MOCK_STATS });

mock.onGet('/reports/revenue').reply(200, {
  data: {
    monthly: MOCK_STATS.monthlyRevenue,
    byPlatform: MOCK_STATS.platformRevenue,
  },
});

mock.onGet('/reports/orders/summary').reply(200, {
  data: {
    total: MOCK_ORDERS.length,
    pending: MOCK_ORDERS.filter((o) => o.status === 'PENDING').length,
    confirmed: MOCK_ORDERS.filter((o) => o.status === 'CONFIRMED').length,
    shipping: MOCK_ORDERS.filter((o) => o.status === 'SHIPPING').length,
  },
});

mock.onGet('/reports/inventory').reply(200, {
  data: {
    totalProducts: MOCK_PRODUCTS.length,
    lowStock: MOCK_PRODUCTS.filter((p) => p.stock < 50).length,
    outOfStock: MOCK_PRODUCTS.filter((p) => p.stock === 0).length,
    totalStock: MOCK_PRODUCTS.reduce((sum, p) => sum + p.stock, 0),
  },
});

// ── System Logs Mock ──────────────────────────────────────────────────────────

mock.onGet('/system-logs').reply((config) => {
  const { query = '', type = '', startDate = '', endDate = '', page = 0, size = 20 } = config.params || {};
  let filtered = [...MOCK_AUDIT_LOGS];

  if (type && type !== 'ALL') {
    filtered = filtered.filter(l => l.type === type);
  }

  if (query) {
    const q = query.toLowerCase();
    filtered = filtered.filter(
      l => (l.message || '').toLowerCase().includes(q) ||
           (l.user || '').toLowerCase().includes(q) ||
           (l.ip || '').toLowerCase().includes(q)
    );
  }

  if (startDate) {
    const start = new Date(startDate);
    filtered = filtered.filter(l => new Date(l.timestamp) >= start);
  }

  if (endDate) {
    const end = new Date(endDate);
    end.setHours(23, 59, 59, 999);
    filtered = filtered.filter(l => new Date(l.timestamp) <= end);
  }

  // Sắp xếp giảm dần theo thời gian
  filtered.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));

  const startIdx = page * size;
  const paginated = filtered.slice(startIdx, startIdx + size);

  return [200, {
    data: {
      content: paginated,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size) || 1,
      number: page,
      size: size
    }
  }];
});

mock.onPut(/\/system-logs\/[^/]+/).reply((config) => {
  const rawId = config.url.split('/').pop();
  const id = isNaN(rawId) ? rawId : parseInt(rawId);
  const data = JSON.parse(config.data);
  const index = MOCK_AUDIT_LOGS.findIndex((l) => String(l.id) === String(id));
  if (index !== -1) {
    MOCK_AUDIT_LOGS[index] = { ...MOCK_AUDIT_LOGS[index], ...data };
    return [200, { data: MOCK_AUDIT_LOGS[index] }];
  }
  return [404, { message: 'Log entry not found' }];
});

mock.onDelete(/\/system-logs\/[^/]+/).reply((config) => {
  const rawId = config.url.split('/').pop();
  const id = isNaN(rawId) ? rawId : parseInt(rawId);
  const index = MOCK_AUDIT_LOGS.findIndex((l) => String(l.id) === String(id));
  if (index !== -1) {
    MOCK_AUDIT_LOGS.splice(index, 1);
    return [200, { data: { success: true } }];
  }
  return [404, { message: 'Log entry not found' }];
});

//  Catch-all cho những endpoint chưa mock 

mock.onAny().reply(() => {
  console.warn(`[MockAPI] Unhandled request: ${arguments?.[0]?.url ?? 'unknown'}`);
  return [200, { data: null }];
});

export default mock;
