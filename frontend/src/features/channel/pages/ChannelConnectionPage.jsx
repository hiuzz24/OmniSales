import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Cable,
  CircleCheck,
  Edit2,
  History,
  Layers3,
  Link2Off,
  PanelsTopLeft,
  Plus,
  RefreshCw,
  Store,
  Wifi,
  WifiOff,
} from 'lucide-react';
import { toast } from 'react-toastify';
import channelApi from '../../../api/channelApi';
import { ROUTES } from '../../../app/router/routes';
import ChannelFormModal from '../components/ChannelFormModal';
import DisconnectChannelDialog from '../components/DisconnectChannelDialog';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import styles from './ChannelConnectionPage.module.css';

const PLATFORM_META = {
  SHOPEE: { label: 'Shopee', color: '#ee4d2d', bg: '#fff1ee', abbr: 'S' },
  TIKTOK: { label: 'TikTok Shop', color: '#010101', bg: '#f0f0f0', abbr: 'T' },
  LAZADA: { label: 'Lazada', color: '#0f146d', bg: '#eef0ff', abbr: 'L' },
  SHOPIFY: { label: 'Shopify', color: '#96bf48', bg: '#f3f9ea', abbr: 'SH' },
  MANUAL: { label: 'Thủ công', color: '#6b7280', bg: '#f3f4f6', abbr: 'M' },
};

const connectionView = (channel) => {
  if (channel.connectionState === 'REVOKED') {
    return { connected: false, label: 'Seller đã thu hồi quyền' };
  }
  if (channel.connectionState === 'TOKEN_EXPIRED' || channel.status === 'ERROR') {
    return { connected: false, label: 'Cần kết nối lại' };
  }
  if (channel.status === 'CONNECTED' && (!channel.connectionState || channel.connectionState === 'CONNECTED')) {
    return { connected: true, label: 'Đã kết nối' };
  }
  return { connected: false, label: 'Ngắt kết nối' };
};

