import styles from './ProductShippingInfo.module.css';

const ProductShippingInfo = ({ weightGrams, dimensions, onChange }) => {
  return (
    <div className={styles.card}>
      <div className={styles.cardTitle}>Thông tin vận chuyển</div>

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
      </div>
    </div>
  );
};

export default ProductShippingInfo;
