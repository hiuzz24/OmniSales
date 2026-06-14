import { Save } from 'lucide-react';
import styles from './ProductChannelSidebar.module.css';

const PLATFORM_ICONS = {
  SHOPEE: { label: 'S', className: 'channelIconShopee' },
  TIKTOK: { label: 'T', className: 'channelIconTiktok' },
  LAZADA: { label: 'L', className: 'channelIconLazada' },
};

const ProductChannelSidebar = ({
  channels = [],
  selectedChannels = [],
  onChannelToggle,
  showProduct,
  onStatusToggle,
  onSubmit,
  onCancel,
  isSubmitting = false,
}) => {
  const getIcon = (platform) => {
    const icon = PLATFORM_ICONS[platform?.toUpperCase()] || { label: '?', className: 'channelIconDefault' };
    return icon;
  };

  return (
    <div className={styles.sidebar}>
      {/* Kênh bán hàng */}
      <div className={styles.card}>
        <div className={styles.cardTitle}>Kênh bán hàng</div>
        <div className={styles.cardSubtitle}>Chọn kênh để đồng bộ sản phẩm</div>

        {channels.length === 0 ? (
          <div className={styles.emptyChannels}>Chưa có kênh nào được thiết lập</div>
        ) : (
          <div className={styles.channelList}>
            {channels.map((channel) => {
              const icon = getIcon(channel.platform);
              const isSelected = selectedChannels.includes(channel.id);
              return (
                <div key={channel.id} className={styles.channelItem}>
                  <div className={styles.channelInfo}>
                    <div className={`${styles.channelIcon} ${styles[icon.className]}`}>
                      {icon.label}
                    </div>
                    <span className={styles.channelName}>
                      {channel.displayName || channel.platform}
                    </span>
                  </div>
                  <input
                    type="checkbox"
                    className={styles.toggle}
                    checked={isSelected}
                    onChange={() => onChannelToggle(channel.id)}
                  />
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Trạng thái */}
      <div className={styles.card}>
        <div className={styles.cardTitle}>Trạng thái</div>
        <div className={styles.statusItem}>
          <span className={styles.statusLabel}>Hiển thị và cho phép đặt hàng</span>
          <input
            type="checkbox"
            className={styles.toggle}
            checked={showProduct}
            onChange={() => onStatusToggle()}
          />
        </div>
      </div>

      {/* Action buttons */}
      <div className={styles.actions}>
        <button
          type="button"
          className={styles.submitBtn}
          onClick={onSubmit}
          disabled={isSubmitting}
        >
          {isSubmitting ? (
            <div className={styles.spinner} />
          ) : (
            <Save className={styles.submitIcon} />
          )}
          {isSubmitting ? 'Đang tạo...' : 'Tạo sản phẩm'}
        </button>
        <button
          type="button"
          className={styles.cancelBtn}
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Hủy
        </button>
      </div>
    </div>
  );
};

export default ProductChannelSidebar;
