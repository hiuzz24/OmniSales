import reportApi from '../../../api/reportApi';

const reportService = {
  getOrdersByChannel: (filters) => reportApi.getOrdersByChannel(filters),
  getReturns: (filters) => reportApi.getReturns(filters),
  getProducts: (filters) => reportApi.getProducts(filters),
};

export default reportService;
