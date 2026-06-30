import { useEffect, useState } from 'react';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Badge from '../../../shared/components/Badge';
import channelApi from '../../../api/channelApi';
import channelConnectionLogApi from '../../../api/channelConnectionLogApi';
import { ROUTES } from '../../../app/router/routes';
import styles from './ChannelConnectionHistoryPage.module.css';

const PLATFORM_LABELS = {
  SHOPEE: 'Shopee',
  LAZADA: 'Lazada',
  TIKTOK: 'TikTok',
  SHOPIFY: 'Shopify',
  MANUAL: 'Thủ công',
};

const ACTION_LABELS = {
  CONNECT: 'Kết nối',
  DISCONNECT: 'Ngắt kết nối',
  RECONNECT: 'Kết nối lại',
};

const STATUS_LABELS = {
  SUCCESS: 'Thành công',
  FAILED: 'Thất bại',
};

const ChannelConnectionHistoryPage = () => {
  const navigate = useNavigate();
  const [logs, setLogs] = useState([]);
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [platformFilter, setPlatformFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [channelFilter, setChannelFilter] = useState('');

  useEffect(() => {
    const fetchChannels = async () => {
      try {
        const response = await channelApi.getAll();
        const data = response.data?.data || response.data || response;
        setChannels(Array.isArray(data) ? data : []);
      } catch (error) {
        setChannels([]);
      }
    };
    fetchChannels();
  }, []);

  useEffect(() => {
    fetchLogs();
  }, [page, platformFilter, statusFilter, actionFilter, channelFilter]);

  const fetchLogs = async () => {
    try {
      setLoading(true);
      const params = { page, size: 20 };
      if (platformFilter) params.platform = platformFilter;
      if (statusFilter) params.status = statusFilter;
      if (actionFilter) params.action = actionFilter;
      if (channelFilter) params.channelId = channelFilter;

      const response = await channelConnectionLogApi.getAll(params);
      const data = response.data?.data || response.data || response;
      setLogs(data.content || []);
      setTotalPages(data.totalPages || 0);
    } catch (error) {
      setLogs([]);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  };

  const resetPage = (setter) => (event) => {
    setter(event.target.value);
    setPage(0);
  };

  const renderPlatform = (platform) => {
    const variant = platform ? platform.toLowerCase() : 'default';
    return <Badge variant={variant}>{PLATFORM_LABELS[platform] || platform || '-'}</Badge>;
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <button className={`${styles.actionBtn} ${styles.secondaryBtn}`} onClick={() => navigate(ROUTES.CHANNELS)}>
            <ArrowLeft size={16} />
            Quay lại kênh bán
          </button>
          <h1 className={styles.title}>Lịch sử kết nối</h1>
          <p className={styles.subtitle}>Theo dõi các lần kết nối, kết nối lại và ngắt kết nối platform</p>
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.filterBar}>
          <select className={styles.filterSelect} value={platformFilter} onChange={resetPage(setPlatformFilter)}>
            <option value="">Tất cả sàn</option>
            <option value="SHOPIFY">Shopify</option>
            <option value="LAZADA">Lazada</option>
            <option value="SHOPEE">Shopee</option>
            <option value="TIKTOK">TikTok</option>
            <option value="MANUAL">Thủ công</option>
          </select>
          <select className={styles.filterSelect} value={statusFilter} onChange={resetPage(setStatusFilter)}>
            <option value="">Tất cả trạng thái</option>
            <option value="SUCCESS">Thành công</option>
            <option value="FAILED">Thất bại</option>
          </select>
          <select className={styles.filterSelect} value={actionFilter} onChange={resetPage(setActionFilter)}>
            <option value="">Tất cả hành động</option>
            <option value="CONNECT">Kết nối</option>
            <option value="RECONNECT">Kết nối lại</option>
            <option value="DISCONNECT">Ngắt kết nối</option>
          </select>
          <select className={styles.filterSelect} value={channelFilter} onChange={resetPage(setChannelFilter)}>
            <option value="">Tất cả kênh</option>
            {channels.map(channel => (
              <option key={channel.id} value={channel.id}>{channel.displayName}</option>
            ))}
          </select>
        </div>

        {loading && logs.length === 0 ? (
          <div className={styles.loadingState}>
            <Loader2 className={styles.spinner} />
            <p>Đang tải lịch sử kết nối...</p>
          </div>
        ) : (
          <>
            <div className={styles.tableWrapper}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Sàn</th>
                    <th>Channel / Account</th>
                    <th>Loại entity</th>
                    <th>Hành động</th>
                    <th>Trạng thái</th>
                    <th>Message / Lỗi</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.length === 0 ? (
                    <tr>
                      <td colSpan="7" className={styles.emptyState}>Chưa có lịch sử kết nối nào</td>
                    </tr>
                  ) : logs.map(log => (
                    <tr key={log.id}>
                      <td>{log.createdAt ? new Date(log.createdAt).toLocaleString('vi-VN') : '-'}</td>
                      <td>{renderPlatform(log.platform)}</td>
                      <td>
                        <div className={styles.channelName}>{log.channelName || '-'}</div>
                        {log.channelId && <div className={styles.muted}>{log.channelId}</div>}
                      </td>
                      <td>{log.entityType || 'CHANNEL'}</td>
                      <td>{ACTION_LABELS[log.action] || log.action}</td>
                      <td>
                        <span className={log.status === 'SUCCESS' ? styles.statusSuccess : styles.statusFailed}>
                          {STATUS_LABELS[log.status] || log.status}
                        </span>
                      </td>
                      <td>
                        {log.status === 'FAILED' ? (
                          <div className={styles.errorText}>{log.errorMessage || log.message || 'Lỗi không xác định'}</div>
                        ) : (
                          <span>{log.message || '-'}</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className={styles.pagination}>
                <button className={styles.pageBtn} disabled={page === 0} onClick={() => setPage(prev => prev - 1)}>
                  Trước
                </button>
                <span className={styles.pageInfo}>Trang {page + 1} / {totalPages}</span>
                <button className={styles.pageBtn} disabled={page >= totalPages - 1} onClick={() => setPage(prev => prev + 1)}>
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

export default ChannelConnectionHistoryPage;
