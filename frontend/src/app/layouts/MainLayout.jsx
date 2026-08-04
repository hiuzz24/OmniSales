import { useState, useEffect, useRef } from 'react';
import { Outlet, NavLink, Link, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, Package, Warehouse, ShoppingCart, Share2,
  Settings, Menu, Bell, Users, ChevronDown,
  PackagePlus, PackageMinus, ArrowRightLeft, ClipboardList,
  Store, LogOut, Shield, AlertTriangle, RefreshCw, Info,
  ChevronRight, User, Tag, Database, ShoppingBag, RotateCcw,
} from 'lucide-react';
import { ROUTES } from '../router/routes';
import { ROLES } from '../../features/auth/constants/roles';
import useAuth from '../../features/auth/hooks/useAuth';
import notificationApi from '../../api/notificationApi';
import { toast } from 'react-toastify';

// ── Role-based nav config ─────────────────────────────────────────────────────
const NAV_ITEMS = [
  { name: 'Dashboard', href: ROUTES.DASHBOARD, icon: LayoutDashboard, roles: [] },
  {
    name: 'Sản phẩm',
    href: '/products',
    icon: Package,
    roles: [],
    children: [
      { name: 'Danh sách sản phẩm', href: ROUTES.PRODUCTS, icon: Package, exact: true, roles: [] },
      { name: 'Danh mục sản phẩm', href: ROUTES.CATEGORIES, icon: Tag, roles: [] },
    ],
  },
  {
    name: 'Kho hàng',
    href: '/warehouse',
    icon: Warehouse,
    roles: [],
    children: [
      { name: 'Tổng quan kho', href: '/inventory', icon: Warehouse, exact: true, roles: [ROLES.OWNER, ROLES.OPERATIONS, ROLES.SALES] },
      { name: 'Phiếu nhập kho', href: ROUTES.WAREHOUSE_IMPORT_RECEIPTS, icon: PackagePlus, roles: [ROLES.OWNER, ROLES.OPERATIONS] },
      { name: 'Phiếu xuất kho', href: ROUTES.STOCK_DELIVERIES, icon: PackageMinus, roles: [ROLES.OWNER, ROLES.OPERATIONS] },
      { name: 'Phiếu kiểm kho', href: '/warehouse/stocktakes', icon: ClipboardList, roles: [ROLES.OWNER, ROLES.OPERATIONS] },
      { name: 'Nhà cung cấp', href: ROUTES.SUPPLIERS, icon: Users, roles: [ROLES.OWNER, ROLES.OPERATIONS, ROLES.SALES] },
      { name: 'Kho hàng', href: ROUTES.WAREHOUSE, icon: Store, roles: [ROLES.OWNER, ROLES.OPERATIONS, ROLES.SYSTEM_ADMIN] },
      { name: 'Lịch sử thay đổi', href: ROUTES.INVENTORY_LOGS, icon: RefreshCw, roles: [ROLES.OWNER, ROLES.OPERATIONS, ROLES.SALES] },
    ],
  },
  { name: 'Đơn mua hàng', href: ROUTES.PURCHASE_ORDERS, icon: ShoppingBag, roles: [ROLES.OWNER, ROLES.OPERATIONS, ROLES.SALES] },
  { name: 'Khách hàng',     href: ROUTES.CUSTOMER_LIST, icon: Users,        roles: [] },
  { name: 'Đơn hàng',       href: '/orders',   icon: ShoppingCart, roles: [] },
  { name: 'Trả hàng', href: ROUTES.ORDER_RETURNS, icon: RotateCcw, roles: [ROLES.OWNER, ROLES.SALES, ROLES.OPERATIONS] },
  { name: 'Kênh bán hàng',  href: ROUTES.CHANNELS, icon: Share2,   roles: [] },
  {
    name: 'Nhân sự',
    href: '/users',
    icon: Users,
    roles: [ROLES.OWNER, ROLES.SYSTEM_ADMIN],
    children: [
      { name: 'Danh sách nhân sự', href: ROUTES.USERS, icon: Users, exact: true, roles: [ROLES.OWNER, ROLES.SYSTEM_ADMIN] },
      { name: 'Lịch sử lời mời', href: ROUTES.USER_INVITATIONS, icon: ClipboardList, roles: [ROLES.OWNER, ROLES.SYSTEM_ADMIN] },
    ],
  },
  { name: 'Logs hệ thống',  href: ROUTES.SYSTEM_LOGS, icon: ClipboardList, roles: [ROLES.SYSTEM_ADMIN] },
  { name: 'Sao lưu dữ liệu', href: ROUTES.BACKUP,      icon: Database,      roles: [ROLES.SYSTEM_ADMIN] },
  { name: 'Giám sát API',    href: ROUTES.API_MONITOR, icon: Shield,        roles: [ROLES.SYSTEM_ADMIN] },
  { name: 'Cấu hình hệ thống', href: ROUTES.SYSTEM_SETTINGS, icon: Settings,  roles: [ROLES.SYSTEM_ADMIN] },
];

