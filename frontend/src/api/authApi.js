import axiosClient from './axiosClient';
import './interceptors';

const authApi = {
  login: async (credentials) => {
    const data = await axiosClient.post('/auth/login', credentials);
    return data;
  },

  logout: async () => {
    const data = await axiosClient.post('/auth/logout');
    return data;
  },

  refreshToken: async () => {
    const data = await axiosClient.post('/auth/refresh');
    return data;
  },

  forgotPassword: async (email) => {
    const data = await axiosClient.post('/auth/forgot-password', { email });
    return data;
  },

  resetPassword: async (token, newPassword) => {
    const data = await axiosClient.post('/auth/reset-password', { token, newPassword });
    return data;
  },

  validateResetToken: async (token) => {
    const data = await axiosClient.get('/auth/change-password/validate', {
      params: { token },
    });
    return data;
  },

  changePassword: async (token, password, confirmPassword) => {
    const data = await axiosClient.post(
      '/auth/change-password',
      {
        token,
        password,
        confirmPassword
      }
    );
    return data;
  },
};

export default authApi;
