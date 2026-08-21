import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  Check,
  AlertTriangle,
  ShoppingCart,
  RefreshCw,
  Package,
  ArrowRightLeft,
  ClipboardList,
  Info,
  Inbox
} from 'lucide-react';
import useAuth from '../../auth/hooks/useAuth';
import notificationApi from '../../../api/notificationApi';
import { getDefaultPageSize } from '../../../shared/utils/systemPreferences';
import { ROUTES } from '../../../app/router/routes';
import Pagination from '../../../shared/components/Pagination';
import styles from './NotificationListPage.module.css';

const NOTIF_META = {
  ALERT: { icon: AlertTriangle, color: '#dc2626', bg: '#fef2f2' },
  LOW_STOCK: { icon: AlertTriangle, color: '#dc2626', bg: '#fef2f2' },
  ORDER: { icon: ShoppingCart, color: '#2563eb', bg: '#eff6ff' },
  ORDER_NEW: { icon: ShoppingCart, color: '#2563eb', bg: '#eff6ff' },
  ORDER_CANCELLED: { icon: ShoppingCart, color: '#dc2626', bg: '#fef2f2' },
  ORDER_PICK_REQUIRED: { icon: Package, color: '#d97706', bg: '#fffbeb' },
  ORDER_READY_SHIP: { icon: ShoppingCart, color: '#0f766e', bg: '#f0fdfa' },
  ORDER_SHIPPED: { icon: ShoppingCart, color: '#0284c7', bg: '#f0f9ff' },
  ORDER_DELIVERED: { icon: ShoppingCart, color: '#059669', bg: '#ecfdf5' },
  ORDER_RETURN_REQUESTED: { icon: RefreshCw, color: '#d97706', bg: '#fffbeb' },
  ORDER_RETURN_REJECTED: { icon: RefreshCw, color: '#dc2626', bg: '#fef2f2' },
  ORDER_RETURN_COMPLETED: { icon: RefreshCw, color: '#059669', bg: '#ecfdf5' },
  ORDER_RETURN_ATTENTION: { icon: AlertTriangle, color: '#dc2626', bg: '#fef2f2' },
  CHANNEL_DISCONNECTED: { icon: AlertTriangle, color: '#dc2626', bg: '#fef2f2' },
  SYNC: { icon: RefreshCw, color: '#059669', bg: '#ecfdf5' },
  SYNC_FAILED: { icon: RefreshCw, color: '#dc2626', bg: '#fef2f2' },
  INVENTORY: { icon: Package, color: '#d97706', bg: '#fffbeb' },
  STOCK_TRANSFER: { icon: ArrowRightLeft, color: '#7c3aed', bg: '#f5f3ff' },
  STOCKTAKE: { icon: ClipboardList, color: '#0f766e', bg: '#f0fdfa' },
  SYSTEM: { icon: Info, color: '#475569', bg: '#f8fafc' },
};

const formatNotificationTime = (value) => {
  if (!value) return '';
  const created = new Date(value);
  const diffMs = Date.now() - created.getTime();
  const minute = 60 * 1000;
  const hour = 60 * minute;
  if (diffMs < minute) return 'Vừa xong';
  if (diffMs < hour) return `${Math.floor(diffMs / minute)} phút trước`;
  if (diffMs < hour * 24) return `${Math.floor(diffMs / hour)} giờ trước`;
  return created.toLocaleDateString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  });
};

const NotificationListPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [activeTab, setActiveTab] = useState('all'); // 'all' or 'unread'
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  
  // Pagination state
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(getDefaultPageSize);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    const applyPageSize = () => setPageSize(getDefaultPageSize());
    window.addEventListener('system-preferences:loaded', applyPageSize);
    return () => window.removeEventListener('system-preferences:loaded', applyPageSize);
  }, []);
  const [totalElements, setTotalElements] = useState(0);

  const fetchUnreadCount = useCallback(async () => {
    if (!user?.id) return;
    try {
      const count = await notificationApi.countUnread();
      setUnreadCount(count);
    } catch (error) {
      console.error('Failed to fetch unread count:', error);
    }
  }, [user]);

  const loadNotifications = useCallback(async () => {
    if (!user?.id) return;
    setLoading(true);
    try {
      const data = await notificationApi.getNotifications({
        unreadOnly: activeTab === 'unread',
        page,
        size: pageSize,
      });
      setNotifications(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Failed to load notifications:', error);
    } finally {
      setLoading(false);
    }
  }, [user, activeTab, page, pageSize]);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      loadNotifications();
      fetchUnreadCount();
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [loadNotifications, fetchUnreadCount]);

  // Listener to refresh if any global changes happen
  useEffect(() => {
    const handleRefresh = () => {
      loadNotifications();
      fetchUnreadCount();
    };
    window.addEventListener('notifications:refresh', handleRefresh);
    return () => {
      window.removeEventListener('notifications:refresh', handleRefresh);
    };
  }, [loadNotifications, fetchUnreadCount]);

  const handleMarkAllAsRead = async () => {
    if (!user?.id || unreadCount === 0) return;
    try {
      await notificationApi.markAllAsRead();
      
      // Dispatch refresh events to let MainLayout know it should reload too
      window.dispatchEvent(new Event('notifications:refresh'));
      
      loadNotifications();
      fetchUnreadCount();
    } catch (error) {
      console.error('Failed to mark all notifications as read:', error);
    }
  };

  const handleNotificationClick = async (notif) => {
    const isUnread = !notif.readAt;
    
    if (isUnread) {
      try {
        await notificationApi.markAsRead(notif.id);
        
        // Notify other layouts/components to refresh their badge counts
        window.dispatchEvent(new Event('notifications:refresh'));
      } catch (error) {
        console.error('Failed to mark notification as read:', error);
      }
    }

    // Navigation logic based on notification parameters
    if (notif.type === 'ORDER_PICK_REQUIRED' && notif.entityId) {
      navigate(`${ROUTES.STOCK_DELIVERY_CREATE}?tab=BY_ORDER&orderId=${notif.entityId}`);
    } else if (notif.entityType === 'ORDER' && notif.entityId) {
      navigate(ROUTES.ORDER_DETAIL.replace(':id', notif.entityId));
    } else if (notif.entityType === 'RETURN' && notif.entityId) {
      navigate(ROUTES.ORDER_RETURN_DETAIL.replace(':id', notif.entityId));
    } else if (notif.entityType === 'CHANNEL') {
      navigate(ROUTES.CHANNELS);
    } else if (notif.entityType === 'INVENTORY' && notif.entityId) {
      if (notif.type === 'STOCK_TRANSFER') {
        navigate(ROUTES.STOCK_TRANSFER, { state: { openTransferId: notif.entityId } });
      } else if (notif.type === 'STOCKTAKE') {
        navigate(ROUTES.STOCKTAKES);
      } else {
        navigate(ROUTES.INVENTORY_DETAIL.replace(':id', notif.entityId));
      }
    } else if (notif.entityType === 'SYNC') {
      navigate(ROUTES.SYNC_HISTORY);
    }
  };

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setPage(0); // Reset page to first page when changing tabs
  };

  return (
    <div className={styles.page}>
      {/* Header section */}
      <div className={styles.pageHeader}>
        <div className={styles.pageTitleContainer}>
          <div className={styles.titleIcon}>
            <Bell size={22} />
          </div>
          <div className={styles.titleText}>
            <h1>Thông báo hệ thống</h1>
            <p>Theo dõi và quản lý tất cả các hoạt động, sự kiện trong cửa hàng của bạn</p>
          </div>
        </div>

        <button
          onClick={handleMarkAllAsRead}
          disabled={unreadCount === 0}
          className={styles.markAllBtn}
        >
          <Check size={16} />
          <span>Đánh dấu đọc tất cả</span>
        </button>
      </div>

      {/* Main card panel */}
      <div className={styles.card}>
        {/* Navigation tabs */}
        <div className={styles.tabsContainer}>
          <button
            onClick={() => handleTabChange('all')}
            className={`${styles.tab} ${activeTab === 'all' ? styles.activeTab : ''}`}
          >
            Tất cả thông báo
          </button>
          <button
            onClick={() => handleTabChange('unread')}
            className={`${styles.tab} ${activeTab === 'unread' ? styles.activeTab : ''}`}
          >
            Thông báo chưa đọc
            {unreadCount > 0 && (
              <span className={styles.badge}>{unreadCount}</span>
            )}
          </button>
        </div>

        {/* Notification list content */}
        <div className={styles.list}>
          {loading ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>
                <RefreshCw size={24} className="animate-spin" />
              </div>
              <h3>Đang tải dữ liệu...</h3>
              <p>Vui lòng đợi giây lát trong khi chúng tôi lấy thông báo của bạn.</p>
            </div>
          ) : notifications.length === 0 ? (
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>
                <Inbox size={28} />
              </div>
              <h3>Không có thông báo nào</h3>
              <p>Bạn không có thông báo nào {activeTab === 'unread' ? 'chưa đọc' : ''} tại thời điểm này.</p>
            </div>
          ) : (
            notifications.map((n, index) => {
              const meta = NOTIF_META[n.type] ?? NOTIF_META.SYSTEM;
              const NIcon = meta.icon;
              const isUnread = !n.readAt;

              return (
                <div
                  key={n.id || index}
                  onClick={() => handleNotificationClick(n)}
                  className={`${styles.item} ${isUnread ? styles.unreadItem : ''}`}
                >
                  <div
                    className={styles.iconWrapper}
                    style={{ backgroundColor: meta.bg, color: meta.color }}
                  >
                    <NIcon size={20} />
                  </div>

                  <div className={styles.textContainer}>
                    <div className={styles.itemTitleRow}>
                      {isUnread && <span className={styles.unreadDot} />}
                      <span className={styles.itemTitle}>{n.title}</span>
                    </div>
                    <p className={styles.itemBody}>{n.body}</p>
                    <span className={styles.itemTime}>{formatNotificationTime(n.createdAt)}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Footer pagination */}
        {!loading && totalElements > 0 && (
          <div className={styles.paginationContainer}>
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={pageSize}
              onPageChange={(nextPage) => setPage(nextPage)}
              itemLabel="thông báo"
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default NotificationListPage;
