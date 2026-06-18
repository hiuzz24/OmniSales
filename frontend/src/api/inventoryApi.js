import axiosClient from './axiosClient';

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
  getInventoryList: (page = 0, size = 10, sortBy = 'updatedAt', sortDir = 'desc', categoryId = null) => {
    const params = { page, size, sortBy, sortDir };
    if (categoryId) {
      return axiosClient.get(`/inventory/category/${categoryId}`, { params });
    }
    return axiosClient.get('/inventory', { params });
  },

  getInventoryItemDetail: (id) => {
    return axiosClient.get(`/inventory/detail/${id}`);
  },

  getTransactions: (variantId, page = 0, size = 10, sortBy = 'performedAt', sortDir = 'desc') => {
    return axiosClient.get('/inventory/detail/transactions', {
      params: { variantId, page, size, sortBy, sortDir }
    });
  },
};

export default inventoryApi;
