import axiosClient from './axiosClient';

const reportApi = {
  getOrdersByChannel: async ({ from, to } = {}) => {
    const response = await axiosClient.get('/reports/orders-by-channel', {
      params: { from, to },
    });
    return response.data.data;
  },
  getReturns: async ({ from, to } = {}) => {
    const response = await axiosClient.get('/reports/returns', { params: { from, to } });
    return response.data.data;
  },
  getProducts: async ({ from, to } = {}) => {
    const response = await axiosClient.get('/reports/products', { params: { from, to } });
    return response.data.data;
  },
};

export default reportApi;
