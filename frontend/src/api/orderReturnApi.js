import axiosClient from './axiosClient';

const orderReturnApi = {
  getAll: async ({ page = 0, size = 5 } = {}) => {
    const response = await axiosClient.get('/order-returns', { params: { page, size } });
    return response.data.data;
  },
  getById: async (id) => (await axiosClient.get(`/order-returns/${id}`)).data.data,
  approve: async (id) => (await axiosClient.post(`/order-returns/${id}/approve`)).data.data,
  reject: async (id, reason) => (await axiosClient.post(`/order-returns/${id}/reject`, { reason })).data.data,
  inspect: async (id, items) => (await axiosClient.post(`/order-returns/${id}/inspect`, { items })).data.data,
  checkAction: async (id) => (await axiosClient.post(`/order-returns/${id}/check-action`)).data.data,
  retryAction: async (id) => (await axiosClient.post(`/order-returns/${id}/retry-action`)).data.data,
  retryStock: async (id) => (await axiosClient.post(`/order-returns/${id}/retry-stock`)).data.data,
};

export default orderReturnApi;
