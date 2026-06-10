import axiosClient from './axiosClient';
import './interceptors';

const authApi = {
  login: async (credentials) => {
    const data = await axiosClient.post('/auth/login', credentials);
    console.log(data);

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

  getMe: async () => {
    const data = await axiosClient.get('/auth/me');
    return data;
  },

  register: async (payload) => {
    const data = await axiosClient.post('/auth/register', payload);
    return data;
  },

  resendVerificationEmail: async (email) => {
    const data = await axiosClient.post(
      '/auth/resend-verification-email',
      null,
      {
        params: { email },
      }
    );
    return data;
  },

  verifyEmail: async (token) => {
    const data = await axiosClient.get('/auth/verify-email', {
      params: { token },
    });
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

  changePassword: async (currentPassword, newPassword) => {
    const data = await axiosClient.post('/auth/change-password', { currentPassword, newPassword });
    return data;
  },
};


export default authApi;
