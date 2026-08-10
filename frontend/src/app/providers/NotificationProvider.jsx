import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import notificationApi from '../../api/notificationApi';
import useAuth from '../../features/auth/hooks/useAuth';
import NotificationContext from './NotificationContext';

const POLL_INTERVAL_MS = 10000;

const NotificationProvider = ({ children }) => {
  const { user } = useAuth();
  const [recentNotifications, setRecentNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const knownIdsRef = useRef(new Set());
  const baselineReadyRef = useRef(false);

  const refresh = useCallback(async ({ announce = true } = {}) => {
    if (!user?.id) {
      setRecentNotifications([]);
      setUnreadCount(0);
      return { notifications: [], unreadCount: 0 };
    }
    setLoading(true);
    try {
      const [page, count] = await Promise.all([
        notificationApi.getNotifications({ page: 0, size: 10 }),
        notificationApi.countUnread(),
      ]);
      const next = page?.content ?? [];
      const nextCount = Number(count ?? 0);
      const newNotifications = baselineReadyRef.current
        ? next.filter((item) => item.id && !knownIdsRef.current.has(item.id))
        : [];

      next.forEach((item) => item.id && knownIdsRef.current.add(item.id));
      baselineReadyRef.current = true;
      setRecentNotifications(next);
      setUnreadCount(nextCount);

      if (announce && newNotifications.length > 0 && !document.hidden) {
        window.dispatchEvent(new CustomEvent('notifications:new', { detail: newNotifications }));
      }
      window.dispatchEvent(new CustomEvent('notifications:updated', {
        detail: { notifications: next, unreadCount: nextCount },
      }));
      return { notifications: next, unreadCount: nextCount };
    } catch (error) {
      console.error('Không thể tải thông báo:', error);
      return { notifications: [], unreadCount: 0 };
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  const markAsRead = useCallback(async (id) => {
    await notificationApi.markAsRead(id);
    setRecentNotifications((items) => items.map((item) =>
      item.id === id ? { ...item, readAt: item.readAt || new Date().toISOString() } : item));
    setUnreadCount((count) => Math.max(0, count - 1));
    await refresh({ announce: false });
  }, [refresh]);

  const markAllAsRead = useCallback(async () => {
    await notificationApi.markAllAsRead();
    setRecentNotifications((items) => items.map((item) => ({
      ...item, readAt: item.readAt || new Date().toISOString(),
    })));
    setUnreadCount(0);
    await refresh({ announce: false });
  }, [refresh]);

  useEffect(() => {
    knownIdsRef.current = new Set();
    baselineReadyRef.current = false;
    const timeout = window.setTimeout(() => refresh({ announce: false }), 0);
    return () => window.clearTimeout(timeout);
  }, [user?.id, refresh]);

  useEffect(() => {
    if (!user?.id) return undefined;
    const interval = window.setInterval(() => refresh(), POLL_INTERVAL_MS);
    const onVisible = () => !document.hidden && refresh();
    const onRefresh = () => refresh({ announce: false });
    document.addEventListener('visibilitychange', onVisible);
    window.addEventListener('notifications:refresh', onRefresh);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('notifications:refresh', onRefresh);
    };
  }, [user?.id, refresh]);

  const value = useMemo(() => ({
    recentNotifications,
    unreadCount,
    loading,
    refresh,
    markAsRead,
    markAllAsRead,
  }), [recentNotifications, unreadCount, loading, refresh, markAsRead, markAllAsRead]);

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
};

export default NotificationProvider;
