import styles from './ChannelFormModal.module.css';

/** Hiển thị các field cấu hình chung của kênh và kho liên kết. */
const ChannelSettingsFields = ({ form, errors, warehouses, isEdit, onChange }) => {
  const isLazada = form.platform === 'LAZADA';
  const isTikTok = form.platform === 'TIKTOK';

  return (
    <>
      <div className={styles.field}>
        <label className={styles.label}>Tên hiển thị <span className={styles.required}>*</span></label>
        <input
          type="text"
          className={`${styles.input} ${errors.displayName ? styles.inputError : ''}`}
          placeholder="Ví dụ: Shop chính hãng"
          value={form.displayName}
          onChange={(event) => onChange('displayName', event.target.value)}
        />
        {errors.displayName && <span className={styles.errorMsg}>{errors.displayName}</span>}
      </div>

      <div className={styles.field}>
        <label className={styles.label}>Tỷ lệ hoa hồng (%)</label>
        <div className={styles.inputGroup}>
          <input
            type="number"
            min="0"
            max="100"
            step="0.01"
            className={`${styles.input} ${errors.commissionRate ? styles.inputError : ''}`}
            value={form.commissionRate}
            onChange={(event) => onChange('commissionRate', event.target.value)}
          />
          <span className={styles.inputSuffix}>%</span>
        </div>
        {errors.commissionRate && <span className={styles.errorMsg}>{errors.commissionRate}</span>}
      </div>

      {isEdit && warehouses.length > 0 && (
        <div className={styles.field}>
          <label className={styles.label}>Kho nguồn tồn kho</label>
          <select
            className={styles.select}
            value={form.defaultWarehouseId}
            onChange={(event) => onChange('defaultWarehouseId', event.target.value)}
          >
            <option value="">Dùng tổng tồn tất cả kho</option>
            {warehouses.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
            ))}
          </select>
        </div>
      )}

      {isLazada && isEdit && (
        <div className={styles.field}>
          <label className={styles.label}>Lazada warehouse code</label>
          <input
            type="text"
            className={styles.input}
            value={form.lazadaWarehouseCode}
            onChange={(event) => onChange('lazadaWarehouseCode', event.target.value)}
          />
        </div>
      )}

      {isTikTok && isEdit && (
        <>
          <div className={styles.field}>
            <label className={styles.label}>TikTok Shop Cipher <span className={styles.required}>*</span></label>
            <input
              type="text"
              className={`${styles.input} ${errors.shopCipher ? styles.inputError : ''}`}
              value={form.shopCipher}
              onChange={(event) => onChange('shopCipher', event.target.value)}
            />
            {errors.shopCipher && <span className={styles.errorMsg}>{errors.shopCipher}</span>}
          </div>
          <div className={styles.field}>
            <label className={styles.label}>TikTok warehouse ID chính</label>
            <input
              type="text"
              className={styles.input}
              value={form.tiktokWarehouseId}
              onChange={(event) => onChange('tiktokWarehouseId', event.target.value)}
            />
          </div>
        </>
      )}
    </>
  );
};

export default ChannelSettingsFields;