const ROLE_HIDDEN = {
  [ROLES.SALES]: ['Sản phẩm', 'Kênh bán hàng', 'Nhân sự'],
  [ROLES.OPERATIONS]: ['Nhân sự'],
  [ROLES.OWNER]: [],
  [ROLES.SYSTEM_ADMIN]: ['Sản phẩm', 'Kho hàng', 'Khách hàng', 'Đơn hàng', 'Kênh bán hàng'],
};

const isVisible = (item, role) => {
  if (!role) return false;
  if (item.roles && item.roles.length > 0) return item.roles.includes(role);
  return !(ROLE_HIDDEN[role] ?? []).includes(item.name);
};

const NOTIF_META = {
  ALERT: { icon: AlertTriangle, color: '#dc2626', bg: '#fef2f2' },
  LOW_STOCK: { icon: AlertTriangle, color: '#dc2626', bg: '#fef2f2' },
  ORDER: { icon: ShoppingCart, color: '#2563eb', bg: '#eff6ff' },
  ORDER_NEW: { icon: ShoppingCart, color: '#2563eb', bg: '#eff6ff' },
  ORDER_CANCELLED: { icon: ShoppingCart, color: '#dc2626', bg: '#fef2f2' },
  ORDER_PICK_REQUIRED: { icon: PackageMinus, color: '#d97706', bg: '#fffbeb' },
  ORDER_READY_SHIP: { icon: ShoppingCart, color: '#0f766e', bg: '#f0fdfa' },
  SYNC: { icon: RefreshCw, color: '#059669', bg: '#ecfdf5' },
  SYNC_FAILED: { icon: RefreshCw, color: '#dc2626', bg: '#fef2f2' },
  INVENTORY: { icon: Package, color: '#d97706', bg: '#fffbeb' },
  STOCK_TRANSFER: { icon: ArrowRightLeft, color: '#7c3aed', bg: '#f5f3ff' },
  STOCKTAKE: { icon: ClipboardList, color: '#0f766e', bg: '#f0fdfa' },
  SYSTEM: { icon: Info, color: '#475569', bg: '#f8fafc' },
};

const ROLE_LABEL = {
  [ROLES.OWNER]: 'Owner', [ROLES.OPERATIONS]: 'Operations',
  [ROLES.SALES]: 'Sales', [ROLES.SYSTEM_ADMIN]: 'Admin',
};

const getInitials = (name) => {
  if (!name) return '?';
  const p = name.trim().split(' ');
  return p.length === 1 ? p[0][0].toUpperCase() : (p[0][0] + p[p.length - 1][0]).toUpperCase();
};

