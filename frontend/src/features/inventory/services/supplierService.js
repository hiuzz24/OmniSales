import axiosClient from '../../../api/axiosClient';

const supplierService = {
  // Lấy danh sách nhà cung cấp (hỗ trợ phân trang từ backend nếu có)
  getSuppliers: async (params) => {
    // Nếu backend chưa phân trang, params này có thể dùng để filter ở backend sau này
    const response = await axiosClient.get('/suppliers', { params });
    return response;
  },

  getAll: async (params) => {
    const response = await axiosClient.get('/suppliers', { params });
    return response;
  },

  // Lấy chi tiết một nhà cung cấp
  getSupplierById: async (id) => {
    const response = await axiosClient.get(`/suppliers/${id}`);
    return response;
  },

  // Tạo mới nhà cung cấp
  createSupplier: async (data) => {
    const response = await axiosClient.post('/suppliers', data);
    return response;
  },

  updateSupplier: async (id, data) => {
    const response = await axiosClient.put(`/suppliers/${id}`, data);
    return response;
  },

  // Xóa mềm
  softDeleteSupplier: async (id) => {
    const response = await axiosClient.patch(`/suppliers/${id}/status`, {
      isActive: false
    });
    return response;
  }
};

export default supplierService;
