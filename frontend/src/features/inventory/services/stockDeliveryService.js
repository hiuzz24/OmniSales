import stockDeliveryApi from '../../../api/stockDeliveryApi';

const stockDeliveryService = {
  // Create a new stock delivery
  createStockDelivery: async (deliveryData) => {
    try {
      const response = await stockDeliveryApi.createStockDelivery(deliveryData);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  // Update draft stock delivery
  updateStockDelivery: async (deliveryId, deliveryData) => {
    try {
      const response = await stockDeliveryApi.updateStockDelivery(deliveryId, deliveryData);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  // Get stock delivery by ID
  getStockDeliveryById: async (deliveryId) => {
    try {
      const response = await stockDeliveryApi.getStockDeliveryById(deliveryId);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  // Get all stock deliveries with filters and pagination
  getAllStockDeliveries: async (filters = {}) => {
    try {
      const response = await stockDeliveryApi.getAllStockDeliveries(filters);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  // Confirm stock delivery
  confirmStockDelivery: async (deliveryId) => {
    try {
      const response = await stockDeliveryApi.confirmStockDelivery(deliveryId);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  // Cancel stock delivery
  cancelStockDelivery: async (deliveryId) => {
    try {
      const response = await stockDeliveryApi.cancelStockDelivery(deliveryId);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  // Get delivery statistics
  getDeliveryStatistics: async () => {
    try {
      const response = await stockDeliveryApi.getDeliveryStatistics();
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  syncPendingMarketplaceInventory: async () => {
    try {
      const response = await stockDeliveryApi.syncPendingMarketplaceInventory();
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  getOrderCandidates: async (params = {}) => {
    try {
      const response = await stockDeliveryApi.getOrderCandidates(params);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },

  createFromOrders: async (orderIds) => {
    try {
      const response = await stockDeliveryApi.createFromOrders(orderIds);
      return response.data;
    } catch (error) {
      throw error.response?.data || error.message;
    }
  },
};

export default stockDeliveryService;
