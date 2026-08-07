import { Search } from 'lucide-react';
import styles from './ProductFilterBar.module.css';

const PLATFORM_LABELS = {
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
  TIKTOK: 'TikTok Shop',
};

const ProductFilterBar = ({
  searchInput,
  onSearchChange,
  statusFilter,
  onStatusChange,
  platformFilters = [],
  onPlatformChange,
}) => {
  const togglePlatform = (platform) => {
    onPlatformChange(
      platformFilters.includes(platform)
        ? platformFilters.filter((item) => item !== platform)
        : [...platformFilters, platform],
    );
  };

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
          onChange={(event) => onSearchChange(event.target.value)}
        />
      </div>

      <div className={styles.platformFilterWrap}>
        <div className={styles.platformHeader}>
          <span className={styles.platformLabel}>Kênh bán</span>
          {platformFilters.length > 0 && (
            <button type="button" className={styles.platformClear} onClick={() => onPlatformChange([])}>
              Xoa
            </button>
          )}
        </div>
        <div className={styles.platformFilterGroup} aria-label="Lọc theo kênh bán">
          {Object.entries(PLATFORM_LABELS).map(([value, label]) => {
            const active = platformFilters.includes(value);
            return (
              <button
                key={value}
                type="button"
                className={`${styles.platformFilterTag} ${styles[`platformFilter${value}`]} ${active ? styles.platformFilterTagActive : ''}`}
                onClick={() => togglePlatform(value)}
                aria-pressed={active}
              >
                <span className={styles.platformFilterDot} />
                <span>{label}</span>
                {active && <span className={styles.platformFilterCheck}>✓</span>}
              </button>
            );
          })}
        </div>
      </div>

      <div className={styles.selectContainer}>
        <select
          className={styles.selectInput}
          value={statusFilter}
          onChange={(event) => onStatusChange(event.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Ngừng bán</option>
        </select>
        <div className={styles.selectIconWrapper}>
          <svg className={styles.selectIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>
    </div>
  );
};

export default ProductFilterBar;

