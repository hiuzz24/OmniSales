import axiosClient from "./axiosClient";
import './interceptors';

const transferApi = {
    getTransfers: async (params) => {
        const response = await axiosClient.get('/transfer', { params });
        return response.data?.data ?? response.data;
    },

    // Lấy danh sách variant có tồn kho tại kho xuất
    getAvailableVariants: async (warehouseId) => {
        const response = await axiosClient.get('/transfer/available-variants', { params: { warehouseId } });
        return response.data;
    },

    // Tạo phiếu chuyển kho mới
    createTransfer: async (payload) => {
        const response = await axiosClient.post('/transfer', payload);
        return response.data;
    },

    // Lấy mã chuyển kho gợi ý từ BE
    getSuggestedCode: async () => {
        const response = await axiosClient.get('/transfer/suggested-code');
        return response.data;
    },
};

export default transferApi;
