import { createContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import { getAccessToken, clearAccessToken } from '../../../api/interceptors';

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
    try {
      await authService.logout();
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
