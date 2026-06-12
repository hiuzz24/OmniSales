import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ROUTES } from './routes';
import PrivateRoute from './PrivateRoute';
import PublicRoute from './PublicRoute';
import RoleRoute from './RoleRoute';
import { ROLES } from '../../features/auth/constants/roles';
import LoginPage from '../../features/auth/pages/LoginPage';
import HomePage from '../../features/auth/pages/HomePage';
import AboutPage from '../../features/auth/pages/AboutPage';
import AdminPage from '../../features/system/pages/AdminPage';
import DashboardPage from '../../features/dashboard/pages/DashboardPage';
import EmptyLayout from '../layouts/EmptyLayout';
import MainLayout from '../layouts/MainLayout';
import InventoryReceiptPage from '../../features/inventory/pages/InventoryReceiptPage';
import InventoryReceiptCreatePage from '../../features/inventory/pages/InventoryReceiptCreatePage';

const AppRouter = () => {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route element={<PublicRoute />}>
          <Route element={<EmptyLayout />}>
            <Route path={ROUTES.HOME} element={<HomePage />} />
            <Route path={ROUTES.ABOUT} element={<AboutPage />} />
          </Route>
          <Route path={ROUTES.LOGIN} element={<LoginPage />} />
        </Route>

        {/* Private routes */}
        <Route element={<PrivateRoute />}>
          {/* Admin — no sidebar */}
          <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
            <Route path={ROUTES.ADMIN} element={<AdminPage />} />
          </Route>

          {/* All regular pages — wrapped in MainLayout (sidebar + topbar) */}
          <Route element={<MainLayout />}>
            <Route element={<RoleRoute allowedRoles={[ROLES.OPERATIONS, ROLES.SALES, ROLES.OWNER]} />}>
              <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.OPERATIONS]} />}>
              <Route path={ROUTES.WAREHOUSE_RECEIPTS} element={<InventoryReceiptPage />} />
              <Route path={ROUTES.WAREHOUSE_RECEIPT_CREATE} element={<InventoryReceiptCreatePage />} />
            </Route>
          </Route>
        </Route>

        <Route path="/" element={<Navigate to={ROUTES.HOME} replace />} />
        <Route path="*" element={<Navigate to={ROUTES.HOME} replace />} />
      </Routes>
    </BrowserRouter>
  );
};

export default AppRouter;
