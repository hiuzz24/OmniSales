import axiosClient from '../../../api/axiosClient';
import '../../../api/interceptors';

const warehouseService = {
  getWarehouses: async (params) => {
    const response = await axiosClient.get('/warehouses', { params });
    return response;
  },
  getAll: () => axiosClient.get('/warehouses'),
  getWarehouseById: (id) => axiosClient.get(`/warehouses/${id}`),
  createWarehouse: (data) => axiosClient.post('/warehouses', data),
  updateWarehouse: (id, data) => axiosClient.put(`/warehouses/${id}`, data),
  toggleWarehouseStatus: (id, isActive) => axiosClient.patch(`/warehouses/${id}/status`, { isActive }),
  getMaster: () => axiosClient.get('/warehouses/master'),
  getUserWarehouse: (userId) => axiosClient.get(`/warehouses/userWarehouse/${userId}`),
};

export default warehouseService;

