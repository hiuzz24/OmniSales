import axiosClient from "./axiosClient"

const SYNC_TIMEOUT_MS = Number(import.meta.env.VITE_SYNC_API_TIMEOUT) || 300000;
const syncRequestConfig = { timeout: SYNC_TIMEOUT_MS };

const channelApi = {
    getAll: async () => {
        const data = await axiosClient.get('/channels');
        return data;
    },
    create: async (payload) => {
        const data = await axiosClient.post('/channels', payload);
        return data;
    },
    update: async (id, payload) => {
        const data = await axiosClient.put(`/channels/${id}`, payload);
        return data;
    },
    delete: async (id) => {
        const data = await axiosClient.delete(`/channels/${id}`);
        return data;
    },
    authorizeShopify: async (shop) => {
        const data = await axiosClient.get(`/channels/shopify/authorize?shop=${encodeURIComponent(shop)}`);
        return data;
    },
    authorizeLazada: async () => {
        const data = await axiosClient.get(`/channels/lazada/authorize`);
        return data;
    },
    sync: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync`, null, syncRequestConfig);
        return data;
    },
    syncFromApp: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync/from-app`, null, syncRequestConfig);
        return data;
    },
    syncAllFromApp: async () => {
        const data = await axiosClient.post('/channels/sync/from-app', null, syncRequestConfig);
        return data;
    },
    syncFromMarketplace: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync/from-marketplace`, null, syncRequestConfig);
        return data;
    },
    enqueueSyncFromMarketplace: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync/from-marketplace/jobs`);
        return data;
    },
    getSyncJob: async (jobId) => {
        const data = await axiosClient.get(`/channels/sync-jobs/${jobId}`);
        return data;
    },
}
export default channelApi;
