import styles from './TabOverview.module.css';

const TabOverview = ({ product }) => {
  // Compute price range if multiple variants
  let priceDisplay = '0 đ';
  let costDisplay = '0 đ';
  let profitDisplay = '0 đ';
  let profitVal = 0;

  if (product.variants && product.variants.length > 0) {
    if (product.variants.length === 1) {
      const v = product.variants[0];
      priceDisplay = `${v.price?.toLocaleString()} đ`;
      costDisplay = `${v.costPrice?.toLocaleString() || 0} đ`;
      profitVal = (v.price || 0) - (v.costPrice || 0);
      profitDisplay = `${profitVal.toLocaleString()} đ`;
    } else {
      const prices = product.variants.map(v => v.price || 0);
      const minP = Math.min(...prices);
      const maxP = Math.max(...prices);
      priceDisplay = minP === maxP ? `${minP.toLocaleString()} đ` : `${minP.toLocaleString()} - ${maxP.toLocaleString()} đ`;
      const costs = product.variants.map(v => v.costPrice || 0);
      const minC = Math.min(...costs);
      const maxC = Math.max(...costs);
      costDisplay = minC === maxC ? `${minC.toLocaleString()} đ` : `${minC.toLocaleString()} - ${maxC.toLocaleString()} đ`;

      const profits = product.variants.map(v => (v.price || 0) - (v.costPrice || 0));
      const minProfit = Math.min(...profits);
      const maxProfit = Math.max(...profits);
      profitDisplay = minProfit === maxProfit ? `${minProfit.toLocaleString()} đ` : `${minProfit.toLocaleString()} - ${maxProfit.toLocaleString()} đ`;
    }
  }

  const barcodeDisplay = product.variants?.length === 1 ? product.variants[0].barcode : 'Theo biến thể';

  return (
    <div className={styles.tabContainer}>
      <div className={styles.grid}>
        {/* Thông tin cơ bản */}
        <div className={styles.card}>
          <h3 className={styles.cardTitle}>Thông tin cơ bản</h3>
          <div className={styles.infoList}>
            <div className={styles.infoRow}>
              <span className={styles.label}>Tên sản phẩm:</span>
              <span className={styles.value}>{product.name}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>SKU:</span>
              <span className={styles.value}>{product.sku}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>Barcode:</span>
              <span className={styles.value}>{barcodeDisplay || 'N/A'}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>Danh mục:</span>
              <span className={styles.value}>{product.categoryName || 'N/A'}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>Thương hiệu:</span>
              <span className={styles.value}>{product.brand || 'N/A'}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>Trạng thái:</span>
              <span className={product.status === 'ACTIVE' ? styles.statusActive : styles.statusDraft}>
                {product.status === 'ACTIVE' ? 'Hoạt động' : 'Bản nháp'}
              </span>
            </div>
          </div>
        </div>

        {/* Giá & Chi phí */}
        <div className={styles.card}>
          <h3 className={styles.cardTitle}>Giá & Chi phí</h3>
          <div className={styles.infoList}>
            <div className={styles.infoRow}>
              <span className={styles.label}>Giá bán:</span>
              <span className={styles.valueStrong}>{priceDisplay}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>Giá vốn:</span>
              <span className={styles.value}>{costDisplay}</span>
            </div>
            <div className={styles.divider}></div>
            <div className={styles.infoRow}>
              <span className={styles.label}>Lợi nhuận/SP:</span>
              <span className={styles.valueProfit}>{profitDisplay}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Mô tả sản phẩm */}
      <div className={styles.card}>
        <h3 className={styles.cardTitle}>Mô tả sản phẩm</h3>
        <p className={styles.description}>
          {product.description || 'Chưa có mô tả.'}
        </p>
      </div>

      {/* Thông tin vận chuyển */}
      <div className={styles.card}>
        <h3 className={styles.cardTitle}>Thông tin vận chuyển</h3>
        <div className={styles.infoList}>
          <div className={styles.infoRow}>
            <span className={styles.label}>Khối lượng:</span>
            <span className={styles.value}>{product.weightGrams ? `${product.weightGrams}g` : 'N/A'}</span>
          </div>
          <div className={styles.infoRow}>
            <span className={styles.label}>Kích thước:</span>
            <span className={styles.value}>{product.attributes?.dimensions || 'N/A'}</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TabOverview;
