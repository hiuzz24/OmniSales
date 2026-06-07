import { Navigate, Outlet } from 'react-router-dom';
import useAuth from '../../features/auth/hooks/useAuth';
import { getRoleHome } from '../../features/auth/constants/roles';

const PublicRoute = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) return null;

  return user ? <Navigate to={getRoleHome(user.role)} replace /> : <Outlet />;
};

export default PublicRoute;
