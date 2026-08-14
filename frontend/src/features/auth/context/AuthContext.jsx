import { createContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import { getAccessToken, clearAccessToken } from '../../../api/interceptors';

const USER_KEY = 'osms_user';

export const AuthContext = createContext(null);

/** Cung cấp trạng thái đăng nhập và các thao tác phiên cho toàn bộ ứng dụng. */
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // Khôi phục hồ sơ đăng nhập một lần; refresh token vẫn nằm trong cookie HTTP-only.
  useEffect(() => {
  const token = getAccessToken();

  if (token) {
    setUser(authService.getUserFromStorage());
  }

  setIsLoading(false);
}, []);

  const isAuthenticated = !!user;

  // Đăng nhập, lưu access token và cập nhật hồ sơ người dùng hiện tại.
  const login = async (credentials) => {
    const data = await authService.login(credentials);
    setUser(data.user);
    return data;
  };

  // Thu hồi phiên backend rồi xóa trạng thái đăng nhập phía trình duyệt.
  const logout = async () => {
    try {
      await authService.logout();
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      setUser(null);
    }
  };

  /**
   * Update the in-memory and storage user object (called after profile update).
   */
  // Cập nhật một phần hồ sơ đang lưu sau khi người dùng sửa thông tin cá nhân.
  const updateUser = (updatedFields) => {
    const updated = { ...user, ...updatedFields };
    setUser(updated);
    const storage = localStorage.getItem(USER_KEY) ? localStorage : sessionStorage;
    storage.setItem(USER_KEY, JSON.stringify(updated));
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
};
