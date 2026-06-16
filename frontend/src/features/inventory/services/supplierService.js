import axiosClient from '../../../api/axiosClient';
import '../../../api/interceptors';

const supplierService = {
  getAll: () => axiosClient.get('/suppliers'),
};

export default supplierService;
