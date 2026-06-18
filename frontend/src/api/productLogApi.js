import axiosClient from './axiosClient';

const productLogApi = {
  getAll: (params) => {
    return axiosClient.get('/product-logs', { params });
  },
};

export default productLogApi;
