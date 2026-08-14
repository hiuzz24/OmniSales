import authApi from '../../../api/authApi';
import { setAccessToken, clearAccessToken } from '../../../api/interceptors';

const USER_KEY = 'osms_user';

const authService = {
  /** Đăng nhập và lưu token/user vào đúng storage theo lựa chọn ghi nhớ. */
  login: async ({ email, password, rememberMe = true }) => {
    const data = await authApi.login({ email, password });
    setAccessToken(data.accessToken, rememberMe);
    if (rememberMe) {
      localStorage.setItem(USER_KEY, JSON.stringify(data.user));
      sessionStorage.removeItem(USER_KEY);
    } else {
      sessionStorage.setItem(USER_KEY, JSON.stringify(data.user));
      localStorage.removeItem(USER_KEY);
    }
    return data;
  },

  /** Đăng xuất và luôn xóa sạch dữ liệu phiên ở trình duyệt. */
  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      clearAccessToken();
      localStorage.removeItem(USER_KEY);
      sessionStorage.removeItem(USER_KEY);
    }
  },

  /** Khôi phục thông tin user đã lưu khi tải lại ứng dụng. */
  getUserFromStorage: () => {
    try {
      const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },

  /** Chuyển yêu cầu quên mật khẩu xuống API Auth. */
  forgotPassword: async (email) => {
    const data = await authApi.forgotPassword(email);
    return data;
  },

  /** Chuyển yêu cầu đặt lại mật khẩu xuống API Auth. */
  resetPassword: async (token, newPassword) => {
    const data = await authApi.resetPassword(token, newPassword);
    return data;
  },

  /** Xác thực reset token với backend. */
  validateResetToken: async (token) => {
    const data = await authApi.validateResetToken(token);
    return data;
  },

  /** Đổi mật khẩu bằng reset token. */
  changePassword: async (token, password, confirmPassword) => {
    const data = await authApi.changePassword(
      token,
      password,
      confirmPassword
    );
    return data;
  },

  /** Đổi mật khẩu bắt buộc của user vừa đăng nhập. */
  changePasswordAfterLogin: async (oldPassword, newPassword, confirmPassword) => {
    const data = await authApi.changePasswordAfterLogin(oldPassword, newPassword, confirmPassword);
    return data;
  },

  /** Mời một user mới theo email và vai trò. */
  inviteUser: async (email, roleName) => {
    const data = await authApi.inviteUser(email, roleName);
    return data;
  },

  /** Kiểm tra token của lời mời đăng ký. */
  validateInviteToken: async (token) => {
    const data = await authApi.validateInviteToken(token);
    return data;
  },

  /** Hoàn tất việc chấp nhận lời mời. */
  acceptInvite: async (data) => {
    const res = await authApi.acceptInvite(data);
    return res;
  },
};

export default authService;
