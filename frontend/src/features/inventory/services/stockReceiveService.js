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
  syncReceiptMarketplaceInventory: (id) => axiosClient.post(`/receipts/${id}/sync-marketplace-inventory`),
  downloadExtraItemsTemplate: (id) => axiosClient.get(`/receipts/${id}/import-extra-items/template`, { responseType: 'blob' }),
  downloadNewReceiptExtraItemsTemplate: () => axiosClient.get('/receipts/import-extra-items/template', { responseType: 'blob' }),
  previewExtraItemsImport: (id, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axiosClient.post(`/receipts/${id}/import-extra-items/preview`, formData);
  },
  confirmExtraItemsImport: (id, rows) => axiosClient.post(`/receipts/${id}/import-extra-items/confirm`, { rows }),
};

export default stockReceiveService;
