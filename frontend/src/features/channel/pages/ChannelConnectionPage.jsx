import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  BarChart3,
  CheckCircle2,
  Link2Off,
  Music2,
  Plus,
  RefreshCw,
  Settings,
  ShoppingBag,
  ShoppingCart,
  Store,
  Wifi,
  WifiOff,
  Share2,
} from 'lucide-react';
import { toast } from 'react-toastify';
import channelApi from '../../../api/channelApi';
import ChannelFormModal from '../components/ChannelFormModal';
import styles from './ChannelConnectionPage.module.css';
import PageHeader from '../../../shared/components/PageHeader';

const PLATFORM_META = {
  SHOPEE: { label: 'Shopee', color: '#0284c7', bg: '#eff6ff', icon: ShoppingBag },
  TIKTOK: { label: 'TikTok Shop', color: '#6d5bd0', bg: '#f5f3ff', icon: Music2 },
  LAZADA: { label: 'Lazada', color: '#64748b', bg: '#f8fafc', icon: ShoppingCart },
  SHOPIFY: { label: 'Shopify', color: '#15803d', bg: '#f0fdf4', icon: Store },
  MANUAL: { label: 'Thủ công', color: '#475569', bg: '#f8fafc', icon: Store },
};

const getPlatformMeta = (platform) => PLATFORM_META[platform] || PLATFORM_META.MANUAL;

const getChannelStats = (channel) => ({
  productCount: Number(channel.metadata?.productCount || 0),
  skuVariantCount: Number(channel.metadata?.skuVariantCount || 0),
  warehouseCount: Number(channel.metadata?.warehouseCount || 0),
});

const getAccountLabel = (channel) =>
  channel.metadata?.accountId
  || channel.metadata?.shopDomain
  || channel.metadata?.accountName
  || '-';

