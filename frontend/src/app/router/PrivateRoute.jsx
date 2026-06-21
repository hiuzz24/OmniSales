import { Navigate, Outlet, useLocation } from 'react-router-dom';
import useAuth from '../../features/auth/hooks/useAuth';
import { ROUTES } from './routes';

const PrivateRoute = () => {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) return null;

  if (!user) return <Navigate to={ROUTES.LOGIN} replace />;

  if (user.passwordExpired && location.pathname !== ROUTES.FORCE_CHANGE_PASSWORD) {
    return <Navigate to={ROUTES.FORCE_CHANGE_PASSWORD} replace />;
  }

  if (!user.passwordExpired && location.pathname === ROUTES.FORCE_CHANGE_PASSWORD) {
    return <Navigate to={ROUTES.HOME} replace />;
  }

  return <Outlet />;
};

export default PrivateRoute;
