import { createContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import { getAccessToken, clearAccessToken } from '../../../api/interceptors';
import { isTokenExpired } from '../../../shared/utils/tokenUtils';

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = getAccessToken();

    if (token && !isTokenExpired(token)) {
      const storedUser = authService.getUserFromStorage();
      setUser(storedUser);
    } else {
      clearAccessToken();
      localStorage.removeItem('osms_user');
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

  return (
    <AuthContext.Provider value={{ user, isAuthenticated, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
