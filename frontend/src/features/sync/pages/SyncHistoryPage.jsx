import { useState, useEffect } from 'react';
import { Loader2 } from 'lucide-react';
import syncApi from '../../../api/syncApi';
import channelApi from '../../../api/channelApi';
import Badge from '../../../shared/components/Badge';
import styles from './SyncHistoryPage.module.css';

const SyncHistoryPage = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  
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

  const fetchLogs = async () => {
    try {
      setLoading(true);
      const params = {
        page,
        size: 20
      };
      if (statusFilter) params.status = statusFilter;
      if (channelFilter) params.channelId = channelFilter;
      
      const res = await syncApi.getLogs(params);
      const data = res.data?.data || res.data || res;
      setLogs(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (error) {
      console.error('Lỗi khi tải lịch sử đồng bộ:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [page, statusFilter, channelFilter]);

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

  return (
    <div className={styles.page}>
      <div className={styles.header}>
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
                    logs.map((log) => (
                      <tr key={log.id}>
                        <td>
                          {new Date(log.startedAt).toLocaleString('vi-VN')}
                        </td>
                        <td>
                          <div className={styles.productName}>{log.productName || '-'}</div>
                          {log.productSku && <div className={styles.productSku}>{log.productSku}</div>}
                        </td>
                        <td>
                          {getChannelBadge(log.platform)}
                          {log.channelName && <div className={styles.productSku}>{log.channelName}</div>}
                        </td>
                        <td>{getStatusBadge(log.status)}</td>
                        <td>
                          {log.status === 'SYNCED' ? (
                            <span style={{ color: '#166534', fontSize: '13px' }}>Đồng bộ {log.successCount}/{log.totalItems} sản phẩm</span>
                          ) : (
                            <div className={styles.errorText}>{log.errorSummary || 'Lỗi không xác định'}</div>
                          )}
                        </td>
                        <td>{log.triggeredByEmail || 'Hệ thống'}</td>
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

export default SyncHistoryPage;

