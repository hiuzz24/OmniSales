import axiosClient from "./axiosClient"

const SYNC_TIMEOUT_MS = Number(import.meta.env.VITE_SYNC_API_TIMEOUT) || 300000;
const syncRequestConfig = { timeout: SYNC_TIMEOUT_MS };

const channelApi = {
    // Lấy danh sách kênh và trạng thái kết nối.
    getAll: async () => {
        const data = await axiosClient.get('/channels');
        return data;
    },
    // Tạo kênh được cấu hình thủ công.
    create: async (payload) => {
        const data = await axiosClient.post('/channels', payload);
        return data;
    },
    // Cập nhật thông tin được phép sửa của kênh.
    update: async (id, payload) => {
        const data = await axiosClient.put(`/channels/${id}`, payload);
        return data;
    },
    // Ngắt kết nối và xóa mềm kênh.
    delete: async (id) => {
        const data = await axiosClient.delete(`/channels/${id}`);
        return data;
    },
    // Lấy URL OAuth Shopify cho shop đã nhập.
    authorizeShopify: async (shop) => {
        const data = await axiosClient.get(`/channels/shopify/authorize?shop=${encodeURIComponent(shop)}`);
        return data;
    },
    // Lấy URL OAuth Lazada cho ứng dụng hiện tại.
    authorizeLazada: async () => {
        const data = await axiosClient.get(`/channels/lazada/authorize`);
        return data;
    },
    // Chạy chiều đồng bộ mặc định của một kênh.
    sync: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync`, null, syncRequestConfig);
        return data;
    },
    // Đẩy dữ liệu OSMS lên một kênh.
    syncFromApp: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync/from-app`, null, syncRequestConfig);
        return data;
    },
    // Đẩy dữ liệu OSMS lên tất cả kênh đã kết nối.
    syncAllFromApp: async () => {
        const data = await axiosClient.post('/channels/sync/from-app', null, syncRequestConfig);
        return data;
    },
    // Kéo dữ liệu từ một sàn theo luồng đồng bộ hiện có.
    syncFromMarketplace: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync/from-marketplace`, null, syncRequestConfig);
        return data;
    },
    // Đưa yêu cầu kéo dữ liệu từ sàn vào hàng đợi.
    enqueueSyncFromMarketplace: async (id) => {
        const data = await axiosClient.post(`/channels/${id}/sync/from-marketplace/jobs`);
        return data;
    },
    // Đọc tiến độ job đồng bộ từ sàn.
    getSyncJob: async (jobId) => {
        const data = await axiosClient.get(`/channels/sync-jobs/${jobId}`);
        return data;
    },
}
export default channelApi;
