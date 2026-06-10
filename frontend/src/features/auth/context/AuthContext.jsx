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

  const login = async (credentials) => {
    const data = await authService.login(credentials);
    setUser(data.user);
    return data;
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  const register = async (payload) => {
    return authService.register(payload);
  };

  const resendVerificationEmail = async (email) => {
    return authService.resendVerificationEmail(email);
  };

  const [isLoadingVerifyEmail, setIsLoadingVerifyEmail] = useState(false);

  const verifyEmail = async (token) => {
    setIsLoadingVerifyEmail(true);
    try {
      return authService.verifyEmail(token);
    } finally {
      setIsLoadingVerifyEmail(false);
    }
  };


  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        login,
        logout,
        register,
        resendVerificationEmail,
        verifyEmail,
        isLoadingVerify: isLoadingVerifyEmail,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};


