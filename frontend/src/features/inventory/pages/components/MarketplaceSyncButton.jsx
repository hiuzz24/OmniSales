import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ChevronDown, DownloadCloud, RefreshCw, UploadCloud } from 'lucide-react';
import { toast } from 'react-toastify';
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
    description: 'Đẩy thay đổi sản phẩm và tồn kho lên từng sàn',
    icon: UploadCloud,
    color: '#0f766e',
    background: '#ecfdf5',
    channelTitle: 'Chọn sàn để đẩy dữ liệu lên',
  },
};

const shellStyle = {
  position: 'relative',
  display: 'inline-flex',
};

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

const menuTitleStyle = {
  margin: '2px 4px 10px',
  color: '#020617',
  fontSize: 13,
  fontWeight: 800,
};

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

const optionTitleStyle = {
  display: 'block',
  fontSize: 13.5,
  fontWeight: 800,
};

const optionSubStyle = {
  display: 'block',
  marginTop: 2,
  fontSize: 12,
  color: '#64748b',
};

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

const getChannelLabel = (channel) => {
  const platformLabel = PLATFORM_LABELS[channel.platform] ?? channel.platform;
  return `${platformLabel} - ${channel.displayName ?? 'Chưa đặt tên'}`;
};

const toCount = (value) => Number(value ?? 0).toLocaleString('vi-VN');

const defaultSuccessMessage = ({ channel, direction, result }) => {
  const channelLabel = getChannelLabel(channel);
  if (direction === 'from-marketplace') {
    return `Đã đồng bộ ${channelLabel}: lấy ${toCount(result?.productCount)} sản phẩm, ${toCount(result?.variantCount)} sản phẩm con từ sàn về ứng dụng.`;
  }

  return `Đã đồng bộ ${channelLabel}: đẩy ${toCount(result?.productCount)} sản phẩm, ${toCount(result?.pushedVariantCount)} SKU tồn kho lên sàn.`;
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
  const menuRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [direction, setDirection] = useState(null);
  const [channels, setChannels] = useState([]);
  const [loadingChannels, setLoadingChannels] = useState(false);
  const [syncingChannelId, setSyncingChannelId] = useState(null);
  const [buttonHovered, setButtonHovered] = useState(false);

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

  const loadChannels = async () => {
    setLoadingChannels(true);
    try {
      const data = await channelSyncService.getSyncableChannels();
      setChannels(data);
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể tải danh sách sàn đã liên kết.');
      setChannels([]);
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
    setDirection(nextDirection);
    if (channels.length === 0) {
      await loadChannels();
    }
  };

  const syncChannel = async (channel) => {
    if (!direction || syncingChannelId) return;

    setSyncingChannelId(channel.id);
    const channelLabel = getChannelLabel(channel);
    const isFromMarketplace = direction === 'from-marketplace';
    try {
      const result = isFromMarketplace
        ? await channelSyncService.syncChannelFromMarketplace(channel.id)
        : await channelSyncService.syncChannelFromApp(channel.id);

      const message = getSuccessMessage?.({ channel, direction, result })
        || defaultSuccessMessage({ channel, direction, result });
      toast.success(message);
      await onSynced?.({ channel, direction, result });
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || `Không thể đồng bộ ${channelLabel}.`);
    } finally {
      setSyncingChannelId(null);
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
          <div style={emptyStyle}>Chưa có kênh Lazada/Shopify đang kết nối để đồng bộ.</div>
        ) : (
          <div style={{ display: 'grid', gap: 8 }}>
            {channels.map((channel) => {
              const platformStyle = PLATFORM_COLORS[channel.platform] ?? { color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
              const syncing = syncingChannelId === channel.id;
              return (
                <button
                  key={channel.id}
                  type="button"
                  disabled={Boolean(syncingChannelId)}
                  style={{
                    ...optionButtonStyle,
                    opacity: syncingChannelId && !syncing ? 0.62 : 1,
                    cursor: syncingChannelId ? 'not-allowed' : 'pointer',
                  }}
                  onClick={() => syncChannel(channel)}
                >
                  <span style={{ ...iconWrapStyle, color: platformStyle.color, background: platformStyle.bg, border: `1px solid ${platformStyle.border}` }}>
                    {syncing ? <RefreshCw size={15} /> : ({ SHOPIFY: 'S', LAZADA: 'L', TIKTOK: 'T' }[channel.platform] ?? '?')}
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
      <button
        type="button"
        className={buttonClassName}
        style={{
          ...(buttonClassName ? {} : mainButtonStyle),
          ...(!buttonClassName && buttonHovered && !syncingChannelId ? mainButtonHoverStyle : {}),
          ...buttonStyle,
          opacity: syncingChannelId ? 0.72 : 1,
        }}
        disabled={Boolean(syncingChannelId)}
        onClick={openMenu}
        onMouseEnter={() => setButtonHovered(true)}
        onMouseLeave={() => setButtonHovered(false)}
        onMouseDown={() => {
          if (!buttonClassName && !syncingChannelId) setButtonHovered(false);
        }}
        onMouseUp={() => {
          if (!buttonClassName && !syncingChannelId) setButtonHovered(true);
        }}
        title="Chọn chiều đồng bộ Lazada/Shopify"
      >
        <RefreshCw className={iconClassName} size={16} />
        Đồng bộ
        <ChevronDown size={15} />
      </button>
      {open && (
        <div style={menuStyle}>
          {direction ? renderChannelList() : renderDirectionOptions()}
        </div>
      )}
    </div>
  );
}
