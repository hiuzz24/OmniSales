import axiosClient from './axiosClient';

const orderApi = {
  // Lấy danh sách đơn hàng theo bộ lọc và phân trang.
  getAll: async ({ status, channelId, customerId, keyword, from, to, page = 0, size = 20 } = {}) => {
    const params = { page, size };
    if (status) params.status = status;
    if (channelId) params.channelId = channelId;
    if (customerId) params.customerId = customerId;
    if (keyword) params.keyword = keyword;
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await axiosClient.get('/orders', { params });
    return response.data.data;
  },

  // Lấy chi tiết một đơn đã import.
  getById: async (id) => {
    const response = await axiosClient.get(`/orders/${id}`);
    return response.data.data;
  },

  // Chuyển trạng thái đơn qua policy của backend.
  updateStatus: async (id, status) => {
    const response = await axiosClient.patch(`/orders/${id}/status`, null, { params: { status } });
    return response.data.data;
  },

  // Hủy đơn bằng endpoint nghiệp vụ chuyên dụng.
  cancel: async (id, data = {}) => {
    const response = await axiosClient.post(`/orders/${id}/cancel`, data);
    return response.data.data;
  },

  // Lấy lý do hủy hợp lệ theo platform của đơn.
  getCancelReasons: async (id) => {
    const response = await axiosClient.get(`/orders/${id}/cancel-reasons`);
    return response.data.data;
  },

  // Yêu cầu URL phiếu vận chuyển chính thức từ platform.
  createShippingLabel: async (id) => {
    const response = await axiosClient.post(`/orders/${id}/shipping-label`);
    return response.data.data;
  },

  // Lấy audit log trạng thái của đơn.
  getHistory: async (id, page = 0, size = 20) => {
    const response = await axiosClient.get(`/orders/${id}/history`, { params: { page, size } });
    return response.data.data;
  },

  // Cập nhật trạng thái thanh toán nội bộ được phép chỉnh tay.
  updatePaymentStatus: async (id, paymentStatus) => {
    const response = await axiosClient.patch(`/orders/${id}/payment-status`, null, { params: { paymentStatus } });
    return response.data.data;
  },

  // Lấy số liệu tổng hợp cho màn danh sách đơn.
  getStats: async () => {
    const response = await axiosClient.get('/orders/stats');
    return response.data.data;
  },

  // Đếm đơn chưa được liên kết khách hàng nội bộ.
  getUncustomerdCount: async () => {
    const response = await axiosClient.get('/orders/uncustomerd-count');
    return response.data.data;
  },

  // Tạo các job kéo đơn thủ công theo kênh và khoảng thời gian.
  pullOrders: async (data) => {
    const response = await axiosClient.post('/orders/pull', data);
    return response.data.data;
  },

  // Đọc tiến độ một job kéo đơn.
  getPullJob: async (id) => {
    const response = await axiosClient.get(`/orders/pull/${id}`);
    return response.data.data;
  },

  // Lấy các job kéo đơn đang chờ để tiếp tục polling.
  getActivePullJobs: async () => {
    const response = await axiosClient.get('/orders/pull/active');
    return response.data.data;
  },
};

export default orderApi;
