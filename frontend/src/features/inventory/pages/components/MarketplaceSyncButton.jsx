import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, ChevronDown, DownloadCloud, Link2, Loader2, RefreshCw, UploadCloud } from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../../app/router/routes';
import channelSyncService from '../../services/channelSyncService';

const PLATFORM_LABELS = {
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
  TIKTOK: 'TikTok Shop',
};

const PLATFORM_COLORS = {
  LAZADA: { color: '#1d2b8f', bg: '#eef2ff', border: '#c7d2fe' },
  SHOPIFY: { color: '#3f6212', bg: '#f0fdf4', border: '#bbf7d0' },
  TIKTOK: { color: '#010101', bg: '#f8fafc', border: '#cbd5e1' },
};

const DIRECTION_OPTIONS = {
  'from-marketplace': {
    title: 'Đồng bộ từ sàn',
    description: 'Kéo sản phẩm và tồn kho từ từng sàn về ứng dụng',
    icon: DownloadCloud,
    color: '#1d4ed8',
    background: '#eff6ff',
    channelTitle: 'Chọn sàn để kéo dữ liệu về',
  },
  'from-app': {
    title: 'Đồng bộ từ ứng dụng',
    description: 'Chỉ cập nhật tồn kho và giá từ các phiếu nhập/xuất kho chưa đồng bộ',
    icon: UploadCloud,
    color: '#0f766e',
    background: '#ecfdf5',
  },
};

const shellStyle = { position: 'relative', display: 'inline-flex' };
const mainButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 7,
  border: 'none',
  borderRadius: 8,
  color: '#fff',
  fontSize: 14,
  fontWeight: 600,
  padding: '10px 20px',
  cursor: 'pointer',
  transition: 'all 0.2s ease',
  fontFamily: 'inherit',
  whiteSpace: 'nowrap',
  background: 'linear-gradient(135deg, #0d9488 0%, #0f766e 100%)',
  boxShadow: '0 4px 14px 0 rgba(13, 148, 136, 0.3)',
};
const mainButtonHoverStyle = {
  background: 'linear-gradient(135deg, #0f766e 0%, #115e59 100%)',
  transform: 'translateY(-2px)',
  boxShadow: '0 6px 20px rgba(13, 148, 136, 0.4)',
};
const menuStyle = {
  position: 'absolute',
  top: 'calc(100% + 8px)',
  right: 0,
  zIndex: 40,
  width: 330,
  maxWidth: 'calc(100vw - 32px)',
  padding: 10,
  border: '1px solid #e5e7eb',
  borderRadius: 12,
  background: '#fff',
  boxShadow: '0 18px 38px rgba(15, 23, 42, 0.16)',
};
const menuTitleStyle = { margin: '2px 4px 10px', color: '#020617', fontSize: 13, fontWeight: 800 };
const optionButtonStyle = {
  width: '100%',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  minHeight: 46,
  padding: '9px 10px',
  border: '1px solid #e5e7eb',
  borderRadius: 9,
  background: '#fff',
  color: '#020617',
  cursor: 'pointer',
  fontFamily: 'inherit',
  textAlign: 'left',
};
const optionTitleStyle = { display: 'block', fontSize: 13.5, fontWeight: 800 };
const optionSubStyle = { display: 'block', marginTop: 2, fontSize: 12, color: '#64748b' };
const iconWrapStyle = {
  width: 30,
  height: 30,
  borderRadius: 8,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
};
const backButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 5,
  border: 'none',
  background: 'transparent',
  color: '#475569',
  fontSize: 12.5,
  fontWeight: 700,
  padding: '4px 3px 10px',
  cursor: 'pointer',
  fontFamily: 'inherit',
};
const emptyStyle = {
  padding: '14px 10px',
  border: '1px dashed #dbe4ef',
  borderRadius: 9,
  color: '#64748b',
  fontSize: 13,
  textAlign: 'center',
};
const emptyTitleStyle = { display: 'block', color: '#0f172a', fontSize: 13.5, fontWeight: 800 };
const emptyDescStyle = { display: 'block', marginTop: 4, color: '#64748b', lineHeight: 1.45 };
const linkButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 6,
  minHeight: 36,
  marginTop: 10,
  padding: '8px 12px',
  border: '1px solid #bfdbfe',
  borderRadius: 8,
  background: '#eff6ff',
  color: '#1d4ed8',
  fontSize: 12.5,
  fontWeight: 800,
  cursor: 'pointer',
  fontFamily: 'inherit',
};
const toastLinkButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  marginLeft: 4,
  padding: 0,
  border: 'none',
  background: 'transparent',
  color: '#fff',
  fontSize: 'inherit',
  fontWeight: 600,
  cursor: 'pointer',
  fontFamily: 'inherit',
  textDecoration: 'underline',
};
const progressPanelStyle = {
  ...menuStyle,
  position: 'fixed',
  top: 'auto',
  right: 24,
  bottom: 24,
  zIndex: 1000,
  width: 420,
  maxWidth: 'calc(100vw - 32px)',
  padding: 16,
  borderRadius: 10,
  boxShadow: '0 22px 48px rgba(15, 23, 42, 0.22)',
};
const progressTrackStyle = {
  height: 8,
  marginTop: 10,
  overflow: 'hidden',
  borderRadius: 999,
  background: '#e2e8f0',
};

