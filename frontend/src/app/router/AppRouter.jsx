import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ROUTES } from './routes';
import PrivateRoute from './PrivateRoute';
import PublicRoute from './PublicRoute';
import RoleRoute from './RoleRoute';
import { ROLES } from '../../features/auth/constants/roles';
import LoginPage from '../../features/auth/pages/LoginPage';
import AdminPage from '../../features/system/pages/AdminPage';
import DashboardPage from '../../features/dashboard/pages/DashboardPage';

const AppRouter = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<PublicRoute />}>
          <Route path={ROUTES.LOGIN} element={<LoginPage />} />
        </Route>

        <Route element={<PrivateRoute />}>
          <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
            <Route path={ROUTES.ADMIN} element={<AdminPage />} />
          </Route>

          <Route element={<RoleRoute allowedRoles={[ROLES.OPERATIONS, ROLES.SALES, ROLES.OWNER]} />}>
            <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
          </Route>
        </Route>

        <Route path="/" element={<Navigate to={ROUTES.LOGIN} replace />} />
        <Route path="*" element={<Navigate to={ROUTES.LOGIN} replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default AppRouter;
