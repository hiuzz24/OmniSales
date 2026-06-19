import axiosClient from "./axiosClient";

const inventoryApi = {
    // Get inventory items by warehouse
    getByWarehouse: async (warehouseId, params) => {
        const response = await axiosClient.get(`/inventory/warehouses/${warehouseId}/items`, { params });
        return response;
    },

    // Get inventory item by variant
    getByVariant: async (warehouseId, variantId) => {
        const response = await axiosClient.get(`/inventory/warehouses/${warehouseId}/variants/${variantId}`);
        return response;
    },

    // Search products/variants for inventory
    searchVariants: async (params) => {
        const response = await axiosClient.get('/products/variants/search', { params });
        return response;
    }
};

export default inventoryApi;
