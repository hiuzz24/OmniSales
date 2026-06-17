import { Info } from 'lucide-react';
import styles from './ProductPriceStock.module.css';

const ProductPriceStock = ({ price, costPrice, onChange, errors = {} }) => {
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
            value={price ?? ''}
            onChange={(e) => onChange('price', e.target.value)}
            min="0"
          />
          {errors.price && <span className={styles.errorText}>{errors.price}</span>}
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Giá vốn (đ)</label>
          <input
            id="product-cost-price"
            type="number"
            className={styles.input}
            placeholder="80000"
            value={costPrice ?? ''}
            onChange={(e) => onChange('costPrice', e.target.value)}
            min="0"
          />
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
