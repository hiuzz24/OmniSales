import axiosClient from './axiosClient';
import './interceptors';

const unwrap = (res) => res.data?.data ?? res.data;

const backupApi = {
  getBackups: async ({ page = 0, size = 20 } = {}) => {
    const res = await axiosClient.get('/backups', { params: { page, size } });
    return unwrap(res);
  },

  createBackup: async () => {
    const res = await axiosClient.post('/backups');
    return unwrap(res);
  },

  deleteBackup: async (id) => {
    const res = await axiosClient.delete(`/backups/${id}`);
    return unwrap(res);
  },

  restoreBackup: async (id, password) => {
    const res = await axiosClient.post(`/backups/${id}/restore`, { password });
    return unwrap(res);
  },

  downloadBackup: async (id) => {
    const res = await axiosClient.get(`/backups/${id}/download`, {
      responseType: 'blob'
    });
    return res;
  }
};

export default backupApi;
