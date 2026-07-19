import {
  Activity,
  DollarSign,
  Package,
  RefreshCw,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import styles from './ProductStatsGrid.module.css';

const currencyFormatter = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
});

const numberFormatter = new Intl.NumberFormat('vi-VN');

const Trend = ({ value, label = 'so với tháng trước' }) => {
  if (value === null || value === undefined) {
    return <div className={`${styles.trend} ${styles.textGray}`}>Chưa có dữ liệu tháng trước</div>;
  }

  const numericValue = Number(value);
  const isUp = numericValue > 0;
  const isDown = numericValue < 0;
  const Icon = isDown ? TrendingDown : TrendingUp;
  const tone = isUp ? styles.textGreen : isDown ? styles.textAmber : styles.textGray;
  const prefix = isUp ? '+' : '';

  return (
    <div className={`${styles.trend} ${tone}`}>
      <Icon className={styles.trendIcon} />
      {prefix}{numberFormatter.format(numericValue)}% {label}
    </div>
  );
};

const ProductStatsGrid = ({ insights, loading, error, onRetry }) => {
  if (loading && !insights) {
    return (
      <div className={styles.feedbackCard}>
        <RefreshCw className={`${styles.feedbackIcon} ${styles.spin}`} />
        Đang tải thống kê sản phẩm...
      </div>
    );
  }

  if (error && !insights) {
    return (
      <div className={styles.feedbackCard}>
        <span>{error}</span>
        <button type="button" className={styles.retryButton} onClick={onRetry}>
          <RefreshCw size={16} />
          Thử lại
        </button>
      </div>
    );
  }

  if (!insights) return null;

  const { inventory, sales, monthlyComparison } = insights;
  const grossProfitAvailable = sales.grossProfit !== null && sales.grossProfit !== undefined;

  return (
    <div className={styles.grid}>
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Tồn kho</span>
          <Package className={styles.iconBlue} />
        </div>
        <div className={styles.value}>{numberFormatter.format(inventory.quantityOnHand || 0)}</div>
        <div className={`${styles.trend} ${inventory.lowStock ? styles.textAmber : styles.textGreen}`}>
          {inventory.lowStock
            ? <TrendingDown className={styles.trendIcon} />
            : <TrendingUp className={styles.trendIcon} />}
          {inventory.lowStock ? 'Sắp hết hàng' : 'Tồn kho ổn định'}
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Đã bán</span>
          <Activity className={styles.iconIndigo} />
        </div>
        <div className={styles.value}>{numberFormatter.format(sales.unitsSold || 0)}</div>
        <Trend value={monthlyComparison.unitsSoldChangePercent} />
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Doanh thu</span>
          <DollarSign className={styles.iconGreen} />
        </div>
        <div className={styles.value}>{currencyFormatter.format(sales.revenue || 0)}</div>
        <Trend value={monthlyComparison.revenueChangePercent} />
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <span className={styles.title}>Lợi nhuận gộp</span>
          <TrendingUp className={styles.iconPurple} />
        </div>
        <div className={styles.value}>
          {grossProfitAvailable ? currencyFormatter.format(sales.grossProfit) : 'Chưa xác định'}
        </div>
        {grossProfitAvailable ? (
          <>
            <Trend value={monthlyComparison.grossProfitChangePercent} />
            <div className={`${styles.trend} ${styles.textGray}`}>
              Biên lợi nhuận: {numberFormatter.format(Number(sales.marginPercent || 0))}%
            </div>
          </>
        ) : (
          <div className={`${styles.trend} ${styles.textAmber}`}>Thiếu dữ liệu giá vốn</div>
        )}
        {!sales.costDataComplete && grossProfitAvailable && (
          <div className={styles.costNote}>Một phần lợi nhuận sử dụng giá vốn hiện tại.</div>
        )}
      </div>
    </div>
  );
};

export default ProductStatsGrid;
