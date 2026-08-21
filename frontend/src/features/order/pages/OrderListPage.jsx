import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../app/router/routes';
import {
  ShoppingCart, FileDown, Eye, Search,
  TrendingUp, Clock, CheckCircle, Package, Truck, XCircle,
  Store, ShoppingBag, PenTool, History, CloudDownload, Printer, Loader2, AlertTriangle,
} from 'lucide-react';
import PageHeader from '../../../shared/components/PageHeader';
import Pagination from '../../../shared/components/Pagination';
import orderService from '../services/orderService';
import orderApi from '../../../api/orderApi';
import channelApi from '../../../api/channelApi';
import ExportOrdersModal from '../components/ExportOrdersModal';
import PullOrdersModal from '../components/PullOrdersModal';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import styles from './OrderListPage.module.css';

const PAGE_SIZE = 5;
const PULL_JOB_STORAGE_KEY = 'osms.orderPullJobIds';

const STATUS_CONFIG = {
  PENDING:    { label: 'Chờ xử lý',  icon: Clock,       color: 'orange'    },
  WAITING_STOCK: { label: 'Chờ hàng', icon: Clock, color: 'orange' },
  CONFIRMED:  { label: 'Đã xác nhận', icon: CheckCircle, color: 'blue'     },
  PROCESSING: { label: 'Đang xử lý',  icon: Package,     color: 'amber'    },
  SHIPPED:    { label: 'Sẵn sàng giao', icon: Truck,       color: 'teal'    },
  IN_TRANSIT: { label: 'Đang vận chuyển', icon: Truck,    color: 'sky'     },
  DELIVERED:  { label: 'Đã giao',      icon: CheckCircle, color: 'green'    },
  CANCELLED:  { label: 'Đã hủy',       icon: XCircle,     color: 'red'      },
};

const CHANNEL_CONFIG = {
  Shopee:  { icon: ShoppingBag, bg: '#fff5f5', color: '#e11d48', label: 'Shopee'  },
  Lazada:  { icon: Store,       bg: '#fff7ed', color: '#f97316', label: 'Lazada'  },
  TikTok:  { icon: ShoppingBag, bg: '#fdf2f8', color: '#db2777', label: 'TikTok'  },
  Website: { icon: PenTool,     bg: '#f0fdf4', color: '#16a34a', label: 'Website' },
  Manual:  { icon: PenTool,     bg: '#f5f3ff', color: '#7c3aed', label: 'Manual'  },
};

/** Chọn icon platform tương ứng để hiển thị trong bảng order. */
const getChannelIcon = (channelName) => {
  if (!channelName) return null;
  const name = channelName.trim();
  const key = Object.keys(CHANNEL_CONFIG).find((k) =>
    name.toLowerCase().includes(k.toLowerCase())
  );
  return CHANNEL_CONFIG[key] || {
    icon: Store,
    bg: '#f8fafc',
    color: '#64748b',
    label: channelName,
  };
};

const PAYMENT_CONFIG = {
  UNPAID:  { label: 'Chưa thanh toán', className: 'payUnpaid'  },
  PAID:    { label: 'Đã thanh toán',   className: 'payPaid'    },
};

/** Xác định order đã đủ điều kiện lấy phiếu vận chuyển hay chưa. */
const getShippingLabelAvailability = (order) => {
  if (order?.platform === 'TIKTOK') {
    return order.status === 'SHIPPED'
      ? { enabled: true, title: 'In phiếu vận chuyển TikTok' }
      : { enabled: false, title: 'Đơn chưa sẵn sàng in phiếu' };
  }
  if (order?.platform === 'LAZADA') {
    return order.status === 'SHIPPED'
      ? { enabled: true, title: 'In phiếu vận chuyển Lazada' }
      : { enabled: false, title: 'Đơn chưa sẵn sàng in phiếu' };
  }
  return { enabled: false, title: 'V1 chưa hỗ trợ in phiếu cho kênh này' };
};

