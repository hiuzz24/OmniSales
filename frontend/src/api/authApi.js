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

  changePasswordAfterLogin: async (oldPassword, newPassword, confirmPassword) => {
    const res = await axiosClient.post('/auth/changes-password-after-login', {
      oldPassword,
      newPassword,
      confirmPassword,
    });
    return res.data?.data ?? res.data;
  },

  inviteUser: async (email, roleName) => {
    const res = await axiosClient.post('/auth/invite-user', { email, roleName });
    return res.data;
  },

  validateInviteToken: async (token) => {
    const res = await axiosClient.get('/auth/accept-invite/validate', {
      params: { token },
    });
    return res.data?.data ?? res.data;
  },

  acceptInvite: async (data) => {
    const res = await axiosClient.post('/auth/accept-invite', data);
    return res.data;
  },
};

export default authApi;
