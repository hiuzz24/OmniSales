import axiosClient from '../../../api/axiosClient';

const stocktakeService = {
  getStocktakes: (params) => axiosClient.get('/stocktakes', { params }),
  getStatistics: () => axiosClient.get('/stocktakes/statistics'),
  getById: (id) => axiosClient.get(`/stocktakes/${id}`),
  create: (payload, complete = false) => axiosClient.post('/stocktakes', payload, { params: { complete } }),
  update: (id, payload) => axiosClient.put(`/stocktakes/${id}`, payload),
  changeStatus: (id, status) => axiosClient.put(`/stocktakes/${id}/status`, { status }),
  syncPendingMarketplaceInventory: () => axiosClient.post('/stocktakes/sync-marketplace-inventory'),
  syncStocktakeMarketplaceInventory: (id) => axiosClient.post(`/stocktakes/${id}/sync-marketplace-inventory`),
};

export default stocktakeService;
