import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import productLogApi from '../../../api/productLogApi';
import { ROUTES } from '../../../app/router/routes';
import styles from './ProductLogPage.module.css';

const ProductLogPage = () => {
  const navigate = useNavigate();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchLogs = async () => {
    try {
      setLoading(true);
      const res = await productLogApi.getAll({ page, size: 20 });
      const data = res.data?.data || res.data || res;
      setLogs(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (error) {
      console.error('Lỗi khi tải lịch sử:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [page]);

  const getActionBadge = (action) => {
    switch (action) {
      case 'CREATE': return <span className={`${styles.badge} ${styles.badgeCreate}`}>TẠO MỚI</span>;
      case 'UPDATE': return <span className={`${styles.badge} ${styles.badgeUpdate}`}>CẬP NHẬT</span>;
      case 'DELETE': return <span className={`${styles.badge} ${styles.badgeDelete}`}>XÓA</span>;
      default: return <span className={styles.badge}>{action}</span>;
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.PRODUCTS)}>
          <ArrowLeft size={20} />
          <span>Quay lại</span>
        </button>
        <div>
          <h1 className={styles.title}>Lịch sử sản phẩm</h1>
          <p className={styles.subtitle}>Theo dõi tất cả các thay đổi của sản phẩm</p>
        </div>
      </div>

      <div className={styles.card}>
        {loading && logs.length === 0 ? (
          <div className={styles.loadingState}>
            <Loader2 className={styles.spinner} />
            <p>Đang tải dữ liệu...</p>
          </div>
        ) : (
          <>
            <div className={styles.tableWrapper}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Hành động</th>
                    <th>Tên sản phẩm</th>
                    <th>SKU / Variant SKU</th>
                    <th>Người thực hiện</th>
                    <th>Ghi chú</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.length === 0 ? (
                    <tr>
                      <td colSpan="6" className={styles.emptyState}>Chưa có lịch sử nào</td>
                    </tr>
                  ) : (
                    logs.map((log) => (
                      <tr key={log.id}>
                        <td>
                          {new Date(log.performedAt).toLocaleString('vi-VN')}
                        </td>
                        <td>{getActionBadge(log.action)}</td>
                        <td>{log.productName || '-'}</td>
                        <td>
                          <div className={styles.skuPrimary}>{log.productSku || '-'}</div>
                          {log.variantSku && <div className={styles.skuSecondary}>Variant: {log.variantSku}</div>}
                        </td>
                        <td>{log.performedByEmail || 'Hệ thống'}</td>
                        <td>
                          <div>{log.notes || '-'}</div>
                          {log.fieldChanges?.message && (
                            <div className={styles.changesMsg}>{log.fieldChanges.message}</div>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            
            {totalPages > 1 && (
              <div className={styles.pagination}>
                <button 
                  className={styles.pageBtn} 
                  disabled={page === 0}
                  onClick={() => setPage(prev => prev - 1)}
                >
                  Trước
                </button>
                <span className={styles.pageInfo}>
                  Trang {page + 1} / {totalPages}
                </span>
                <button 
                  className={styles.pageBtn} 
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(prev => prev + 1)}
                >
                  Sau
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default ProductLogPage;
