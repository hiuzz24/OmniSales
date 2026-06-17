import { createContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import { getAccessToken, clearAccessToken } from '../../../api/interceptors';
import { isTokenExpired } from '../../../shared/utils/tokenUtils';

const USER_KEY = 'osms_user';

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
  const token = getAccessToken();

  if (token) {
    setUser(authService.getUserFromStorage());
  }

  setIsLoading(false);
}, []);

  const isAuthenticated = !!user;

  const login = async (credentials) => {
    const data = await authService.login(credentials);
    setUser(data.user);
    return data;
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  /**
   * Update the in-memory and storage user object (called after profile update).
   */
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