const terminalJobStatuses = new Set(['SYNCED', 'FAILED']);
const wait = (milliseconds) => new Promise((resolve) => window.setTimeout(resolve, milliseconds));
const remoteSyncJobs = new Map();
const remoteSyncListeners = new Set();

const emitRemoteSyncJobs = () => {
  const jobs = Array.from(remoteSyncJobs.values())
    .sort((first, second) => String(first.startedAt || '').localeCompare(String(second.startedAt || '')));
  remoteSyncListeners.forEach((listener) => listener(jobs));
};

const upsertRemoteSyncJob = (job) => {
  if (!job?.jobId) return;
  remoteSyncJobs.set(job.jobId, job);
  emitRemoteSyncJobs();
};

const subscribeRemoteSyncJobs = (listener) => {
  remoteSyncListeners.add(listener);
  listener(Array.from(remoteSyncJobs.values()));
  return () => remoteSyncListeners.delete(listener);
};

const getChannelLabel = (channel) => {
  const platformLabel = PLATFORM_LABELS[channel.platform] ?? channel.platform;
  return `${platformLabel} - ${channel.displayName ?? 'Chưa đặt tên'}`;
};
const toCount = (value) => Number(value ?? 0).toLocaleString('vi-VN');
const formatApplicationSyncTime = (value) => {
  if (!value) return 'lần đồng bộ đầu tiên';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'lần đồng bộ ứng dụng gần nhất'
    : date.toLocaleString('vi-VN');
};

const showApplicationSyncStarted = (syncableChannels) => {
  const cursors = syncableChannels
    .map((channel) => channel.lastSyncedApplicationAt)
    .filter(Boolean)
    .sort();
  const cursor = cursors.length === 0 ? null : cursors[0];
  toast.info(
    cursor
      ? `Đang đồng bộ các phiếu nhập/xuất kho thay đổi sau ${formatApplicationSyncTime(cursor)}.`
      : 'Đang kiểm tra các phiếu nhập/xuất kho chưa từng đồng bộ lên sàn.',
  );
};

const hasNoApplicationChanges = (result) => Number(result?.pushedVariantCount || 0) === 0;
const syncIdentity = (detail) => detail?.sellerId || detail?.shopId || detail?.shopDomain || 'chưa có seller/shop id';

const formatDetailLine = (detail) => {
  const platform = PLATFORM_LABELS[detail?.platform] ?? detail?.platform ?? 'Sàn';
  const status = String(detail?.status || '').toUpperCase() === 'SYNCED' ? 'OK' : 'Lỗi';
  return `${platform} (${syncIdentity(detail)}): ${status}, SP ${toCount(detail?.productCount)}, SKU ${toCount(detail?.variantCount || detail?.pushedVariantCount)}`;
};

const formatSyncResultMessage = (fallbackMessage, result) => {
  const details = Array.isArray(result?.details) ? result.details : [];
  if (details.length === 0) return result?.message || fallbackMessage;
  return `${result?.message || fallbackMessage}\n${details.map(formatDetailLine).join('\n')}`;
};

const defaultSuccessMessage = ({ channel, direction, result }) => {
  const channelLabel = getChannelLabel(channel);
  if (direction === 'from-marketplace') {
    return `Đã đồng bộ ${channelLabel}: lấy ${toCount(result?.productCount)} sản phẩm, ${toCount(result?.variantCount)} sản phẩm con từ sàn về ứng dụng.`;
  }
  return `Đã đồng bộ ${channelLabel}: cập nhật tồn kho và giá cho ${toCount(result?.pushedVariantCount)} SKU trên sàn.`;
};