/** Trả về cảnh báo yêu cầu hủy TikTok đang hoạt động để hiển thị ngay trên danh sách. */
const getBuyerCancellationBadge = (order) => {
  const cancellation = order?.platformMetadata?.tiktok?.buyerCancellation;
  if (!cancellation?.active) return null;

  if (cancellation.cancelStatus === 'CANCELLATION_REQUEST_SUCCESS') {
    return {
      label: 'Đang hoàn tất hủy',
      title: 'Seller đã chấp thuận; đang chờ TikTok hoàn tất yêu cầu hủy',
      className: 'cancellationPendingCompletion',
    };
  }
  if (cancellation.cancelStatus === 'CANCELLATION_REQUEST_PENDING') {
    return cancellation.sellerActionRequired
      ? {
          label: 'Cần duyệt hủy',
          title: 'Khách hàng yêu cầu hủy; Seller cần duyệt hoặc từ chối',
          className: 'cancellationActionRequired',
        }
      : {
          label: 'Yêu cầu hủy',
          title: 'TikTok đang tiếp nhận yêu cầu hủy và chưa xác định bước xử lý tiếp theo',
          className: 'cancellationPending',
        };
  }
  return null;
};

/** Hiển thị danh sách đơn, polling trạng thái, kéo đơn và in phiếu vận chuyển. */
const OrderListPage = () => {
  const { user } = useAuth();
  const canPullOrders = user?.role === ROLES.OWNER || user?.role === ROLES.SALES;
  const navigate = useNavigate();

  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [waitingStockExpired, setWaitingStockExpired] = useState(false);
  const [channelFilter, setChannelFilter] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [channels, setChannels] = useState([]);

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [stats, setStats] = useState(null);
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [isPullModalOpen, setIsPullModalOpen] = useState(false);
  const [isStartingPull, setIsStartingPull] = useState(false);
  const [pullJobs, setPullJobs] = useState([]);
  const [printingOrderIds, setPrintingOrderIds] = useState(() => new Set());
  const [selectedOrderIds, setSelectedOrderIds] = useState(() => new Set());
  const [batchCancelling, setBatchCancelling] = useState(false);
  const pullFailuresRef = useRef(0);
  const pullStartedAtRef = useRef(Date.now());

  // Tải danh sách kênh phục vụ bộ lọc và modal kéo đơn.
  const fetchChannels = useCallback(async () => {
    try {
      const response = await channelApi.getAll();
      setChannels(response?.data?.data || response?.data || response || []);
    } catch {
      setChannels([]);
    }
  }, []);

  // Tải trang đơn hiện tại; chế độ silent hỗ trợ polling nền không làm nhấp nháy UI.
  const fetchOrders = useCallback(async ({ silent = false } = {}) => {
    if (!silent) setLoading(true);
    try {
      const params = {
        page,
        size: PAGE_SIZE,
        keyword: keyword || undefined,
        status: statusFilter || undefined,
        channelId: channelFilter || undefined,
        from: fromDate || undefined,
        to: toDate || undefined,
        waitingStockExpired: statusFilter === 'WAITING_STOCK' && waitingStockExpired,
      };
      const data = await orderService.getAll(params);
      setOrders(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Failed to fetch orders:', error);
      setOrders([]);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [page, keyword, statusFilter, channelFilter, fromDate, toDate, waitingStockExpired]);

  // Tải các chỉ số tổng hợp trên đầu màn danh sách.
  const fetchStats = useCallback(async () => {
    try {
      const data = await orderService.getStats();
      setStats(data);
    } catch (error) {
      console.error('Failed to fetch stats:', error);
    }
  }, []);

  useEffect(() => { fetchChannels(); }, [fetchChannels]);
  useEffect(() => { fetchOrders(); }, [fetchOrders]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  useEffect(() => {
    const refreshInterval = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        fetchOrders({ silent: true });
        fetchStats();
      }
    }, 5_000);

    return () => window.clearInterval(refreshInterval);
  }, [fetchOrders, fetchStats]);

  useEffect(() => { setPage(0); }, [keyword, statusFilter, channelFilter, fromDate, toDate, waitingStockExpired]);

  useEffect(() => {
    if (statusFilter !== 'WAITING_STOCK') setWaitingStockExpired(false);
    setSelectedOrderIds(new Set());
  }, [statusFilter, waitingStockExpired]);

  useEffect(() => {
    if (!canPullOrders) {
      setPullJobs([]);
      return undefined;
    }
    let cancelled = false;
    // Khôi phục polling cho các job kéo đơn còn PENDING sau khi tải lại trang.
    const recover = async () => {
      try {
        const storedIds = JSON.parse(sessionStorage.getItem(PULL_JOB_STORAGE_KEY) || '[]');
        const active = await orderApi.getActivePullJobs();
        const activeIds = new Set((active || []).map((job) => job.id));
        const recovered = await Promise.all(storedIds.filter((id) => !activeIds.has(id))
          .map((id) => orderApi.getPullJob(id).catch(() => null)));
        if (!cancelled) setPullJobs([...(active || []), ...recovered.filter(Boolean)]);
      } catch {
        if (!cancelled) setPullJobs([]);
      }
    };
    recover();
    return () => { cancelled = true; };
  }, [canPullOrders]);

  useEffect(() => {
    const pendingIds = pullJobs.filter((job) => job.status === 'PENDING').map((job) => job.id);
    sessionStorage.setItem(PULL_JOB_STORAGE_KEY, JSON.stringify(pendingIds));
    if (pendingIds.length === 0 || pullFailuresRef.current >= 3) return undefined;
    if (Date.now() - pullStartedAtRef.current > 35 * 60 * 1000) return undefined;
    const timer = window.setInterval(async () => {
      try {
        const updates = await Promise.all(pendingIds.map((id) => orderApi.getPullJob(id)));
        pullFailuresRef.current = 0;
        const completed = updates.some((job) => job.status !== 'PENDING');
        setPullJobs((current) => current.map((job) => updates.find((value) => value.id === job.id) || job));
        if (completed) {
          updates.filter((job) => job.status !== 'PENDING').forEach((job) => {
            if (job.status === 'SYNCED') {
              toast.success(`Đã kéo ${job.successCount || 0} đơn từ ${job.channelName || job.platform}`);
            } else {
              toast.error(`Kéo đơn từ ${job.channelName || job.platform} chưa hoàn tất`);
            }
          });
          fetchOrders({ silent: true });
          fetchStats();
        }
      } catch {
        pullFailuresRef.current += 1;
      }
    }, 5_000);
    return () => window.clearInterval(timer);
  }, [pullJobs, fetchOrders, fetchStats]);

  // Khởi tạo job kéo đơn thủ công bền vững và để modal polling tiến độ.
  const handlePullOrders = async (payload) => {
    if (!canPullOrders) return;
    setIsStartingPull(true);
    try {
      const jobs = await orderApi.pullOrders(payload);
      setPullJobs((current) => [...current.filter((job) => !jobs.some((next) => next.id === job.id)), ...jobs]);
      pullFailuresRef.current = 0;
      pullStartedAtRef.current = Date.now();
      setIsPullModalOpen(false);
      toast.success('Đã bắt đầu kéo đơn. Job sẽ tiếp tục chạy ở nền.');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể bắt đầu kéo đơn');
    } finally {
      setIsStartingPull(false);
    }
  };

  // Xóa toàn bộ bộ lọc và quay về trang đầu.
  const handleReset = () => {
    setKeyword('');
    setStatusFilter('');
    setChannelFilter('');
    setFromDate('');
    setToDate('');
    setWaitingStockExpired(false);
    setPage(0);
  };

  const handleBatchCancel = async () => {
    const ids = [...selectedOrderIds];
    if (ids.length === 0 || batchCancelling) return;
    if (!window.confirm(`Hủy ${ids.length} đơn chờ hàng do hết tồn?`)) return;
    setBatchCancelling(true);
    try {
      const results = await orderService.cancelBatchWaitingStock(ids);
      const successCount = (results || []).filter((item) => item.success).length;
      const failedCount = (results || []).length - successCount;
      if (successCount > 0) toast.success(`Đã xử lý hủy ${successCount} đơn`);
      if (failedCount > 0) toast.error(`${failedCount} đơn chưa thể hủy; hãy xem lại trạng thái sàn`);
      setSelectedOrderIds(new Set());
      await fetchOrders({ silent: true });
      await fetchStats();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể hủy các đơn đã chọn');
    } finally {
      setBatchCancelling(false);
    }
  };

  // Mở tab chờ trước rồi chuyển tới URL phiếu vận chuyển có chữ ký của platform.
  const handlePrintShippingLabel = async (order) => {
    const availability = getShippingLabelAvailability(order);
    if (!availability.enabled || printingOrderIds.has(order.id)) return;

    const previewWindow = window.open('', '_blank');
    if (!previewWindow) {
      toast.error('Trình duyệt đã chặn tab xem phiếu. Vui lòng cho phép mở cửa sổ mới.');
      return;
    }
    previewWindow.opener = null;
    previewWindow.document.title = 'Đang chuẩn bị phiếu vận chuyển';
    previewWindow.document.body.innerHTML = '<p style="font-family:Arial,sans-serif;padding:24px;color:#334155">Đang chuẩn bị phiếu vận chuyển...</p>';

    setPrintingOrderIds((current) => new Set(current).add(order.id));
    try {
      const label = await orderService.createShippingLabel(order.id);
      const documentUrl = new URL(label.documentUrl);
      if (documentUrl.protocol !== 'https:') {
        throw new Error('Invalid shipping label URL');
      }
      previewWindow.location.replace(documentUrl.toString());
    } catch (error) {
      if (!previewWindow.closed) previewWindow.close();
      toast.error(error?.response?.data?.message || 'Không thể lấy phiếu vận chuyển từ sàn');
    } finally {
      setPrintingOrderIds((current) => {
        const next = new Set(current);
        next.delete(order.id);
        return next;
      });
    }
  };

  // Định dạng giá trị tiền theo locale Việt Nam.
  const formatCurrency = (amount) => {
    if (amount == null) return '-';
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0,
    }).format(amount);
  };

  // Định dạng thời gian tạo đơn để hiển thị trong bảng.
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  };

  // Hiển thị thời gian order đã ở trạng thái chờ tồn kho.
  const formatWaitingDuration = (startedAt) => {
    if (!startedAt) return null;
    const elapsedMinutes = Math.max(0, Math.floor((Date.now() - new Date(startedAt).getTime()) / 60000));
    const days = Math.floor(elapsedMinutes / 1440);
    const hours = Math.floor((elapsedMinutes % 1440) / 60);
    const minutes = elapsedMinutes % 60;
    if (days > 0) return `${days} ngày ${hours} giờ`;
    if (hours > 0) return `${hours} giờ ${minutes} phút`;
    return `${minutes} phút`;
  };

  // Lấy nhãn và màu hiển thị của trạng thái đơn.
  const getStatusConfig = (status) => STATUS_CONFIG[status] || { label: status, color: 'slate' };
  // Ghép class CSS tương ứng với trạng thái đơn.
  const getStatusClassName = (status) => {
    if (status === 'IN_TRANSIT') return 'statusInTransit';
    if (status === 'WAITING_STOCK') return 'statusWaitingStock';
    return `status${status.charAt(0) + status.slice(1).toLowerCase()}`;
  };
  // Lấy nhãn và class của trạng thái thanh toán.
  const getPaymentConfig = (status) => PAYMENT_CONFIG[status] || { label: status, className: 'payUnpaid' };

  // Gộp tên và số lượng item thành nội dung tóm tắt trong một ô bảng.
  const getItemsSummary = (items) => {
    if (!items || items.length === 0) return '-';
    if (items.length === 1) return items[0].name;
    return `${items[0].name} (+${items.length - 1} sản phẩm)`;
  };

  const statsItems = stats ? [
    { label: 'Tổng đơn',        value: stats.totalOrders,    icon: ShoppingCart, color: 'slate'  },
    { label: 'Chờ xử lý',      value: stats.pendingCount,  icon: Clock,        color: 'orange'  },
    { label: 'Chờ hàng', value: stats.waitingStockCount, icon: Clock, color: 'orange' },
    { label: 'Đã xác nhận',     value: stats.confirmedCount, icon: CheckCircle, color: 'blue'    },
    { label: 'Đang xử lý',      value: stats.processingCount,icon: Package,     color: 'amber'   },
    { label: 'Đã giao',         value: stats.deliveredCount,icon: Truck,       color: 'green'   },
    { label: 'Doanh thu',       value: formatCurrency(stats.totalRevenue), icon: TrendingUp, color: 'blue', isVND: true },
  ] : [];

  const actions = (
    <>
      {canPullOrders && (
        <button
          className={`${styles.headerActionBtn} ${styles.secondaryBtn}`}
          onClick={() => setIsPullModalOpen(true)}
          title="Kéo đơn từ sàn"
        >
          <CloudDownload size={15} />
          Kéo đơn{pullJobs.some((job) => job.status === 'PENDING') ? ` (${pullJobs.filter((job) => job.status === 'PENDING').length})` : ''}
        </button>
      )}
      <button
        className={`${styles.headerActionBtn} ${styles.secondaryBtn}`}
        onClick={() => navigate(ROUTES.ORDER_LOGS)}
      >
        <History size={15} />
        Nhật ký đơn hàng
      </button>
      <button className={styles.btnOutline} onClick={() => setIsExportModalOpen(true)}>
        <FileDown size={15} />
        Xuất Excel
      </button>
    </>
  );

  return (
    <div className={`${styles.page} product-workspace`}>
      <div className={styles.pageHeaderShell}>
        <PageHeader
          title="Đơn hàng"
          subtitle="Quản lý và theo dõi đơn hàng theo kênh bán hàng"
          icon={<ShoppingCart size={20} />}
          actions={actions}
        />
      </div>

      {/* Stats */}
      {stats && (
        <div className={styles.statsGrid}>
          {statsItems.map((s) => (
            <div key={s.label} className={styles.statCard}>
              <div className={styles.statHeader}>
                <span className={styles.statLabel}>{s.label}</span>
                <div className={`${styles.statIconWrap} ${styles[`statIcon_${s.color}`]}`}>
                  <s.icon size={14} />
                </div>
              </div>
              <p className={`${styles.statValue} ${s.isVND ? styles.statValueSmall : ''}`}>{s.value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className={styles.filterCard}>
        <div className={styles.filterRow}>
          <div className={styles.searchWrapper}>
            <Search size={16} className={styles.searchIcon} />
            <input
              type="text"
              placeholder="Tìm theo mã đơn, tên khách hàng..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className={styles.searchInput}
            />
          </div>
          <select className={styles.select} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(STATUS_CONFIG).map(([key, cfg]) => (
              <option key={key} value={key}>{cfg.label}</option>
            ))}
          </select>
          {statusFilter === 'WAITING_STOCK' && (
            <label className={styles.expiredToggle}>
              <input
                type="checkbox"
                checked={waitingStockExpired}
                onChange={(event) => setWaitingStockExpired(event.target.checked)}
              />
              Chỉ đơn quá hạn
            </label>
          )}
          <select className={styles.select} value={channelFilter} onChange={(e) => setChannelFilter(e.target.value)}>
            <option value="">Tất cả kênh</option>
            {channels.map((ch) => (
              <option key={ch.id} value={ch.id}>
                {ch.name || ch.displayName || ch.platform || 'Kênh không tên'}
              </option>
            ))}
          </select>
          <input
            type="date"
            className={styles.dateInput}
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
          />
          <span className={styles.dateSeparator}>—</span>
          <input
            type="date"
            className={styles.dateInput}
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
          />
          <button type="button" className={styles.filterBtn} onClick={() => fetchOrders()}>
            <Search size={14} />
            Lọc
          </button>
          <button type="button" className={styles.resetBtn} onClick={handleReset}>
            Reset
          </button>
        </div>
      </div>

      {/* Table */}
      <div className={styles.tableCard}>
        <div className={styles.tableHeader}>
          <div className={styles.tableHeadingGroup}>
            <span className={styles.tableHeadingIcon}><ShoppingCart aria-hidden="true" /></span>
            <div>
              <h2 className={styles.tableTitle}>Danh sách đơn hàng</h2>
              <p className={styles.tableSubtitle}>Theo dõi trạng thái và thanh toán theo từng kênh.</p>
            </div>
          </div>
          <span className={styles.tableCount}>{totalElements}</span>
          {statusFilter === 'WAITING_STOCK' && waitingStockExpired && (
            <button
              type="button"
              className={styles.batchCancelBtn}
              disabled={selectedOrderIds.size === 0 || batchCancelling}
              onClick={handleBatchCancel}
            >
              <XCircle size={14} />
              {batchCancelling ? 'Đang hủy...' : `Hủy đơn đã chọn (${selectedOrderIds.size})`}
            </button>
          )}
        </div>
        <div className={styles.tableResponsive}>
          <table className={`${styles.table} ${orders.length === PAGE_SIZE ? styles.tableFilled : ''}`}>
          <thead>
            <tr>
              {statusFilter === 'WAITING_STOCK' && waitingStockExpired && (
                <th className={styles.selectColumn}>
                  <input
                    type="checkbox"
                    aria-label="Chọn tất cả đơn trên trang"
                    checked={orders.length > 0 && orders.every((order) => selectedOrderIds.has(order.id))}
                    onChange={(event) => setSelectedOrderIds(event.target.checked
                      ? new Set(orders.map((order) => order.id)) : new Set())}
                  />
                </th>
              )}
              <th className={styles.thPl}>Mã đơn</th>
              <th>Ngày đặt</th>
              <th>Kênh</th>
              <th>Khách hàng</th>
              <th>Sản phẩm</th>
              <th style={{ textAlign: 'center' }}>Tổng tiền</th>
              <th>Trạng thái</th>
              <th>Thanh toán</th>
              <th className={styles.thAction}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={statusFilter === 'WAITING_STOCK' && waitingStockExpired ? 10 : 9} className={styles.emptyCell}>Đang tải...</td>
              </tr>
            ) : orders.length === 0 ? (
              <tr>
                <td colSpan={statusFilter === 'WAITING_STOCK' && waitingStockExpired ? 10 : 9} className={styles.emptyCell}>
                  {keyword || statusFilter || channelFilter || fromDate || toDate
                    ? 'Không tìm thấy đơn hàng nào'
                    : 'Chưa có đơn hàng nào'}
                </td>
              </tr>
            ) : (
              orders.map((order) => {
                const sc = getStatusConfig(order.status);
                const pc = getPaymentConfig(order.paymentStatus);
                const StatusIcon = sc.icon;
                const shippingLabel = getShippingLabelAvailability(order);
                const isPrintingLabel = printingOrderIds.has(order.id);
                const cancellationBadge = getBuyerCancellationBadge(order);
                return (
                  <tr
                    key={order.id}
                    className={`${styles.tableRow} ${order.stockReadyForConfirmation ? styles.stockReadyRow : ''} ${cancellationBadge ? styles.cancellationRow : ''}`}
                  >
                    {statusFilter === 'WAITING_STOCK' && waitingStockExpired && (
                      <td className={styles.selectColumn}>
                        <input
                          type="checkbox"
                          aria-label={`Chọn đơn ${order.externalOrderId}`}
                          checked={selectedOrderIds.has(order.id)}
                          onChange={(event) => setSelectedOrderIds((current) => {
                            const next = new Set(current);
                            if (event.target.checked) next.add(order.id); else next.delete(order.id);
                            return next;
                          })}
                        />
                      </td>
                    )}
                    <td className={styles.thPl}>
                      <div className={styles.orderCodeCell}>
                        <span className={styles.orderCode}>{order.externalOrderId}</span>
                        {order.status === 'WAITING_STOCK' && order.stockReadyForConfirmation && (
                          <small className={styles.stockReadyBadge} title="Tồn đủ theo lần kiểm tra gần nhất; vẫn cần xác nhận lại để giữ tồn">
                            <CheckCircle size={11} />
                            Đã có hàng
                          </small>
                        )}
                        {cancellationBadge && (
                          <span
                            className={`${styles.cancellationBadge} ${styles[cancellationBadge.className]}`}
                            title={cancellationBadge.title}
                          >
                            <AlertTriangle size={11} />
                            {cancellationBadge.label}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className={styles.orderDate}>
                      {formatDate(order.createdAt)}
                    </td>
                    <td>
                      {order.channelName && (() => {
                        const ch = getChannelIcon(order.channelName);
                        const Icon = ch.icon;
                        return (
                          <div className={styles.channelCell}>
                            <div
                              className={styles.channelIcon}
                              style={{ '--channel-bg': ch.bg, '--channel-color': ch.color }}
                            >
                              <Icon size={13} />
                            </div>
                            <span className={styles.channelLabel} style={{ '--channel-color': ch.color }}>
                              {ch.label}
                            </span>
                          </div>
                        );
                      })()}
                    </td>
                    <td className={styles.customerCell}>
                      {order.buyerName || '-'}
                      {order.buyerPhone && (
                        <span className={styles.customerPhone}>
                          {order.buyerPhone}
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={styles.itemsSummary} title={order.items?.map(i => i.name).join(', ')}>
                        {getItemsSummary(order.items)}
                      </span>
                    </td>
                    <td className={styles.totalCell}>
                      {formatCurrency(order.totalAmount)}
                    </td>
                    <td>
                      <div className={styles.statusCell}>
                        <span className={`${styles.statusBadge} ${styles[getStatusClassName(order.status)]}`}>
                          <StatusIcon size={11} />
                          {sc.label}
                        </span>
                        {order.waitingStockAt && order.status === 'WAITING_STOCK' && (
                          <small className={styles.waitingDuration}>
                            Đã chờ {formatWaitingDuration(order.waitingStockAt)}
                          </small>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={`${styles.payBadge} ${styles[pc.className]}`}>
                        {pc.label}
                      </span>
                    </td>
                    <td className={styles.tdAction}>
                      <div className={styles.actionGroup}>
                        <button
                          type="button"
                          onClick={() => handlePrintShippingLabel(order)}
                          title={shippingLabel.title}
                          aria-label={shippingLabel.title}
                          disabled={!shippingLabel.enabled || isPrintingLabel}
                          className={`${styles.detailBtn} ${styles.printBtn}`}
                        >
                          {isPrintingLabel
                            ? <Loader2 size={16} className={styles.buttonSpinner} />
                            : <Printer size={16} />}
                        </button>
                        <button
                          type="button"
                          onClick={() => navigate(ROUTES.ORDER_DETAIL.replace(':id', order.id))}
                          title="Xem chi tiết"
                          className={styles.detailBtn}
                        >
                          <Eye size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
          </table>
        </div>
        <Pagination
          className={styles.tablePagination}
          currentPage={page}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={PAGE_SIZE}
          currentCount={orders.length}
          itemLabel="đơn hàng"
          onPageChange={setPage}
        />
      </div>

      <ExportOrdersModal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
        currentFilters={{ keyword, status: statusFilter, channelId: channelFilter, from: fromDate, to: toDate }}
      />
      {canPullOrders && (
        <PullOrdersModal
          open={isPullModalOpen}
          channels={channels}
          submitting={isStartingPull}
          onClose={() => setIsPullModalOpen(false)}
          onSubmit={handlePullOrders}
        />
      )}
    </div>
  );
};

export default OrderListPage;
