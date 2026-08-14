import axiosClient from './axiosClient';

const orderReturnApi = {
  // Lấy danh sách phiếu trả hàng có phân trang.
  getAll: async ({ page = 0, size = 5 } = {}) => {
    const response = await axiosClient.get('/order-returns', { params: { page, size } });
    return response.data.data;
  },
  // Lấy đầy đủ chi tiết một phiếu trả hàng.
  getById: async (id) => (await axiosClient.get(`/order-returns/${id}`)).data.data,
  // Duyệt yêu cầu trả hàng trên platform.
  approve: async (id) => (await axiosClient.post(`/order-returns/${id}/approve`)).data.data,
  // Lấy các lý do từ chối còn hợp lệ trên platform.
  getRejectOptions: async (id) => (await axiosClient.get(`/order-returns/${id}/reject-options`)).data.data,
  // Từ chối yêu cầu trả hàng bằng reason code đã chọn.
  reject: async (id, payload) => (await axiosClient.post(`/order-returns/${id}/reject`, payload)).data.data,
  // Lưu số lượng nhận, đạt, hỏng và thiếu sau kiểm hàng.
  inspect: async (id, items) => (await axiosClient.post(`/order-returns/${id}/inspect`, { items })).data.data,
  // Đồng bộ snapshot mới nhất mà không lặp lại action.
  refresh: async (id) => (await axiosClient.post(`/order-returns/${id}/refresh`)).data.data,
  // Kiểm tra kết quả action chưa xác định trên platform.
  checkAction: async (id) => (await axiosClient.post(`/order-returns/${id}/check-action`)).data.data,
  // Thử lại action đã được backend xác định là an toàn.
  retryAction: async (id) => (await axiosClient.post(`/order-returns/${id}/retry-action`)).data.data,
  // Thử lại bước nhập kho nội bộ mà không gọi platform.
  retryStock: async (id) => (await axiosClient.post(`/order-returns/${id}/retry-stock`)).data.data,
};

export default orderReturnApi;
