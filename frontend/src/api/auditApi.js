import axiosClient from './axiosClient';
import './interceptors';

const unwrap = (res) => res.data?.data ?? res.data;

const auditApi = {
  getLogs: async (params) => {
    const res = await axiosClient.get('/system-logs', { params });
    return unwrap(res);
  },

  updateLog: async (id, data) => {
    const res = await axiosClient.put(`/system-logs/${id}`, data);
    return unwrap(res);
  },

  deleteLog: async (id) => {
    const res = await axiosClient.delete(`/system-logs/${id}`);
    return unwrap(res);
  },
};

export default auditApi;
