import axiosClient from "./axiosClient"

const productApi = {
    // Lấy danh sách sản phẩm theo bộ lọc và phân trang.
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

    // Tạo sản phẩm OSMS mới cùng dữ liệu biến thể và kênh được chọn.
    create: async (data) => {
        const response = await axiosClient.post('/products', data);
        return response;
    },

    // Lấy đầy đủ chi tiết một sản phẩm.
    getById: async (id) => {
        const response = await axiosClient.get(`/products/${id}`);
        return response;
    },

    // Cập nhật catalog nội bộ của sản phẩm.
    update: async (id, data) => {
        const response = await axiosClient.put(`/products/${id}`, data);
        return response;
    },

    // Xóa mềm sản phẩm khỏi catalog đang hoạt động.
    delete: async (id) => {
        const response = await axiosClient.delete(`/products/${id}/delete`);
        return response;
    },

    // Đưa yêu cầu đồng bộ sản phẩm lên tất cả kênh vào hàng đợi.
    sync: async (productId) => {
        const response = await axiosClient.post(`/products/${productId}/sync/async`);
        return response;
    },

    // Đưa yêu cầu đồng bộ sản phẩm lên một kênh vào hàng đợi.
    syncChannel: async (productId, channelId) => {
        const response = await axiosClient.post(`/products/${productId}/channels/${channelId}/sync/async`);
        return response;
    },

    // Lấy cấu hình đăng bán của sản phẩm trên một kênh.
    getChannelConfig: async (productId, channelId) => {
        const response = await axiosClient.get(`/products/${productId}/channels/${channelId}/config`);
        return response;
    },

    // Lưu cấu hình đăng bán của sản phẩm trên một kênh.
    updateChannelConfig: async (productId, channelId, data) => {
        const response = await axiosClient.put(`/products/${productId}/channels/${channelId}/config`, data);
        return response;
    },

    // Gửi file Excel để import sản phẩm vào OSMS.
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
