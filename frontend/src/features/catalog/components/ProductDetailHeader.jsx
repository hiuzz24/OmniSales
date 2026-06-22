import { ArrowLeft, Cloud, Edit, Trash2 } from 'lucide-react';
import styles from './ProductDetailHeader.module.css';

const ProductDetailHeader = ({ product, onBack, onDelete, onEdit }) => {
  const primaryImage = product.images?.find(img => img.isPrimary)?.url || 'https://via.placeholder.com/40';

  return (
    <div className={styles.header}>
      <button className={styles.backBtn} onClick={onBack}>
        <ArrowLeft className={styles.backIcon} />
        Quay lại
      </button>

      <div className={styles.mainInfo}>
        <img src={primaryImage} alt={product.name} className={styles.thumbnail} />
        <div className={styles.textContainer}>
          <div className={styles.titleRow}>
            <h1 className={styles.title}>{product.name || 'Đang cập nhật'}</h1>
            {product.status === 'ACTIVE' ? (
              <span className={styles.badgeActive}>Hoạt động</span>
            ) : (
              <span className={styles.badgeDraft}>Bản nháp</span>
            )}
          </div>
          <p className={styles.sku}>SKU: {product.sku}</p>
        </div>
      </div>

      <div className={styles.actions}>
        <button className={styles.btnSync}>
          <Cloud className={styles.btnIcon} />
          Đồng bộ
        </button>
        <button className={styles.btnEdit} onClick={onEdit}>
          <Edit className={styles.btnIcon} />
          Chỉnh sửa
        </button>
        <button
          className={styles.btnDanger}
          onClick={() => {
            if (window.confirm('Bạn có chắc chắn muốn xóa sản phẩm này không? Hành động này không thể hoàn tác.')) {
              onDelete && onDelete();
            }
          }}
        >
          <Trash2 className={styles.btnIcon} />
          Xóa
        </button>
      </div>
    </div>
  );
};

export default ProductDetailHeader;
