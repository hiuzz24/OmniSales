import axiosClient from "./axiosClient";
import './interceptors';

const inventoryApi = {
    // Get inventory items by warehouse
    getByWarehouse: async (warehouseId, params) => {
        const response = await axiosClient.get(`/inventory/warehouses/${warehouseId}/items`, { params });
        return response;
    },

    getAvailableVariantsByWarehouse: async (warehouseId) => {
        const response = await axiosClient.get(`/inventory/warehouses/${warehouseId}/available-variants`);
        return response;
    },

  getInventoryList: async (page = 0, size = 10, sortBy = 'updatedAt', sortDir = 'desc', categoryId = null, channelId = null, localOnly = false, filters = {}) => {
    const params = { page, size, sortBy, sortDir };
    if (channelId) params.channelId = channelId;
    if (localOnly) params.localOnly = true;
    if (filters.keyword) params.keyword = filters.keyword;
    if (filters.status && filters.status !== 'all') params.status = filters.status;
    if (filters.warehouseId && filters.warehouseId !== 'all') params.warehouseId = filters.warehouseId;
    if (filters.platforms?.length) params.platforms = filters.platforms.join(',');
    const res = categoryId
      ? await axiosClient.get(`/inventory/category/${categoryId}`, { params })
      : await axiosClient.get('/inventory', { params });
    return res.data?.data ?? res.data;
  },

  getInventoryGroups: async (page = 0, size = 10, sortBy = 'updatedAt', sortDir = 'desc', categoryId = null, channelId = null, localOnly = false, filters = {}) => {
    const params = { page, size, sortBy, sortDir };
    if (categoryId) params.categoryId = categoryId;
    if (channelId) params.channelId = channelId;
    if (localOnly) params.localOnly = true;
    if (filters.keyword) params.keyword = filters.keyword;
    if (filters.status && filters.status !== 'all') params.status = filters.status;
    if (filters.warehouseId && filters.warehouseId !== 'all') params.warehouseId = filters.warehouseId;
    if (filters.platforms?.length) params.platforms = filters.platforms.join(',');
    const res = await axiosClient.get('/inventory/groups', { params });
    return res.data?.data ?? res.data;
  },

  getInventorySummary: async () => {
    const res = await axiosClient.get('/inventory/summary');
    return res.data?.data ?? res.data;
  },

  getLowStockItems: async () => {
    const res = await axiosClient.get('/inventory/items/low-stock');
    return res.data?.data ?? res.data;
  },

    // Get inventory item by variant
    getByVariant: async (warehouseId, variantId) => {
        const response = await axiosClient.get(`/inventory/warehouses/${warehouseId}/variants/${variantId}`);
        return response;
    },

    // Search products/variants for inventory
    searchVariants: async (params) => {
        const response = await axiosClient.get('/products/variants/search', { params });
        return response;
    },


  getInventoryItemDetail: async (id) => {
    const res = await axiosClient.get(`/inventory/detail/${id}`);
    return res.data?.data ?? res.data;
  },

  updateInventoryItemDetail: async (id, data) => {
    const res = await axiosClient.put(`/inventory/detail/${id}`, data);
    return res.data?.data ?? res.data;
  },

  getTransactions: async (variantId = null, page = 0, size = 10, sortBy = 'performedAt', sortDir = 'desc') => {
    const params = { page, size, sortBy, sortDir };
    if (variantId) params.variantId = variantId;
    const res = await axiosClient.get('/inventory/detail/transactions', {
      params
    });
    return res.data?.data ?? res.data;
  },

  getInventoryLogs: async (params) => {
    const res = await axiosClient.get('/inventory/log', { params });
    return res.data;
  },
};

export default inventoryApi;
