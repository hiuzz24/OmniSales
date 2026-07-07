import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  ChevronDown,
  CircleDollarSign,
  Layers,
  PackageSearch,
  RefreshCw,
  ShoppingCart,
  Store,
  TrendingUp,
  Warehouse,
  Users,
  Shield,
  Link,
} from 'lucide-react';
import PageHeader from '../../../shared/components/PageHeader';
import channelApi from '../../../api/channelApi';
import inventoryApi from '../../../api/inventoryApi';
import orderApi from '../../../api/orderApi';
import productApi from '../../../api/productApi';
import userApi from '../../../api/userApi';
import syncApi from '../../../api/syncApi';
import channelConnectionLogApi from '../../../api/channelConnectionLogApi';
import notificationApi from '../../../api/notificationApi';
import useAuth from '../../auth/hooks/useAuth';
import { ROLES } from '../../auth/constants/roles';
import styles from './DashboardPage.module.css';

const PAGE_FETCH_SIZE = 1000;
const CHART_COLORS = ['#2563eb', '#059669', '#f97316', '#7c3aed', '#dc2626', '#64748b'];

const STATUS_LABELS = {
  PENDING: 'Chờ xử lý',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};

const PERIOD_OPTIONS = [
  { value: '7', label: '7 ngày' },
  { value: '30', label: '30 ngày' },
  { value: '90', label: '90 ngày' },
  { value: 'custom', label: 'Tùy chọn' },
];

const GROUP_OPTIONS = [
  { value: 'day', label: 'Theo ngày' },
  { value: 'week', label: 'Theo tuần' },
  { value: 'month', label: 'Theo tháng' },
];

const INITIAL_DASHBOARD_DATA = {
  channels: [],
  orders: [],
  orderTotal: 0,
  inventory: [],
  inventoryTotal: 0,
  productsTotal: 0,
  lowStockItems: [],
};

const formatCurrency = (value) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value || 0));

const compactCurrency = (value) => {
  const amount = Number(value || 0);
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toFixed(1)} tỷ`;
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toFixed(1)} tr`;
  return formatCurrency(amount);
};

const formatDate = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const toDateInput = (date) => date.toISOString().slice(0, 10);

const getDefaultFromDate = (days) => {
  const date = new Date();
  date.setDate(date.getDate() - Number(days) + 1);
  return toDateInput(date);
};

const getOrderDate = (order) => new Date(order.createdAt || order.updatedAt || Date.now());

const getChannelName = (order) =>
  order.channelName || order.platform || order.channel?.name || 'Không xác định';

const normalizePage = (payload) => {
  const data = payload?.data?.data ?? payload?.data ?? payload;
  if (Array.isArray(data)) return { content: data, totalElements: data.length };
  if (Array.isArray(data?.content)) return data;
  if (Array.isArray(data?.data)) return { content: data.data, totalElements: data.total ?? data.data.length };
  return { content: [], totalElements: 0 };
};

const getPeriodKey = (date, groupBy) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  if (groupBy === 'month') return `${year}-${month}`;
  if (groupBy === 'week') {
    const firstDay = new Date(year, date.getMonth(), date.getDate());
    const weekday = firstDay.getDay() || 7;
    firstDay.setDate(firstDay.getDate() + 1 - weekday);
    return `${toDateInput(firstDay)} tuần`;
  }
  return `${year}-${month}-${day}`;
};

const orderRevenue = (order) =>
  order.status === 'CANCELLED' ? 0 : Number(order.totalAmount || order.subtotal || 0);

const getAvailableQty = (item) => Number(item.availableQuantity ?? item.quantityOnHand ?? 0);

const isLowStock = (item) => {
  const available = getAvailableQty(item);
  const threshold = Number(item.lowStockThreshold ?? 0);
  return item.isLowStock || available <= threshold;
};

