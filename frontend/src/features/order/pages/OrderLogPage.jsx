import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Filter, Loader2, FileText } from 'lucide-react';
import orderLogApi from '../../../api/orderLogApi';
import { ROUTES } from '../../../app/router/routes';
import Pagination from '../../../shared/components/Pagination';
import styles from './OrderLogPage.module.css';

const ACTION_BADGE_MAP = {
  CREATE:         { label: 'Create',  className: 'badgeGreen' },
  UPDATE:         { label: 'Update',  className: 'badgeBlue' },
  DELETE:         { label: 'Delete',  className: 'badgeRed' },
  STATUS_CHANGE:  { label: 'Status',  className: 'badgeBlue' },
  ITEM_UPDATE:    { label: 'Items',  className: 'badgePurple' },
  CUSTOMER_UPDATE:{ label: 'Customer',className: 'badgeGreen' },
  NOTES_ADD:      { label: 'Notes',  className: 'badgeSlate' },
  ORDER_CANCEL:   { label: 'Cancel', className: 'badgeRed' },
  ORDER_IMPORT:   { label: 'Import', className: 'badgeTeal' },
  SYNC_UPDATE:    { label: 'Sync',  className: 'badgeYellow' },
  PAYMENT_UPDATE: { label: 'Payment',className: 'badgeOrange' },
};

