import axiosClient from './axiosClient';
import './interceptors';

const authApi = {
  /** Gửi thông tin đăng nhập và nhận dữ liệu phiên đăng nhập. */
  login: async (credentials) => {
    const res = await axiosClient.post('/auth/login', credentials);
    return res.data?.data ?? res.data;
  },

  /** Kết thúc phiên đăng nhập hiện tại ở backend. */
  logout: async () => {
    const res = await axiosClient.post('/auth/logout');
    return res.data?.data ?? res.data;
  },

  /** Dùng refresh token trong cookie để cấp lại access token. */
  refreshToken: async () => {
    const res = await axiosClient.post('/auth/refresh');
    return res.data?.data ?? res.data;
  },

  /** Yêu cầu backend gửi email đặt lại mật khẩu. */
  forgotPassword: async (email) => {
    const res = await axiosClient.post('/auth/forgot-password', { email });
    return res.data?.data ?? res.data;
  },

  /** Đặt mật khẩu mới bằng reset token nhận từ email. */
  resetPassword: async (token, newPassword) => {
    const res = await axiosClient.post('/auth/reset-password', { token, newPassword });
    return res.data?.data ?? res.data;
  },

  /** Kiểm tra reset token trước khi hiển thị form đổi mật khẩu. */
  validateResetToken: async (token) => {
    const data = await axiosClient.get('/auth/change-password/validate', {
      params: { token },
    });
    return data;
  },

  /** Đổi mật khẩu bằng token của luồng quên mật khẩu. */
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

  /** Đổi mật khẩu bắt buộc ngay sau lần đăng nhập đầu tiên. */
  changePasswordAfterLogin: async (oldPassword, newPassword, confirmPassword) => {
    const res = await axiosClient.post('/auth/changes-password-after-login', {
      oldPassword,
      newPassword,
      confirmPassword,
    });
    return res.data?.data ?? res.data;
  },

  /** Gửi lời mời tạo tài khoản với vai trò đã chọn. */
  inviteUser: async (email, roleName) => {
    const res = await axiosClient.post('/auth/invite-user', { email, roleName });
    return res.data;
  },

  /** Kiểm tra invite token trước khi cho người dùng đăng ký. */
  validateInviteToken: async (token) => {
    const res = await axiosClient.get('/auth/accept-invite/validate', {
      params: { token },
    });
    return res.data?.data ?? res.data;
  },

  /** Hoàn tất đăng ký tài khoản từ lời mời. */
  acceptInvite: async (data) => {
    const res = await axiosClient.post('/auth/accept-invite', data);
    return res.data;
  },
};

export default authApi;
