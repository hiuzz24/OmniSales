import axiosClient from '../../../api/axiosClient';
import '../../../api/interceptors';

const stockReceiveService = {
  createReceipt: (data) => axiosClient.post('/receipts', data),
  getReceipts: (params) => axiosClient.get('/receipts', { params }),
  getReceiptStatistics: () => axiosClient.get('/receipts/statistics'),
  getNextReceiptCode: () => axiosClient.get('/receipts/next-code'),
  getReceiptById: (id) => axiosClient.get(`/receipts/${id}`),
  updateReceipt: (id, data) => axiosClient.put(`/receipts/${id}`, data),
  completeReceipt: (id) => axiosClient.patch(`/receipts/${id}/complete`),
  syncPendingMarketplaceInventory: () => axiosClient.post('/receipts/sync-marketplace-inventory'),
};

export default stockReceiveService;
