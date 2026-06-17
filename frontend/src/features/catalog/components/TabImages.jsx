import { Image as ImageIcon } from 'lucide-react';
import styles from './TabImages.module.css';

const TabImages = ({ product }) => {
  const images = product.images || [];

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.titleRow}>
          <ImageIcon className={styles.titleIcon} />
          <h3 className={styles.cardTitle}>Hình ảnh sản phẩm</h3>
        </div>
        <p className={styles.cardSubtitle}>Thư viện hình ảnh của sản phẩm</p>
      </div>

      {images.length === 0 ? (
        <div className={styles.emptyState}>Sản phẩm chưa có hình ảnh</div>
      ) : (
        <div className={styles.imageGrid}>
          {images.map((img, index) => (
            <div key={img.id || index} className={styles.imageWrapper}>
              <img src={img.url} alt={`Product ${index}`} className={styles.image} />
              {img.isPrimary && (
                <span className={styles.primaryBadge}>Ảnh chính</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default TabImages;
