import styles from './ProductShippingInfo.module.css';
import { useFormContext } from 'react-hook-form';

/** Nhập kích thước đóng gói và ngưỡng cảnh báo tồn thấp của Product. */
const ProductShippingInfo = () => {
  const { register, formState: { errors } } = useFormContext();
  return (
  <div className={styles.card}>
    <div className={styles.cardTitle}>Thông tin vận hành</div>
    <div className={styles.row}>
      <div className={styles.field}>
        <label className={styles.label}>Khối lượng đóng gói (kg) *</label>
        <input id="product-package-weight" type="number" min="0" step="0.001" className={`${styles.input} ${errors.packageWeightKg ? styles.inputError : ''}`}
          {...register('packageWeightKg')} />
        {errors.packageWeightKg && <span className={styles.errorText}>{errors.packageWeightKg.message}</span>}
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Chiều rộng đóng gói (cm) *</label>
        <input id="product-package-width" type="number" min="0" step="0.001" className={`${styles.input} ${errors.packageWidthCm ? styles.inputError : ''}`}
          {...register('packageWidthCm')} />
        {errors.packageWidthCm && <span className={styles.errorText}>{errors.packageWidthCm.message}</span>}
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Chiều cao đóng gói (cm) *</label>
        <input id="product-package-height" type="number" min="0" step="0.001" className={`${styles.input} ${errors.packageHeightCm ? styles.inputError : ''}`}
          {...register('packageHeightCm')} />
        {errors.packageHeightCm && <span className={styles.errorText}>{errors.packageHeightCm.message}</span>}
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Chiều dài đóng gói (cm) *</label>
        <input id="product-package-length" type="number" min="0" step="0.001" className={`${styles.input} ${errors.packageLengthCm ? styles.inputError : ''}`}
          {...register('packageLengthCm')} />
        {errors.packageLengthCm && <span className={styles.errorText}>{errors.packageLengthCm.message}</span>}
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Ngưỡng cảnh báo tồn thấp *</label>
        <input id="product-low-stock-threshold" type="number" min="0" step="1"
          className={`${styles.input} ${errors.lowStockThreshold ? styles.inputError : ''}`}
          {...register('lowStockThreshold')} />
        {errors.lowStockThreshold && <span className={styles.errorText}>{errors.lowStockThreshold.message}</span>}
      </div>
    </div>
  </div>);
};

export default ProductShippingInfo;
