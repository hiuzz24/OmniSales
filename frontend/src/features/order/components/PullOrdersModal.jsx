import { useEffect, useMemo, useState } from 'react';
import { Download, X } from 'lucide-react';
import styles from './PullOrdersModal.module.css';

const supportedPlatforms = new Set(['LAZADA', 'SHOPIFY', 'TIKTOK']);
const localDateTime = (date) => {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
};

const PullOrdersModal = ({ open, channels, submitting, onClose, onSubmit }) => {
  const availableChannels = useMemo(() => channels.filter((channel) =>
    channel.status === 'CONNECTED' && supportedPlatforms.has(channel.platform)), [channels]);
  const [selected, setSelected] = useState([]);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  useEffect(() => {
    if (!open) return;
    const end = new Date();
    setTo(localDateTime(end));
    setFrom(localDateTime(new Date(end.getTime() - 24 * 60 * 60 * 1000)));
    setSelected([]);
  }, [open]);

  if (!open) return null;
  const toggle = (id) => setSelected((current) =>
    current.includes(id) ? current.filter((value) => value !== id) : [...current, id]);
  const submit = (event) => {
    event.preventDefault();
    onSubmit({ channelIds: selected, from: new Date(from).toISOString(), to: new Date(to).toISOString() });
  };

  return (
    <div className={styles.overlay} onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <form className={styles.modal} onSubmit={submit} role="dialog" aria-modal="true" aria-labelledby="pull-orders-title">
        <header className={styles.header}>
          <div className={styles.titleGroup}>
            <span className={styles.icon}><Download size={18} /></span>
            <div><h2 id="pull-orders-title">Kéo đơn từ sàn</h2><p>Job chạy nền, bạn có thể rời trang sau khi bắt đầu.</p></div>
          </div>
          <button type="button" className={styles.close} onClick={onClose} aria-label="Đóng"><X size={18} /></button>
        </header>
        <div className={styles.body}>
          <fieldset className={styles.channels}>
            <legend>Kênh cần kéo đơn</legend>
            {availableChannels.length === 0 ? <p className={styles.empty}>Không có kênh Lazada, Shopify hoặc TikTok đang kết nối.</p> :
              availableChannels.map((channel) => (
                <label className={styles.channel} key={channel.id}>
                  <input type="checkbox" checked={selected.includes(channel.id)} onChange={() => toggle(channel.id)} />
                  <span><strong>{channel.displayName || channel.name}</strong><small>{channel.platform}</small></span>
                </label>
              ))}
          </fieldset>
          <div className={styles.range}>
            <label>Từ thời điểm<input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} required /></label>
            <label>Đến thời điểm<input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} required /></label>
          </div>
          <p className={styles.hint}>Khoảng thời gian tối đa 7 ngày. Đơn đã tồn tại sẽ được cập nhật, không tạo bản ghi trùng.</p>
        </div>
        <footer className={styles.footer}>
          <button type="button" className={styles.cancel} onClick={onClose}>Hủy</button>
          <button type="submit" className={styles.submit} disabled={submitting || selected.length === 0}>
            <Download size={16} />{submitting ? 'Đang tạo job...' : 'Bắt đầu kéo đơn'}
          </button>
        </footer>
      </form>
    </div>
  );
};

export default PullOrdersModal;
