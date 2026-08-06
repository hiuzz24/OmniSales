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
import SystemLogPage from '../../features/system/pages/SystemLogPage';
import BackupPage from '../../features/system/pages/BackupPage';
import ApiMonitorPage from '../../features/system/pages/ApiMonitorPage';
import SystemSettingsPage from '../../features/system/pages/SystemSettingsPage';
import DashboardPage from '../../features/dashboard/pages/DashboardPage';
import ProfilePage from '../../features/user/pages/ProfilePage';
import UserListPage from '../../features/user/pages/UserListPage';
import UserInviteListPage from '../../features/user/pages/UserInviteListPage';
import UserDetailPage from '../../features/user/pages/UserDetailPage';
import InviteRegisterPage from '../../features/auth/pages/InviteRegisterPage';
import ProductManagementPage from '../../features/catalog/pages/ProductManagementPage';
import ProductCreatePage from '../../features/catalog/pages/ProductCreatePage';
import ProductEditPage from '../../features/catalog/pages/ProductEditPage';
import ProductDetailPage from '../../features/catalog/pages/ProductDetailPage';
import ProductLogPage from '../../features/catalog/pages/ProductLogPage';
import CategoryPage from '../../features/catalog/pages/CategoryPage';
import CustomerListPage from '../../features/customer/pages/CustomerListPage';
import CustomerDetailPage from '../../features/customer/pages/CustomerDetailPage';
import CustomerCreatePage from '../../features/customer/pages/CustomerCreatePage';
import CustomerEditPage from '../../features/customer/pages/CustomerEditPage';
import InventoryPage from '../../features/inventory/pages/inventory/InventoryPage';
import InventoryDetailPage from '../../features/inventory/pages/inventory/InventoryDetailPage';
import InventoryLogPage from '../../features/inventory/pages/inventory/InventoryLogPage';
import InventoryIssuePage from '../../features/inventory/pages/InventoryIssuePage';
import OrderListPage from '../../features/order/pages/OrderListPage';
import OrderDetailPage from '../../features/order/pages/OrderDetailPage';
import OrderLogPage from '../../features/order/pages/OrderLogPage';
import OrderReturnListPage from '../../features/orderreturn/pages/OrderReturnListPage';
import OrderReturnDetailPage from '../../features/orderreturn/pages/OrderReturnDetailPage';
import EmptyLayout from '../layouts/EmptyLayout';
import MainLayout from '../layouts/MainLayout';
import StockReceivePage from '../../features/inventory/pages/stockreceive/StockReceivePage';
import StockReceiveCreatePage from '../../features/inventory/pages/stockreceive/StockReceiveCreatePage';
import StockReceiveManualCreatePage from '../../features/inventory/pages/stockreceive/StockReceiveManualCreatePage';
import StockReceiveDetailPage from '../../features/inventory/pages/stockreceive/StockReceiveDetailPage';
import StockReceiveEditPage from '../../features/inventory/pages/stockreceive/StockReceiveEditPage';
import StockDeliveryPage from '../../features/inventory/pages/stockdelivery/StockDeliveryPage';
import StockDeliveryCreatePage from '../../features/inventory/pages/stockdelivery/StockDeliveryCreatePage';
import StockDeliveryDetailPage from '../../features/inventory/pages/stockdelivery/StockDeliveryDetailPage';
import ChannelConnectionPage from '../../features/channel/pages/ChannelConnectionPage';
import ChannelConnectionHistoryPage from '../../features/channel/pages/ChannelConnectionHistoryPage';
import StockDeliveryEditPage from '../../features/inventory/pages/stockdelivery/StockDeliveryEditPage';
import StocktakePage from '../../features/inventory/pages/stocktake/StocktakePage';
import StocktakeCreatePage from '../../features/inventory/pages/stocktake/StocktakeCreatePage';
import StocktakeDetailPage from '../../features/inventory/pages/stocktake/StocktakeDetailPage';
import SyncHistoryPage from '../../features/sync/pages/SyncHistoryPage';
import SupplierPage from '../../features/inventory/pages/supplier/SupplierPage.jsx';
import WarehousePage from '../../features/inventory/pages/WarehousePage';
import WarehouseDetailPage from '../../features/inventory/pages/WarehouseDetailPage';

