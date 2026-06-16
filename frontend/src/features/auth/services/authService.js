import authApi from '../../../api/authApi';
import { setAccessToken, clearAccessToken } from '../../../api/interceptors';

const USER_KEY = 'osms_user';

const authService = {
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

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      clearAccessToken();
      localStorage.removeItem(USER_KEY);
      sessionStorage.removeItem(USER_KEY);
    }
  },

  getUserFromStorage: () => {
    try {
      const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },

  forgotPassword: async (email) => {
    const data = await authApi.forgotPassword(email);
    return data;
  },

  resetPassword: async (token, newPassword) => {
    const data = await authApi.resetPassword(token, newPassword);
    return data;
  },

  validateResetToken: async (token) => {
    const data = await authApi.validateResetToken(token);
    return data;
  },

  changePassword: async (token, password, confirmPassword) => {
    const data = await authApi.changePassword(
      token,
      password,
      confirmPassword
    );
    return data;
  },
};

export default authService;
