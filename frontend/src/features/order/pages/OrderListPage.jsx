import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../app/router/routes';
import {
  ShoppingCart, FileDown, Eye, Search,
  TrendingUp, Clock, CheckCircle, Package, Truck, XCircle,
  Store, ShoppingBag, PenTool, History,
} from 'lucide-react';
import PageHeader from '../../../shared/components/PageHeader';
import orderService from '../services/orderService';
import orderApi from '../../../api/orderApi';
import channelApi from '../../../api/channelApi';
import ExportOrdersModal from '../components/ExportOrdersModal';
import styles from './OrderListPage.module.css';

const PAGE_SIZE = 20;

const STATUS_CONFIG = {
  PENDING:    { label: 'Chờ xử lý',  icon: Clock,       color: 'orange'    },
  CONFIRMED:  { label: 'Đã xác nhận', icon: CheckCircle, color: 'blue'     },
  PROCESSING: { label: 'Đang xử lý',  icon: Package,     color: 'amber'    },
  SHIPPED:    { label: 'Đang giao',    icon: Truck,       color: 'teal'    },
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

const OrderListPage = () => {
  const navigate = useNavigate();

  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
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

  const fetchChannels = useCallback(async () => {
    try {
      const response = await channelApi.getAll();
      setChannels(response?.data?.data || response?.data || response || []);
    } catch {
      setChannels([]);
    }
  }, []);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size: PAGE_SIZE,
        keyword: keyword || undefined,
        status: statusFilter || undefined,
        channelId: channelFilter || undefined,
        from: fromDate || undefined,
        to: toDate || undefined,
      };
      const data = await orderService.getAll(params);
      setOrders(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Failed to fetch orders:', error);
      setOrders([]);
    } finally {
      setLoading(false);
    }
  }, [page, keyword, statusFilter, channelFilter, fromDate, toDate]);

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

  useEffect(() => { setPage(0); }, [keyword, statusFilter, channelFilter, fromDate, toDate]);

  const handleReset = () => {
    setKeyword('');
    setStatusFilter('');
    setChannelFilter('');
    setFromDate('');
    setToDate('');
    setPage(0);
  };

  const formatCurrency = (amount) => {
    if (amount == null) return '-';
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0,
    }).format(amount);
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  };

  const getStatusConfig = (status) => STATUS_CONFIG[status] || { label: status, color: 'slate' };
  const getPaymentConfig = (status) => PAYMENT_CONFIG[status] || { label: status, className: 'payUnpaid' };

  const getItemsSummary = (items) => {
    if (!items || items.length === 0) return '-';
    if (items.length === 1) return items[0].name;
    return `${items[0].name} (+${items.length - 1} sản phẩm)`;
  };

  const statsItems = stats ? [
    { label: 'Tổng đơn',        value: stats.totalOrders,    icon: ShoppingCart, color: 'slate'  },
    { label: 'Chờ xử lý',      value: stats.pendingCount,  icon: Clock,        color: 'orange'  },
    { label: 'Đã xác nhận',     value: stats.confirmedCount, icon: CheckCircle, color: 'blue'    },
    { label: 'Đang xử lý',      value: stats.processingCount,icon: Package,     color: 'amber'   },
    { label: 'Đã giao',         value: stats.deliveredCount,icon: Truck,       color: 'green'   },
    { label: 'Doanh thu',       value: formatCurrency(stats.totalRevenue), icon: TrendingUp, color: 'blue', isVND: true },
  ] : [];

  const actions = (
    <>
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
    <div className={styles.page}>
      <PageHeader
        title="Đơn hàng"
        subtitle="Quản lý và theo dõi đơn hàng theo kênh bán hàng"
        icon={<ShoppingCart size={20} />}
        actions={actions}
      />

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
          <select className={styles.select} value={channelFilter} onChange={(e) => setChannelFilter(e.target.value)}>
            <option value="">Tất cả kênh</option>
            {channels.map((ch) => (
              <option key={ch.id} value={ch.id}>{ch.name}</option>
            ))}
          </select>
          <input
            type="date"
            className={styles.dateInput}
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
          />
          <span style={{ color: '#94a3b8', fontSize: 13 }}>—</span>
          <input
            type="date"
            className={styles.dateInput}
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
          />
          <button className={styles.filterBtn} onClick={() => fetchOrders()}>
            <Search size={14} />
            Lọc
          </button>
          <button className={styles.resetBtn} onClick={handleReset}>
            Reset
          </button>
        </div>
      </div>

      {/* Table */}
      <div className={styles.tableCard}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th className={styles.thPl}>Mã đơn</th>
              <th>Ngày đặt</th>
              <th>Kênh</th>
              <th>Khách hàng</th>
              <th>Sản phẩm</th>
              <th style={{ textAlign: 'center' }}>Tổng tiền</th>
              <th>Trạng thái</th>
              <th>Thanh toán</th>
              <th className={styles.thAction}></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={9} className={styles.emptyCell}>Đang tải...</td>
              </tr>
            ) : orders.length === 0 ? (
              <tr>
                <td colSpan={9} className={styles.emptyCell}>
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
                return (
                  <tr key={order.id} className={styles.tableRow}>
                    <td className={styles.thPl}>
                      <span className={styles.orderCode}>{order.externalOrderId}</span>
                    </td>
                    <td style={{ fontSize: 12, color: '#64748b', whiteSpace: 'nowrap' }}>
                      {formatDate(order.createdAt)}
                    </td>
                    <td>
                      {order.channelName && (() => {
                        const ch = getChannelIcon(order.channelName);
                        const Icon = ch.icon;
                        return (
                          <div style={{
                            display: 'flex', alignItems: 'center', gap: 6,
                          }}>
                            <div style={{
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              width: 24, height: 24, borderRadius: 6,
                              background: ch.bg, color: ch.color,
                            }}>
                              <Icon size={13} />
                            </div>
                            <span style={{
                              fontSize: 12.5, fontWeight: 600, color: ch.color,
                            }}>
                              {ch.label}
                            </span>
                          </div>
                        );
                      })()}
                    </td>
                    <td style={{ fontWeight: 600, color: '#0f172a', fontSize: 13.5 }}>
                      {order.buyerName || '-'}
                      {order.buyerPhone && (
                        <span style={{ display: 'block', fontSize: 11.5, color: '#94a3b8', fontWeight: 400 }}>
                          {order.buyerPhone}
                        </span>
                      )}
                    </td>
                    <td>
                      <span className={styles.itemsSummary} title={order.items?.map(i => i.name).join(', ')}>
                        {getItemsSummary(order.items)}
                      </span>
                    </td>
                    <td style={{ textAlign: 'center', color: '#059669', fontWeight: 700 }}>
                      {formatCurrency(order.totalAmount)}
                    </td>
                    <td>
                      <span className={`${styles.statusBadge} ${styles[`status${order.status.charAt(0) + order.status.slice(1).toLowerCase()}`]}`}>
                        <StatusIcon size={11} />
                        {sc.label}
                      </span>
                    </td>
                    <td>
                      <span className={`${styles.payBadge} ${styles[pc.className]}`}>
                        {pc.label}
                      </span>
                    </td>
                    <td className={styles.tdAction}>
                      <div className={styles.actionGroup}>
                        <button
                          className={styles.actionBtn}
                          onClick={() => navigate(ROUTES.ORDER_DETAIL.replace(':id', order.id))}
                          title="Xem chi tiết"
                        >
                          <Eye size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
        <div className={styles.tableFooter}>
          <span className={styles.footerInfo}>
            Hiển thị {orders.length} / {totalElements} đơn hàng
          </span>
          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button className={styles.pageBtn} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>‹</button>
              <span className={styles.pageInfo}>Trang {page + 1} / {totalPages}</span>
              <button className={styles.pageBtn} disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>›</button>
            </div>
          )}
        </div>
      </div>

      <ExportOrdersModal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
        currentFilters={{ keyword, status: statusFilter, channelId: channelFilter, from: fromDate, to: toDate }}
      />
    </div>
  );
};

export default OrderListPage;
