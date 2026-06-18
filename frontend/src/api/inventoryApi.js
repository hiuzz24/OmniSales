import axiosClient from './axiosClient';
import './interceptors';

const inventoryApi = {
  /**
   * GET /api/inventory
   * @param {number} page   - 0-indexed page number
   * @param {number} size   - page size
   * @param {string} sortBy - field to sort by (default: updatedAt)
   * @param {string} sortDir - asc | desc
   * @param {string|null} categoryId - category filter
   * @returns {Promise<PageResponse<InventoryItemResponse>>}
   */
  getInventoryList: async (page = 0, size = 10, sortBy = 'updatedAt', sortDir = 'desc', categoryId = null) => {
    const params = { page, size, sortBy, sortDir };
    const res = categoryId
      ? await axiosClient.get(`/inventory/category/${categoryId}`, { params })
      : await axiosClient.get('/inventory', { params });
    return res.data?.data ?? res.data;
  },

  getInventoryItemDetail: async (id) => {
    const res = await axiosClient.get(`/inventory/detail/${id}`);
    return res.data?.data ?? res.data;
  },

  getTransactions: async (variantId, page = 0, size = 10, sortBy = 'performedAt', sortDir = 'desc') => {
    const res = await axiosClient.get('/inventory/detail/transactions', {
      params: { variantId, page, size, sortBy, sortDir }
    });
    return res.data?.data ?? res.data;
  },
};

export default inventoryApi;