const buildDashboardModel = ({ orders, orderTotal, channels, inventory, lowStockItems }, groupBy) => {
  const channelNames = (() => {
    const names = [...new Set(orders.map(getChannelName))];
    return names.length
      ? names
      : channels.map((channel) => channel.name || channel.displayName || channel.platform).filter(Boolean);
  })();

  const totalRevenue = orders.reduce((sum, order) => sum + orderRevenue(order), 0);
  const deliveredRevenue = orders
    .filter((order) => order.status === 'DELIVERED')
    .reduce((sum, order) => sum + Number(order.totalAmount || 0), 0);

  const statusCounts = orders.reduce((acc, order) => {
    const status = order.status || 'UNKNOWN';
    acc[status] = (acc[status] || 0) + 1;
    return acc;
  }, {});

  const orderStatusChart = Object.entries(statusCounts).map(([status, count]) => ({
    status,
    label: STATUS_LABELS[status] || status,
    orders: count,
  }));

  const revenueMap = new Map();
  orders.forEach((order) => {
    const key = getPeriodKey(getOrderDate(order), groupBy);
    const channelName = getChannelName(order);
    const row = revenueMap.get(key) || { period: key };
    row[channelName] = Number(row[channelName] || 0) + orderRevenue(order);
    revenueMap.set(key, row);
  });

  const revenueChart = [...revenueMap.values()]
    .sort((a, b) => a.period.localeCompare(b.period))
    .map((row) => {
      channelNames.forEach((name) => {
        row[name] = Number(row[name] || 0);
      });
      return row;
    });

  const channelRevenue = Object.entries(
    orders.reduce((acc, order) => {
      const name = getChannelName(order);
      acc[name] = (acc[name] || 0) + orderRevenue(order);
      return acc;
    }, {})
  )
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value);

  const topProductMap = new Map();
  orders.forEach((order) => {
    (order.items || []).forEach((item) => {
      const name = item.name || item.variantName || item.sku || 'Không xác định';
      const current = topProductMap.get(name) || { name, sku: item.sku, sold: 0, revenue: 0 };
      current.sold += Number(item.quantity || 0);
      current.revenue += Number(item.totalPrice || item.unitPrice || 0) * Number(item.quantity || 1);
      topProductMap.set(name, current);
    });
  });

  const topProducts = [...topProductMap.values()]
    .sort((a, b) => b.sold - a.sold)
    .slice(0, 6);

  const recentOrders = [...orders]
    .sort((a, b) => getOrderDate(b) - getOrderDate(a))
    .slice(0, 8);

  const deliveredOrders = statusCounts.DELIVERED || 0;
  const resolvedOrderTotal = orderTotal || orders.length;

  return {
    channelNames,
    totalRevenue,
    deliveredRevenue,
    orderCount: resolvedOrderTotal,
    deliveredRate: resolvedOrderTotal ? Math.round((deliveredOrders * 100) / resolvedOrderTotal) : 0,
    orderStatusChart,
    revenueChart,
    channelRevenue,
    topProducts,
    recentOrders,
    lowStockCount: lowStockItems.length || inventory.filter(isLowStock).length,
    negativeStockCount: inventory.filter((item) => getAvailableQty(item) < 0).length,
  };
};

const SelectControl = ({ label, value, onChange, children }) => (
  <label className={styles.fieldLabel}>
    <span>{label}</span>
    <span className={styles.selectWrapper}>
      <select className={styles.select} value={value} onChange={(event) => onChange(event.target.value)}>
        {children}
      </select>
      <ChevronDown className={styles.selectIcon} size={14} />
    </span>
  </label>
);

const SummaryCard = ({ label, value, helper, icon: Icon, tone }) => (
  <div className={styles.summaryCard}>
    <div className={`${styles.summaryIconWrap} ${styles[tone]}`}>
      <Icon size={20} />
    </div>
    <div className={styles.summaryInfo}>
      <span className={styles.summaryLabel}>{label}</span>
      <span className={styles.summaryValue}>{value}</span>
      <span className={styles.summaryHelper}>{helper}</span>
    </div>
  </div>
);

const ChartTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div className={styles.chartTooltip}>
      <div className={styles.tooltipTitle}>{label}</div>
      {payload.map((entry) => (
        <div key={`${entry.dataKey}-${entry.name}`} className={styles.tooltipRow}>
          <span>{entry.name || entry.dataKey}</span>
          <strong>
            {entry.dataKey === 'orders' || entry.name === 'Đơn hàng'
              ? Number(entry.value || 0).toLocaleString('vi-VN')
              : compactCurrency(entry.value)}
          </strong>
        </div>
      ))}
    </div>
  );
};

const Panel = ({ title, subtitle, action, children, className = '' }) => (
  <section className={`${styles.tableCard} ${className}`}>
    <div className={styles.tableCardHeader}>
      <div>
        <h2 className={styles.tableTitle}>{title}</h2>
        {subtitle && <p className={styles.tableSubtitle}>{subtitle}</p>}
      </div>
      {action}
    </div>
    {children}
  </section>
);

