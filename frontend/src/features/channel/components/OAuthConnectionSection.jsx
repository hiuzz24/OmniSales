import { ExternalLink, Loader2 } from 'lucide-react';
import styles from './ChannelFormModal.module.css';

const OAuthConnectionSection = ({
  platformName,
  description,
  color,
  buttonLabel,
  isRedirecting,
  showCancel = false,
  onConnect,
  onCancel,
}) => (
  <div className={styles.shopifyOAuthBox}>
    <div className={styles.shopifyOAuthInfo}>
      <span className={styles.shopifyBadge} style={{ background: color, color: '#fff' }}>OAuth 2.0</span>
      <p>{description}</p>
    </div>
    <div className={styles.actions}>
      {showCancel && (
        <button type="button" className={styles.cancelBtn} onClick={onCancel} disabled={isRedirecting}>
          Hủy
        </button>
      )}
      <button
        type="button"
        className={styles.shopifyConnectBtn}
        style={{ background: color, color: '#fff', borderColor: color }}
        onClick={onConnect}
        disabled={isRedirecting}
        aria-label={buttonLabel || `Kết nối với ${platformName}`}
      >
        {isRedirecting ? (
          <><Loader2 size={16} className={styles.spinIcon} /> Đang chuyển hướng...</>
        ) : (
          <><ExternalLink size={16} /> {buttonLabel || `Kết nối với ${platformName}`}</>
        )}
      </button>
    </div>
  </div>
);

export default OAuthConnectionSection;
