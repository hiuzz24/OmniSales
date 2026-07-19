import { useCallback, useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import productApi from '../../../api/productApi';
import styles from './TabInventory.module.css';

const numberFormatter = new Intl.NumberFormat('vi-VN');

const TabInventory = ({
  productId,
  insights,
  insightsLoading,
  insightsError,
  onRetryInsights,
}) => {
  const [transactionPage, setTransactionPage] = useState({
    content: [],
    page: 0,
    totalPages: 0,
    totalElements: 0,
    first: true,
    last: true,
  });
  const [page, setPage] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');

  const fetchHistory = useCallback(async () => {
    try {
      setHistoryLoading(true);
      setHistoryError('');
      const response = await productApi.getInventoryTransactions(productId, page, 30);
      const data = response.data?.data || response.data || response;
      setTransactionPage(data);
    } catch (error) {
      setHistoryError('Không tải được lịch sử xuất nhập kho.');
    } finally {
      setHistoryLoading(false);
    }
  }, [page, productId]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const handleRetry = () => {
    onRetryInsights();
    fetchHistory();
  };

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
    return `${before} → ${after} (${formatQuantityChange(change)})`;
  };

  const isInboundTransaction = (type) => (
    ['IMPORT', 'INBOUND', 'TRANSFER_IN', 'ORDER_CANCEL'].includes(type)
  );

  if (insightsLoading && !insights) {
    return <div className={styles.feedbackState}>Đang tải dữ liệu tồn kho...</div>;
  }

  if (insightsError && !insights) {
    return (
      <div className={styles.feedbackState}>
        <span>{insightsError}</span>
        <button type="button" className={styles.retryButton} onClick={handleRetry}>
          <RefreshCw size={16} />
          Thử lại
        </button>
      </div>
    );
  }

  if (!insights) return null;

  const inventory = insights.inventory;
  const warehouses = insights.warehouses || [];
  const transactions = transactionPage.content || [];

  return (
    <div className={styles.tabContainer}>
      <div className={styles.card}>
        <div className={styles.cardHeaderRow}>
          <div>
            <h3 className={styles.cardTitle}>Thông tin tồn kho</h3>
            <p className={styles.cardSubtitle}>Số lượng thực tế trên toàn bộ kho đang hoạt động</p>
          </div>
          <button type="button" className={styles.iconButton} onClick={handleRetry} title="Làm mới dữ liệu">
            <RefreshCw size={17} />
          </button>
        </div>

        <div className={styles.summaryGrid}>
          <div className={styles.summaryItem}>
            <span>Tổng tồn</span>
            <strong>{numberFormatter.format(inventory.quantityOnHand || 0)}</strong>
          </div>
          <div className={styles.summaryItem}>
            <span>Đã đặt trước</span>
            <strong className={styles.subValueWarning}>{numberFormatter.format(inventory.reservedQuantity || 0)}</strong>
          </div>
          <div className={styles.summaryItem}>
            <span>Có thể bán</span>
            <strong className={styles.subValueSuccess}>{numberFormatter.format(inventory.availableQuantity || 0)}</strong>
          </div>
        </div>

        <div className={styles.formula}>
          Available = Quantity on hand - Reserved: {numberFormatter.format(inventory.availableQuantity || 0)} ={' '}
          {numberFormatter.format(inventory.quantityOnHand || 0)} - {numberFormatter.format(inventory.reservedQuantity || 0)}
        </div>

        <div className={styles.warehouseSection}>
          <h4 className={styles.sectionTitle}>Chi tiết theo kho</h4>
          {warehouses.length === 0 ? (
            <div className={styles.emptyWarehouse}>Sản phẩm chưa có tồn kho tại kho đang hoạt động.</div>
          ) : (
            <div className={styles.warehouseGrid}>
              {warehouses.map((warehouse) => (
                <div className={styles.warehouseItem} key={warehouse.warehouseId}>
                  <div className={styles.warehouseHeader}>
                    <strong>{warehouse.warehouseName}</strong>
                    <span className={warehouse.lowStock ? styles.lowStock : styles.inStock}>
                      {warehouse.lowStock ? 'Sắp hết hàng' : 'Ổn định'}
                    </span>
                  </div>
                  <dl className={styles.warehouseMetrics}>
                    <div><dt>Tổng tồn</dt><dd>{numberFormatter.format(warehouse.quantityOnHand || 0)}</dd></div>
                    <div><dt>Đã giữ</dt><dd>{numberFormatter.format(warehouse.reservedQuantity || 0)}</dd></div>
                    <div><dt>Có thể bán</dt><dd>{numberFormatter.format(warehouse.availableQuantity || 0)}</dd></div>
                  </dl>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h3 className={styles.cardTitle}>Lịch sử xuất nhập kho</h3>
          <p className={styles.cardSubtitle}>Các giao dịch kho hàng mới nhất của sản phẩm</p>
        </div>

        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Biến thể</th>
                <th>Kho</th>
                <th>Loại</th>
                <th>Trước / Sau</th>
                <th>Người thực hiện</th>
                <th>Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              {historyLoading ? (
                <tr><td colSpan="7" className={styles.emptyState}>Đang tải lịch sử xuất nhập kho...</td></tr>
              ) : historyError ? (
                <tr><td colSpan="7" className={styles.errorState}>{historyError}</td></tr>
              ) : transactions.length === 0 ? (
                <tr><td colSpan="7" className={styles.emptyState}>Chưa có giao dịch kho cho sản phẩm này</td></tr>
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
                    <td>{item.performedByName || '-'}</td>
                    <td className={styles.textGray}>{item.note || '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {!historyLoading && !historyError && transactionPage.totalPages > 0 && (
          <div className={styles.pagination}>
            <button type="button" disabled={transactionPage.first} onClick={() => setPage((value) => value - 1)}>
              Trước
            </button>
            <span>Trang {transactionPage.page + 1} / {transactionPage.totalPages}</span>
            <button type="button" disabled={transactionPage.last} onClick={() => setPage((value) => value + 1)}>
              Sau
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default TabInventory;