const formatLastSync = (value) => {
  if (!value) return 'Chưa đồng bộ';
  return new Date(value).toLocaleString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

const ChannelConnectionPage = () => {
  const [channels, setChannels] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState('create');
  const [selectedChannel, setSelectedChannel] = useState(null);
  const [confirmDisconnect, setConfirmDisconnect] = useState(null);
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [syncingChannelId, setSyncingChannelId] = useState(null);

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

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadChannels();
  }, [loadChannels]);

  useEffect(() => {
    const success = searchParams.get('success');
    const error = searchParams.get('error');

    if (success === 'true') {
      toast.success('Kết nối Shopify thành công.');
      navigate('/channels', { replace: true });
      // eslint-disable-next-line react-hooks/set-state-in-effect
      loadChannels();
    } else if (success === 'lazada_connected') {
      toast.success('Kết nối Lazada thành công.');
      navigate('/channels', { replace: true });
      loadChannels();
    } else if (error) {
      const msg = error === 'oauth_failed'
        ? 'Kết nối Shopify thất bại.'
        : 'Kết nối kênh thất bại. Vui lòng thử lại.';
      toast.error(msg);
      navigate('/channels', { replace: true });
    }
  }, [loadChannels, navigate, searchParams]);

  const openCreate = () => {
    setModalMode('create');
    setSelectedChannel(null);
    setIsModalOpen(true);
  };

  const openEdit = (channel) => {
    setModalMode('edit');
    setSelectedChannel(channel);
    setIsModalOpen(true);
  };

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

  const handleSync = async (channel) => {
    if (channel.platform !== 'LAZADA' || channel.status !== 'CONNECTED') {
      toast.error('Kênh Lazada chưa kết nối hợp lệ. Vui lòng kết nối lại Lazada.');
      return;
    }

    setSyncingChannelId(channel.id);
    try {
      const res = await channelApi.sync(channel.id);
      const result = res.data?.data || res.data;
      toast.success(
        `Đồng bộ ${channel.displayName}: ${result?.productCount ?? 0} sản phẩm, ${result?.variantCount ?? 0} SKU, ${result?.warehouseCount ?? 0} kho`
      );
      loadChannels();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể đồng bộ kênh');
    } finally {
      setSyncingChannelId(null);
    }
  };

  const connectedCount = useMemo(
    () => channels.filter((channel) => channel.status === 'CONNECTED').length,
    [channels],
  );

  const actions = (
    <button className={styles.addBtn} type="button" onClick={openCreate}>
          <Plus size={16} />
          Kết nối kênh mới
        </button>
  );

  return (
    <div className={styles.page}>
      <PageHeader
        title="Quản lý kênh bán hàng"
        subtitle="Kết nối và quản lý các kênh bán hàng của bạn"
        icon={<Share2 size={20} />}
        actions={actions}
      />

      {isLoading ? (
        <div className={styles.loading}>
          <RefreshCw className={styles.loadingIcon} size={24} />
          <span>Đang tải danh sách kênh...</span>
        </div>
      ) : channels.length === 0 ? (
        <div className={styles.empty}>
          <Wifi size={48} className={styles.emptyIcon} />
          <p className={styles.emptyTitle}>Chưa có kênh nào được kết nối</p>
          <p className={styles.emptyDesc}>Nhấn "Kết nối kênh mới" để bắt đầu đồng bộ sản phẩm và kho.</p>
          <button className={styles.addBtn} type="button" onClick={openCreate}>
            <Plus size={16} />
            Kết nối kênh mới
          </button>
        </div>
      ) : (
        <>
          <div className={styles.summaryLine}>
            <span>{channels.length} kênh bán hàng</span>
            <span>{connectedCount} kênh đang hoạt động</span>
          </div>

          <div className={styles.channelGrid}>
            {channels.map((channel) => {
              const meta = getPlatformMeta(channel.platform);
              const PlatformIcon = meta.icon;
              const stats = getChannelStats(channel);
              const isConnected = channel.status === 'CONNECTED';
              const isSyncing = syncingChannelId === channel.id;
              const canSync = isConnected && channel.platform === 'LAZADA';

              return (
                <section key={channel.id} className={styles.channelCard}>
                  <div className={styles.cardTop}>
                    <div className={styles.channelCell}>
                      <div className={styles.platformAvatar} style={{ color: meta.color, background: meta.bg }}>
                        <PlatformIcon size={24} />
                      </div>
                      <div>
                        <div className={styles.channelName}>{channel.displayName || meta.label}</div>
                        <div className={styles.badgeRow}>
                          <span className={`${styles.statusBadge} ${isConnected ? styles.connected : styles.disconnected}`}>
                            {isConnected ? <CheckCircle2 size={12} /> : <WifiOff size={12} />}
                            {isConnected ? 'Đã kết nối' : 'Ngắt kết nối'}
                          </span>
                          {channel.syncEnabled === false && (
                            <span className={styles.warningBadge}>Tạm dừng</span>
                          )}
                        </div>
                      </div>
                    </div>

                    <button
                      className={`${styles.toggleBtn} ${isConnected ? styles.toggleOn : ''}`}
                      type="button"
                      aria-label="Trạng thái kết nối"
                      title={isConnected ? 'Đã kết nối' : 'Ngắt kết nối'}
                    />
                  </div>

                  <div className={styles.metricGrid}>
                    <div className={styles.metricItem}>
                      <span>Sản phẩm</span>
                      <strong>{stats.productCount.toLocaleString('vi-VN')}</strong>
                    </div>
                    <div className={styles.metricItem}>
                      <span>SKU</span>
                      <strong>{stats.skuVariantCount.toLocaleString('vi-VN')}</strong>
                    </div>
                    <div className={styles.metricItem}>
                      <span>Kho</span>
                      <strong>{stats.warehouseCount.toLocaleString('vi-VN')}</strong>
                    </div>
                  </div>

                  <div className={styles.syncBlock}>
                    <div className={styles.syncInfo}>
                      <span>Đồng bộ lần cuối:</span>
                      <strong>{formatLastSync(channel.lastSyncedAt)}</strong>
                    </div>
                    <div className={styles.syncInfo}>
                      <span>{channel.platform === 'LAZADA' ? 'Seller ID:' : 'Shop ID:'}</span>
                      <strong>{getAccountLabel(channel)}</strong>
                    </div>
                  </div>

                  <div className={styles.cardActions}>
                    <button
                      className={styles.syncBtn}
                      type="button"
                      onClick={() => handleSync(channel)}
                      disabled={!canSync || isSyncing}
                      title={canSync ? 'Đồng bộ sản phẩm và kho' : 'Chỉ hỗ trợ đồng bộ trực tiếp cho kênh Lazada đã kết nối'}
                    >
                      <RefreshCw size={15} className={isSyncing ? styles.loadingIcon : ''} />
                      {isSyncing ? 'Đang đồng bộ' : 'Đồng bộ'}
                    </button>
                    <button className={styles.settingsBtn} type="button" onClick={() => openEdit(channel)}>
                      <Settings size={15} />
                      Cài đặt
                    </button>
                    <button
                      className={styles.iconBtn}
                      type="button"
                      title="Thống kê"
                      onClick={() => toast.info('Thống kê kênh đang được phát triển')}
                    >
                      <BarChart3 size={15} />
                    </button>
                    <button
                      className={styles.iconBtnDanger}
                      type="button"
                      title="Ngắt kết nối"
                      onClick={() => setConfirmDisconnect(channel)}
                    >
                      <Link2Off size={15} />
                    </button>
                  </div>
                </section>
              );
            })}
          </div>
        </>
      )}

      {isModalOpen && (
        <ChannelFormModal
          mode={modalMode}
          channelData={selectedChannel}
          onClose={() => setIsModalOpen(false)}
          onSuccess={loadChannels}
        />
      )}

      {confirmDisconnect && (
        <div className={styles.confirmOverlay} onClick={(event) => event.target === event.currentTarget && setConfirmDisconnect(null)}>
          <div className={styles.confirmDialog}>
            <div className={styles.confirmIcon}>
              <Link2Off size={28} />
            </div>
            <h3 className={styles.confirmTitle}>Xác nhận ngắt kết nối</h3>
            <p className={styles.confirmDesc}>
              Bạn có chắc muốn ngắt kết nối kênh <strong>"{confirmDisconnect.displayName}"</strong>?
              Các sản phẩm đã đồng bộ sẽ không bị ảnh hưởng, nhưng kênh này sẽ không thể đồng bộ mới.
            </p>
            <div className={styles.confirmActions}>
              <button className={styles.confirmCancelBtn} onClick={() => setConfirmDisconnect(null)} disabled={isDisconnecting}>
                Hủy
              </button>
              <button className={styles.confirmDestructBtn} onClick={handleDisconnect} disabled={isDisconnecting}>
                {isDisconnecting ? <><span className={styles.spinner} /> Đang ngắt...</> : 'Ngắt kết nối'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ChannelConnectionPage;
