import { useCallback, useEffect, useState } from 'react';
import { Eye, PackageCheck, RefreshCw, RotateCcw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import orderReturnApi from '../../../api/orderReturnApi';
import { ROUTES } from '../../../app/router/routes';
import { formatExternalReturnId } from '../utils/orderReturnDisplay';
import styles from './OrderReturn.module.css';

const STATUS_LABELS = {
  PENDING_APPROVAL: 'Chờ duyệt',
  REJECTED: 'Đã từ chối',
  AWAITING_RETURN: 'Chờ khách gửi hàng',
  RETURN_IN_TRANSIT: 'Đang hoàn về',
  INSPECTED: 'Đã kiểm hàng',
  PLATFORM_PROCESSING: 'Sàn đang xử lý',
  PENDING_STOCK: 'Chờ nhập kho',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Lỗi dữ liệu',
};

const OrderReturnListPage = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setResult(await orderReturnApi.getAll({ page, size: 5 }));
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể tải danh sách trả hàng');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  const rows = result.content ?? [];

  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.heading}>
          <span className={styles.headingIcon}><RotateCcw size={22} /></span>
          <div><h1>Trả hàng</h1><p>Theo dõi yêu cầu hoàn, kiểm hàng và nhập lại tồn đủ điều kiện.</p></div>
        </div>
        <button className={styles.secondaryButton} onClick={load} disabled={loading}><RefreshCw size={17} /> Làm mới</button>
      </header>
      <section className={styles.tableShell}>
        <div className={styles.sectionHeader}>
          <div><h2><PackageCheck size={18} /> Danh sách yêu cầu</h2><span>{result.totalElements ?? 0} yêu cầu trả hàng</span></div>
        </div>
        <div className={styles.tableViewport}>
          <table>
            <thead><tr><th>Mã return</th><th>Đơn hàng</th><th>Sàn / Kênh</th><th>Trạng thái</th><th>Xử lý API</th><th>Cập nhật</th><th aria-label="Chi tiết" /></tr></thead>
            <tbody>
              {loading ? <tr><td colSpan="7" className={styles.empty}>Đang tải dữ liệu...</td></tr>
                : rows.length === 0 ? <tr><td colSpan="7" className={styles.empty}>Chưa có yêu cầu trả hàng.</td></tr>
                  : rows.map((item) => (
                    <tr key={item.id}>
                      <td><strong title={item.externalReturnId}>{formatExternalReturnId(item.externalReturnId)}</strong></td>
                      <td>{item.externalOrderId}</td>
                      <td><span className={`${styles.platform} ${styles[item.platform?.toLowerCase()]}`}>{item.platform}</span><small>{item.channelName}</small></td>
                      <td><span className={`${styles.status} ${styles[item.status?.toLowerCase()]}`}>{STATUS_LABELS[item.status] ?? item.status}</span></td>
                      <td><span className={styles.actionState}>{item.actionState}</span>{item.actionError && <small className={styles.errorText}>{item.actionError}</small>}</td>
                      <td>{item.updatedAt ? new Date(item.updatedAt).toLocaleString('vi-VN') : '-'}</td>
                      <td><button className={styles.iconButton} title="Xem chi tiết" aria-label={`Xem return ${item.externalReturnId}`} onClick={() => navigate(ROUTES.ORDER_RETURN_DETAIL.replace(':id', item.id))}><Eye size={17} /></button></td>
                    </tr>
                  ))}
            </tbody>
          </table>
        </div>
        <footer className={styles.pagination}>
          <span>Trang {Math.min(page + 1, Math.max(result.totalPages ?? 1, 1))} / {Math.max(result.totalPages ?? 1, 1)}</span>
          <div><button disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Trước</button><button disabled={page + 1 >= (result.totalPages ?? 0)} onClick={() => setPage((value) => value + 1)}>Sau</button></div>
        </footer>
      </section>
    </main>
  );
};

export default OrderReturnListPage;
