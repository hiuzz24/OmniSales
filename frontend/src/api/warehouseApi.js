import axiosClient from "./axiosClient";

const warehouseApi = {
    // Get all warehouses
    getAll: async (params) => {
        const response = await axiosClient.get('/inventory/warehouses', { params });
        return response;
    },

    // Get warehouse by ID
    getById: async (id) => {
        const response = await axiosClient.get(`/inventory/warehouses/${id}`);
        return response;
    },

    // Create new warehouse
    create: async (data) => {
        const response = await axiosClient.post('/inventory/warehouses', data);
        return response;
    },

    // Update warehouse
    update: async (id, data) => {
        const response = await axiosClient.put(`/inventory/warehouses/${id}`, data);
        return response;
    },

    // Delete warehouse
    delete: async (id) => {
        const response = await axiosClient.delete(`/inventory/warehouses/${id}`);
        return response;
    }
};

export default warehouseApi;