const DataTable = ({ columns, data, emptyText }) => {
  const [sorting, setSorting] = useState([]);
  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  return (
    <div className={styles.tableWrapper}>
      <table className={styles.table}>
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th key={header.id} className={styles.th}>
                  {header.isPlaceholder ? null : (
                    <button
                      className={styles.thButton}
                      type="button"
                      onClick={header.column.getToggleSortingHandler()}
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {header.column.getIsSorted() === 'asc' && <span aria-hidden="true">↑</span>}
                      {header.column.getIsSorted() === 'desc' && <span aria-hidden="true">↓</span>}
                    </button>
                  )}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.length === 0 ? (
            <tr>
              <td className={styles.emptyRow} colSpan={columns.length}>{emptyText}</td>
            </tr>
          ) : (
            table.getRowModel().rows.map((row, index) => (
              <tr key={row.id} className={`${styles.tr} ${index % 2 === 1 ? styles.trAlt : ''}`}>
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id} className={styles.td}>
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};

const LoadingBlock = () => (
  <div className={styles.loadingOverlay}>
    <RefreshCw size={28} className={styles.spinnerIcon} />
    <span>Đang tải dữ liệu dashboard...</span>
  </div>
);

export default function DashboardPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === ROLES.SYSTEM_ADMIN;

  const [period, setPeriod] = useState('30');
  const [groupBy, setGroupBy] = useState('day');
  const [channelId, setChannelId] = useState('');
  const [fromDate, setFromDate] = useState(getDefaultFromDate(30));
  const [toDate, setToDate] = useState(toDateInput(new Date()));
  const [data, setData] = useState(INITIAL_DASHBOARD_DATA);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  // State for Admin dashboard
  const [adminData, setAdminData] = useState({
    users: [],
    channels: [],
    syncLogs: [],
    connectionLogs: [],
    notifications: [],
    stats: {
      totalUsers: 0,
      activeUsers: 0,
      inactiveUsers: 0,
      lockedUsers: 0,
      totalChannels: 0,
      connectedChannels: 0,
      disconnectedChannels: 0,
      errorChannels: 0,
      totalSyncs: 0,
      successSyncs: 0,
      failedSyncs: 0,
      syncSuccessRate: 0,
      totalAlerts: 0,
    }
  });

  const effectiveFromDate = period === 'custom' ? fromDate : getDefaultFromDate(period);
  const effectiveToDate = period === 'custom' ? toDate : toDateInput(new Date());

  const fetchDashboard = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [channelRes, orderRes, inventoryRes, lowStockRes, productRes] = await Promise.all([
        channelApi.getAll(),
        orderApi.getAll({
          channelId: channelId || undefined,
          from: effectiveFromDate || undefined,
          to: effectiveToDate || undefined,
          page: 0,
          size: PAGE_FETCH_SIZE,
        }),
        inventoryApi.getInventoryList(0, PAGE_FETCH_SIZE, 'updatedAt', 'desc'),
        inventoryApi.getLowStockItems(),
        productApi.getAll(0, 1),
      ]);

      const channelData = channelRes?.data?.data ?? channelRes?.data ?? [];
      const orderPage = normalizePage(orderRes);
      const inventoryPage = normalizePage(inventoryRes);
      const productPage = normalizePage(productRes);

      setData({
        channels: Array.isArray(channelData) ? channelData : [],
        orders: orderPage.content ?? [],
        orderTotal: orderPage.totalElements ?? orderPage.content?.length ?? 0,
        inventory: inventoryPage.content ?? [],
        inventoryTotal: inventoryPage.totalElements ?? inventoryPage.content?.length ?? 0,
        productsTotal: productPage.totalElements ?? productPage.content?.length ?? 0,
        lowStockItems: Array.isArray(lowStockRes) ? lowStockRes : [],
      });
    } catch (err) {
      console.error('Failed to load dashboard:', err);
      setError('Không thể tải dữ liệu dashboard. Vui lòng kiểm tra backend và thử lại.');
      setData(INITIAL_DASHBOARD_DATA);
    } finally {
      setLoading(false);
    }
  }, [channelId, effectiveFromDate, effectiveToDate]);

  const fetchAdminDashboard = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [usersRes, channelsRes, syncLogsRes, connLogsRes, notifsRes] = await Promise.all([
        userApi.getAllUsers(0, 100),
        channelApi.getAll(),
        syncApi.getLogs({ page: 0, size: 100 }),
        channelConnectionLogApi.getAll({ page: 0, size: 10 }),
        notificationApi.getNotifications({ page: 0, size: 20 })
      ]);

      const usersList = usersRes?.content ?? usersRes ?? [];
      const channelsList = channelsRes?.data?.data ?? channelsRes?.data ?? channelsRes ?? [];
      const syncLogsPage = syncLogsRes?.data?.data ?? syncLogsRes?.data ?? syncLogsRes ?? {};
      const syncLogsList = syncLogsPage?.content ?? [];
      const connLogsPage = connLogsRes?.data?.data ?? connLogsRes?.data ?? connLogsRes ?? {};
      const connLogsList = connLogsPage?.content ?? [];
      const notifsPage = notifsRes?.data?.data ?? notifsRes?.data ?? notifsRes ?? {};
      const notifsList = notifsPage?.content ?? [];

      // Calculate stats
      const totalUsers = usersList.length;
      const activeUsers = usersList.filter(u => u.status === 'ACTIVE').length;
      const inactiveUsers = usersList.filter(u => u.status === 'INACTIVE').length;
      const lockedUsers = usersList.filter(u => u.status === 'LOCKED').length;

      const totalChannels = channelsList.length;
      const connectedChannels = channelsList.filter(c => c.status === 'CONNECTED').length;
      const disconnectedChannels = channelsList.filter(c => c.status === 'DISCONNECTED').length;
      const errorChannels = channelsList.filter(c => c.status === 'ERROR' || c.status === 'PENDING').length;

      const totalSyncs = syncLogsList.length;
      const successSyncs = syncLogsList.filter(l => l.status === 'SYNCED').length;
      const failedSyncs = syncLogsList.filter(l => l.status === 'FAILED').length;
      const syncSuccessRate = totalSyncs ? Math.round((successSyncs * 100) / totalSyncs) : 0;

      // Count unread system alerts in notifications list
      const totalAlerts = notifsList.filter(n => !n.readAt).length;

      setAdminData({
        users: usersList,
        channels: channelsList,
        syncLogs: syncLogsList.slice(0, 10),
        connectionLogs: connLogsList,
        notifications: notifsList,
        stats: {
          totalUsers,
          activeUsers,
          inactiveUsers,
          lockedUsers,
          totalChannels,
          connectedChannels,
          disconnectedChannels,
          errorChannels,
          totalSyncs,
          successSyncs,
          failedSyncs,
          syncSuccessRate,
          totalAlerts,
        }
      });
    } catch (err) {
      console.error('Error fetching admin dashboard:', err);
      setError('Có lỗi xảy ra khi tải dữ liệu tổng quan hệ thống.');
    } finally {
      setLoading(false);
    }
  }, [user?.id]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      if (isAdmin) {
        fetchAdminDashboard();
      } else {
        fetchDashboard();
      }
    }, 0);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [isAdmin, fetchDashboard, fetchAdminDashboard, reloadKey]);

  // Admin Dashboard Columns
  const syncColumns = useMemo(() => [
    {
      accessorKey: 'jobType',
      header: 'Kiểu đồng bộ',
      cell: ({ row }) => {
        const type = row.original.jobType;
        if (type === 'PRODUCT_SYNC') return 'Đồng bộ sản phẩm';
        if (type === 'LAZADA_IMPORT') return 'Kéo dữ liệu Lazada';
        if (type === 'LAZADA_LOCAL_CHANGES_SYNC') return 'Đẩy thay đổi lên Lazada';
        return type;
      }
    },
    {
      accessorKey: 'channel',
      header: 'Kênh',
      cell: ({ row }) => row.original.channel?.displayName || row.original.channel?.platform || 'Hệ thống'
    },
    {
      accessorKey: 'status',
      header: 'Trạng thái',
      cell: ({ row }) => {
        const status = row.original.status;
        return (
          <span className={`${styles.statusBadge} ${status === 'SYNCED' ? styles.statusSuccess : styles.statusFailed}`}>
            {status === 'SYNCED' ? 'Thành công' : 'Thất bại'}
          </span>
        );
      }
    },
    {
      accessorKey: 'processed',
      header: 'Đã xử lý (Thành công/Lỗi)',
      cell: ({ row }) => {
        const total = row.original.totalItems || 0;
        const success = row.original.successCount || 0;
        const fail = row.original.failCount || 0;
        return `${total} (${success}/${fail})`;
      }
    },
    {
      accessorKey: 'startedAt',
      header: 'Bắt đầu',
      cell: ({ row }) => formatDate(row.original.startedAt)
    },
    {
      accessorKey: 'errorSummary',
      header: 'Chi tiết lỗi',
      cell: ({ row }) => (
        <span className={styles.errorText} title={row.original.errorSummary}>
          {row.original.errorSummary || '-'}
        </span>
      )
    }
  ], []);

  const connectionColumns = useMemo(() => [
    {
      accessorKey: 'displayName',
      header: 'Tên kênh',
      cell: ({ row }) => (
        <strong>{row.original.displayName || row.original.name}</strong>
      )
    },
    {
      accessorKey: 'platform',
      header: 'Nền tảng',
      cell: ({ row }) => (
        <span className={styles.platformLabel}>{row.original.platform}</span>
      )
    },
    {
      accessorKey: 'status',
      header: 'Trạng thái kết nối',
      cell: ({ row }) => {
        const status = row.original.status;
        let cls = styles.connDisconnected;
        let label = 'Ngắt kết nối';
        if (status === 'CONNECTED') {
          cls = styles.connConnected;
          label = 'Đang kết nối';
        } else if (status === 'ERROR') {
          cls = styles.connError;
          label = 'Lỗi kết nối';
        } else if (status === 'PENDING') {
          cls = styles.connPending;
          label = 'Chờ kết nối';
        }
        return <span className={`${styles.connStatus} ${cls}`}>{label}</span>;
      }
    },
    {
      accessorKey: 'lastSyncedAt',
      header: 'Đồng bộ lần cuối',
      cell: ({ row }) => formatDate(row.original.lastSyncedAt)
    }
  ], []);

  const dashboard = useMemo(
    () => buildDashboardModel(data, groupBy),
    [data, groupBy]
  );

  const lowStockRows = useMemo(
    () => (data.lowStockItems.length ? data.lowStockItems : data.inventory.filter(isLowStock)).slice(0, 8),
    [data.inventory, data.lowStockItems]
  );

  const recentOrderColumns = useMemo(() => [
    {
      accessorKey: 'externalOrderId',
      header: 'Mã đơn',
      cell: ({ row }) => (
        <span className={styles.orderCode}>{row.original.externalOrderId || row.original.id}</span>
      ),
    },
    {
      accessorKey: 'createdAt',
      header: 'Ngày đặt',
      cell: ({ row }) => <span className={styles.mutedText}>{formatDate(row.original.createdAt)}</span>,
    },
    {
      accessorKey: 'channelName',
      header: 'Kênh bán',
      cell: ({ row }) => <span className={styles.channelChip}>{getChannelName(row.original)}</span>,
    },
    {
      accessorKey: 'status',
      header: 'Trạng thái',
      cell: ({ row }) => STATUS_LABELS[row.original.status] || row.original.status || '-',
    },
    {
      accessorKey: 'totalAmount',
      header: 'Tổng tiền',
      cell: ({ row }) => (
        <span className={styles.moneyText}>{formatCurrency(row.original.totalAmount)}</span>
      ),
    },
  ], []);

  const lowStockColumns = useMemo(() => [
    {
      accessorKey: 'variantSku',
      header: 'SKU',
      cell: ({ row }) => <span className={styles.skuChip}>{row.original.variantSku || '-'}</span>,
    },
    {
      accessorKey: 'variantName',
      header: 'Sản phẩm',
      cell: ({ row }) => (
        <div>
          <div className={styles.productName}>{row.original.variantName || '-'}</div>
          <div className={styles.productSku}>{row.original.warehouseName || 'Chưa có kho'}</div>
        </div>
      ),
    },
    {
      accessorKey: 'availableQuantity',
      header: 'Có thể bán',
      cell: ({ row }) => {
        const qty = getAvailableQty(row.original);
        return (
          <span className={qty <= 0 ? styles.qtyDanger : styles.qtyWarn}>
            {qty.toLocaleString('vi-VN')}
          </span>
        );
      },
    },
    {
      accessorKey: 'lowStockThreshold',
      header: 'Tồn tối thiểu',
      cell: ({ row }) => Number(row.original.lowStockThreshold || 0).toLocaleString('vi-VN'),
    },
  ], []);

  const handleRefresh = useCallback(() => {
    setReloadKey((value) => value + 1);
    window.dispatchEvent(new Event('notifications:refresh'));
  }, []);

  const actions = (
    <button
      className={`${styles.actionBtn} ${styles.primaryBtn}`}
      type="button"
      onClick={handleRefresh}
      disabled={loading}
    >
      <RefreshCw className={loading ? styles.spinIcon : styles.primaryIcon} size={16} />
      Làm mới
    </button>
  );

  if (isAdmin) {
    return (
      <div className={styles.page}>
        <PageHeader
          title="System Administration Dashboard"
          subtitle="Giám sát trạng thái hoạt động hệ thống, người dùng, đồng bộ sàn và cảnh báo lỗi"
          icon={<Activity size={20} />}
          actions={actions}
        />

        <div className={styles.summaryGrid}>
          <SummaryCard
            label="Tổng số người dùng"
            value={adminData.stats.totalUsers.toString()}
            helper={`Đang hoạt động: ${adminData.stats.activeUsers} | Khóa: ${adminData.stats.lockedUsers}`}
            icon={Users}
            tone="blueTone"
          />
          <SummaryCard
            label="Kết nối sàn"
            value={`${adminData.stats.connectedChannels}/${adminData.stats.totalChannels}`}
            helper={`Chưa kết nối: ${adminData.stats.disconnectedChannels} | Lỗi: ${adminData.stats.errorChannels}`}
            icon={Link}
            tone="greenTone"
          />
          <SummaryCard
            label="Sức khỏe đồng bộ"
            value={`${adminData.stats.syncSuccessRate}%`}
            helper={`Thành công: ${adminData.stats.successSyncs} | Thất bại: ${adminData.stats.failedSyncs}`}
            icon={RefreshCw}
            tone="slateTone"
          />
          <SummaryCard
            label="Cảnh báo hệ thống (Chưa đọc)"
            value={adminData.stats.totalAlerts.toString()}
            helper="Cần kiểm tra ngay lập tức"
            icon={AlertTriangle}
            tone={adminData.stats.totalAlerts > 0 ? 'redTone' : 'slateTone'}
          />
        </div>

        {error && (
          <div className={`${styles.alert} ${styles.alertError}`} role="alert">
            <AlertCircle size={16} className={styles.alertIcon} />
            <span>{error}</span>
          </div>
        )}

        {loading ? (
          <LoadingBlock />
        ) : (
          <>
            <div className={styles.adminMainLayout}>
              {/* Left Column: Sync Jobs & Connection status */}
              <div className={styles.adminLeftCol}>
                <Panel
                  title="Tiến trình đồng bộ gần đây"
                  subtitle="Nhật ký các lượt đồng bộ dữ liệu"
                  action={<RefreshCw size={18} className={styles.headerIcon} />}
                >
                  <DataTable
                    columns={syncColumns}
                    data={adminData.syncLogs}
                    emptyText="Không có dữ liệu đồng bộ gần đây"
                  />
                </Panel>

                <Panel
                  title="Trạng thái các kênh liên kết"
                  subtitle="Tình trạng đồng bộ và kết nối của từng gian hàng"
                  action={<Store size={18} className={styles.headerIcon} />}
                >
                  <DataTable
                    columns={connectionColumns}
                    data={adminData.channels}
                    emptyText="Chưa cấu hình kênh bán hàng nào"
                  />
                </Panel>
              </div>

              {/* Right Column: Alerts & Users */}
              <div className={styles.adminRightCol}>
                <Panel
                  title="Cảnh báo hệ thống"
                  subtitle="Lỗi đồng bộ hoặc cảnh báo tồn kho thấp"
                  action={<AlertTriangle size={18} className={styles.headerIcon} />}
                >
                  <div className={styles.alertsContainer}>
                    {adminData.notifications.length === 0 ? (
                      <div className={styles.emptyAlerts}>Không có cảnh báo hoạt động nào</div>
                    ) : (
                      adminData.notifications.slice(0, 5).map(n => (
                        <div key={n.id} className={`${styles.alertItem} ${!n.readAt ? styles.alertUnread : ''} ${n.type === 'SYNC_FAILED' ? styles.alertTypeSync : styles.alertTypeStock}`}>
                          <div className={styles.alertHeader}>
                            <strong>{n.title}</strong>
                            <span className={styles.alertTime}>{formatDate(n.createdAt)}</span>
                          </div>
                          <p className={styles.alertBody}>{n.body}</p>
                        </div>
                      ))
                    )}
                  </div>
                </Panel>

                <Panel
                  title="Tài khoản người dùng"
                  subtitle="Danh sách các thành viên truy cập hệ thống"
                  action={<Users size={18} className={styles.headerIcon} />}
                >
                  <div className={styles.userList}>
                    {adminData.users.slice(0, 6).map(u => (
                      <div key={u.id} className={styles.userRow}>
                        <div className={styles.userInfo}>
                          <strong>{u.fullName || u.email}</strong>
                          <span>{u.role || 'Nhân viên'}</span>
                        </div>
                        <span className={`${styles.userBadge} ${u.status === 'ACTIVE' ? styles.userActive : styles.userInactive}`}>
                          {u.status === 'ACTIVE' ? 'Hoạt động' : 'Tạm khóa'}
                        </span>
                      </div>
                    ))}
                  </div>
                </Panel>
              </div>
            </div>
          </>
        )}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <PageHeader
        title="Dashboard"
        subtitle="Tổng quan doanh thu, đơn hàng và tồn kho theo kênh bán hàng"
        icon={<Activity size={20} />}
        actions={actions}
      />

      <div className={styles.summaryGrid}>
        <SummaryCard
          label="Tổng doanh thu"
          value={compactCurrency(dashboard.totalRevenue)}
          helper={`Đã giao: ${compactCurrency(dashboard.deliveredRevenue)}`}
          icon={CircleDollarSign}
          tone="greenTone"
        />
        <SummaryCard
          label="Đơn hàng"
          value={dashboard.orderCount.toLocaleString('vi-VN')}
          helper={`${dashboard.deliveredRate}% đơn đã giao`}
          icon={ShoppingCart}
          tone="blueTone"
        />
        <SummaryCard
          label="Sản phẩm / SKU"
          value={`${data.productsTotal.toLocaleString('vi-VN')} / ${data.inventoryTotal.toLocaleString('vi-VN')}`}
          helper="Catalog và tồn kho"
          icon={Layers}
          tone="slateTone"
        />
        <SummaryCard
          label="Cảnh báo tồn kho"
          value={dashboard.lowStockCount.toLocaleString('vi-VN')}
          helper={`Tồn âm: ${dashboard.negativeStockCount.toLocaleString('vi-VN')} SKU`}
          icon={AlertTriangle}
          tone={dashboard.negativeStockCount > 0 ? 'redTone' : 'amberTone'}
        />
      </div>

      {error && (
        <div className={`${styles.alert} ${styles.alertError}`} role="alert">
          <AlertCircle size={16} className={styles.alertIcon} />
          <span>{error}</span>
        </div>
      )}

      {!loading && dashboard.lowStockCount > 0 && (
        <div className={`${styles.alert} ${styles.alertWarn}`}>
          <AlertTriangle size={16} className={styles.alertIcon} />
          <span>Có {dashboard.lowStockCount} SKU dưới mức tồn tối thiểu. Cần kiểm tra kế hoạch nhập hàng.</span>
        </div>
      )}

      <div className={styles.filtersPanel}>
        <div className={styles.filtersRow}>
          <SelectControl label="Thời gian" value={period} onChange={setPeriod}>
            {PERIOD_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </SelectControl>
          <SelectControl label="Nhóm doanh thu" value={groupBy} onChange={setGroupBy}>
            {GROUP_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </SelectControl>
          <SelectControl label="Kênh bán" value={channelId} onChange={setChannelId}>
            <option value="">Tất cả kênh</option>
            {data.channels.map((channel) => (
              <option key={channel.id} value={channel.id}>
                {channel.name || channel.displayName || channel.platform}
              </option>
            ))}
          </SelectControl>
          <label className={styles.fieldLabel}>
            <span>Từ ngày</span>
            <input
              className={styles.input}
              disabled={period !== 'custom'}
              type="date"
              value={effectiveFromDate}
              onChange={(event) => setFromDate(event.target.value)}
            />
          </label>
          <label className={styles.fieldLabel}>
            <span>Đến ngày</span>
            <input
              className={styles.input}
              disabled={period !== 'custom'}
              type="date"
              value={effectiveToDate}
              onChange={(event) => setToDate(event.target.value)}
            />
          </label>
        </div>
      </div>

      {loading ? (
        <Panel title="Dữ liệu dashboard" subtitle="Đang đồng bộ dữ liệu từ backend">
          <LoadingBlock />
        </Panel>
      ) : (
        <>
          <div className={styles.chartGrid}>
            <Panel
              title="Doanh thu theo thời gian"
              subtitle="Phân tích doanh thu theo ngày, tuần hoặc tháng"
              action={<CalendarDays size={18} className={styles.headerIcon} />}
              className={styles.revenuePanel}
            >
              <div className={styles.chartBodyLarge}>
                {dashboard.revenueChart.length === 0 ? (
                  <div className={styles.emptyChart}>Chưa có doanh thu trong khoảng thời gian đã chọn</div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={dashboard.revenueChart} margin={{ left: 4, right: 20, top: 8, bottom: 0 }}>
                      <defs>
                        {dashboard.channelNames.map((name, index) => (
                          <linearGradient key={name} id={`dashboard-channel-${index}`} x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor={CHART_COLORS[index % CHART_COLORS.length]} stopOpacity={0.62} />
                            <stop offset="95%" stopColor={CHART_COLORS[index % CHART_COLORS.length]} stopOpacity={0.08} />
                          </linearGradient>
                        ))}
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis dataKey="period" tick={{ fontSize: 12, fill: '#64748b' }} stroke="#cbd5e1" />
                      <YAxis tickFormatter={compactCurrency} tick={{ fontSize: 12, fill: '#64748b' }} stroke="#cbd5e1" />
                      <Tooltip content={<ChartTooltip />} />
                      <Legend wrapperStyle={{ fontSize: 12 }} />
                      {dashboard.channelNames.map((name, index) => (
                        <Area
                          key={name}
                          dataKey={name}
                          name={name}
                          stackId="revenue"
                          stroke={CHART_COLORS[index % CHART_COLORS.length]}
                          fill={`url(#dashboard-channel-${index})`}
                          strokeWidth={2}
                        />
                      ))}
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
            </Panel>

            <Panel
              title="Trạng thái đơn hàng"
              subtitle="Tổng hợp theo trạng thái"
              action={<CheckCircle2 size={18} className={styles.headerIcon} />}
            >
              <div className={styles.chartBody}>
                {dashboard.orderStatusChart.length === 0 ? (
                  <div className={styles.emptyChart}>Chưa có đơn hàng trong kỳ</div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={dashboard.orderStatusChart}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                      <XAxis dataKey="label" tick={{ fontSize: 12, fill: '#64748b' }} stroke="#cbd5e1" />
                      <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} stroke="#cbd5e1" />
                      <Tooltip content={<ChartTooltip />} />
                      <Bar dataKey="orders" name="Đơn hàng" radius={[6, 6, 0, 0]}>
                        {dashboard.orderStatusChart.map((entry, index) => (
                          <Cell key={entry.status} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </Panel>
          </div>

          <div className={styles.detailGrid}>
            <Panel
              title="Doanh thu theo sàn"
              subtitle="Tỷ trọng doanh thu từng kênh"
              action={<Store size={18} className={styles.headerIcon} />}
            >
              <div className={styles.channelRevenueBody}>
                <div className={styles.pieBox}>
                  {dashboard.channelRevenue.length === 0 ? (
                    <div className={styles.emptyChart}>Chưa có dữ liệu</div>
                  ) : (
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={dashboard.channelRevenue}
                          dataKey="value"
                          nameKey="name"
                          innerRadius={48}
                          outerRadius={72}
                          paddingAngle={2}
                        >
                          {dashboard.channelRevenue.map((entry, index) => (
                            <Cell key={entry.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip formatter={(value) => formatCurrency(value)} />
                      </PieChart>
                    </ResponsiveContainer>
                  )}
                </div>
                <div className={styles.channelList}>
                  {dashboard.channelRevenue.slice(0, 5).map((item, index) => (
                    <div key={item.name} className={styles.channelRow}>
                      <span className={styles.channelDot} style={{ backgroundColor: CHART_COLORS[index % CHART_COLORS.length] }} />
                      <span className={styles.channelName}>{item.name}</span>
                      <strong>{compactCurrency(item.value)}</strong>
                    </div>
                  ))}
                </div>
              </div>
            </Panel>

            <Panel
              title="Sản phẩm bán chạy"
              subtitle="Top sản phẩm theo số lượng bán"
              action={<TrendingUp size={18} className={styles.headerIcon} />}
            >
              <div className={styles.topProductList}>
                {dashboard.topProducts.length === 0 ? (
                  <div className={styles.emptyChart}>Chưa có dữ liệu sản phẩm bán chạy</div>
                ) : (
                  dashboard.topProducts.map((product, index) => (
                    <div key={product.name} className={styles.topProductItem}>
                      <span className={styles.rankBadge}>{index + 1}</span>
                      <div className={styles.topProductMain}>
                        <span className={styles.productName}>{product.name}</span>
                        <span className={styles.productSku}>
                          Đã bán {product.sold.toLocaleString('vi-VN')} · {formatCurrency(product.revenue)}
                        </span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </Panel>
          </div>

          <Panel
            title="Cảnh báo tồn kho thấp"
            subtitle="SKU dưới mức tồn tối thiểu hoặc có thể bán âm"
            action={<Warehouse size={18} className={styles.headerIcon} />}
          >
            <DataTable
              columns={lowStockColumns}
              data={lowStockRows}
              emptyText="Không có SKU dưới mức tồn tối thiểu"
            />
          </Panel>

          <Panel
            title="Đơn hàng gần đây"
            subtitle="Các đơn mới nhất trong bộ lọc hiện tại"
            action={<PackageSearch size={18} className={styles.headerIcon} />}
          >
            <DataTable
              columns={recentOrderColumns}
              data={dashboard.recentOrders}
              emptyText="Chưa có đơn hàng trong khoảng thời gian đã chọn"
            />
          </Panel>
        </>
      )}
    </div>
  );
}
