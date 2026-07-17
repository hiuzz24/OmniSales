import { Save } from 'lucide-react';
import { useFormContext, useWatch } from 'react-hook-form';
import styles from './ProductChannelSidebar.module.css';

const PLATFORM_ICONS = {
  SHOPEE: { label: 'S', className: 'channelIconShopee' },
  TIKTOK: { label: 'T', className: 'channelIconTiktok' },
  LAZADA: { label: 'L', className: 'channelIconLazada' },
};

const ProductChannelSidebar = ({
  channels = [],
  onSubmit,
  onInvalid,
  onCancel,
  isEditMode = false,
}) => {
  const { control, setValue, handleSubmit, formState: { isSubmitting } } = useFormContext();
  const [selectedChannels = [], showProduct, channelConfigs = {}] = useWatch({ control, name: ['channelIds', 'status', 'channelConfigs'] });
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
            {channels.map((channel, index) => {
              const icon = getIcon(channel.platform);
              const channelId = channel.id || channel._id || (channel.platform + index);
              const isSelected = selectedChannels.includes(channelId);
              const commissionRate = channel.commissionRate || 0;
              const config = channelConfigs[channelId];
              const configurationLabel = !isSelected
                ? null
                : channel.platform === 'SHOPIFY'
                  ? 'Ready'
                  : config?.categoryId
                    ? 'Đã chọn danh mục'
                    : 'Cấu hình sau';

              return (
                <div key={channelId} className={styles.channelItem}>
                  <div className={styles.channelInfo}>
                    <div className={`${styles.channelIcon} ${styles[icon.className]}`}>
                      {icon.label}
                    </div>
                    <div className={styles.channelDetails}>
                      <span className={styles.channelName}>
                        {channel.displayName || channel.platform}
                      </span>
                      <span className={styles.commissionRate}>
                        % hoa hồng: {commissionRate}%
                      </span>
                      {configurationLabel && <span className={styles.configurationStatus}>{configurationLabel}</span>}
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    className={styles.toggle}
                    checked={isSelected}
                    onChange={() => setValue('channelIds', isSelected ? selectedChannels.filter((id) => id !== channelId) : [...selectedChannels, channelId], { shouldDirty: true })}
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
            checked={showProduct === 'ACTIVE'}
            onChange={() => setValue('status', showProduct === 'ACTIVE' ? 'DRAFT' : 'ACTIVE', { shouldDirty: true })}
          />
        </div>
      </div>

      {/* Action buttons */}
      <div className={styles.actions}>
        <button
          type="button"
          className={styles.submitBtn}
          onClick={handleSubmit(onSubmit, onInvalid)}
          disabled={isSubmitting}
        >
          {isSubmitting ? (
            <div className={styles.spinner} />
          ) : (
            <Save className={styles.submitIcon} />
          )}
          {isSubmitting ? (isEditMode ? 'Đang cập nhật...' : 'Đang tạo...') : (isEditMode ? 'Cập nhật' : 'Tạo sản phẩm')}
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