const OrderLogPage = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const fetchLogs = async (pg = page) => {
    setLoading(true);
    try {
      const params = { page: pg, size: 20 };
      if (dateFrom) params.from = `${dateFrom}T00:00:00Z`;
      if (dateTo) params.to = `${dateTo}T23:59:59Z`;

      const res = await orderLogApi.getAll(params);
      const data = res?.data?.data || res?.data || res;
      setLogs(data?.content || []);
      setTotalPages(data?.totalPages || 0);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      console.error('Lỗi khi tải lịch sử đơn hàng:', err);
      setLogs([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchLogs(0); }, [dateFrom, dateTo]);
  useEffect(() => { fetchLogs(page); }, [page]);

  const handleReset = () => {
    setDateFrom('');
    setDateTo('');
    setPage(0);
  };

  const getBadge = (action) => {
    const cfg = ACTION_BADGE_MAP[action] || { label: action, className: 'badgeSlate' };
    return (
      <span className={`${styles.badge} ${styles[cfg.className]}`}>
        {cfg.label}
      </span>
    );
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  };

  const ORDER_STATUS_STYLE_MAP = {
    PENDING:       { bg: '#fef9c3', color: '#854d0e', label: 'Chờ xử lý' },
    CONFIRMED:     { bg: '#dbeafe', color: '#1e40af', label: 'Đã xác nhận' },
    PROCESSING:    { bg: '#ede9fe', color: '#6d28d9', label: 'Đang xử lý' },
    SHIPPED:       { bg: '#e0f2fe', color: '#075985', label: 'Sẵn sàng giao' },
    IN_TRANSIT:    { bg: '#e0f2fe', color: '#0369a1', label: 'Đang vận chuyển' },
    DELIVERED:     { bg: '#dcfce7', color: '#166534', label: 'Đã giao hàng' },
    CANCELLED:     { bg: '#fee2e2', color: '#b91c1c', label: 'Đã hủy' },
  };

  const PAYMENT_STATUS_STYLE_MAP = {
    UNPAID:   { bg: '#fef9c3', color: '#854d0e', label: 'Chưa thanh toán' },
    PAID:     { bg: '#dcfce7', color: '#166534', label: 'Đã thanh toán' },
    REFUNDED: { bg: '#f1f5f9', color: '#475569', label: 'Đã hoàn tiền' },
  };

  const getOrderStatusStyle = (s) => {
    if (!s) return null;
    return ORDER_STATUS_STYLE_MAP[s] || { bg: '#f1f5f9', color: '#475569', label: s };
  };

  const getPaymentStatusStyle = (s) => {
    if (!s) return null;
    return PAYMENT_STATUS_STYLE_MAP[s] || { bg: '#f1f5f9', color: '#475569', label: s };
  };

  const StatusBadge = ({ status, isPayment }) => {
    const styleMap = isPayment ? getPaymentStatusStyle(status) : getOrderStatusStyle(status);
    if (!styleMap) return <span style={{ color: '#94a3b8', fontSize: '12px' }}>—</span>;
    return (
      <span style={{
        display: 'inline-flex', alignItems: 'center',
        padding: '3px 10px', borderRadius: '6px',
        fontSize: '11.5px', fontWeight: 700,
        background: styleMap.bg, color: styleMap.color,
        whiteSpace: 'nowrap', border: '1px solid transparent',
      }}>
        {styleMap.label}
      </span>
    );
  };

  const getChangesData = (changes) => {
    if (!changes) return { oldStatus: null, newStatus: null, isPayment: false, extra: null };

    const oldStatus  = changes.oldStatus          || changes.old_status;
    const newStatus  = changes.newStatus          || changes.new_status;
    const oldPay     = changes.oldPaymentStatus;
    const newPay     = changes.newPaymentStatus;

    if (oldPay !== undefined || newPay !== undefined) {
      const extra = { ...changes };
      delete extra.oldPaymentStatus;
      delete extra.newPaymentStatus;
      return { oldStatus: oldPay, newStatus: newPay, isPayment: true, extra: extra };
    }

    if (oldStatus !== undefined || newStatus !== undefined) {
      const extra = { ...changes };
      delete extra.oldStatus;
      delete extra.newStatus;
      delete extra.old_status;
      delete extra.new_status;
      return { oldStatus, newStatus, isPayment: false, extra: extra };
    }

    return { oldStatus: null, newStatus: null, isPayment: false, extra: changes };
  };

  const formatChanges = (changes) => {
    if (!changes) return null;
    if (typeof changes !== 'object') return String(changes);
    const entries = Object.entries(changes);
    if (entries.length === 0) return null;
    return entries.map(([k, v]) => `${k}: ${v}`).join(', ');
  };

  return (
    <div className={styles.page}>
      {/* Header */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <Link to={ROUTES.ORDER_LIST} className={styles.backBtn}>
            <ArrowLeft size={16} />
            Quay lại
          </Link>
          <div>
            <h1 className={styles.title}>Order Logs</h1>
            <p className={styles.subtitle}>Lịch sử thay đổi của tất cả đơn hàng</p>
          </div>
        </div>
      </div>

      {/* Stat strip */}
      <div className={styles.statStrip}>
        <div className={styles.statCard}>
          <span className={styles.statLabel}>Tổng bản ghi</span>
          <span className={`${styles.statValue} ${styles.statValueBlue}`}>{totalElements.toLocaleString('vi-VN')}</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statLabel}>Trang hiện tại</span>
          <span className={styles.statValue}>{page + 1} / {totalPages || 1}</span>
        </div>
        <div className={styles.statCard}>
          <span className={styles.statLabel}>Hiển thị / trang</span>
          <span className={`${styles.statValue} ${styles.statValueGreen}`}>{logs.length}</span>
        </div>
      </div>

      {/* Filters Card */}
      <div className={styles.filterCard}>
        <div className={styles.filterCardHeader}>
          <div className={styles.filterTitle}>
            <Filter size={14} />
            Bộ lọc
          </div>
          <button className={styles.resetBtn} onClick={handleReset}>Đặt lại</button>
        </div>
        <div className={styles.filterGrid}>
          {/* Date range */}
          <div className={styles.filterField}>
            <label className={styles.filterLabel}>Khoảng thời gian</label>
            <div className={styles.dateRange}>
              <input
                type="date"
                className={styles.dateInput}
                value={dateFrom}
                onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
              />
              <span className={styles.dateSep}>—</span>
              <input
                type="date"
                className={styles.dateInput}
                value={dateTo}
                onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Table Card */}
      <div className={styles.tableCard}>
        <div className={styles.tableCardHeader}>
          <span className={styles.tableTitle}>
            Lịch sử thay đổi ({totalElements} bản ghi)
          </span>
        </div>
        <div className={styles.tableWrap}>
          {loading ? (
            <div className={styles.loadingCell}>
              <Loader2 size={20} className={styles.spinner} />
              Đang tải...
            </div>
          ) : logs.length === 0 ? (
            <div className={styles.emptyCell}>
              <div className={styles.emptyIcon}>
                <FileText size={22} />
              </div>
              Chưa có thay đổi nào.
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Đơn hàng</th>
                  <th>Hành động</th>
                  <th>Người thực hiện</th>
                  <th>Trạng thái cũ</th>
                  <th>Trạng thái mới</th>
                  <th>Chi tiết thay đổi</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => {
                  const { oldStatus, newStatus, isPayment, extra } = getChangesData(log.changes);
                  return (
                  <tr key={log.id} className={styles.tableRow}>
                    <td className={styles.tdTime}>{formatDate(log.performedAt)}</td>
                    <td>
                      <div className={styles.entityCell}>
                        <span className={styles.entityName}>{log.entityName || '-'}</span>
                        {log.entityId && (
                          <span className={styles.entityId}>
                            #{log.entityId.slice(0, 8)}
                          </span>
                        )}
                      </div>
                    </td>
                    <td>{getBadge(log.action)}</td>
                    <td className={styles.tdActor}>{log.actorEmail || '-'}</td>
                    <td>
                      <StatusBadge status={oldStatus} isPayment={isPayment} />
                    </td>
                    <td>
                      <div className={styles.statusTransition}>
                        <StatusBadge status={newStatus} isPayment={isPayment} />
                      </div>
                    </td>
                    <td className={styles.tdNotes}>{formatChanges(extra)}</td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination */}
        {!loading && totalElements > 0 && (
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={20}
            currentCount={logs.length}
            itemLabel="bản ghi"
            onPageChange={setPage}
          />
        )}
        {totalPages < 0 && (
          <div className={styles.pagination}>
            <span className={styles.pageInfo}>
              Hiển thị {logs.length} / {totalElements} bản ghi
            </span>
            <div className={styles.pageBtns}>
              <button
                className={styles.pageBtn}
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </button>
              <span className={styles.pageInfoBold}>{page + 1} / {totalPages || 1}</span>
              <button
                className={styles.pageBtn}
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
                <ArrowRight size={13} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default OrderLogPage;
