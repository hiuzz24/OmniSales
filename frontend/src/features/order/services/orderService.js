import orderApi from '../../../api/orderApi';

const orderService = {
  /** Lấy danh sách order theo bộ lọc và phân trang. */
  getAll: async (params = {}) => {
    return await orderApi.getAll(params);
  },

  /** Lấy chi tiết một order. */
  getById: async (id) => {
    return await orderApi.getById(id);
  },

  /** Cập nhật trạng thái nghiệp vụ của order. */
  updateStatus: async (id, status) => {
    return await orderApi.updateStatus(id, status);
  },

  /** Hủy order qua luồng hủy chuyên biệt. */
  cancel: async (id, data) => {
    return await orderApi.cancel(id, data);
  },

  /** Lấy danh sách lý do hủy mà platform đang hỗ trợ. */
  getCancelReasons: async (id) => {
    return await orderApi.getCancelReasons(id);
  },

  /** Yêu cầu platform tạo URL phiếu vận chuyển chính thức. */
  createShippingLabel: async (id) => {
    return await orderApi.createShippingLabel(id);
  },

  /** Lấy lịch sử thay đổi của order. */
  getHistory: async (id, page, size) => {
    return await orderApi.getHistory(id, page, size);
  },

  /** Cập nhật trạng thái thanh toán theo quyền và validation backend. */
  updatePaymentStatus: async (id, paymentStatus) => {
    return await orderApi.updatePaymentStatus(id, paymentStatus);
  },

  /** Lấy số liệu tổng hợp cho danh sách order. */
  getStats: async () => {
    return await orderApi.getStats();
  },

  /** Đếm order chưa được liên kết customer nội bộ. */
  getUncustomerdCount: async () => {
    return await orderApi.getUncustomerdCount();
  },
};

export default orderService;
