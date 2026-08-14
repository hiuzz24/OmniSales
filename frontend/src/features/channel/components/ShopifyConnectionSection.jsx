import { useMemo } from 'react';
import OAuthConnectionSection from './OAuthConnectionSection';
import { canonicalShopifyDomain } from '../utils/shopifyShop';
import styles from './ChannelFormModal.module.css';

/** Nhận tên miền Shopify và bắt đầu luồng cài đặt ứng dụng. */
const ShopifyConnectionSection = ({ value, error, isRedirecting, onChange, onConnect, onCancel }) => {
  const preview = useMemo(() => {
    if (!value.trim()) return null;
    try {
      return canonicalShopifyDomain(value);
    } catch {
      return null;
    }
  }, [value]);

  return (
    <>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="shopify-domain">
          Shop Domain <span className={styles.required}>*</span>
        </label>
        <input
          id="shopify-domain"
          type="text"
          className={`${styles.input} ${error ? styles.inputError : ''}`}
          placeholder="mystore hoặc https://mystore.myshopify.com/admin"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={isRedirecting}
        />
        {error && <span className={styles.errorMsg}>{error}</span>}
        <span className={styles.fieldHint}>
          {preview ? `Shop sẽ kết nối: ${preview}` : 'Nhập handle, domain myshopify.com hoặc URL Shopify đầy đủ.'}
        </span>
      </div>
      <OAuthConnectionSection
        platformName="Shopify"
        description="Bạn sẽ được chuyển đến Shopify Admin để cấp quyền và kết nối shop."
        color="#5e8e3e"
        isRedirecting={isRedirecting}
        showCancel
        onConnect={onConnect}
        onCancel={onCancel}
      />
    </>
  );
};

export default ShopifyConnectionSection;
