import axiosClient from '../../../api/axiosClient';
import '../../../api/interceptors';

const stockReceiveService = {
  createReceipt: (data) => axiosClient.post('/receipts', data),
  createManualReceipt: (data) => axiosClient.post('/receipts/manual', data),
  getReceipts: (params) => axiosClient.get('/receipts', { params }),
  getReceiptStatistics: () => axiosClient.get('/receipts/statistics'),
  getNextReceiptCode: () => axiosClient.get('/receipts/next-code'),
  getReceiptById: (id) => axiosClient.get(`/receipts/${id}`),
  updateReceipt: (id, data) => axiosClient.put(`/receipts/${id}`, data),
  completeReceipt: (id) => axiosClient.patch(`/receipts/${id}/complete`),
  syncPendingMarketplaceInventory: () => axiosClient.post('/receipts/sync-marketplace-inventory'),
  syncReceiptMarketplaceInventory: (id) => axiosClient.post(`/receipts/${id}/sync-marketplace-inventory`),
  downloadNewReceiptExtraItemsTemplate: () => axiosClient.get('/receipts/import-extra-items/template', { responseType: 'blob' }),
  downloadExtraItemsTemplate: (id) => axiosClient.get(`/receipts/${id}/import-extra-items/template`, { responseType: 'blob' }),
  previewExtraItemsImport: (id, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axiosClient.post(`/receipts/${id}/import-extra-items/preview`, formData, {
      headers: { 'Content-Type': undefined },
    });
  },
  confirmExtraItemsImport: (id, rows) => axiosClient.post(`/receipts/${id}/import-extra-items/confirm`, { rows }),
};

export default stockReceiveService;
