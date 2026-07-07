import { useEffect, useMemo, useState } from 'react';
import inventoryApi from '../../../api/inventoryApi';
import styles from './TabInventory.module.css';

const TabInventory = ({ product }) => {
  const [transactions, setTransactions] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');

  // Aggregate real inventory data from variants
  const totalQuantity = product.variants?.reduce((sum, v) => sum + (v.quantityOnHand || 0), 0) || 0;
  const availableQuantity = product.variants?.reduce((sum, v) => sum + (v.availableQuantity || 0), 0) || 0;
  const reservedQuantity = totalQuantity - availableQuantity;

  const variantIds = useMemo(() => {
    const ids = product.variants
      ?.map((variant) => variant.id || variant.variantId)
      .filter(Boolean) || [];
    return [...new Set(ids)];
  }, [product.variants]);

  useEffect(() => {
    let cancelled = false;

    const fetchHistory = async () => {
      if (variantIds.length === 0) {
        setTransactions([]);
        return;
      }

      try {
        setHistoryLoading(true);
        setHistoryError('');
        const pages = await Promise.all(
          variantIds.map((variantId) => inventoryApi.getTransactions(variantId, 0, 20))
        );
        if (cancelled) return;

        const merged = pages
          .flatMap((page) => page.content || [])
          .sort((a, b) => new Date(b.performedAt || 0) - new Date(a.performedAt || 0))
          .slice(0, 30);
        setTransactions(merged);
      } catch (error) {
        console.error('Lỗi khi tải lịch sử xuất nhập kho:', error);
        if (!cancelled) {
          setHistoryError('Không tải được lịch sử xuất nhập kho.');
        }
      } finally {
        if (!cancelled) {
          setHistoryLoading(false);
        }
      }
    };

    fetchHistory();

    return () => {
      cancelled = true;
    };
  }, [variantIds]);

  const formatDateTime = (value) => {
    if (!value) return '-';
    return new Date(value).toLocaleString('vi-VN');
  };

  const formatQuantityChange = (value) => {
    const number = Number(value ?? 0);
    return number > 0 ? `+${number}` : `${number}`;
  };

  const buildQuantityDetail = (transaction) => {
    const before = Number(transaction.quantityBefore ?? 0);
    const after = Number(transaction.quantityAfter ?? 0);
    const change = Number(transaction.quantityChange ?? after - before);
    const direction = change >= 0 ? 'lên' : 'xuống';
    const productName = transaction.variantName || 'Sản phẩm';
    const sku = transaction.variantSku ? ` (${transaction.variantSku})` : '';
    const warehouseName = transaction.warehouseName || 'Chưa rõ kho';
    return `${productName}${sku} tại ${warehouseName}: từ ${before} ${direction} ${after} (${formatQuantityChange(change)})`;
  };

  const isInboundTransaction = (type) => ['IMPORT', 'INBOUND', 'TRANSFER_IN', 'ORDER_CANCEL'].includes(type);

  return (
    <div className={styles.tabContainer}>
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h3 className={styles.cardTitle}>Thông tin tồn kho</h3>
          <p className={styles.cardSubtitle}>Chi tiết về số lượng và trạng thái kho hàng</p>
        </div>

        <div className={styles.statsGrid}>
          <div className={styles.statBox}>
            <div className={styles.statLabel}>Tổng số lượng trong kho</div>
            <div className={styles.statValueMain}>{totalQuantity}</div>
            <div className={styles.statSubBox}>
              <div className={styles.subLabelWarning}>Đã đặt trước (Reserved)</div>
              <div className={styles.subValueWarning}>{reservedQuantity}</div>
            </div>
          </div>
          <div className={styles.statBox}>
            <div className={styles.statLabel}>Kho</div>
            <div className={styles.statValueMain}>Kho Quận 1</div>
            <div className={styles.statSubBox}>
              <div className={styles.subLabelSuccess}>Có thể bán (Available)</div>
              <div className={styles.subValueSuccess}>{availableQuantity}</div>
            </div>
          </div>
        </div>

        <div className={styles.formula}>
          Công thức: Available = Warehouse Quantity - Reserved<br/>
          {availableQuantity} = {totalQuantity} - {reservedQuantity}
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h3 className={styles.cardTitle}>Lịch sử xuất nhập kho</h3>
          <p className={styles.cardSubtitle}>Các giao dịch kho hàng gần đây</p>
        </div>
        
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Sản phẩm</th>
                <th>Kho</th>
                <th>Loại</th>
                <th>Chi tiết tồn kho</th>
                <th>Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              {historyLoading ? (
                <tr>
                  <td colSpan="6" className={styles.emptyState}>Đang tải lịch sử xuất nhập kho...</td>
                </tr>
              ) : historyError ? (
                <tr>
                  <td colSpan="6" className={styles.errorState}>{historyError}</td>
                </tr>
              ) : transactions.length === 0 ? (
                <tr>
                  <td colSpan="6" className={styles.emptyState}>Chưa có giao dịch xuất nhập kho cho sản phẩm này</td>
                </tr>
              ) : (
                transactions.map((item) => (
                  <tr key={item.id}>
                    <td>{formatDateTime(item.performedAt)}</td>
                    <td>
                      <div className={styles.productName}>{item.variantName || '-'}</div>
                      {item.variantSku && <div className={styles.productSku}>{item.variantSku}</div>}
                    </td>
                    <td>{item.warehouseName || '-'}</td>
                    <td>
                      <span className={isInboundTransaction(item.type) ? styles.badgeSuccess : styles.badgeBlue}>
                        {item.typeLabel || item.type}
                      </span>
                    </td>
                    <td className={isInboundTransaction(item.type) ? styles.textSuccess : styles.textBlue}>
                      {buildQuantityDetail(item)}
                    </td>
                    <td className={styles.textGray}>{item.note || '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default TabInventory;