import ForceChangePasswordPage from '../../features/auth/pages/ForceChangePasswordPage';
import NotificationListPage from '../../features/user/pages/NotificationListPage';
import PurchaseOrderPage from '../../features/purchase/PurchaseOrderPage';
import PurchaseOrderCreatePage from '../../features/purchase/PurchaseOrderCreatePage';
import PurchaseOrderDetailPage from '../../features/purchase/PurchaseOrderDetailPage';

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
          <Route path={ROUTES.INVITE_USER} element={<InviteRegisterPage />} />
        </Route>

        <Route element={<PrivateRoute />}>
          <Route path={ROUTES.FORCE_CHANGE_PASSWORD} element={<ForceChangePasswordPage />} />

          <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
            <Route path={ROUTES.ADMIN} element={<AdminPage />} />
          </Route>

          {/* All regular user pages use MainLayout (sidebar + topbar) */}
          <Route element={<MainLayout />}>

            <Route path={ROUTES.PROFILE} element={<ProfilePage />} />

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.SYSTEM_ADMIN]} />}>
              <Route path={ROUTES.USERS} element={<UserListPage />} />
              <Route path={ROUTES.USER_INVITATIONS} element={<UserInviteListPage />} />
              <Route path={ROUTES.USER_DETAIL} element={<UserDetailPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.SYSTEM_ADMIN]} />}>
              <Route path={ROUTES.SYSTEM_LOGS} element={<SystemLogPage />} />
              <Route path={ROUTES.BACKUP} element={<BackupPage />} />
              <Route path={ROUTES.API_MONITOR} element={<ApiMonitorPage />} />
              <Route path={ROUTES.SYSTEM_SETTINGS} element={<SystemSettingsPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.OPERATIONS]} />}>
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPTS} element={<StockReceivePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE} element={<StockReceiveCreatePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE_MANUAL} element={<StockReceiveManualCreatePage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_EDIT} element={<StockReceiveEditPage />} />
              <Route path={ROUTES.WAREHOUSE_IMPORT_RECEIPT_DETAIL} element={<StockReceiveDetailPage />} />
              <Route path={ROUTES.STOCK_DELIVERIES} element={<StockDeliveryPage />} />
              <Route path={ROUTES.STOCK_DELIVERY_CREATE} element={<StockDeliveryCreatePage />} />
              <Route path={ROUTES.STOCK_DELIVERY_EDIT} element={<StockDeliveryEditPage />} />
              <Route path={ROUTES.STOCK_DELIVERY_DETAIL} element={<StockDeliveryDetailPage />} />
              <Route path={ROUTES.STOCKTAKES} element={<StocktakePage />} />
              <Route path={ROUTES.STOCKTAKE_CREATE} element={<StocktakeCreatePage />} />
              <Route path={ROUTES.STOCKTAKE_DETAIL} element={<StocktakeDetailPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.SALES]} />}>
              <Route path={ROUTES.PURCHASE_ORDER_CREATE} element={<PurchaseOrderCreatePage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.SALES, ROLES.OPERATIONS]} />}>
              <Route path={ROUTES.PURCHASE_ORDERS} element={<PurchaseOrderPage />} />
              <Route path={ROUTES.PURCHASE_ORDER_DETAIL} element={<PurchaseOrderDetailPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OWNER, ROLES.OPERATIONS, ROLES.SYSTEM_ADMIN]} />}>
              <Route path={ROUTES.WAREHOUSE} element={<WarehousePage />} />
              <Route path={ROUTES.WAREHOUSE_DETAIL} element={<WarehouseDetailPage />} />
            </Route>

            <Route element={<RoleRoute allowedRoles={[ROLES.OPERATIONS, ROLES.SALES, ROLES.OWNER, ROLES.SYSTEM_ADMIN]} />}>
              <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
              <Route path={ROUTES.PRODUCT_LOGS} element={<ProductLogPage />} />
              <Route path={ROUTES.PRODUCT_CREATE} element={<ProductCreatePage />} />
              <Route path={ROUTES.PRODUCT_EDIT} element={<ProductEditPage />} />
              <Route path={ROUTES.PRODUCT_DETAIL} element={<ProductDetailPage />} />
              <Route path={ROUTES.PRODUCTS} element={<ProductManagementPage />} />
              <Route path={ROUTES.CATEGORIES} element={<CategoryPage />} />
              <Route path={ROUTES.CHANNELS} element={<ChannelConnectionPage />} />
              <Route path={ROUTES.CHANNEL_CONNECTION_HISTORY} element={<ChannelConnectionHistoryPage />} />
              <Route path={ROUTES.SYNC_HISTORY} element={<SyncHistoryPage />} />
              <Route path={ROUTES.CUSTOMER_LIST} element={<CustomerListPage />} />
              <Route path={ROUTES.CUSTOMER_CREATE} element={<CustomerCreatePage />} />
              <Route path={ROUTES.CUSTOMER_EDIT} element={<CustomerEditPage />} />
              <Route path={ROUTES.CUSTOMER_DETAIL} element={<CustomerDetailPage />} />

              {/* Inventory */}
              <Route path={ROUTES.INVENTORY} element={<InventoryPage />} />
              <Route path={ROUTES.INVENTORY_DETAIL} element={<InventoryDetailPage />} />
              <Route path={ROUTES.INVENTORY_LOGS} element={<InventoryLogPage />} />
              <Route path={ROUTES.SUPPLIERS} element={<SupplierPage />} />
              <Route path={ROUTES.STOCKTAKE} element={<StocktakePage />} />
              <Route path={ROUTES.ORDER_LIST} element={<OrderListPage />} />
              <Route path={ROUTES.ORDER_DETAIL} element={<OrderDetailPage />} />
              <Route path={ROUTES.ORDER_LOGS} element={<OrderLogPage />} />
              <Route path={ROUTES.ORDER_RETURNS} element={<OrderReturnListPage />} />
              <Route path={ROUTES.ORDER_RETURN_DETAIL} element={<OrderReturnDetailPage />} />
              <Route path={ROUTES.NOTIFICATIONS} element={<NotificationListPage />} />
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
