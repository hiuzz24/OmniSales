import { useEffect, useState } from 'react';
import {
  AlertTriangle, CheckCircle2, Loader2, MapPin, X, RefreshCw,
} from 'lucide-react';
import { toast } from 'react-toastify';
import warehouseApi from '../../../../api/warehouseApi';

const PLATFORM_META = {
  SHOPIFY: { label: 'Shopify', color: '#3f6212', bg: '#f0fdf4', border: '#bbf7d0' },
  LAZADA: { label: 'Lazada', color: '#1d2b8f', bg: '#eef2ff', border: '#c7d2fe' },
  TIKTOK: { label: 'TikTok Shop', color: '#010101', bg: '#f8fafc', border: '#cbd5e1' },
};

const backdropStyle = {
  position: 'fixed',
  inset: 0,
  zIndex: 1200,
  background: 'rgba(15, 23, 42, 0.46)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 20,
};

const dialogStyle = {
  width: 'min(520px, 100%)',
  borderRadius: 14,
  background: '#fff',
  padding: 0,
  boxShadow: '0 24px 60px rgba(15, 23, 42, 0.24)',
  overflow: 'hidden',
};

const headerStyle = {
  padding: '18px 22px 14px',
  borderBottom: '1px solid #f1f5f9',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
};

const titleStyle = {
  margin: 0,
  fontSize: 17,
  fontWeight: 800,
  color: '#0f172a',
  display: 'flex',
  alignItems: 'center',
  gap: 8,
};

const bodyStyle = {
  padding: '16px 22px',
  maxHeight: '60vh',
  overflowY: 'auto',
};

const footerStyle = {
  padding: '14px 22px',
  borderTop: '1px solid #f1f5f9',
  display: 'flex',
  justifyContent: 'flex-end',
  gap: 10,
};

const addressCardStyle = (platformStyle) => ({
  border: `1px solid ${platformStyle.border}`,
  borderRadius: 10,
  padding: '10px 12px',
  background: platformStyle.bg,
  marginBottom: 8,
});

const currentCardStyle = {
  border: '1px solid #e2e8f0',
  borderRadius: 10,
  padding: '10px 12px',
  background: '#f8fafc',
  marginBottom: 12,
};

const labelStyle = {
  fontSize: 11,
  fontWeight: 700,
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  marginBottom: 4,
};

const addressTextStyle = {
  fontSize: 13,
  color: '#0f172a',
  lineHeight: 1.45,
  wordBreak: 'break-word',
};

const statusBannerStyle = (type) => {
  const styles = {
    SAME_AS_CURRENT: { bg: '#f0fdf4', border: '#bbf7d0', color: '#166534', icon: CheckCircle2 },
    ALL_SAME: { bg: '#fffbeb', border: '#fde68a', color: '#92400e', icon: AlertTriangle },
    ALL_DIFFERENT: { bg: '#fef2f2', border: '#fecaca', color: '#991b1b', icon: AlertTriangle },
    NO_CHANNELS: { bg: '#f8fafc', border: '#e2e8f0', color: '#64748b', icon: AlertTriangle },
  };
  return styles[type] || styles.NO_CHANNELS;
};

const statusMessages = {
  SAME_AS_CURRENT: 'Địa chỉ kho trên tất cả các sàn đã khớp với địa chỉ kho hiện tại. Không cần thay đổi.',
  ALL_SAME: 'Tất cả các sàn có cùng địa chỉ kho. Bạn có muốn đổi địa chỉ kho hàng sang địa chỉ này?',
  ALL_DIFFERENT: 'Địa chỉ kho hàng trên các sàn không đồng nhất. Vui lòng cập nhật thủ công tại từng sàn.',
  NO_CHANNELS: 'Không có sàn nào đang kết nối. Vui lòng kết nối sàn trước khi đồng bộ.',
};

