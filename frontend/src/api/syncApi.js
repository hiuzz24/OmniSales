import axiosClient from './axiosClient';

const syncApi = {
  getLogs: (params) => {
    return axiosClient.get('/sync-logs', { params });
  },
};

export default syncApi;

