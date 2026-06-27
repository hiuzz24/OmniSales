import axiosClient from './axiosClient';

const channelConnectionLogApi = {
  getAll: async (params) => {
    const response = await axiosClient.get('/channel-connection-logs', { params });
    return response;
  },
};

export default channelConnectionLogApi;
