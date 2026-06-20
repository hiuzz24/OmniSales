import axiosClient from "./axiosClient"

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
}
export default channelApi;
