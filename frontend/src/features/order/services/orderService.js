import orderApi from '../../../api/orderApi';

const orderService = {
  getAll: async (params = {}) => {
    return await orderApi.getAll(params);
  },

  getById: async (id) => {
    return await orderApi.getById(id);
  },

  create: async (data) => {
    return await orderApi.create(data);
  },

  update: async (id, data) => {
    return await orderApi.update(id, data);
  },

  updateStatus: async (id, status) => {
    return await orderApi.updateStatus(id, status);
  },

  cancel: async (id, data) => {
    return await orderApi.cancel(id, data);
  },

  getCancelReasons: async (id) => {
    return await orderApi.getCancelReasons(id);
  },

  getHistory: async (id, page, size) => {
    return await orderApi.getHistory(id, page, size);
  },

  updatePaymentStatus: async (id, paymentStatus) => {
    return await orderApi.updatePaymentStatus(id, paymentStatus);
  },

  getStats: async () => {
    return await orderApi.getStats();
  },
};

export default orderService;
