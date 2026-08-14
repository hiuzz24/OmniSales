import { Info } from 'lucide-react';
import styles from './ProductPriceStock.module.css';

/** Hiển thị và cập nhật giá, giá vốn cùng thông tin tồn của sản phẩm đơn. */
const ProductPriceStock = ({
  price,
  costPrice,
  onChange,
  errors = {},
  channels = [],
  selectedChannels = [],
  disablePrice = false,
  disableCostPrice = false,
}) => {
  // Hiển thị giá gợi ý theo cấu hình tỷ giá của từng platform.
  const renderSuggestedPrices = () => {
    const numPrice = Number(price);
    if (!numPrice || isNaN(numPrice) || numPrice <= 0) return null;

    const selectedList = channels.filter((c, i) => selectedChannels.includes(c.id || c._id || (c.platform + i)));
    if (selectedList.length === 0) return null;

    return (
      <div style={{ marginTop: '8px', fontSize: '13px', color: '#059669', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ fontWeight: 500, color: '#374151' }}>Giá bán đề xuất:</div>
        {selectedList.map((channel, i) => {
          const channelId = channel.id || channel._id || (channel.platform + i);
          const rate = (channel.commissionRate || 0) / 100;
          if (rate >= 1) return <div key={channelId}>• {channel.platform}: N/A</div>;
          const suggested = Math.round(numPrice / (1 - rate));
          return <div key={channelId}>• {channel.platform}: {suggested.toLocaleString('vi-VN')}đ</div>;
        })}
      </div>
    );
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardTitle}>Giá & Chi phí</div>

      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label}>
            Giá bán (đ) <span className={styles.required}>*</span>
          </label>
          <input
            id="product-price"
            type="number"
            className={`${styles.input} ${errors.price ? styles.inputError : ''}`}
            placeholder="150000"
            value={disablePrice ? '0' : (price ?? '')}
            onChange={(e) => onChange('price', disablePrice ? '0' : e.target.value)}
            min="0"
            disabled={disablePrice}
          />
          {renderSuggestedPrices()}
          {errors.price && <span className={styles.errorText}>{errors.price.message}</span>}
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Giá vốn (đ) <span className={styles.required}>*</span></label>
          <input
            id="product-cost-price"
            type="number"
            className={`${styles.input} ${errors.costPrice ? styles.inputError : ''}`}
            placeholder="80000"
            value={disableCostPrice ? (costPrice ?? '0') : (costPrice ?? '')}
            onChange={(e) => onChange('costPrice', disableCostPrice ? (costPrice ?? '0') : e.target.value)}
            min="0"
            disabled={disableCostPrice}
          />
          {errors.costPrice && <span className={styles.errorText}>{errors.costPrice.message}</span>}
        </div>
      </div>

      <div className={styles.infoBox}>
        <Info className={styles.infoIcon} />
        <div className={styles.infoContent}>
          <div className={styles.infoTitle}>Lưu ý về tồn kho</div>
          <div className={styles.infoText}>
            Số lượng tồn kho sẽ được quản lý ở module Kho hàng. Sau khi tạo sản phẩm, bạn có thể nhập kho tại trang quản lý tồn kho.
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductPriceStock;
