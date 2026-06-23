import axiosClient from './axiosClient';
import './interceptors';

const unwrap = (res) => res.data?.data ?? res.data;

const notificationApi = {
  getNotifications: async ({ userId, unreadOnly = false, page = 0, size = 20 }) => {
    const res = await axiosClient.get('/notifications', {
      params: { userId, unreadOnly, page, size },
    });
    return unwrap(res);
  },

  countUnread: async (userId) => {
    const res = await axiosClient.get('/notifications/unread-count', {
      params: { userId },
    });
    return unwrap(res);
  },

  markAsRead: async (id) => {
    const res = await axiosClient.patch(`/notifications/${id}/read`);
    return unwrap(res);
  },

  markAllAsRead: async (userId) => {
    const res = await axiosClient.post('/notifications/mark-all-read', null, {
      params: { userId },
    });
    return unwrap(res);
  },
};

export default notificationApi;
