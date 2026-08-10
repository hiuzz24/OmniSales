import axiosClient from './axiosClient';

const orderApi = {
  getAll: async ({ status, channelId, customerId, keyword, from, to, page = 0, size = 20 } = {}) => {
    const params = { page, size };
    if (status) params.status = status;
    if (channelId) params.channelId = channelId;
    if (customerId) params.customerId = customerId;
    if (keyword) params.keyword = keyword;
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await axiosClient.get('/orders', { params });
    return response.data.data;
  },

  getById: async (id) => {
    const response = await axiosClient.get(`/orders/${id}`);
    return response.data.data;
  },

  create: async (data) => {
    const response = await axiosClient.post('/orders', data);
    return response.data.data;
  },

  update: async (id, data) => {
    const response = await axiosClient.put(`/orders/${id}`, data);
    return response.data.data;
  },

  updateStatus: async (id, status) => {
    const response = await axiosClient.patch(`/orders/${id}/status`, null, { params: { status } });
    return response.data.data;
  },

  cancel: async (id, data = {}) => {
    const response = await axiosClient.post(`/orders/${id}/cancel`, data);
    return response.data.data;
  },

  getCancelReasons: async (id) => {
    const response = await axiosClient.get(`/orders/${id}/cancel-reasons`);
    return response.data.data;
  },

  createShippingLabel: async (id) => {
    const response = await axiosClient.post(`/orders/${id}/shipping-label`);
    return response.data.data;
  },

  getHistory: async (id, page = 0, size = 20) => {
    const response = await axiosClient.get(`/orders/${id}/history`, { params: { page, size } });
    return response.data.data;
  },

  updatePaymentStatus: async (id, paymentStatus) => {
    const response = await axiosClient.patch(`/orders/${id}/payment-status`, null, { params: { paymentStatus } });
    return response.data.data;
  },

  getStats: async () => {
    const response = await axiosClient.get('/orders/stats');
    return response.data.data;
  },

  getUncustomerdCount: async () => {
    const response = await axiosClient.get('/orders/uncustomerd-count');
    return response.data.data;
  },

  pullOrders: async (data) => {
    const response = await axiosClient.post('/orders/pull', data);
    return response.data.data;
  },

  getPullJob: async (id) => {
    const response = await axiosClient.get(`/orders/pull/${id}`);
    return response.data.data;
  },

  getActivePullJobs: async () => {
    const response = await axiosClient.get('/orders/pull/active');
    return response.data.data;
  },
};

export default orderApi;
