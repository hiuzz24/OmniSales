import authApi from '../../../api/authApi';
import { setAccessToken, clearAccessToken } from '../../../api/interceptors';

const USER_KEY = 'osms_user';

const authService = {
  login: async ({ email, password }) => {
    const data = await authApi.login({ email, password });
    setAccessToken(data.accessToken);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    return data;
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      clearAccessToken();
      localStorage.removeItem(USER_KEY);
    }
  },

  getUserFromStorage: () => {
    try {
      const raw = localStorage.getItem(USER_KEY);
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

  register: async ({ fullName, email, password, phone, city, address, shopName, confirmPassword }) => {
    const payload = {
      fullName,
      email,
      password,
      confirmPassword: confirmPassword ?? password,
      phone,
      shopName,
      address,
      city,
    };
    return authApi.register(payload);
  },


  resendVerificationEmail: async (email) => {
    return authApi.resendVerificationEmail(email);
  },

  verifyEmail: async (token) => {
    return authApi.verifyEmail(token);
  },


  changePassword: async (currentPassword, newPassword) => {
    const data = await authApi.changePassword(currentPassword, newPassword);
    return data;
  },
};


export default authService;
