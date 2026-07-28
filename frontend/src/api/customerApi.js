import axiosClient from './axiosClient';

const customerApi = {
  getAll: async (page = 0, size = 20, search = '', status = 'ALL', gender = 'ALL') => {
    const params = { page, size };
    if (search) params.search = search;
    if (status && status !== 'ALL') params.status = status;
    if (gender && gender !== 'ALL') params.gender = gender;
    const response = await axiosClient.get('/customers', { params });
    return response.data.data;
  },

  getById: async (id) => {
    const response = await axiosClient.get(`/customers/${id}`);
    return response.data.data;
  },

  create: async (data) => {
    const response = await axiosClient.post('/customers', data);
    return response.data.data;
  },

  update: async (id, data) => {
    const response = await axiosClient.put(`/customers/${id}`, data);
    return response.data.data;
  },

  delete: async (id) => {
    await axiosClient.delete(`/customers/${id}`);
  },

  getStats: async () => {
    const response = await axiosClient.get('/customers/stats');
    return response.data.data;
  },

  getPageWithOrderCustomers: async (page = 0, size = 20, search = '', status = 'ALL', gender = 'ALL') => {
    const params = { page, size };
    if (search) params.search = search;
    if (status && status !== 'ALL') params.status = status;
    if (gender && gender !== 'ALL') params.gender = gender;
    const response = await axiosClient.get('/customers/page-with-order-customers', { params });
    return response.data.data;
  },

  syncFromOrders: async () => {
    const response = await axiosClient.post('/customers/sync-from-orders');
    return response.data.data;
  },
};

export default customerApi;