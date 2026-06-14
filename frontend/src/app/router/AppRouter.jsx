import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ROUTES } from './routes';
import PrivateRoute from './PrivateRoute';
import PublicRoute from './PublicRoute';
import RoleRoute from './RoleRoute';
import { ROLES } from '../../features/auth/constants/roles';
import LoginPage from '../../features/auth/pages/LoginPage';
import ForgotPassword from '../../features/auth/pages/ForgotPasswordPage';
import ChangePasswordPage from '../../features/auth/pages/ChangePasswordPage';
import HomePage from '../../features/auth/pages/HomePage';
import AboutPage from '../../features/auth/pages/AboutPage';
import AdminPage from '../../features/system/pages/AdminPage';
import DashboardPage from '../../features/dashboard/pages/DashboardPage';
import EmptyLayout from '../layouts/EmptyLayout';

const AppRouter = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<PublicRoute />}>
          <Route element={<EmptyLayout />}>
            <Route path={ROUTES.HOME} element={<HomePage />} />
            <Route path={ROUTES.ABOUT} element={<AboutPage />} />
          </Route>
          <Route path={ROUTES.LOGIN} element={<LoginPage />} />
          <Route path={ROUTES.FORGOT_PASSWORD} element={<ForgotPassword />} />
          <Route path={ROUTES.CHANGE_PASSWORD} element={<ChangePasswordPage />} />
          <Route path={ROUTES.RESET_PASSWORD} element={<ChangePasswordPage />} />
        </Route>

        <Route element={<PrivateRoute />}>
          <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
            <Route path={ROUTES.ADMIN} element={<AdminPage />} />
          </Route>

          <Route element={<RoleRoute allowedRoles={[ROLES.OPERATIONS, ROLES.SALES, ROLES.OWNER]} />}>
            <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
          </Route>
        </Route>

        <Route path="/" element={<Navigate to={ROUTES.HOME} replace />} />
        <Route path="*" element={<Navigate to={ROUTES.HOME} replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default AppRouter;
