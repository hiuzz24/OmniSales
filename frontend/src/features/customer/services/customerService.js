import customerApi from '../../../api/customerApi';

const customerService = {
  getAll: async (page = 0, size = 20, search = '', status = 'ALL', gender = 'ALL') => {
    const data = await customerApi.getAll(page, size, search, status, gender);
    return data;
  },

  getById: async (id) => {
    const data = await customerApi.getById(id);
    return data;
  },

  create: async (data) => {
    const customer = await customerApi.create(data);
    return customer;
  },

  update: async (id, data) => {
    const customer = await customerApi.update(id, data);
    return customer;
  },

  delete: async (id) => {
    await customerApi.delete(id);
  },

  getStats: async () => {
    return await customerApi.getStats();
  },

  getPageWithOrderCustomers: async (page = 0, size = 20, search = '', status = 'ALL', gender = 'ALL') => {
    return await customerApi.getPageWithOrderCustomers(page, size, search, status, gender);
  },

  syncFromOrders: async () => {
    return await customerApi.syncFromOrders();
  },
};

export default customerService;