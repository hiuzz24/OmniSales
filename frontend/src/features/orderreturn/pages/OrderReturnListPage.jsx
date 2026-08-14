import { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Eye,
  PackageSearch,
  RefreshCw,
  RotateCcw,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import orderReturnApi from '../../../api/orderReturnApi';
import { ROUTES } from '../../../app/router/routes';
import PageHeader from '../../../shared/components/PageHeader';
import {
  formatExternalReturnId,
  formatPlatformLabel,
  formatReturnDateTime,
  formatReturnErrorMessage,
  ORDER_RETURN_STATUS_LABELS,
  RETURN_ACTION_LABELS,
  RETURN_ACTION_STATE_LABELS,
} from '../utils/orderReturnDisplay';
import styles from './OrderReturnListPage.module.css';

const PAGE_SIZE = 6;

/** Hiển thị danh sách phiếu trả hàng và polling phiếu mới mỗi năm giây. */
const OrderReturnListPage = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [lastLoadedAt, setLastLoadedAt] = useState(null);

  // Làm mới danh sách trả hàng có phân trang để polling hiển thị phiếu mới từ platform.
  const load = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      setResult(await orderReturnApi.getAll({ page, size: PAGE_SIZE }));
      setLastLoadedAt(new Date());
    } catch (error) {
      if (!silent) {
        toast.error(error.response?.data?.message || 'Không thể tải danh sách trả hàng');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    const initialLoadId = window.setTimeout(() => load(), 0);
    return () => window.clearTimeout(initialLoadId);
  }, [load]);

  useEffect(() => {
    const pollingId = window.setInterval(() => load(true), 5000);
    return () => window.clearInterval(pollingId);
  }, [load]);

  const rows = result.content ?? [];
  const totalPages = Math.max(result.totalPages ?? 0, 1);
  // Điều hướng tới chi tiết phiếu trả được chọn.
  const openDetail = (returnId) => {
    navigate(ROUTES.ORDER_RETURN_DETAIL.replace(':id', returnId));
  };

  return (
    <main className={`${styles.page} product-workspace`}>
      <div className={styles.pageHeaderShell}>
        <PageHeader
          title="Trả hàng"
          subtitle="Theo dõi yêu cầu hoàn, kiểm hàng và nhập lại tồn đủ điều kiện"
          icon={<RotateCcw size={20} />}
          actions={(
            <button
              type="button"
              className={styles.headerActionBtn}
              onClick={() => load()}
              disabled={loading}
            >
              <RefreshCw size={16} className={loading ? styles.spinning : undefined} />
              Làm mới
            </button>
          )}
        />
      </div>

      <section className={styles.listPanel} aria-labelledby="return-list-title">
        <header className={styles.panelHeader}>
          <div className={styles.panelTitleRow}>
            <div className={styles.panelHeadingGroup}>
              <span className={styles.panelHeadingIcon}><PackageSearch size={17} /></span>
              <div>
                <h2 id="return-list-title">Danh sách yêu cầu trả hàng</h2>
                <p>Theo dõi trạng thái xử lý theo từng sàn bán hàng.</p>
              </div>
            </div>
            <div className={styles.panelMeta}>
              {lastLoadedAt && (
                <span className={styles.syncTimestamp}>
                  <Clock3 size={14} />
                  Cập nhật {lastLoadedAt.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                </span>
              )}
              <span className={styles.totalCount}>{result.totalElements ?? 0}</span>
            </div>
          </div>
        </header>

        <div className={styles.tableScroll}>
          <table className={`${styles.returnTable} ${rows.length === PAGE_SIZE ? styles.tableFilled : ''}`}>
            <thead>
              <tr>
                <th>Mã trả hàng</th>
                <th>Đơn hàng</th>
                <th>Sàn / Kênh</th>
                <th>Trạng thái</th>
                <th>Trạng thái xử lý</th>
                <th>Cập nhật</th>
                <th className={styles.actionColumn}><span className={styles.srOnly}>Thao tác</span></th>
              </tr>
            </thead>
            <tbody>
              {loading && Array.from({ length: 4 }, (_, index) => (
                <tr key={`skeleton-${index}`} className={styles.skeletonRow}>
                  {Array.from({ length: 7 }, (__, cellIndex) => (
                    <td key={cellIndex}><span className={styles.skeletonLine} /></td>
                  ))}
                </tr>
              ))}

              {!loading && rows.length === 0 && (
                <tr>
                  <td colSpan="7" className={styles.emptyCell}>
                    <PackageSearch size={30} />
                    <strong>Chưa có yêu cầu trả hàng</strong>
                    <span>Yêu cầu mới từ sàn sẽ xuất hiện tại đây.</span>
                  </td>
                </tr>
              )}

              {!loading && rows.map((item) => {
                const issueMessage = formatReturnErrorMessage(item.actionError || item.lastSyncError);
                return (
                  <tr key={item.id} className={styles.dataRow}>
                    <td>
                      <button
                        type="button"
                        className={styles.returnCodeButton}
                        title={item.externalReturnId}
                        onClick={() => openDetail(item.id)}
                      >
                        {formatExternalReturnId(item.externalReturnId)}
                      </button>
                      <span className={styles.cellHint}>{formatReturnDateTime(item.createdAt)}</span>
                    </td>
                    <td>
                      <span className={styles.orderCode}>{item.externalOrderId || '-'}</span>
                    </td>
                    <td>
                      <span className={`${styles.platformBadge} ${styles[item.platform?.toLowerCase()]}`}>
                        {formatPlatformLabel(item.platform)}
                      </span>
                      <span className={styles.cellHint} title={item.channelName}>{item.channelName || '-'}</span>
                    </td>
                    <td>
                      <span className={`${styles.statusBadge} ${styles[item.status?.toLowerCase()]}`}>
                        {ORDER_RETURN_STATUS_LABELS[item.status] ?? item.status}
                      </span>
                    </td>
                    <td>
                      <div className={styles.actionCell}>
                        <span className={`${styles.actionBadge} ${styles[`action_${item.actionState?.toLowerCase()}`]}`}>
                          {RETURN_ACTION_STATE_LABELS[item.actionState] ?? item.actionState ?? '-'}
                        </span>
                        {item.lastAction && (
                          <span className={styles.cellHint}>{RETURN_ACTION_LABELS[item.lastAction] ?? item.lastAction}</span>
                        )}
                        {issueMessage && (
                          <span className={styles.rowIssue} title={issueMessage}>
                            <AlertTriangle size={12} /> {issueMessage}
                          </span>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={styles.dateValue}>{formatReturnDateTime(item.updatedAt)}</span>
                    </td>
                    <td className={styles.actionColumn}>
                      <button
                        type="button"
                        className={styles.iconButton}
                        title="Xem chi tiết"
                        aria-label={`Xem yêu cầu trả hàng ${formatExternalReturnId(item.externalReturnId)}`}
                        onClick={() => openDetail(item.id)}
                      >
                        <Eye size={17} />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <footer className={styles.pagination}>
          <span>Trang <strong>{Math.min(page + 1, totalPages)}</strong> / {totalPages}</span>
          <div className={styles.paginationActions}>
            <button
              type="button"
              className={styles.paginationButton}
              disabled={page === 0}
              onClick={() => setPage((value) => value - 1)}
              aria-label="Trang trước"
              title="Trang trước"
            >
              <ChevronLeft size={17} />
            </button>
            <button
              type="button"
              className={styles.paginationButton}
              disabled={page + 1 >= (result.totalPages ?? 0)}
              onClick={() => setPage((value) => value + 1)}
              aria-label="Trang sau"
              title="Trang sau"
            >
              <ChevronRight size={17} />
            </button>
          </div>
        </footer>
      </section>
    </main>
  );
};

export default OrderReturnListPage;
