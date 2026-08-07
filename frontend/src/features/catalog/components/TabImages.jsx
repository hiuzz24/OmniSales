import { Image as ImageIcon } from 'lucide-react';
import styles from './TabImages.module.css';

const TabImages = ({ product }) => {
  const images = product.images || [];

  const variantImages = (product.variants || []).flatMap(v => {
    return (v.images || []).map(img => ({
      ...img,
      variantName: [v.optionValues?.Size, v.optionValues?.['Màu']].filter(Boolean).join(' / ') || v.sku || 'Biến thể'
    }));
  });

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.titleRow}>
          <ImageIcon className={styles.titleIcon} />
          <h3 className={styles.cardTitle}>Hình ảnh sản phẩm</h3>
        </div>
        <p className={styles.cardSubtitle}>Thư viện hình ảnh của sản phẩm</p>
      </div>

      {images.length === 0 && variantImages.length === 0 ? (
        <div className={styles.emptyState}>Sản phẩm chưa có hình ảnh</div>
      ) : (
        <div className={styles.gallerySections}>
          {images.length > 0 && (
            <section className={styles.gallerySection}>
              <h4 className={styles.galleryTitle}>Ảnh chung sản phẩm</h4>
              <div className={styles.imageGrid}>
                {images.map((img, index) => (
                  <div key={img.id || `main-${index}`} className={styles.imageWrapper}>
                    <img src={img.url} alt={`Product ${index}`} className={styles.image} />
                    {img.isPrimary && (
                      <span className={styles.primaryBadge}>Ảnh chính</span>
                    )}
                  </div>
                ))}
              </div>
            </section>
          )}

          {variantImages.length > 0 && (
            <section className={styles.gallerySection}>
              <h4 className={styles.galleryTitle}>Ảnh theo biến thể</h4>
              <div className={styles.imageGrid}>
                {variantImages.map((vImg, index) => (
                  <div key={vImg.id || `var-${index}`} className={styles.imageWrapper}>
                    <img src={vImg.url} alt={vImg.variantName} className={styles.image} />
                    <span className={styles.variantBadge}>
                      {vImg.variantName}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
};

export default TabImages;
