import axiosClient from '../../../api/axiosClient';
import '../../../api/interceptors';

const warehouseService = {
  getAll: () => axiosClient.get('/warehouses'),
};

export default warehouseService;
