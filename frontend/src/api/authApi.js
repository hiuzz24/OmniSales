import axiosClient from './axiosClient';
import './interceptors';

const authApi = {
  login: async (credentials) => {
    const res = await axiosClient.post('/auth/login', credentials);
    return res.data?.data ?? res.data;
  },

  logout: async () => {
    const res = await axiosClient.post('/auth/logout');
    return res.data?.data ?? res.data;
  },

  refreshToken: async () => {
    const res = await axiosClient.post('/auth/refresh');
    return res.data?.data ?? res.data;
  },

  forgotPassword: async (email) => {
    const res = await axiosClient.post('/auth/forgot-password', { email });
    return res.data?.data ?? res.data;
  },

  resetPassword: async (token, newPassword) => {
    const res = await axiosClient.post('/auth/reset-password', { token, newPassword });
    return res.data?.data ?? res.data;
  },

  changePassword: async (currentPassword, newPassword) => {
    const res = await axiosClient.post('/auth/change-password', { currentPassword, newPassword });
    return res.data?.data ?? res.data;
  },
};

export default authApi;