const ChannelConnectionPage = () => {
  const { user } = useAuth();
  const canManageChannels = user?.role === ROLES.OWNER;
  const [channels, setChannels] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState('create');
  const [selectedChannel, setSelectedChannel] = useState(null);
  const [confirmDisconnect, setConfirmDisconnect] = useState(null);
  const [isDisconnecting, setIsDisconnecting] = useState(false);

  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const loadChannels = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await channelApi.getAll();
      const data = res.data?.data || res.data || [];
      setChannels(Array.isArray(data) ? data : []);
    } catch {
      toast.error('Không thể tải danh sách kênh');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => { loadChannels(); }, [loadChannels]);

  useEffect(() => {
    const success = searchParams.get('success');
    const error = searchParams.get('error');

    if (success === 'true') {
      toast.success('🎉 Kết nối Shopify thành công! Kênh đã được thêm vào hệ thống.');
      navigate('/channels', { replace: true });
      loadChannels();
    } else if (success === 'lazada_connected') {
      toast.success('🎉 Kết nối Lazada thành công! Kênh đã được thêm vào hệ thống.');
      navigate('/channels', { replace: true });
      loadChannels();
    } else if (success === 'tiktok_connected') {
      toast.success('Kết nối TikTok Shop thành công! Kênh đã được thêm vào hệ thống.');
      navigate('/channels', { replace: true });
      loadChannels();
    } else if (error) {
      const msg = error === 'channel_identity_conflict'
        ? 'Không thể kết nối vì shop này đang thuộc nhiều kênh. Vui lòng kiểm tra dữ liệu kênh trùng.'
        : error === 'tiktok_oauth_failed'
          ? 'Kết nối TikTok Shop thất bại.'
          : error === 'oauth_failed' ? 'Kết nối Shopify thất bại.' : 'Kết nối kênh thất bại. Vui lòng thử lại.';
      toast.error(msg);
      navigate('/channels', { replace: true });
    }
  }, []);

  const openCreate = () => { setModalMode('create'); setSelectedChannel(null); setIsModalOpen(true); };
  const openEdit = (ch) => { setModalMode('edit'); setSelectedChannel(ch); setIsModalOpen(true); };

  const handleDisconnect = async () => {
    if (!confirmDisconnect) return;
    setIsDisconnecting(true);
    try {
      await channelApi.delete(confirmDisconnect.id);
      toast.success(`Đã ngắt kết nối kênh "${confirmDisconnect.displayName}"`);
      setConfirmDisconnect(null);
      loadChannels();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể ngắt kết nối kênh');
    } finally {
      setIsDisconnecting(false);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.headingGroup}>
          <span className={styles.headerIcon}><Cable aria-hidden="true" /></span>
          <div>
            <h1 className={styles.title}>Kênh Bán hàng</h1>
            <p className={styles.subtitle}>Quản lý các kênh thương mại điện tử đã kết nối với hệ thống</p>
          </div>
        </div>
        <div className={styles.headerActions}>
          <button type="button" className={`${styles.actionBtn} ${styles.secondaryBtn}`} onClick={() => navigate(ROUTES.CHANNEL_CONNECTION_HISTORY)}>
            <History size={16} />
            Lịch sử kết nối
          </button>
          {canManageChannels && (
            <button type="button" className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={openCreate}>
              <Plus size={16} />
              Thêm kênh mới
            </button>
          )}
        </div>
      </div>

      <div className={styles.statsRow}>
        <div className={styles.statCard}>
          <span className={`${styles.statIcon} ${styles.statIconBlue}`}><Layers3 aria-hidden="true" /></span>
          <div>
            <div className={styles.statValue}>{channels.length}</div>
            <div className={styles.statLabel}>Kênh đã kết nối</div>
          </div>
        </div>
        <div className={styles.statCard}>
          <span className={`${styles.statIcon} ${styles.statIconGreen}`}><CircleCheck aria-hidden="true" /></span>
          <div>
            <div className={styles.statValue}>
              {channels.filter(c => connectionView(c).connected).length}
            </div>
            <div className={styles.statLabel}>Đang hoạt động</div>
          </div>
        </div>
        <div className={styles.statCard}>
          <span className={`${styles.statIcon} ${styles.statIconIndigo}`}><PanelsTopLeft aria-hidden="true" /></span>
          <div>
            <div className={styles.statValue}>
              {[...new Set(channels.map(c => c.platform))].length}
            </div>
            <div className={styles.statLabel}>Nền tảng</div>
          </div>
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.cardHeaderIcon}><Store aria-hidden="true" /></span>
          <div>
            <h2 className={styles.cardTitle}>Danh sách kênh</h2>
            <p className={styles.cardSubtitle}>Theo dõi kết nối và cấu hình của từng gian hàng.</p>
          </div>
          <span className={styles.channelCount}>{channels.length}</span>
        </div>
        {isLoading ? (
          <div className={styles.loading}>
            <RefreshCw className={styles.loadingIcon} size={24} />
            <span>Đang tải danh sách kênh...</span>
          </div>
        ) : channels.length === 0 ? (
          <div className={styles.empty}>
            <Wifi size={48} className={styles.emptyIcon} />
            <p className={styles.emptyTitle}>Chưa có kênh nào được kết nối</p>
            <p className={styles.emptyDesc}>Nhấn "Thêm kênh mới" để bắt đầu đồng bộ sản phẩm lên các sàn TMĐT</p>
            {canManageChannels && (
              <button type="button" className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={openCreate}>
                <Plus size={16} /> Thêm kênh mới
              </button>
            )}
          </div>
        ) : (
          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Kênh</th>
                  <th>Nền tảng</th>
                  <th>Hoa hồng</th>
                  <th>Trạng thái</th>
                  <th>Ngày thêm</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {channels.map(ch => {
                  const meta = PLATFORM_META[ch.platform] || PLATFORM_META.MANUAL;
                  const connection = connectionView(ch);
                  const isConnected = connection.connected;
                  return (
                    <tr key={ch.id}>
                      <td>
                        <div className={styles.channelCell}>
                          <div
                            className={styles.platformAvatar}
                            style={{ color: meta.color, background: meta.bg }}
                          >
                            {meta.abbr}
                          </div>
                          <div>
                            <div className={styles.channelName}>{ch.displayName}</div>
                            <div className={styles.channelMeta}>{ch.metadata?.shopDomain || ''}</div>
                            {!isConnected && ch.refreshError && (
                              <div className={styles.refreshError} title={ch.refreshError}>{ch.refreshError}</div>
                            )}
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className={styles.platformTag} style={{ color: meta.color, background: meta.bg }}>
                          {meta.label}
                        </span>
                      </td>
                      <td className={styles.commissionCell}>
                        {ch.commissionRate != null ? `${ch.commissionRate}%` : '—'}
                      </td>
                      <td>
                        <span className={`${styles.statusBadge} ${isConnected ? styles.connected : styles.disconnected}`}>
                          {isConnected ? <Wifi size={12} /> : <WifiOff size={12} />}
                          {connection.label}
                        </span>
                      </td>
                      <td className={styles.dateCell}>
                        {ch.createdAt ? new Date(ch.createdAt).toLocaleDateString('vi-VN') : '—'}
                      </td>
                      <td>
                        {canManageChannels && (
                          <div className={styles.actionBtns}>
                            <button
                              type="button"
                              className={styles.editBtn}
                              onClick={() => openEdit(ch)}
                              title="Chỉnh sửa"
                            >
                              <Edit2 size={15} />
                            </button>
                            <button
                              type="button"
                              className={styles.disconnectBtn}
                              onClick={() => setConfirmDisconnect(ch)}
                              title="Ngắt kết nối"
                            >
                              <Link2Off size={15} />
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Channel Form Modal */}
      {canManageChannels && isModalOpen && (
        <ChannelFormModal
          mode={modalMode}
          channelData={selectedChannel}
          onClose={() => setIsModalOpen(false)}
          onSuccess={loadChannels}
        />
      )}

      {canManageChannels && (
        <DisconnectChannelDialog
          channel={confirmDisconnect}
          isDisconnecting={isDisconnecting}
          onCancel={() => setConfirmDisconnect(null)}
          onConfirm={handleDisconnect}
        />
      )}
    </div>
  );
};

export default ChannelConnectionPage;
