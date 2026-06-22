import { useCallback, useState } from 'react';
import { AlertTriangle, X } from 'lucide-react';

export default function useConfirmDialog() {
  const [dialog, setDialog] = useState(null);

  const confirm = useCallback((options = {}) => new Promise((resolve) => {
    setDialog({
      title: options.title ?? 'Xác nhận thao tác',
      message: options.message ?? 'Bạn có chắc chắn muốn tiếp tục?',
      confirmLabel: options.confirmLabel ?? options.confirmText ?? 'Xác nhận',
      cancelLabel: options.cancelLabel ?? options.cancelText ?? 'Hủy',
      tone: options.tone ?? 'warning',
      resolve,
    });
  }), []);

  const close = (result) => {
    dialog?.resolve(result);
    setDialog(null);
  };

  const ConfirmDialog = dialog ? (
    <div style={backdropStyle} onClick={(event) => event.target === event.currentTarget && close(false)}>
      <div style={dialogStyle}>
        <div style={headerStyle}>
          <div style={{ ...iconWrapStyle, background: dialog.tone === 'danger' ? '#fff1f2' : '#fffbeb' }}>
            <AlertTriangle size={22} color={dialog.tone === 'danger' ? '#e11d48' : '#d97706'} />
          </div>
          <button type="button" onClick={() => close(false)} style={closeButtonStyle}>
            <X size={18} />
          </button>
        </div>
        <h2 style={titleStyle}>{dialog.title}</h2>
        <p style={messageStyle}>{dialog.message}</p>
        <div style={footerStyle}>
          <button type="button" onClick={() => close(false)} style={cancelButtonStyle}>
            {dialog.cancelLabel}
          </button>
          <button
            type="button"
            onClick={() => close(true)}
            style={{
              ...confirmButtonStyle,
              background: dialog.tone === 'danger' ? '#dc2626' : '#020617',
            }}
          >
            {dialog.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  ) : null;

  return { confirm, ConfirmDialog };
}

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
  width: 'min(420px, 100%)',
  borderRadius: 12,
  background: '#fff',
  padding: 22,
  boxShadow: '0 24px 60px rgba(15, 23, 42, 0.24)',
};

const headerStyle = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 };
const iconWrapStyle = { width: 44, height: 44, borderRadius: 12, display: 'flex', alignItems: 'center', justifyContent: 'center' };
const closeButtonStyle = { width: 32, height: 32, border: 'none', borderRadius: 8, background: 'transparent', color: '#64748b', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const titleStyle = { margin: 0, fontSize: 20, color: '#020617', fontWeight: 800 };
const messageStyle = { margin: '10px 0 0', color: '#475569', fontSize: 14, lineHeight: 1.55, whiteSpace: 'pre-line' };
const footerStyle = { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 22 };
const cancelButtonStyle = { height: 38, padding: '0 14px', borderRadius: 8, border: '1px solid #dbe4ef', background: '#fff', color: '#020617', fontWeight: 700, cursor: 'pointer' };
const confirmButtonStyle = { height: 38, padding: '0 16px', borderRadius: 8, border: 'none', color: '#fff', fontWeight: 800, cursor: 'pointer' };
