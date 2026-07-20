import styles from './ProductShippingInfo.module.css';
import { useFormContext } from 'react-hook-form';

const ProductShippingInfo = () => {
  const { register, formState: { errors } } = useFormContext();
  return (
  <div className={styles.card}>
    <div className={styles.cardTitle}>Thong tin van hanh</div>
    <div className={styles.row}>
      <div className={styles.field}>
        <label className={styles.label}>Package Weight (kg)</label>
        <input id="product-package-weight" type="number" min="0" step="0.001" className={styles.input}
          {...register('packageWeightKg')} />
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Package Width (cm)</label>
        <input id="product-package-width" type="number" min="0" step="0.001" className={styles.input}
          {...register('packageWidthCm')} />
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Package Height (cm)</label>
        <input id="product-package-height" type="number" min="0" step="0.001" className={styles.input}
          {...register('packageHeightCm')} />
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Package Length (cm)</label>
        <input id="product-package-length" type="number" min="0" step="0.001" className={styles.input}
          {...register('packageLengthCm')} />
      </div>
      <div className={styles.field}>
        <label className={styles.label}>Nguong canh bao ton thap</label>
        <input id="product-low-stock-threshold" type="number" min="0" step="1"
          className={`${styles.input} ${errors.lowStockThreshold ? styles.inputError : ''}`}
          {...register('lowStockThreshold')} />
        {errors.lowStockThreshold && <span className={styles.errorText}>{errors.lowStockThreshold.message}</span>}
      </div>
    </div>
  </div>);
};

export default ProductShippingInfo;
