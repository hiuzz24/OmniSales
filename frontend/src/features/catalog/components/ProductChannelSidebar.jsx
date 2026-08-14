import { Save } from 'lucide-react';
import { useFormContext, useWatch } from 'react-hook-form';
import styles from './ProductChannelSidebar.module.css';

const PLATFORM_ICONS = {
  SHOPEE: { label: 'S', className: 'channelIconShopee' },
  TIKTOK: { label: 'T', className: 'channelIconTiktok' },
  LAZADA: { label: 'L', className: 'channelIconLazada' },
  SHOPIFY: { label: 'SH', className: 'channelIconShopify' },
};

/** Cho phép chọn các kênh sẽ được cấu hình và liên kết với sản phẩm. */
const ProductChannelSidebar = ({
  channels = [],
  onSubmit,
  onInvalid,
  onCancel,
  warehouses = [],
  selectedWarehouseId = '',
  onWarehouseChange,
  isEditMode = false,
}) => {
  const { control, setValue, handleSubmit, formState: { isSubmitting } } = useFormContext();
  const [selectedChannels = [], showProduct, channelConfigs = {}] = useWatch({
    control,
    name: ['channelIds', 'status', 'channelConfigs'],
  });
  const selectedCount = selectedChannels.length;
  // Trả về biểu tượng hiển thị theo platform.
  const getIcon = (platform) => PLATFORM_ICONS[platform?.toUpperCase()] || { label: '?', className: 'channelIconDefault' };

  // Thêm hoặc bỏ channel ID khỏi danh sách kênh đã chọn.
  const toggleChannel = (channelId, isSelected) => {
    setValue(
      'channelIds',
      isSelected
        ? selectedChannels.filter((id) => id !== channelId)
        : [...selectedChannels, channelId],
      { shouldDirty: true },
    );
  };

  return (
    <div className={styles.sidebar}>
      <div className={styles.card}>
        <div className={styles.cardTitle}>{isEditMode ? 'Liên kết sàn bán' : 'Kênh bán hàng'}</div>
        <div className={styles.cardSubtitle}>
          {isEditMode
            ? 'Bật các sàn muốn liên kết. Các sàn này sẽ dùng chung tồn kho mặc định của sản phẩm.'
            : 'Chọn kênh để đồng bộ sản phẩm'}
        </div>
        <div className={styles.channelSummary}>
          <span>{selectedCount} sàn đang active</span>
          <strong>{selectedCount > 0 ? 'Dùng chung tồn kho' : 'Chưa chọn sàn'}</strong>
        </div>

        {channels.length === 0 ? (
          <div className={styles.emptyChannels}>Chưa có kênh nào được thiết lập</div>
        ) : (
          <div className={styles.channelList}>
            {channels.map((channel, index) => {
              const icon = getIcon(channel.platform);
              const id = channel.id || channel._id || `${channel.platform}${index}`;
              const isSelected = selectedChannels.includes(id);
              const commissionRate = channel.commissionRate || 0;
              const config = channelConfigs[id];
              const configurationLabel = !isSelected
                ? null
                : channel.platform === 'SHOPIFY'
                  ? 'Sẵn sàng đồng bộ'
                  : config?.categoryId
                    ? 'Đã có danh mục'
                    : 'Cần cấu hình danh mục';

              return (
                <div key={id} className={`${styles.channelItem} ${isSelected ? styles.channelItemActive : ''}`}>
                  <div className={styles.channelInfo}>
                    <div className={`${styles.channelIcon} ${styles[icon.className]}`}>
                      {icon.label}
                    </div>
                    <div className={styles.channelDetails}>
                      <span className={styles.channelName}>
                        {channel.displayName || channel.platform}
                      </span>
                      <span className={styles.commissionRate}>
                        {channel.platform} · hoa hồng {commissionRate}%
                      </span>
                      {configurationLabel && <span className={styles.configurationStatus}>{configurationLabel}</span>}
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    className={styles.toggle}
                    checked={isSelected}
                    aria-label={`Bật liên kết ${channel.displayName || channel.platform}`}
                    onChange={() => toggleChannel(id, isSelected)}
                  />
                </div>
              );
            })}
          </div>
        )}
      </div>

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

      {warehouses.length > 0 && (
        <div className={styles.card}>
          <div className={styles.cardTitle}>Kho nguồn tồn kho</div>
          <div className={styles.cardSubtitle}>
            Kho được dùng để tạo tồn ban đầu và làm nguồn đồng bộ cho các kênh đã chọn.
          </div>
          <select
            className={styles.select}
            value={selectedWarehouseId}
            onChange={(event) => onWarehouseChange?.(event.target.value)}
          >
            {warehouses.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>
                {warehouse.name}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className={styles.actions}>
        <button
          type="button"
          className={styles.submitBtn}
          onClick={handleSubmit(onSubmit, onInvalid)}
          disabled={isSubmitting}
        >
          {isSubmitting ? <div className={styles.spinner} /> : <Save className={styles.submitIcon} />}
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
