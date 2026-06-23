import inventoryApi from '../../../api/inventoryApi';

const inventoryService = {
  /**
   * Lấy danh sách tồn kho với phân trang từ BE.
   * @param {number} page   - 0-indexed
   * @param {number} size
   * @param {string} sortBy
   * @param {string} sortDir
   * @param {string|null} categoryId
   * @returns {Promise<PageResponse<InventoryItemResponse>>}
   */
  getInventoryList: async (page = 0, size = 10, sortBy = 'updatedAt', sortDir = 'desc', categoryId = null) => {
    return await inventoryApi.getInventoryList(page, size, sortBy, sortDir, categoryId);
  },

  getLowStockItems: async () => {
    return await inventoryApi.getLowStockItems();
  },
};

export default inventoryService;
