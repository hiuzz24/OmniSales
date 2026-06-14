import { Search } from 'lucide-react';
import styles from './ProductFilterBar.module.css';

const PLATFORM_LABELS = {
  SHOPEE: 'Shopee',
  TIKTOK: 'TikTok Shop',
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
  MANUAL: 'Thủ công',
};

const ProductFilterBar = ({ searchInput, onSearchChange, statusFilter, onStatusChange, platformFilter, onPlatformChange }) => {
  return (
    <div className={styles.filterBar}>
      <div className={styles.searchContainer}>
        <div className={styles.searchIconWrapper}>
          <Search className={styles.searchIcon} />
        </div>
        <input
          type="text"
          className={styles.searchInput}
          placeholder="Tìm kiếm sản phẩm theo tên hoặc SKU..."
          value={searchInput}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>
      
      <div className={styles.selectContainer}>
        <select 
          className={styles.selectInput}
          value={platformFilter}
          onChange={(e) => onPlatformChange(e.target.value)}
        >
          <option value="">Tất cả kênh</option>
          {Object.entries(PLATFORM_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <div className={styles.selectIconWrapper}>
          <svg className={styles.selectIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7"></path></svg>
        </div>
      </div>
      
      <div className={styles.selectContainer}>
        <select 
          className={styles.selectInput}
          value={statusFilter}
          onChange={(e) => onStatusChange(e.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Ngừng bán</option>
          <option value="DRAFT">Nháp</option>
        </select>
        <div className={styles.selectIconWrapper}>
          <svg className={styles.selectIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7"></path></svg>
        </div>
      </div>
    </div>
  );
};

export default ProductFilterBar;

