import axiosClient from './axiosClient';
import './interceptors';

const unwrap = (res) => res.data?.data ?? res.data;

const monitorApi = {
  getSummary: async () => {
    const res = await axiosClient.get('/admin/monitor/summary');
    return unwrap(res);
  },

  getTraffic: async (range = 'today') => {
    const res = await axiosClient.get('/admin/monitor/traffic', { params: { range } });
    return unwrap(res);
  },

  getEndpoints: async () => {
    const res = await axiosClient.get('/admin/monitor/endpoints');
    return unwrap(res);
  }
};

export default monitorApi;