const formatNotificationTime = (value) => {
  if (!value) return '';
  const created = new Date(value);
  const diffMs = Date.now() - created.getTime();
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diffMs < minute) return 'Vừa xong';
  if (diffMs < hour) return `${Math.floor(diffMs / minute)} phút trước`;
  if (diffMs < day) return `${Math.floor(diffMs / hour)} giờ trước`;
  return created.toLocaleDateString('vi-VN');
};

const pathMatches = (pathname, href, exact = false) =>
  exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);

const notificationKey = (notification, index) => [
  notification?.id,
  notification?.type,
  notification?.entityType,
  notification?.entityId,
  notification?.createdAt,
  index,
].filter((part) => part != null && part !== '').join('-');

// ── Sidebar widths ────────────────────────────────────────────────────────────
const SIDEBAR_OPEN = 256; // px — 16rem / w-64
const SIDEBAR_CLOSE = 80;  // px — 5rem  / w-20

export default function MainLayout() {
  const [open, setOpen] = useState(true);
  const [expanded, setExpanded] = useState([]);
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const notifRef = useRef(null);
  const seenNotificationIdsRef = useRef(new Set());
  const notificationBaselineReadyRef = useRef(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const role = user?.role;
  const displayName = user?.fullName || user?.full_name || user?.name || user?.email || 'User';
  const sidebarW = open ? SIDEBAR_OPEN : SIDEBAR_CLOSE;

  // Close notif on outside click
  useEffect(() => {
    const h = (e) => { if (notifRef.current && !notifRef.current.contains(e.target)) setNotifOpen(false); };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  // Auto-expand active parent
  useEffect(() => {
    NAV_ITEMS.forEach((item) => {
      if (item.children?.some((c) => pathMatches(location.pathname, c.href, c.exact))) {
        setExpanded((prev) => prev.includes(item.name) ? prev : [...prev, item.name]);
      }
    });
  }, [location.pathname]);

  const isActive = (href, exact = false) => {
    if (href === ROUTES.DASHBOARD) return location.pathname === ROUTES.DASHBOARD;
    if (href === '/inventory') return location.pathname === href;
    return pathMatches(location.pathname, href, exact);
  };

  const navigateFromNotification = (notification) => {
    if (notification.type === 'ORDER_PICK_REQUIRED' && notification.entityId) {
      navigate(`${ROUTES.STOCK_DELIVERY_CREATE}?tab=BY_ORDER&orderId=${notification.entityId}`);
    } else if (notification.entityType === 'ORDER' && notification.entityId) {
      navigate(ROUTES.ORDER_DETAIL.replace(':id', notification.entityId));
    } else if (notification.entityType === 'INVENTORY' && notification.entityId) {
      if (notification.type === 'STOCK_TRANSFER') {
        navigate(ROUTES.STOCK_TRANSFER, { state: { openTransferId: notification.entityId } });
      } else if (notification.type === 'STOCKTAKE') {
        navigate(ROUTES.STOCKTAKES);
      } else {
        navigate(ROUTES.INVENTORY_DETAIL.replace(':id', notification.entityId));
      }
    } else if (notification.entityType === 'PURCHASE') {
      navigate(ROUTES.PURCHASE_ORDERS);
    } else if (notification.entityType === 'RECEIPT' && notification.entityId) {
      navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_DETAIL.replace(':id', notification.entityId));
    } else if (notification.entityType === 'SYNC') {
      navigate(ROUTES.SYNC_HISTORY);
    }
    setNotifOpen(false);
  };

  const handleNotificationClick = async (notification) => {
    if (!notification.readAt) {
      await notificationApi.markAsRead(notification.id);
      await loadNotifications();
    }
    navigateFromNotification(notification);
  };

  const loadNotifications = async () => {
    if (!user?.id) {
      setNotifications([]);
      setUnreadCount(0);
      return;
    }
    try {
      const [list, count] = await Promise.all([
        notificationApi.getNotifications({ userId: user.id, page: 0, size: 10 }),
        notificationApi.countUnread(user.id),
      ]);
      const loadedNotifications = list?.content ?? [];
      setNotifications(loadedNotifications);
      setUnreadCount(Number(count ?? 0));

      if (!notificationBaselineReadyRef.current) {
        loadedNotifications.forEach((notification) => {
          if (notification.id) seenNotificationIdsRef.current.add(notification.id);
        });
        notificationBaselineReadyRef.current = true;
      } else {
        loadedNotifications.forEach((notification) => {
          if (!notification.id || seenNotificationIdsRef.current.has(notification.id)) return;
          const isWorkflowNotification = [
            'ORDER_READY_SHIP',
          ].includes(notification.type);
          if (!isWorkflowNotification || notification.readAt) {
            seenNotificationIdsRef.current.add(notification.id);
            return;
          }
          if (document.hidden) return;

          seenNotificationIdsRef.current.add(notification.id);
          const actionLabel = 'Mở chi tiết đơn hàng';
          toast.info(
            <div style={{ display: 'grid', gap: 5, cursor: 'pointer' }}>
              <strong style={{ color: '#0f172a' }}>{notification.title}</strong>
              {notification.body && (
                <div style={{ color: '#475569', lineHeight: 1.4 }}>{notification.body}</div>
              )}
              <div style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 3,
                color: '#2563eb',
                fontSize: 12,
                fontWeight: 700,
              }}>
                {actionLabel}
                <ChevronRight size={13} />
              </div>
            </div>,
            {
              autoClose: 10000,
              closeOnClick: true,
              style: { cursor: 'pointer' },
              onClick: () => handleNotificationClick(notification),
            },
          );
        });
      }
    } catch (err) {
      console.error('Lỗi tải thông báo:', err);
      setNotifications([]);
      setUnreadCount(0);
    }
  };

  useEffect(() => {
    seenNotificationIdsRef.current = new Set();
    notificationBaselineReadyRef.current = false;
    loadNotifications();
    const interval = setInterval(() => {
      loadNotifications();
    }, 10000);
    return () => clearInterval(interval);
  }, [user?.id]);

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (!document.hidden) {
        loadNotifications();
      }
    };
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
  }, [user?.id]);

  useEffect(() => {
    window.addEventListener('notifications:refresh', loadNotifications);
    return () => window.removeEventListener('notifications:refresh', loadNotifications);
  }, [user?.id]);

  const toggleMenu = (name) =>
    setExpanded((prev) => prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]);

  const handleLogout = async () => { await logout(); navigate(ROUTES.LOGIN); };

  const visibleItems = NAV_ITEMS.filter((item) => isVisible(item, role));

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: '#f8fafc' }}>

      {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <aside style={{
        position: 'fixed', left: 0, top: 0, zIndex: 40,
        height: '100vh', width: sidebarW,
        backgroundColor: '#ffffff',
        borderRight: '1px solid #e2e8f0',
        transition: 'width 0.3s ease',
        display: 'flex', flexDirection: 'column',
        overflow: 'hidden',
      }}>
        {/* Logo */}
        <div style={{
          height: 64, display: 'flex', alignItems: 'center',
          justifyContent: open ? 'space-between' : 'center',
          padding: open ? '0 16px' : '0',
          borderBottom: '1px solid #e2e8f0', flexShrink: 0,
        }}>
          {open && <span style={{ fontWeight: 700, fontSize: 20, color: '#0f172a' }}>OmniSales</span>}
          <button
            onClick={() => setOpen((v) => !v)}
            style={{
              padding: 6, borderRadius: 8, border: 'none', background: 'none',
              cursor: 'pointer', color: '#64748b', display: 'flex', alignItems: 'center',
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f1f5f9'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <Menu size={20} />
          </button>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: '12px', overflowY: 'auto', overflowX: 'hidden' }}>
          {visibleItems.map((item) => {
            const Icon = item.icon;
            const hasChildren = !!item.children?.length;
            const parentActive = item.children?.some((c) => isActive(c.href, c.exact));
            const isExp = expanded.includes(item.name);

            return (
              <div key={`${item.href}-${item.name}`} style={{ marginBottom: 2 }}>
                {hasChildren ? (
                  <button
                    onClick={() => toggleMenu(item.name)}
                    style={{
                      width: '100%', display: 'flex', alignItems: 'center',
                      gap: 12, padding: '10px 12px', borderRadius: 8,
                      border: 'none', cursor: 'pointer', textAlign: 'left',
                      fontSize: 14, fontWeight: 500,
                      backgroundColor: parentActive ? '#eff6ff' : 'transparent',
                      color: parentActive ? '#2563eb' : '#475569',
                      justifyContent: open ? 'flex-start' : 'center',
                    }}
                    onMouseEnter={(e) => { if (!parentActive) e.currentTarget.style.backgroundColor = '#f1f5f9'; }}
                    onMouseLeave={(e) => { if (!parentActive) e.currentTarget.style.backgroundColor = 'transparent'; }}
                  >
                    <Icon size={20} style={{ flexShrink: 0 }} />
                    {open && (
                      <>
                        <span style={{ flex: 1 }}>{item.name}</span>
                        <ChevronDown size={16} style={{ transform: isExp ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }} />
                      </>
                    )}
                  </button>
                ) : (
                  <NavLink
                    to={item.href}
                    style={({ isActive: na }) => ({
                      display: 'flex', alignItems: 'center',
                      gap: 12, padding: '10px 12px', borderRadius: 8,
                      textDecoration: 'none', fontSize: 14, fontWeight: 500,
                      backgroundColor: (na || isActive(item.href)) ? '#eff6ff' : 'transparent',
                      color: (na || isActive(item.href)) ? '#2563eb' : '#475569',
                      justifyContent: open ? 'flex-start' : 'center',
                    })}
                    onMouseEnter={(e) => { if (!isActive(item.href)) e.currentTarget.style.backgroundColor = '#f1f5f9'; }}
                    onMouseLeave={(e) => { if (!isActive(item.href)) e.currentTarget.style.backgroundColor = isActive(item.href) ? '#eff6ff' : 'transparent'; }}
                  >
                    <Icon size={20} style={{ flexShrink: 0 }} />
                    {open && <span>{item.name}</span>}
                  </NavLink>
                )}

                {/* Children */}
                {hasChildren && open && isExp && (
                  <div style={{ marginLeft: 16, paddingLeft: 16, borderLeft: '1px solid #e2e8f0', marginTop: 4 }}>
                    {item.children.filter((child) => isVisible(child, role)).map((child) => {
                      const CIcon = child.icon;
                      const active = isActive(child.href, child.exact);
                      return (
                        <NavLink
                          key={`${item.href}-${child.href}`}
                          to={child.href}
                          end={child.exact}
                          style={() => ({
                            display: 'flex', alignItems: 'center', gap: 10,
                            padding: '8px 12px', borderRadius: 8,
                            textDecoration: 'none', fontSize: 13,
                            fontWeight: active ? 600 : 400,
                            backgroundColor: active ? '#eff6ff' : 'transparent',
                            color: active ? '#2563eb' : '#64748b',
                            marginBottom: 2,
                          })}
                          onMouseEnter={(e) => { if (!active) e.currentTarget.style.backgroundColor = '#f1f5f9'; }}
                          onMouseLeave={(e) => { if (!active) e.currentTarget.style.backgroundColor = 'transparent'; }}
                        >
                          <CIcon size={16} style={{ flexShrink: 0 }} />
                          <span>{child.name}</span>
                        </NavLink>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>
      </aside>

      {/* ── Main ─────────────────────────────────────────────────────────── */}
      <div style={{ marginLeft: sidebarW, flex: 1, display: 'flex', flexDirection: 'column', transition: 'margin-left 0.3s ease', minWidth: 0 }}>

        {/* Header */}
        <header style={{
          position: 'sticky', top: 0, zIndex: 30, height: 64,
          backgroundColor: '#ffffff', borderBottom: '1px solid #e2e8f0',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '0 24px', flexShrink: 0,
        }}>
          <span style={{ fontWeight: 600, fontSize: 14, color: '#0f172a' }}>
            Hệ thống quản lý bán hàng đa kênh
          </span>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {/* Bell */}
            <div ref={notifRef} style={{ position: 'relative' }}>
              <button
                onClick={() => {
                  setNotifOpen((v) => !v);
                  loadNotifications();
                }}
                style={{
                  position: 'relative', width: 36, height: 36,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  borderRadius: 8, border: 'none', background: 'none', cursor: 'pointer', color: '#475569',
                }}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f1f5f9'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                <Bell size={20} />
                {unreadCount > 0 && (
                  <span style={{
                    position: 'absolute', top: 2, right: 2,
                    width: 16, height: 16, borderRadius: '50%',
                    backgroundColor: '#ef4444', color: '#fff',
                    fontSize: 10, fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>{unreadCount}</span>
                )}
              </button>

              {notifOpen && (
                <div style={{
                  position: 'absolute', right: 0, top: 44,
                  width: 384, backgroundColor: '#fff',
                  border: '1px solid #e2e8f0', borderRadius: 16,
                  boxShadow: '0 20px 40px rgba(0,0,0,0.1)', zIndex: 50, overflow: 'hidden',
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: '1px solid #f1f5f9' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Bell size={16} color="#475569" />
                      <span style={{ fontWeight: 600, fontSize: 14, color: '#0f172a' }}>Thông báo</span>
                      {unreadCount > 0 && (
                        <span style={{ backgroundColor: '#ef4444', color: '#fff', fontSize: 11, fontWeight: 700, borderRadius: 999, padding: '1px 6px' }}>{unreadCount}</span>
                      )}
                    </div>
                    {unreadCount > 0 && (
                      <button onClick={async () => {
                        await notificationApi.markAllAsRead(user.id);
                        await loadNotifications();
                      }}
                        style={{ fontSize: 12, color: '#2563eb', fontWeight: 500, border: 'none', background: 'none', cursor: 'pointer' }}>
                        Đánh dấu đã đọc
                      </button>
                    )}
                  </div>

                  <div style={{ maxHeight: 320, overflowY: 'auto' }}>
                    {notifications.length === 0 && (
                      <div style={{ padding: '28px 20px', textAlign: 'center', color: '#94a3b8', fontSize: 13 }}>
                        Chưa có thông báo mới
                      </div>
                    )}
                    {notifications.map((n, index) => {
                      const meta = NOTIF_META[n.type] ?? NOTIF_META.SYSTEM;
                      const NIcon = meta.icon;
                      const isUnread = !n.readAt;
                      return (
                        <div key={notificationKey(n, index)}
                          onClick={() => handleNotificationClick(n)}
                          style={{
                            display: 'flex', gap: 12, padding: '14px 20px', cursor: 'pointer',
                            backgroundColor: isUnread ? 'rgba(239,246,255,0.5)' : 'transparent',
                            borderBottom: '1px solid #f1f5f9',
                          }}
                          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = isUnread ? '#eff6ff' : '#f8fafc'}
                          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = isUnread ? 'rgba(239,246,255,0.5)' : 'transparent'}
                        >
                          <div style={{ width: 36, height: 36, borderRadius: 12, backgroundColor: meta.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                            <NIcon size={16} color={meta.color} />
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
                              {isUnread && <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#3b82f6', flexShrink: 0 }} />}
                              <span style={{ fontSize: 13, fontWeight: isUnread ? 600 : 400, color: isUnread ? '#0f172a' : '#475569', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.title}</span>
                            </div>
                            <p style={{ fontSize: 12, color: '#94a3b8', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', margin: 0 }}>{n.body}</p>
                            <p style={{ fontSize: 10, color: '#cbd5e1', marginTop: 4, margin: '4px 0 0' }}>{formatNotificationTime(n.createdAt)}</p>
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  <div style={{ padding: '12px 20px', borderTop: '1px solid #f1f5f9', backgroundColor: 'rgba(248,250,252,0.5)' }}>
                    <button onClick={() => { setNotifOpen(false); navigate(ROUTES.NOTIFICATIONS); }}
                      style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, width: '100%', fontSize: 13, color: '#2563eb', fontWeight: 500, border: 'none', background: 'none', cursor: 'pointer' }}>
                      Xem tất cả thông báo <ChevronRight size={14} />
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* User dropdown */}
            <UserDropdown user={user} role={role} onLogout={handleLogout} />
          </div>
        </header>

        {/* Content */}
        <main style={{ flex: 1, padding: 24, overflowY: 'auto' }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

// ── UserDropdown ──────────────────────────────────────────────────────────────
function UserDropdown({ user, role, onLogout }) {
  const [show, setShow] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setShow(false); };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const displayName = user?.fullName || user?.full_name || user?.name || user?.email || 'User';

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setShow((v) => !v)}
        style={{
          display: 'flex', alignItems: 'center', gap: 8,
          padding: '6px 10px 6px 6px', borderRadius: 8,
          border: 'none', background: 'none', cursor: 'pointer',
        }}
        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f1f5f9'}
        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
      >
        <div style={{
          width: 28, height: 28, borderRadius: '50%',
          backgroundColor: '#2563eb', color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 11, fontWeight: 700, flexShrink: 0,
        }}>
          {getInitials(displayName)}
        </div>
        <div style={{ textAlign: 'left' }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a', lineHeight: 1.3, maxWidth: 100, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {displayName}
          </div>
          <div style={{ fontSize: 11, color: '#64748b' }}>
            {({ [ROLES.OWNER]: 'Owner', [ROLES.OPERATIONS]: 'Operations', [ROLES.SALES]: 'Sales', [ROLES.SYSTEM_ADMIN]: 'Admin' })[role] ?? role}
          </div>
        </div>
      </button>

      {show && (
        <div style={{
          position: 'absolute', right: 0, top: '100%', marginTop: 4,
          width: 200, backgroundColor: '#fff', border: '1px solid #e2e8f0',
          borderRadius: 12, boxShadow: '0 8px 24px rgba(0,0,0,0.08)',
          zIndex: 50, overflow: 'hidden',
        }}>
          <div style={{ padding: '10px 16px 10px', borderBottom: '1px solid #f1f5f9' }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{displayName}</div>
            <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>{user?.email}</div>
          </div>

          {[
            { label: 'Hồ sơ', icon: User,     href: ROUTES.PROFILE },
            { label: 'Cài đặt', icon: Settings, href: '/settings' },
          ].map((m) => (
            <Link key={m.label} to={m.href} onClick={() => setShow(false)}
              style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 16px', fontSize: 13, color: '#374151', textDecoration: 'none' }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <m.icon size={15} /> {m.label}
            </Link>
          ))}

          {role === ROLES.SYSTEM_ADMIN && (
            <Link to="/admin" onClick={() => setShow(false)}
              style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 16px', fontSize: 13, color: '#374151', textDecoration: 'none', borderTop: '1px solid #f1f5f9' }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Shield size={15} /> Admin Panel
            </Link>
          )}

          <div style={{ borderTop: '1px solid #f1f5f9' }}>
            <button onClick={onLogout}
              style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '9px 16px', fontSize: 13, color: '#dc2626', border: 'none', background: 'none', cursor: 'pointer', width: '100%', textAlign: 'left' }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#fef2f2'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <LogOut size={15} /> Đăng xuất
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
