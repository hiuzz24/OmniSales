import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Clock, Plus, RefreshCw, Trash2, User, Search } from 'lucide-react';
import productLogApi from '../../../api/productLogApi';
import { ROUTES } from '../../../app/router/routes';
import Pagination from '../../../shared/components/Pagination';
import styles from './ProductLogPage.module.css';

const ACTION_CONFIG = {
  CREATE: {
    label: 'Tạo mới',
    className: 'badgeCreate',
    icon: <Plus size={13} />,
    color: '#16a34a',
    bg: '#dcfce7',
  },
  UPDATE: {
    label: 'Cập nhật',
    className: 'badgeUpdate',
    icon: <RefreshCw size={13} />,
    color: '#d97706',
    bg: '#fef3c7',
  },
  DELETE: {
    label: 'Xóa',
    className: 'badgeDelete',
    icon: <Trash2 size={13} />,
    color: '#dc2626',
    bg: '#fee2e2',
  },
};

/** Trả về cấu hình nhãn và màu tương ứng với loại thay đổi Product. */
const getActionBadge = (action) => {
  const config = ACTION_CONFIG[action] || { label: action, className: '', icon: null, color: '#6b7280', bg: '#f3f4f6' };
  return (
    <span className={styles.badge} style={{ color: config.color, backgroundColor: config.bg }}>
      {config.icon}
      {config.label}
    </span>
  );
};

/** Hiển thị một chỉ số tổng hợp trong lịch sử sản phẩm. */
const StatCard = ({ icon, label, value, color }) => (
  <div className={styles.statCard} style={{ '--stat-color': color }}>
    <div className={styles.statIcon}>{icon}</div>
    <div className={styles.statContent}>
      <div className={styles.statValue}>{value}</div>
      <div className={styles.statLabel}>{label}</div>
    </div>
  </div>
);

