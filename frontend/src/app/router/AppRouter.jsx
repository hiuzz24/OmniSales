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
import StockReceivePage from '../../features/inventory/pages/StockReceivePage';
import StockReceiveCreatePage from '../../features/inventory/pages/StockReceiveCreatePage';
import StockReceiveDetailPage from '../../features/inventory/pages/StockReceiveDetailPage';
import StockReceiveEditPage from '../../features/inventory/pages/StockReceiveEditPage';

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
        </Route>

        <Route element={<PrivateRoute />}>
          <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
            <Route path={ROUTES.ADMIN} element={<AdminPage />} />
          </Route>

          {/* All regular user pages use MainLayout (sidebar + topbar) */}
          <Route element={<MainLayout />}>
            <Route element={<RoleRoute allowedRoles={[ROLES.OPERATIONS, ROLES.SALES, ROLES.OWNER]} />}>
              <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.OPERATIONS]} />}>
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPTS} element={<StockReceivePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE} element={<StockReceiveCreatePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_EDIT} element={<StockReceiveEditPage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_DETAIL} element={<StockReceiveDetailPage />} />
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