export default function MarketplaceSyncButton({
  onSynced,
  className,
  style,
  buttonClassName,
  buttonStyle,
  iconClassName,
  allowedDirections = ['from-marketplace', 'from-app'],
  getSuccessMessage,
}) {
  const navigate = useNavigate();
  const menuRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [direction, setDirection] = useState(null);
  const [channels, setChannels] = useState([]);
  const [loadingChannels, setLoadingChannels] = useState(false);
  const [syncingChannelId, setSyncingChannelId] = useState(null);
  const [activeJobs, setActiveJobs] = useState([]);
  const [buttonHovered, setButtonHovered] = useState(false);

  useEffect(() => subscribeRemoteSyncJobs(setActiveJobs), []);

  useEffect(() => {
    if (!open) return undefined;
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setOpen(false);
        setDirection(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  const runningRemoteJobs = activeJobs.filter((job) => !terminalJobStatuses.has(String(job.status || '').toUpperCase()));
  const activeJobChannelIds = new Set(runningRemoteJobs.map((job) => job.channelId));
  const totalRemoteItems = runningRemoteJobs.reduce((sum, job) => sum + Number(job.totalItems || 0), 0);
  const processedRemoteItems = runningRemoteJobs.reduce((sum, job) => sum + Number(job.processedItems || 0), 0);
  const remoteProgressPercent = totalRemoteItems > 0
    ? Math.min(100, Math.round((processedRemoteItems * 100) / totalRemoteItems))
    : runningRemoteJobs.length > 0 ? 2 : 0;
  const remoteSuccessCount = runningRemoteJobs.reduce((sum, job) => sum + Number(job.successCount || 0), 0);
  const remoteFailCount = runningRemoteJobs.reduce((sum, job) => sum + Number(job.failCount || 0), 0);

  const goToChannels = () => {
    setOpen(false);
    setDirection(null);
    navigate(ROUTES.CHANNELS);
  };

  const showNoChannelToast = () => {
    toast.error(
      <span>
        Chưa kết nối với kênh bán hàng nào.
        <button
          type="button"
          style={toastLinkButtonStyle}
          onClick={() => {
            toast.dismiss();
            goToChannels();
          }}
        >
          Kết nối kênh bán hàng ngay!!!
        </button>
      </span>,
      { autoClose: 6000 }
    );
  };

  const loadChannels = async () => {
    setLoadingChannels(true);
    try {
      const data = await channelSyncService.getSyncableChannels();
      setChannels(data);
      return data;
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể tải danh sách sàn đã liên kết.');
      setChannels([]);
      return [];
    } finally {
      setLoadingChannels(false);
    }
  };

  const openMenu = async () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (nextOpen) {
      setDirection(allowedDirections.length === 1 ? allowedDirections[0] : null);
      await loadChannels();
    }
  };

  const selectDirection = async (nextDirection) => {
    if (nextDirection === 'from-app') {
      const syncableChannels = channels.length > 0 ? channels : await loadChannels();
      if (syncableChannels.length === 0) {
        setOpen(false);
        setDirection(null);
        showNoChannelToast();
        return;
      }

      setOpen(false);
      setDirection(null);
      setSyncingChannelId('all-connected-channels');
      try {
        showApplicationSyncStarted(syncableChannels);
        const result = await channelSyncService.syncAllFromApp();
        const summaryMessage = formatSyncResultMessage(
          'Đã cập nhật tồn kho và giá từ các phiếu kho lên tất cả sàn đã liên kết.',
          result,
        );
        if (String(result?.status || '').toUpperCase() === 'FAILED') toast.warn(summaryMessage);
        else if (hasNoApplicationChanges(result)) toast.info(summaryMessage);
        else toast.success(summaryMessage);
        await onSynced?.({ channel: null, direction: nextDirection, result });
      } catch (error) {
        const message = error?.response?.data?.message || error?.message || 'Không thể đồng bộ thay đổi lên các sàn đã liên kết.';
        const normalizedMessage = message.toLowerCase();
        if (normalizedMessage.includes('chưa') || normalizedMessage.includes('chua') || normalizedMessage.includes('no channel')) {
          showNoChannelToast();
        } else {
          toast.error(message);
        }
      } finally {
        setSyncingChannelId(null);
      }
      return;
    }

    setDirection(nextDirection);
    if (channels.length === 0) await loadChannels();
  };

  const syncChannel = async (channel) => {
    if (!direction) return;
    const channelLabel = getChannelLabel(channel);
    const isFromMarketplace = direction === 'from-marketplace';
    try {
      if (isFromMarketplace) {
        if (activeJobChannelIds.has(channel.id)) {
          toast.info(`${channelLabel} đang đồng bộ.`);
          return;
        }

        let job = await channelSyncService.enqueueSyncFromMarketplace(channel.id);
        upsertRemoteSyncJob(job);
        setOpen(false);

        while (!terminalJobStatuses.has(String(job?.status || '').toUpperCase())) {
          await wait(1200);
          job = await channelSyncService.getSyncJob(job.jobId);
          upsertRemoteSyncJob(job);
        }

        if (String(job.status).toUpperCase() === 'FAILED') {
          toast.error(job.message || `Đồng bộ ${channelLabel} thất bại.`);
        } else {
          toast.success(`Đã đồng bộ ${channelLabel}: ${toCount(job.successCount)} sản phẩm con thành công.`);
          await onSynced?.({ channel, direction, result: job });
        }
        return;
      }

      if (syncingChannelId) return;
      setSyncingChannelId(channel.id);
      showApplicationSyncStarted([channel]);
      const result = await channelSyncService.syncChannelFromApp(channel.id);
      const message = getSuccessMessage?.({ channel, direction, result })
        || defaultSuccessMessage({ channel, direction, result });
      const summaryMessage = formatSyncResultMessage(message, result);
      if (String(result?.status || '').toUpperCase() === 'FAILED') toast.warn(summaryMessage);
      else if (hasNoApplicationChanges(result)) toast.info(summaryMessage);
      else toast.success(summaryMessage);
      await onSynced?.({ channel, direction, result });
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || `Không thể đồng bộ ${channelLabel}.`);
    } finally {
      if (!isFromMarketplace) setSyncingChannelId(null);
    }
  };

  const renderDirectionOptions = () => (
    <>
      <div style={menuTitleStyle}>Chọn chiều đồng bộ</div>
      <div style={{ display: 'grid', gap: 8 }}>
        {allowedDirections.map((optionKey) => {
          const option = DIRECTION_OPTIONS[optionKey];
          if (!option) return null;
          const Icon = option.icon;
          return (
            <button key={optionKey} type="button" style={optionButtonStyle} onClick={() => selectDirection(optionKey)}>
              <span style={{ ...iconWrapStyle, color: option.color, background: option.background }}>
                <Icon size={16} />
              </span>
              <span>
                <span style={optionTitleStyle}>{option.title}</span>
                <span style={optionSubStyle}>{option.description}</span>
              </span>
            </button>
          );
        })}
      </div>
    </>
  );

  const renderChannelList = () => {
    const title = DIRECTION_OPTIONS[direction]?.channelTitle ?? 'Chọn sàn để đồng bộ';
    return (
      <>
        {allowedDirections.length > 1 && (
          <button type="button" style={backButtonStyle} onClick={() => setDirection(null)}>
            <ArrowLeft size={14} />
            Quay lại
          </button>
        )}
        <div style={menuTitleStyle}>{title}</div>
        {loadingChannels ? (
          <div style={emptyStyle}>Đang tải danh sách sàn...</div>
        ) : channels.length === 0 ? (
          <div style={emptyStyle}>
            <span style={emptyTitleStyle}>Chưa có kênh bán hàng đang kết nối</span>
            <span style={emptyDescStyle}>Kết nối Lazada, Shopify hoặc TikTok Shop để bắt đầu đồng bộ sản phẩm và tồn kho.</span>
            <button type="button" style={linkButtonStyle} onClick={goToChannels}>
              <Link2 size={14} />
              Kết nối kênh bán hàng
            </button>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 8 }}>
            {channels.map((channel) => {
              const platformStyle = PLATFORM_COLORS[channel.platform] ?? { color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
              const syncing = syncingChannelId === channel.id || activeJobChannelIds.has(channel.id);
              const disabled = direction === 'from-app' ? Boolean(syncingChannelId) : activeJobChannelIds.has(channel.id);
              return (
                <button
                  key={channel.id}
                  type="button"
                  disabled={disabled}
                  style={{
                    ...optionButtonStyle,
                    opacity: disabled && !syncing ? 0.62 : 1,
                    cursor: disabled ? 'not-allowed' : 'pointer',
                  }}
                  onClick={() => syncChannel(channel)}
                >
                  <span style={{ ...iconWrapStyle, color: platformStyle.color, background: platformStyle.bg, border: `1px solid ${platformStyle.border}` }}>
                    {syncing ? <Loader2 size={15} className="osms-sync-spin" /> : ({ SHOPIFY: 'S', LAZADA: 'L', TIKTOK: 'T' }[channel.platform] ?? '?')}
                  </span>
                  <span style={{ minWidth: 0 }}>
                    <span style={optionTitleStyle}>{PLATFORM_LABELS[channel.platform] ?? channel.platform}</span>
                    <span style={{ ...optionSubStyle, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {syncing ? 'Đang đồng bộ...' : channel.displayName}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </>
    );
  };

  return (
    <div ref={menuRef} className={className} style={{ ...shellStyle, ...style }}>
      <style>{`
        @keyframes osms-sync-spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .osms-sync-spin {
          animation: osms-sync-spin 0.9s linear infinite;
        }
        @media (prefers-reduced-motion: reduce) {
          .osms-sync-spin {
            animation: none;
          }
        }
      `}</style>
      <button
        type="button"
        className={buttonClassName}
        style={{
          ...(buttonClassName ? {} : mainButtonStyle),
          ...(!buttonClassName && buttonHovered && !syncingChannelId ? mainButtonHoverStyle : {}),
          ...buttonStyle,
          opacity: syncingChannelId === 'all-connected-channels' ? 0.72 : 1,
        }}
        disabled={syncingChannelId === 'all-connected-channels'}
        onClick={openMenu}
        onMouseEnter={() => setButtonHovered(true)}
        onMouseLeave={() => setButtonHovered(false)}
        onMouseDown={() => {
          if (!buttonClassName && !syncingChannelId) setButtonHovered(false);
        }}
        onMouseUp={() => {
          if (!buttonClassName && !syncingChannelId) setButtonHovered(true);
        }}
        title="Chọn chiều đồng bộ dữ liệu"
      >
        {syncingChannelId === 'all-connected-channels'
          ? <Loader2 className={`${iconClassName || ''} osms-sync-spin`} size={16} />
          : <RefreshCw className={iconClassName} size={16} />}
        {syncingChannelId === 'all-connected-channels'
          ? 'Đang đẩy thay đổi...'
          : runningRemoteJobs.length > 0
            ? `Đồng bộ (${runningRemoteJobs.length})`
            : 'Đồng bộ'}
        <ChevronDown size={15} />
      </button>
      {runningRemoteJobs.length > 0 && (
        <div style={progressPanelStyle} role="status" aria-live="polite">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <span style={menuTitleStyle}>Đang đồng bộ từ sàn</span>
            <strong style={{ color: '#1d4ed8', fontSize: 13 }}>{remoteProgressPercent}%</strong>
          </div>
          <div style={{ marginTop: 8, color: '#475569', fontSize: 12.5, lineHeight: 1.5 }}>
            Đã xử lý <strong>{toCount(processedRemoteItems)}</strong>
            {totalRemoteItems > 0 && <> / <strong>{toCount(totalRemoteItems)}</strong></>} sản phẩm con
          </div>
          <div style={{ marginTop: 4, color: '#64748b', fontSize: 12 }}>
            Tác vụ đang chạy nền, bạn có thể tiếp tục thao tác trên trang.
          </div>
          <div style={progressTrackStyle} aria-hidden="true">
            <div style={{
              width: `${Math.max(remoteProgressPercent || 2, 2)}%`,
              height: '100%',
              borderRadius: 999,
              background: '#2563eb',
              transition: 'width 200ms ease',
            }} />
          </div>
          <div style={{ display: 'flex', gap: 14, marginTop: 9, color: '#64748b', fontSize: 12 }}>
            <span>Thành công: <strong style={{ color: '#15803d' }}>{toCount(remoteSuccessCount)}</strong></span>
            <span>Lỗi: <strong style={{ color: '#b91c1c' }}>{toCount(remoteFailCount)}</strong></span>
          </div>
          <div style={{ display: 'grid', gap: 6, marginTop: 10 }}>
            {runningRemoteJobs.map((job) => (
              <div key={job.jobId} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, color: '#475569', fontSize: 12 }}>
                <span>{PLATFORM_LABELS[job.platform] ?? 'Sàn'} - {job.channelName ?? job.channelId}</span>
                <strong style={{ color: '#1d4ed8' }}>{job.progressPercent || 0}%</strong>
              </div>
            ))}
          </div>
        </div>
      )}
      {open && (
        <div style={menuStyle}>
          {direction ? renderChannelList() : renderDirectionOptions()}
        </div>
      )}
    </div>
  );
}

