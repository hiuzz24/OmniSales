import { TrendingUp, TrendingDown, Package, DollarSign, Activity } from 'lucide-react';
import styles from './ProductStatsGrid.module.css';

const ProductStatsGrid = ({ product }) => {
  // Real calculation for Inventory
  const totalQuantity = product.variants?.reduce((sum, v) => sum + (v.quantityOnHand || 0), 0) || 0;
  const lowStockThreshold = product.lowStockThreshold ?? 5;
  const isLowStock = totalQuantity < lowStockThreshold;

  // Mock data for sales
  const mockSold = 1234;
  const mockSoldTrend = "+12% so với tháng trước";
  const mockRevenue = "185.1M";
  const mockRevenueTrend = "+8% so với tháng trước";
  const mockMargin = "86.4M";
  const mockMarginPercent = "Margin: 47%";

  return (
    <div className={styles.grid}>
      {/* Tồn kho */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Tồn kho</span>
          <Package className={styles.iconBlue} />
        </div>
        <div className={styles.value}>{totalQuantity}</div>
        <div className={`${styles.trend} ${isLowStock ? styles.textAmber : styles.textGreen}`}>
          {isLowStock ? <TrendingDown className={styles.trendIcon} /> : <TrendingUp className={styles.trendIcon} />}
          {isLowStock ? 'Sắp hết hàng' : 'Đủ hàng'}
        </div>
      </div>

      {/* Đã bán (Mock) */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Đã bán <span className={styles.mockNote}>(Mock)</span></span>
          <Activity className={styles.iconIndigo} />
        </div>
        <div className={styles.value}>{mockSold}</div>
        <div className={`${styles.trend} ${styles.textIndigo}`}>
          <TrendingUp className={styles.trendIcon} />
          {mockSoldTrend}
        </div>
      </div>

      {/* Doanh thu (Mock) */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Doanh thu <span className={styles.mockNote}>(Mock)</span></span>
          <DollarSign className={styles.iconGreen} />
        </div>
        <div className={styles.value}>{mockRevenue}</div>
        <div className={`${styles.trend} ${styles.textGreen}`}>
          <TrendingUp className={styles.trendIcon} />
          {mockRevenueTrend}
        </div>
      </div>

      {/* Lợi nhuận gộp (Mock) */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Lợi nhuận gộp <span className={styles.mockNote}>(Mock)</span></span>
          <TrendingUp className={styles.iconPurple} />
        </div>
        <div className={styles.value}>{mockMargin}</div>
        <div className={`${styles.trend} ${styles.textGray}`}>
          {mockMarginPercent}
        </div>
      </div>
    </div>
  );
};

export default ProductStatsGrid;
