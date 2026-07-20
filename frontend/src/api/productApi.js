import axiosClient from "./axiosClient"

const productApi = {
    getAll: async (page, size, keyword, status, platform) => {
        const params = { page, size };
        if (keyword) params.keyword = keyword;
        if (status) params.status = status;
        if (platform) params.platform = platform;
        const data = await axiosClient.get('/products', { params });
        return data;
    },

    create: async (data) => {
        const response = await axiosClient.post('/products', data);
        return response;
    },

    getById: async (id) => {
        const response = await axiosClient.get(`/products/${id}`);
        return response;
    },

    getInsights: async (id) => {
        const response = await axiosClient.get(`/products/${id}/insights`);
        return response;
    },

    getInventoryTransactions: async (id, page = 0, size = 30) => {
        const response = await axiosClient.get(`/products/${id}/inventory-transactions`, {
            params: { page, size },
        });
        return response;
    },

    update: async (id, data) => {
        const response = await axiosClient.put(`/products/${id}`, data);
        return response;
    },

    delete: async (id) => {
        const response = await axiosClient.delete(`/products/${id}/delete`);
        return response;
    },

    sync: async (productId) => {
        const response = await axiosClient.post(`/products/${productId}/sync`);
        return response;
    },

    syncChannel: async (productId, channelId) => {
        const response = await axiosClient.post(`/products/${productId}/channels/${channelId}/sync`);
        return response;
    },

    getChannelConfig: async (productId, channelId) => {
        const response = await axiosClient.get(`/products/${productId}/channels/${channelId}/config`);
        return response;
    },

    updateChannelConfig: async (productId, channelId, data) => {
        const response = await axiosClient.put(`/products/${productId}/channels/${channelId}/config`, data);
        return response;
    },

    importExcel: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await axiosClient.post('/products/import', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        });
        return response;
    }
}
export default productApi;
