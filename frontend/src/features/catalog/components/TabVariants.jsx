import { Boxes } from 'lucide-react';
import styles from './TabVariants.module.css';

const TabVariants = ({ product }) => {
  const variants = product.variants || [];

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <span className={styles.titleIcon}><Boxes aria-hidden="true" /></span>
        <div>
          <h3 className={styles.cardTitle}>Danh sách biến thể</h3>
          <p className={styles.cardSubtitle}>Quản lý các phiên bản khác nhau của sản phẩm</p>
        </div>
      </div>

      {variants.length === 0 ? (
        <div className={styles.emptyState}>Sản phẩm không có biến thể</div>
      ) : (
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Ảnh</th>
                <th>SKU</th>
                <th>Kích thước</th>
                <th>Màu sắc</th>
                <th>Tồn kho</th>
                <th>Đã bán <span className={styles.mockNote}>(Mock)</span></th>
              </tr>
            </thead>
            <tbody>
              {variants.map((variant) => {
                const thumbnail = variant.images && variant.images.length > 0 
                  ? variant.images[0].url 
                  : 'https://via.placeholder.com/40';
                
                const size = variant.optionValues?.['Size'] || '-';
                const color = variant.optionValues?.['Màu'] || '-';
                const stock = variant.quantityOnHand || 0;
                
                // Mock sold for variant
                const mockSold = Math.floor(Math.random() * 300) + 50;

                return (
                  <tr key={variant.id} style={{ opacity: variant.isActive === false ? 0.6 : 1 }}>
                    <td>
                      <img src={thumbnail} alt="variant" className={styles.thumbnail} />
                    </td>
                    <td>
                      <div>{variant.sku}</div>
                      {variant.isActive === false && (
                        <div className={styles.inactiveBadge}>Đã vô hiệu hóa</div>
                      )}
                    </td>
                    <td>{size}</td>
                    <td>{color}</td>
                    <td>{stock}</td>
                    <td>{mockSold}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default TabVariants;
