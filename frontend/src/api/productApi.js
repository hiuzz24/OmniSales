import axiosClient from "./axiosClient"

const productApi = {
    getAll: async (page, size, keyword, status, platforms) => {
        const params = { page, size };
        if (keyword) params.keyword = keyword;
        if (status) params.status = status;
        if (Array.isArray(platforms) && platforms.length) {
            params.platforms = platforms.join(',');
        } else if (platforms) {
            params.platform = platforms;
        }
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

    update: async (id, data) => {
        const response = await axiosClient.put(`/products/${id}`, data);
        return response;
    },

    delete: async (id) => {
        const response = await axiosClient.delete(`/products/${id}/delete`);
        return response;
    },

    sync: async (productId) => {
        const response = await axiosClient.post(`/products/${productId}/sync/async`);
        return response;
    },

    syncChannel: async (productId, channelId) => {
        const response = await axiosClient.post(`/products/${productId}/channels/${channelId}/sync/async`);
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