/** Hiển thị lịch sử thay đổi sản phẩm và biến thể theo bộ lọc. */
const ProductLogPage = () => {
  const navigate = useNavigate();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [stats, setStats] = useState({ total: 0, create: 0, update: 0, delete: 0 });

  // Tải trang product log hiện tại theo từ khóa và loại hành động.
  const fetchLogs = async () => {
    try {
      setLoading(true);
      const res = await productLogApi.getAll({ page, size: 20 });
      const data = res.data?.data || res.data || res;
      const logList = data.content || [];
      setLogs(logList);
      setTotalPages(data.totalPages || 0);

      // Calculate stats from current batch
      setStats(prev => ({
        total: data.totalElements || prev.total,
        create: logList.filter(l => l.action === 'CREATE').length + (page === 0 ? 0 : prev.create),
        update: logList.filter(l => l.action === 'UPDATE').length + (page === 0 ? 0 : prev.update),
        delete: logList.filter(l => l.action === 'DELETE').length + (page === 0 ? 0 : prev.delete),
      }));
    } catch (error) {
      console.error('Lỗi khi tải lịch sử:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [page]);

  // Định dạng thời gian log theo múi giờ hiển thị của trình duyệt.
  const formatTime = (dateStr) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now - date;
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'Vừa xong';
    if (diffMin < 60) return `${diffMin} phút trước`;
    const diffHrs = Math.floor(diffMin / 60);
    if (diffHrs < 24) return `${diffHrs} giờ trước`;
    return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.PRODUCTS)}>
          <ArrowLeft size={18} />
          <span>Quay lại</span>
        </button>
      </div>

      <div className={styles.pageHeader}>
        <div className={styles.headerLeft}>
          <div className={styles.headerIcon}>
            <Clock size={22} />
          </div>
          <div>
            <h1 className={styles.title}>Lịch sử sản phẩm</h1>
            <p className={styles.subtitle}>Theo dõi tất cả thay đổi: tạo mới, cập nhật, xóa sản phẩm</p>
          </div>
        </div>
        <div className={styles.headerRight}>
          <div className={styles.searchBox}>
            <Search size={16} />
            <input placeholder="Tìm kiếm sản phẩm..." className={styles.searchInput} />
          </div>
        </div>
      </div>

      <div className={styles.statsRow}>
        <StatCard icon={<Clock size={18} />} label="Tổng hoạt động" value={stats.total} color="#6366f1" />
        <StatCard icon={<Plus size={18} />} label="Tạo mới" value={logs.filter(l => l.action === 'CREATE').length} color="#16a34a" />
        <StatCard icon={<RefreshCw size={18} />} label="Cập nhật" value={logs.filter(l => l.action === 'UPDATE').length} color="#d97706" />
        <StatCard icon={<Trash2 size={18} />} label="Xóa" value={logs.filter(l => l.action === 'DELETE').length} color="#dc2626" />
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.cardTitle}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            Nhật ký hoạt động
          </div>
          <span className={styles.logCount}>{logs.length} bản ghi / trang</span>
        </div>

        {loading && logs.length === 0 ? (
          <div className={styles.loadingState}>
            <div className={styles.loadingDots}><span/><span/><span/></div>
            <p>Đang tải dữ liệu...</p>
          </div>
        ) : logs.length === 0 ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>
              <Clock size={40} />
            </div>
            <p>Chưa có lịch sử nào</p>
            <span>Thay đổi sản phẩm sẽ xuất hiện tại đây</span>
          </div>
        ) : (
          <>
            <div className={styles.tableWrapper}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th style={{ width: 160 }}>Thời gian</th>
                    <th style={{ width: 110 }}>Hành động</th>
                    <th>Sản phẩm</th>
                    <th style={{ width: 180 }}>SKU / Variant</th>
                    <th style={{ width: 180 }}>Người thực hiện</th>
                    <th>Chi tiết thay đổi</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id} className={styles.tr}>
                      <td>
                        <div className={styles.timeCell}>
                          <div className={styles.timeAbsolute}>
                            {new Date(log.performedAt).toLocaleDateString('vi-VN', {
                              day: '2-digit', month: '2-digit', year: 'numeric'
                            })}
                          </div>
                          <div className={styles.timeRelative}>
                            {formatTime(log.performedAt)}
                          </div>
                        </div>
                      </td>
                      <td>{getActionBadge(log.action)}</td>
                      <td>
                        <div className={styles.productCell}>
                          <div className={styles.productAvatar}>
                            {(log.productName || 'S')[0].toUpperCase()}
                          </div>
                          <span className={styles.productName}>{log.productName || '—'}</span>
                        </div>
                      </td>
                      <td>
                        <code className={styles.skuCode}>{log.productSku || '—'}</code>
                        {log.variantSku && (
                          <div className={styles.variantSku}>Variant: {log.variantSku}</div>
                        )}
                      </td>
                      <td>
                        <div className={styles.userCell}>
                          <div className={styles.userAvatar}>
                            <User size={12} />
                          </div>
                          <span className={styles.userEmail}>{log.performedByEmail || 'Hệ thống'}</span>
                        </div>
                      </td>
                      <td>
                        {log.notes ? (
                          <div className={styles.notes}>{log.notes}</div>
                        ) : log.fieldChanges?.message ? (
                          <div className={styles.notes + ' ' + styles.notesChange}>
                            {log.fieldChanges.message}
                          </div>
                        ) : (
                          <span className={styles.noNotes}>—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={stats.total}
              pageSize={20}
              currentCount={logs.length}
              itemLabel="bản ghi"
              onPageChange={setPage}
            />
            {totalPages < 0 && (
              <div className={styles.pagination}>
                <div className={styles.paginationInfo}>
                  Trang <strong>{page + 1}</strong> / {totalPages}
                </div>
                <div className={styles.paginationControls}>
                  <button
                    className={styles.pageBtn}
                    disabled={page === 0}
                    onClick={() => setPage(prev => prev - 1)}
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 18l-6-6 6-6"/></svg>
                    Trước
                  </button>
                  {Array.from({ length: totalPages }, (_, i) => i).map(num => (
                    <button
                      key={num}
                      className={`${styles.pageBtn} ${num === page ? styles.pageBtnActive : ''}`}
                      onClick={() => setPage(num)}
                    >
                      {num + 1}
                    </button>
                  ))}
                  <button
                    className={styles.pageBtn}
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage(prev => prev + 1)}
                  >
                    Sau
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 18l6-6-6-6"/></svg>
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default ProductLogPage;
