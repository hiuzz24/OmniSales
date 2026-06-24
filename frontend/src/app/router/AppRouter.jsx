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
import ProfilePage from '../../features/user/pages/ProfilePage';
import ProductManagementPage from '../../features/catalog/pages/ProductManagementPage';
import ProductCreatePage from '../../features/catalog/pages/ProductCreatePage';
import ProductEditPage from '../../features/catalog/pages/ProductEditPage';
import ProductDetailPage from '../../features/catalog/pages/ProductDetailPage';
import ProductLogPage from '../../features/catalog/pages/ProductLogPage';
import CustomerListPage from '../../features/customer/pages/CustomerListPage';
import CustomerDetailPage from '../../features/customer/pages/CustomerDetailPage';
import CustomerCreatePage from '../../features/customer/pages/CustomerCreatePage';
import CustomerEditPage from '../../features/customer/pages/CustomerEditPage';
import InventoryPage from '../../features/inventory/pages/inventory/InventoryPage';
import InventoryDetailPage from '../../features/inventory/pages/inventory/InventoryDetailPage';
import InventoryLogPage from '../../features/inventory/pages/inventory/InventoryLogPage';
import InventoryIssuePage from '../../features/inventory/pages/InventoryIssuePage';
import StockTransferPage from '../../features/inventory/pages/StockTransferPage';
import StockTransferCreatePage from '../../features/inventory/pages/stocktransfer/StockTransferCreatePage';
import OrderListPage from '../../features/order/pages/OrderListPage';
import OrderDetailPage from '../../features/order/pages/OrderDetailPage';
import OrderLogPage from '../../features/order/pages/OrderLogPage';
import EmptyLayout from '../layouts/EmptyLayout';
import MainLayout from '../layouts/MainLayout';
import StockReceivePage from '../../features/inventory/pages/stockreceive/StockReceivePage';
import StockReceiveCreatePage from '../../features/inventory/pages/stockreceive/StockReceiveCreatePage';
import StockReceiveDetailPage from '../../features/inventory/pages/stockreceive/StockReceiveDetailPage';
import StockReceiveEditPage from '../../features/inventory/pages/stockreceive/StockReceiveEditPage';
import StockDeliveryPage from '../../features/inventory/pages/stockdelivery/StockDeliveryPage';
import StockDeliveryCreatePage from '../../features/inventory/pages/stockdelivery/StockDeliveryCreatePage';
import StockDeliveryDetailPage from '../../features/inventory/pages/stockdelivery/StockDeliveryDetailPage';
import StockDeliveryEditPage from '../../features/inventory/pages/stockdelivery/StockDeliveryEditPage';
import StocktakePage from '../../features/inventory/pages/stocktake/StocktakePage';
import StocktakeCreatePage from '../../features/inventory/pages/stocktake/StocktakeCreatePage';

import ForceChangePasswordPage from '../../features/auth/pages/ForceChangePasswordPage';

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
          <Route path={ROUTES.FORCE_CHANGE_PASSWORD} element={<ForceChangePasswordPage />} />

          <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
            <Route path={ROUTES.ADMIN} element={<AdminPage />} />
          </Route>

          {/* All regular user pages use MainLayout (sidebar + topbar) */}
          <Route element={<MainLayout />}>

            <Route path={ROUTES.PROFILE} element={<ProfilePage />} />

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.OPERATIONS]} />}>
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPTS} element={<StockReceivePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE} element={<StockReceiveCreatePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_EDIT} element={<StockReceiveEditPage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_DETAIL} element={<StockReceiveDetailPage />} />
              <Route path={ROUTES.STOCK_DELIVERIES} element={<StockDeliveryPage />} />
              <Route path={ROUTES.STOCK_DELIVERY_CREATE} element={<StockDeliveryCreatePage />} />
              <Route path={ROUTES.STOCK_DELIVERY_EDIT} element={<StockDeliveryEditPage />} />
              <Route path={ROUTES.STOCK_DELIVERY_DETAIL} element={<StockDeliveryDetailPage />} />
              <Route path={ROUTES.STOCKTAKES} element={<StocktakePage />} />
              <Route path={ROUTES.STOCKTAKE_CREATE} element={<StocktakeCreatePage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OPERATIONS, ROLES.SALES, ROLES.OWNER]} />}>
              <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
              <Route path={ROUTES.PRODUCT_LOGS} element={<ProductLogPage />} />
              <Route path={ROUTES.PRODUCT_CREATE} element={<ProductCreatePage />} />
              <Route path={ROUTES.PRODUCT_EDIT} element={<ProductEditPage />} />
              <Route path={ROUTES.PRODUCT_DETAIL} element={<ProductDetailPage />} />
              <Route path={ROUTES.PRODUCTS} element={<ProductManagementPage />} />
              <Route path={ROUTES.CUSTOMER_LIST} element={<CustomerListPage />} />
              <Route path={ROUTES.CUSTOMER_CREATE} element={<CustomerCreatePage />} />
              <Route path={ROUTES.CUSTOMER_EDIT} element={<CustomerEditPage />} />
              <Route path={ROUTES.CUSTOMER_DETAIL} element={<CustomerDetailPage />} />

              {/* Inventory */}
              <Route path={ROUTES.INVENTORY} element={<InventoryPage />} />
              <Route path={ROUTES.INVENTORY_DETAIL} element={<InventoryDetailPage />} />
              <Route path={ROUTES.INVENTORY_LOGS} element={<InventoryLogPage />} />
              {/* <Route path={ROUTES.INVENTORY_ISSUE} element={<InventoryIssuePage />} /> */}
              <Route path={ROUTES.STOCK_TRANSFER} element={<StockTransferPage />} />
              <Route path={ROUTES.STOCK_TRANSFER_CREATE} element={<StockTransferCreatePage />} />
              <Route path={ROUTES.STOCKTAKE} element={<StocktakePage />} />
              <Route path={ROUTES.ORDER_LIST} element={<OrderListPage />} />
              <Route path={ROUTES.ORDER_DETAIL} element={<OrderDetailPage />} />
              <Route path={ROUTES.ORDER_LOGS} element={<OrderLogPage />} />
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
