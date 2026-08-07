import axiosClient from './axiosClient';
import './interceptors';

const stockDeliveryApi = {
  // Create stock delivery
  createStockDelivery: (deliveryData) => {
    return axiosClient.post('/stock-deliveries', deliveryData);
  },

  // Update draft stock delivery
  updateStockDelivery: (deliveryId, deliveryData) => {
    return axiosClient.put(`/stock-deliveries/${deliveryId}`, deliveryData);
  },

  // Get stock delivery by ID
  getStockDeliveryById: (deliveryId) => {
    return axiosClient.get(`/stock-deliveries/${deliveryId}`);
  },

  // Get all stock deliveries with filters
  getAllStockDeliveries: (params = {}) => {
    return axiosClient.get('/stock-deliveries', { params });
  },

  // Confirm stock delivery
  confirmStockDelivery: (deliveryId) => {
    return axiosClient.put(`/stock-deliveries/${deliveryId}/confirm`);
  },

  // Cancel stock delivery
  cancelStockDelivery: (deliveryId) => {
    return axiosClient.put(`/stock-deliveries/${deliveryId}/cancel`);
  },

  // Get delivery statistics
  getDeliveryStatistics: () => {
    return axiosClient.get('/stock-deliveries/statistics');
  },

  syncPendingMarketplaceInventory: () => {
    return axiosClient.post('/stock-deliveries/sync-marketplace-inventory');
  },

  getOrderCandidates: (params = {}) => {
    return axiosClient.get('/stock-deliveries/order-candidates', { params });
  },

  getOrderReadiness: (orderId) => {
    return axiosClient.get(`/stock-deliveries/orders/${orderId}/readiness`);
  },

  createFromOrders: (request) => {
    const payload = Array.isArray(request) ? { orderIds: request } : request;
    return axiosClient.post('/stock-deliveries/from-orders', payload);
  },
};

export default stockDeliveryApi;
