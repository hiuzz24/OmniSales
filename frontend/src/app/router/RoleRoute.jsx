import { Navigate, Outlet } from 'react-router-dom';
import useAuth from '../../features/auth/hooks/useAuth';
import { getRoleHome } from '../../features/auth/constants/roles';

const RoleRoute = ({ allowedRoles }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) return null;

  if (!user) return <Navigate to="/login" replace />;

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={getRoleHome(user.role)} replace />;
  }

  return <Outlet />;
};

export default RoleRoute;
