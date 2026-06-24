import axiosClient from './axiosClient';
import './interceptors';

const userApi = {
  /**
   * GET /api/users/me  — current user profile
   */
  getMyProfile: async () => {
    const res = await axiosClient.get('/users/me');
    return res.data?.data ?? res.data;
  },

  /**
   * PUT /api/users/me  — update current user profile
   */
  updateMyProfile: async (data) => {
    const res = await axiosClient.put('/users/me', data);
    return res.data?.data ?? res.data;
  },

  /**
   * GET /api/users/{id}  — get user by ID
   */
  getProfileById: async (id) => {
    const res = await axiosClient.get(`/users/${id}`);
    return res.data?.data ?? res.data;
  },

  /**
   * POST /api/users/me/change-password  — change current user password
   */
  changeMyPassword: async (data) => {
    const res = await axiosClient.post('/users/me/change-password', data);
    return res.data;
  },

  getAllUsers: async (page = 0, size = 100) => {
    const res = await axiosClient.get('/users', { params: { page, size } });
    return res.data?.data ?? res.data;
  },
};

export default userApi;
