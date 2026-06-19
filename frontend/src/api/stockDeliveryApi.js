import axiosClient from './axiosClient';
import './interceptors';

const stockDeliveryApi = {
  // Create stock delivery
  createStockDelivery: (deliveryData) => {
    return axiosClient.post('/stock-deliveries', deliveryData);
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
};

export default stockDeliveryApi;
