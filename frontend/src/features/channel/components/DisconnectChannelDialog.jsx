import { Link2Off } from 'lucide-react';
import styles from '../pages/ChannelConnectionPage.module.css';

const DisconnectChannelDialog = ({ channel, isDisconnecting, onCancel, onConfirm }) => {
  if (!channel) return null;

  return (
    <div className={styles.confirmOverlay} onClick={(event) => event.target === event.currentTarget && onCancel()}>
      <div className={styles.confirmDialog} role="dialog" aria-modal="true" aria-labelledby="disconnect-title">
        <div className={styles.confirmIcon}><Link2Off size={28} /></div>
        <h3 id="disconnect-title" className={styles.confirmTitle}>Xác nhận ngắt kết nối</h3>
        <div className={styles.confirmDesc}>
          <p>Ngắt kết nối kênh <strong>"{channel.displayName}"</strong>?</p>
          <p>Sản phẩm và đơn hàng trong OSMS không bị xóa.</p>
          <p>Đồng bộ sẽ dừng; mapping được lưu lại và khôi phục khi kết nối lại đúng shop.</p>
        </div>
        <div className={styles.confirmActions}>
          <button className={styles.confirmCancelBtn} onClick={onCancel} disabled={isDisconnecting}>Hủy</button>
          <button className={styles.confirmDestructBtn} onClick={onConfirm} disabled={isDisconnecting}>
            {isDisconnecting ? <><span className={styles.spinner} /> Đang ngắt...</> : 'Ngắt kết nối'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default DisconnectChannelDialog;
