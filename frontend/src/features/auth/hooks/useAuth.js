import { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';

/** Trả về AuthContext và chặn việc dùng hook bên ngoài AuthProvider. */
const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default useAuth;
