import axiosClient from '../../../api/axiosClient';
import '../../../api/interceptors';

const warehouseService = {
  getAll: () => axiosClient.get('/warehouses'),
  getUserWarehouse: (userId) => axiosClient.get(`/warehouses/userWarehouse/${userId}`),
};

export default warehouseService;

