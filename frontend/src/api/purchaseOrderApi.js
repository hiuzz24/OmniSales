import axiosClient from './axiosClient';

const purchaseOrderApi = {
  getAll: async ({ page = 0, size = 50, status } = {}) => {
    const response = await axiosClient.get('/purchase-orders', { params: { page, size, status: status || undefined } });
    return response.data?.data ?? response.data;
  },
  getById: async (id) => {
    const response = await axiosClient.get(`/purchase-orders/${id}`);
    return response.data?.data ?? response.data;
  },
  getStatistics: async () => {
    const response = await axiosClient.get('/purchase-orders/statistics');
    return response.data?.data ?? response.data;
  },
  getFormOptions: async () => {
    const response = await axiosClient.get('/purchase-orders/form-options');
    return response.data?.data ?? response.data;
  },
  getNextCode: async () => {
    const response = await axiosClient.get('/purchase-orders/next-code');
    return response.data?.data ?? response.data;
  },
  getSuppliers: async ({ page = 0, size = 20, keyword = '' } = {}) => {
    const response = await axiosClient.get('/suppliers', { params: { page, size, keyword } });
    return response.data?.data ?? response.data;
  },
  createSupplier: async (payload) => {
    const response = await axiosClient.post('/suppliers', payload);
    return response.data?.data ?? response.data;
  },
  create: async (payload) => {
    const response = await axiosClient.post('/purchase-orders', payload);
    return response.data?.data ?? response.data;
  },
  update: async (id, payload) => {
    const response = await axiosClient.put(`/purchase-orders/${id}`, payload);
    return response.data?.data ?? response.data;
  },
  send: async (id) => {
    const response = await axiosClient.patch(`/purchase-orders/${id}/send`);
    return response.data?.data ?? response.data;
  },
  cancel: async (id) => {
    const response = await axiosClient.patch(`/purchase-orders/${id}/cancel`);
    return response.data?.data ?? response.data;
  },
};

export default purchaseOrderApi;