export default function WarehouseAddressSyncDialog({ open, onClose }) {
  const [loading, setLoading] = useState(false);
  const [comparing, setComparing] = useState(false);
  const [applying, setApplying] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (open) {
      compareAddresses();
    } else {
      setResult(null);
    }
  }, [open]);

  const compareAddresses = async () => {
    setComparing(true);
    try {
      const res = await warehouseApi.compareAddresses();
      const data = res?.data?.data ?? res?.data ?? res;
      setResult(data);
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể so sánh địa chỉ kho.');
      onClose();
    } finally {
      setComparing(false);
    }
  };

  const handleApply = async () => {
    setApplying(true);
    try {
      await warehouseApi.applyAddressSync(true);
      toast.success('Đã đồng bộ địa chỉ kho hàng thành công. Kho cũ đã bị vô hiệu hóa.');
      onClose(true);
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Không thể đồng bộ địa chỉ kho.');
    } finally {
      setApplying(false);
    }
  };

  const handleClose = () => {
    onClose(false);
  };

  if (!open) return null;

  const status = result?.status;
  const StatusBanner = statusBannerStyle(status);
  const StatusIcon = StatusBanner.icon;

  return (
    <div style={backdropStyle} onClick={(e) => e.target === e.currentTarget && handleClose()}>
      <div style={dialogStyle}>
        {/* Header */}
        <div style={headerStyle}>
          <div style={titleStyle}>
            <MapPin size={18} color="#2563eb" />
            Đồng bộ địa chỉ kho
          </div>
          <button
            type="button"
            onClick={handleClose}
            style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#94a3b8', padding: 4 }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div style={bodyStyle}>
          {comparing ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '32px 0', color: '#64748b' }}>
              <Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} />
              <span style={{ fontSize: 13, fontWeight: 600 }}>Đang so sánh địa chỉ kho từ các sàn...</span>
            </div>
          ) : result ? (
            <>
              {/* Status banner */}
              <div style={{
                display: 'flex', alignItems: 'flex-start', gap: 8,
                padding: '10px 12px', borderRadius: 10,
                background: StatusBanner.bg, border: `1px solid ${StatusBanner.border}`,
                marginBottom: 14,
              }}>
                <StatusIcon size={16} color={StatusBanner.color} style={{ marginTop: 1, flexShrink: 0 }} />
                <span style={{ fontSize: 13, color: StatusBanner.color, lineHeight: 1.5 }}>
                  {statusMessages[status]}
                </span>
              </div>

              {/* Current warehouse address */}
              {result.currentWarehouseAddress && (
                <div style={currentCardStyle}>
                  <div style={{ ...labelStyle, color: '#64748b' }}>Địa chỉ kho hiện tại</div>
                  <div style={addressTextStyle}>{result.currentWarehouseAddress}</div>
                </div>
              )}

              {/* Platform addresses */}
              {result.platformAddresses?.length > 0 && (
                <div>
                  <div style={{ ...labelStyle, color: '#94a3b8', marginBottom: 8 }}>Địa chỉ kho mặc định trên sàn</div>
                  {result.platformAddresses.map((pa, idx) => {
                    const meta = PLATFORM_META[pa.platform] || { label: pa.platform, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
                    return (
                      <div key={idx} style={addressCardStyle(meta)}>
                        <div style={{ ...labelStyle, color: meta.color, marginBottom: 4 }}>
                          {meta.label} — {pa.channelName}
                        </div>
                        <div style={addressTextStyle}>{pa.address || '(không có địa chỉ)'}</div>
                      </div>
                    );
                  })}
                </div>
              )}
            </>
          ) : null}
        </div>

        {/* Footer */}
        <div style={footerStyle}>
          <button
            type="button"
            onClick={handleClose}
            style={{
              height: 38, padding: '0 14px', borderRadius: 8,
              border: '1px solid #dbe4ef', background: '#fff',
              color: '#020617', fontWeight: 700, cursor: 'pointer',
              fontSize: 13, fontFamily: 'inherit',
            }}
          >
            Đóng
          </button>
          {status === 'ALL_SAME' && (
            <button
              type="button"
              onClick={handleApply}
              disabled={applying}
              style={{
                height: 38, padding: '0 16px', borderRadius: 8,
                border: 'none', background: '#b45309',
                color: '#fff', fontWeight: 800, cursor: 'pointer',
                display: 'flex', alignItems: 'center', gap: 6,
                fontSize: 13, fontFamily: 'inherit',
              }}
            >
              {applying ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <RefreshCw size={14} />}
              {applying ? 'Đang đồng bộ...' : 'Xác nhận đồng bộ'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
