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

mock.onGet('/warehouses').reply(200, { data: MOCK_WAREHOUSES });

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

//  Catch-all cho những endpoint chưa mock 

mock.onAny().reply(() => {
  console.warn(`[MockAPI] Unhandled request: ${arguments?.[0]?.url ?? 'unknown'}`);
  return [200, { data: null }];
});

export default mock;
