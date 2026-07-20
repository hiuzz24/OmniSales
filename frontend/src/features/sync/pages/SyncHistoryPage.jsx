import { useState, useEffect, useCallback } from 'react';
import { Loader2, ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import syncApi from '../../../api/syncApi';
import channelApi from '../../../api/channelApi';
import Badge from '../../../shared/components/Badge';
import Pagination from '../../../shared/components/Pagination';
import styles from './SyncHistoryPage.module.css';

const PAGE_SIZE = 20;

const JOB_TYPE_LABELS = {
  SHOPIFY_REMOTE_IMPORT_SYNC: 'Đồng bộ từ Shopify về ứng dụng',
  LAZADA_IMPORT: 'Đồng bộ từ Lazada về ứng dụng',
  SHOPIFY_LOCAL_CHANGES_SYNC: 'Đồng bộ từ ứng dụng lên Shopify',
  LAZADA_LOCAL_CHANGES_SYNC: 'Đồng bộ từ ứng dụng lên Lazada',
  PRODUCT_SYNC: 'Đồng bộ sản phẩm',
};

const SyncHistoryPage = () => {
  const navigate = useNavigate();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  
  const [statusFilter, setStatusFilter] = useState('');
  const [channelFilter, setChannelFilter] = useState('');
  const [channels, setChannels] = useState([]);

  useEffect(() => {
    const fetchChannels = async () => {
      try {
        const res = await channelApi.getAll();
        const data = res.data?.data || res.data || res;
        setChannels(data);
      } catch (error) {
        console.error('Lỗi khi tải danh sách kênh:', error);
      }
    };
    fetchChannels();
  }, []);

  const fetchLogs = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        page,
        size: PAGE_SIZE
      };
      if (statusFilter) params.status = statusFilter;
      if (channelFilter) params.channelId = channelFilter;
      
      const res = await syncApi.getLogs(params);
      const data = res.data?.data || res.data || res;
      setLogs(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
    } catch (error) {
      console.error('Lỗi khi tải lịch sử đồng bộ:', error);
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, channelFilter]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      fetchLogs();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [fetchLogs]);

  const getStatusBadge = (status) => {
    switch (status) {
      case 'SYNCED': return <span className={styles.statusSynced}>THÀNH CÔNG</span>;
      case 'FAILED': return <span className={styles.statusFailed}>THẤT BẠI</span>;
      case 'PENDING': return <span className={styles.statusPending}>ĐANG XỬ LÝ</span>;
      default: return <span className={styles.statusPending}>{status}</span>;
    }
  };

  const getChannelBadge = (platform) => {
    switch (platform?.toUpperCase()) {
      case 'SHOPEE': return <Badge variant="shopee">Shopee</Badge>;
      case 'LAZADA': return <Badge variant="lazada">Lazada</Badge>;
      case 'TIKTOK': return <Badge variant="tiktok">TikTok</Badge>;
      case 'SHOPIFY': return <Badge variant="shopify">Shopify</Badge>;
      default: return <Badge variant="default">{platform}</Badge>;
    }
  };

  const getSyncSubject = (log) => {
    if (log.productName) {
      return {
        title: log.productName,
        subtitle: log.productSku,
      };
    }

    return {
      title: JOB_TYPE_LABELS[log.jobType] || log.jobType || 'Đồng bộ theo kênh',
      subtitle: log.channelName || 'Toàn bộ kênh bán',
    };
  };

  const getSyncDetail = (log) => {
    if (log.status !== 'SYNCED') {
      return <div className={styles.errorText}>{log.errorSummary || 'Lỗi không xác định'}</div>;
    }

    const inventoryChanges = log.inventoryChanges || [];
    if (inventoryChanges.length > 0) {
      return (
        <div className={styles.changeList}>
          {inventoryChanges.map((change) => (
            <div key={change.id} className={styles.changeItem}>
              {formatInventoryChange(change)}
            </div>
          ))}
        </div>
      );
    }

    const successCount = Number(log.successCount ?? 0).toLocaleString('vi-VN');
    const totalItems = Number(log.totalItems ?? 0).toLocaleString('vi-VN');
    return (
      <span style={{ color: '#166534', fontSize: '13px' }}>
        Đã xử lý {successCount}/{totalItems} mục
      </span>
    );
  };

  const formatInventoryChange = (change) => {
    const before = Number(change.quantityBefore ?? 0);
    const after = Number(change.quantityAfter ?? 0);
    const delta = Number(change.quantityChange ?? after - before);
    const direction = delta >= 0 ? 'lên' : 'giảm còn';
    const deltaText = delta > 0 ? `+${delta}` : `${delta}`;
    const productName = change.variantName || 'Sản phẩm';
    const sku = change.variantSku ? ` (${change.variantSku})` : '';
    const warehouseName = change.warehouseName || 'Chưa rõ kho';

    return `${productName}${sku} tại ${warehouseName}: tồn kho từ ${before} ${direction} ${after} (${deltaText})`;
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <button onClick={() => navigate(-1)} className={styles.backBtn}>
          <ArrowLeft size={20} />
          Quay lại
        </button>
        <h1 className={styles.title}>Lịch sử đồng bộ</h1>
        <p className={styles.subtitle}>Theo dõi quá trình đẩy dữ liệu lên các sàn thương mại điện tử</p>
      </div>

      <div className={styles.card}>
        <div className={styles.filterBar}>
          <select 
            className={styles.filterSelect}
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="SYNCED">Thành công</option>
            <option value="FAILED">Thất bại</option>
            <option value="PENDING">Đang xử lý</option>
          </select>

          <select 
            className={styles.filterSelect}
            value={channelFilter}
            onChange={(e) => { setChannelFilter(e.target.value); setPage(0); }}
          >
            <option value="">Tất cả kênh bán</option>
            {channels.map(ch => (
              <option key={ch.id} value={ch.id}>{ch.displayName}</option>
            ))}
          </select>
        </div>

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
                    <th>Sản phẩm</th>
                    <th>Kênh bán</th>
                    <th>Trạng thái</th>
                    <th>Chi tiết</th>
                    <th>Người thực hiện</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.length === 0 ? (
                    <tr>
                      <td colSpan="6" className={styles.emptyState}>Chưa có lịch sử đồng bộ nào</td>
                    </tr>
                  ) : (
                    logs.map((log) => {
                      const subject = getSyncSubject(log);
                      return (
                        <tr key={log.id}>
                          <td>
                            {new Date(log.startedAt).toLocaleString('vi-VN')}
                          </td>
                          <td>
                            <div className={styles.productName}>{subject.title}</div>
                            {subject.subtitle && <div className={styles.productSku}>{subject.subtitle}</div>}
                          </td>
                          <td>
                            {getChannelBadge(log.platform)}
                            {log.channelName && <div className={styles.productSku}>{log.channelName}</div>}
                          </td>
                          <td>{getStatusBadge(log.status)}</td>
                          <td>{getSyncDetail(log)}</td>
                          <td>{log.triggeredByEmail || 'Hệ thống'}</td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
            
            {totalPages > 1 && (
              <Pagination
                currentPage={page}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={PAGE_SIZE}
                currentCount={logs.length}
                itemLabel="lần đồng bộ"
                onPageChange={setPage}
              />
            )}
            {totalPages < 0 && (
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

export default SyncHistoryPage;

