import { Navigate, Outlet } from 'react-router-dom';
import useAuth from '../../features/auth/hooks/useAuth';
import { ROUTES } from './routes';

const PrivateRoute = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) return null;

  return user ? <Outlet /> : <Navigate to={ROUTES.LOGIN} replace />;
};

export default PrivateRoute;
