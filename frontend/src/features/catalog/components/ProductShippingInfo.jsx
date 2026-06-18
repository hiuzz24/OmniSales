import styles from './ProductShippingInfo.module.css';

const ProductShippingInfo = ({ weightGrams, dimensions, lowStockThreshold, onChange, errors = {} }) => {
  return (
    <div className={styles.card}>
      <div className={styles.cardTitle}>Thông tin vận hành</div>

      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label}>Khối lượng (g)</label>
          <input
            id="product-weight"
            type="number"
            className={styles.input}
            placeholder="200"
            value={weightGrams ?? ''}
            onChange={(e) => onChange('weightGrams', e.target.value)}
            min="0"
          />
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Kích thước (cm)</label>
          <input
            id="product-dimensions"
            type="text"
            className={styles.input}
            placeholder="40 x 30 x 2"
            value={dimensions || ''}
            onChange={(e) => onChange('dimensions', e.target.value)}
          />
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Ngưỡng cảnh báo tồn thấp</label>
          <input
            id="product-low-stock-threshold"
            type="number"
            className={`${styles.input} ${errors.lowStockThreshold ? styles.inputError : ''}`}
            placeholder="5"
            value={lowStockThreshold ?? ''}
            onChange={(e) => onChange('lowStockThreshold', e.target.value)}
            min="0"
            step="1"
          />
          {errors.lowStockThreshold && <span className={styles.errorText}>{errors.lowStockThreshold}</span>}
        </div>
      </div>
    </div>
  );
};

export default ProductShippingInfo;
