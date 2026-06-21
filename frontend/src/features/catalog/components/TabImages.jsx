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
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          {images.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 600, color: '#374151', marginBottom: '12px' }}>Ảnh chung sản phẩm</h4>
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
            </div>
          )}

          {variantImages.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 600, color: '#374151', marginBottom: '12px' }}>Ảnh theo biến thể</h4>
              <div className={styles.imageGrid}>
                {variantImages.map((vImg, index) => (
                  <div key={vImg.id || `var-${index}`} className={styles.imageWrapper}>
                    <img src={vImg.url} alt={vImg.variantName} className={styles.image} />
                    <span style={{
                      position: 'absolute',
                      bottom: '8px',
                      left: '8px',
                      backgroundColor: 'rgba(0,0,0,0.6)',
                      color: 'white',
                      padding: '2px 8px',
                      borderRadius: '4px',
                      fontSize: '11px',
                      pointerEvents: 'none'
                    }}>
                      {vImg.variantName}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default TabImages;
