import axiosClient from "./axiosClient";

const warehouseApi = {
    // Get all warehouses
    getAll: async (params) => {
        const response = await axiosClient.get('/warehouses', { params });
        return response;
    },

    getMaster: async () => {
        const response = await axiosClient.get('/warehouses/master');
        return response;
    },

    // Get warehouse by ID
    getById: async (id) => {
        const response = await axiosClient.get(`/warehouses/${id}`);
        return response;
    },

    // Create new warehouse
    create: async (data) => {
        const response = await axiosClient.post('/warehouses', data);
        return response;
    },

    // Update warehouse
    update: async (id, data) => {
        const response = await axiosClient.put(`/warehouses/${id}`, data);
        return response;
    },

    // Delete warehouse
    delete: async (id) => {
        const response = await axiosClient.delete(`/warehouses/${id}`);
        return response;
    },

    // Sync warehouse name/address/contact to all connected marketplace channels
    // payload: { name, address, contactName, phone, email?, city?, province?, zip?, countryCode? }
    syncToMarketplaces: async (id, payload) => {
        const response = await axiosClient.post(`/warehouses/${id}/sync-to-marketplaces`, payload);
        return response;
    },
};

export default warehouseApi;
