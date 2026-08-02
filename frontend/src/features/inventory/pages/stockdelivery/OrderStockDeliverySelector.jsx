import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, ChevronLeft, ChevronRight, Eye, Gift, Loader2, PackageCheck, Plus, Search, Trash2, XCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import stockDeliveryService from '../../services/stockDeliveryService';
import OrderDetailPreviewModal from './OrderDetailPreviewModal';
import OrderGiftProductPickerModal from './OrderGiftProductPickerModal';
import styles from './OrderStockDeliverySelector.module.css';

const PAGE_SIZE = 8;

const unwrap = (response) => response?.data ?? response;

const errorText = (error) => {
  if (typeof error === 'string') return error;
  return error?.message || error?.error || 'Không thể xử lý yêu cầu';
};

export default function OrderStockDeliverySelector({ orderId }) {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [selectedIds, setSelectedIds] = useState(() => orderId ? [orderId] : []);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [detailOrderId, setDetailOrderId] = useState(null);
  const [giftPickerOrder, setGiftPickerOrder] = useState(null);
  const [giftItemsByOrderId, setGiftItemsByOrderId] = useState({});
  useEffect(() => {
    setSelectedIds(orderId ? [orderId] : []);
    setGiftItemsByOrderId({});
    setPage(0);
  }, [orderId]);

  const loadCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const response = await stockDeliveryService.getOrderCandidates({
        orderId: orderId || undefined,
        keyword: query || undefined,
        page,
        size: PAGE_SIZE,
      });
      const data = unwrap(response) || {};
      setPageData(data);
      const visibleIds = new Set((data.content || []).map((order) => order.orderId));
      setSelectedIds((current) => current.filter((id) => visibleIds.has(id)));
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [orderId, page, query]);

  useEffect(() => {
    loadCandidates();
  }, [loadCandidates]);

  const orders = pageData.content || [];

  const allSelected = orders.length > 0 && orders.every((order) => selectedIds.includes(order.orderId));
  const selectedCount = selectedIds.length;
  const totalPages = Math.max(1, pageData.totalPages || 1);

  const giftQuantityForOrder = (orderIdValue) => (giftItemsByOrderId[orderIdValue] || [])
    .reduce((sum, item) => sum + Number(item.quantity || 0), 0);

  const selectedQuantity = useMemo(() => {
    const orderQuantity = orders.filter((order) => selectedIds.includes(order.orderId))
      .reduce((sum, order) => sum + (order.totalQuantity || 0), 0);
    const giftQuantity = selectedIds.reduce(
      (sum, selectedOrderId) => sum + (giftItemsByOrderId[selectedOrderId] || [])
        .reduce((giftSum, item) => giftSum + Number(item.quantity || 0), 0),
      0,
    );
    return orderQuantity + giftQuantity;
  }, [giftItemsByOrderId, orders, selectedIds]);

  const overAvailableGifts = useMemo(() => {
    const totals = new Map();
    selectedIds.forEach((selectedOrderId) => {
      (giftItemsByOrderId[selectedOrderId] || []).forEach((item) => {
        const current = totals.get(item.productVariantId) || {
          sku: item.sku,
          quantity: 0,
          availableQuantity: Number(item.availableQuantity ?? 0),
        };
        current.quantity += Number(item.quantity || 0);
        current.availableQuantity = Math.min(
          current.availableQuantity,
          Number(item.availableQuantity ?? 0),
        );
        totals.set(item.productVariantId, current);
      });
    });
    return [...totals.values()].filter((item) => item.quantity > item.availableQuantity);
  }, [giftItemsByOrderId, selectedIds]);

  const submitSearch = (event) => {
    event.preventDefault();
    setPage(0);
    setQuery(keyword.trim());
  };

  const toggleOne = (orderId) => {
    setSelectedIds((current) => current.includes(orderId)
      ? current.filter((id) => id !== orderId)
      : [...current, orderId]);
  };

  const toggleAll = () => {
    setSelectedIds(allSelected ? [] : orders.map((order) => order.orderId));
  };

  const saveGifts = (order, giftItems) => {
    setGiftItemsByOrderId((current) => ({ ...current, [order.orderId]: giftItems }));
    if (giftItems.length > 0) {
      setSelectedIds((current) => current.includes(order.orderId) ? current : [...current, order.orderId]);
    }
    setGiftPickerOrder(null);
  };

  const removeGift = (orderIdValue, productVariantId) => {
    setGiftItemsByOrderId((current) => ({
      ...current,
      [orderIdValue]: (current[orderIdValue] || [])
        .filter((item) => item.productVariantId !== productVariantId),
    }));
  };

  const createDeliveries = async () => {
    if (selectedIds.length === 0) return;
    setSubmitting(true);
    try {
      const response = await stockDeliveryService.createFromOrders({
        orders: selectedIds.map((selectedOrderId) => ({
          orderId: selectedOrderId,
          giftItems: (giftItemsByOrderId[selectedOrderId] || []).map((item) => ({
            productVariantId: item.productVariantId,
            quantity: Number(item.quantity),
          })),
        })),
      });
      const data = unwrap(response) || {};
      setResults(data.results || []);
      if (data.successCount > 0) toast.success(`Đã tạo ${data.successCount} phiếu xuất kho`);
      if (data.failCount > 0) toast.error(`${data.failCount} đơn hàng chưa tạo được phiếu`);
      const successfulOrderIds = new Set((data.results || [])
        .filter((result) => result.status === 'CREATED')
        .map((result) => result.orderId));
      setSelectedIds((current) => current.filter((id) => !successfulOrderIds.has(id)));
      setGiftItemsByOrderId((current) => Object.fromEntries(
        Object.entries(current).filter(([id]) => !successfulOrderIds.has(id)),
      ));
      await loadCandidates();
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.layout}>
      <section className={styles.mainPanel}>
        <div className={styles.panelHeader}>
          <div>
            <h2>Đơn hàng chờ xuất kho</h2>
            <p>Chỉ hiển thị đơn đang xử lý và chưa có phiếu xuất hoạt động.</p>
          </div>
          <span className={styles.count}>{pageData.totalElements || 0} đơn</span>
        </div>

        <form className={styles.searchBar} onSubmit={submitSearch}>
          <Search size={16} />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Tìm mã đơn, khách hàng hoặc SKU..."
          />
          <button type="submit">Tìm kiếm</button>
        </form>

        <div className={styles.tableWrap}>
          <table>
            <thead>
              <tr>
                <th className={styles.checkCell}>
                  <input type="checkbox" checked={allSelected} onChange={toggleAll} aria-label="Chọn tất cả đơn" />
                </th>
                <th>Đơn hàng</th>
                <th>Kênh</th>
                <th>Khách hàng</th>
                <th>Sản phẩm</th>
                <th className={styles.quantityCell}>Số lượng</th>
                <th className={styles.actionCell}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={7} className={styles.state}><Loader2 className={styles.spin} /> Đang tải đơn hàng...</td></tr>
              )}
              {!loading && orders.length === 0 && (
                <tr><td colSpan={7} className={styles.state}>Không có đơn hàng phù hợp để tạo phiếu xuất.</td></tr>
              )}
              {!loading && orders.map((order) => (
                <tr key={order.orderId} className={selectedIds.includes(order.orderId) ? styles.selectedRow : ''}>
                  <td className={styles.checkCell}>
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(order.orderId)}
                      onChange={() => toggleOne(order.orderId)}
                      aria-label={`Chọn đơn ${order.externalOrderId}`}
                    />
                  </td>
                  <td>
                    <strong className={styles.orderCode}>{order.externalOrderId}</strong>
                    <span className={styles.muted}>{order.createdAt ? new Date(order.createdAt).toLocaleString('vi-VN') : '-'}</span>
                  </td>
                  <td>
                    <span className={`${styles.platform} ${styles[`platform${order.platform}`] || ''}`}>{order.platform}</span>
                    <span className={styles.muted}>{order.channelName || '-'}</span>
                  </td>
                  <td>
                    <strong>{order.buyerName || '-'}</strong>
                    <span className={styles.muted}>{order.buyerPhone || '-'}</span>
                  </td>
                  <td>
                    <div className={styles.items}>
                      {(order.items || []).slice(0, 2).map((item) => (
                        <span key={item.orderItemId}>{item.name} <b>x{item.quantity}</b></span>
                      ))}
                      {(order.items || []).length > 2 && <span>+{order.items.length - 2} sản phẩm khác</span>}
                    </div>
                    {(giftItemsByOrderId[order.orderId] || []).length > 0 && (
                      <div className={styles.giftList}>
                        {(giftItemsByOrderId[order.orderId] || []).map((giftItem) => (
                          <span key={giftItem.productVariantId} className={styles.giftItem}>
                            <Gift size={12} />
                            <b>{giftItem.sku}</b> x{giftItem.quantity}
                            <button
                              type="button"
                              onClick={() => removeGift(order.orderId, giftItem.productVariantId)}
                              aria-label={`Bỏ quà ${giftItem.sku}`}
                              title="Bỏ quà"
                            >
                              <Trash2 size={12} />
                            </button>
                          </span>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className={styles.quantityCell}>
                    <strong>{Number(order.totalQuantity || 0) + giftQuantityForOrder(order.orderId)}</strong>
                    {giftQuantityForOrder(order.orderId) > 0 && (
                      <span className={styles.quantityBreakdown}>
                        {Number(order.totalQuantity || 0)} hàng + {giftQuantityForOrder(order.orderId)} quà
                      </span>
                    )}
                  </td>
                  <td className={styles.actionCell}>
                    <div className={styles.actionButtons}>
                      <button
                        type="button"
                        className={styles.viewButton}
                        onClick={() => setDetailOrderId(order.orderId)}
                        aria-label={`Xem chi tiết đơn hàng ${order.externalOrderId}`}
                        title="Xem chi tiết đơn hàng"
                      >
                        <Eye size={16} aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        className={styles.giftButton}
                        onClick={() => setGiftPickerOrder(order)}
                      >
                        <Plus size={15} /> Thêm sản phẩm
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className={styles.pagination}>
          <span>Trang {Math.min(page + 1, totalPages)} / {totalPages}</span>
          <div>
            <button type="button" disabled={page === 0 || loading} onClick={() => setPage((value) => value - 1)} aria-label="Trang trước">
              <ChevronLeft size={16} />
            </button>
            <button type="button" disabled={page + 1 >= totalPages || loading} onClick={() => setPage((value) => value + 1)} aria-label="Trang sau">
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>

      <aside className={styles.sidebar}>
        <div className={styles.summary}>
          <PackageCheck size={20} />
          <div><span>Đã chọn</span><strong>{selectedCount} đơn</strong></div>
          <div><span>Tổng lượng xuất</span><strong>{selectedQuantity}</strong></div>
        </div>
        {overAvailableGifts.length > 0 && (
          <div className={styles.stockWarning}>
            Quà {overAvailableGifts.map((item) => item.sku).join(', ')} đang vượt tồn có thể xuất.
          </div>
        )}
        <button className={styles.createButton} type="button" disabled={selectedCount === 0 || submitting} onClick={createDeliveries}>
          {submitting ? <Loader2 className={styles.spin} size={17} /> : <PackageCheck size={17} />}
          {submitting ? 'Đang tạo phiếu...' : `Tạo phiếu cho ${selectedCount} đơn hàng`}
        </button>
        <p className={styles.note}>Mỗi đơn hàng tạo một phiếu riêng. Hàng đã được giữ trước nên thao tác này không reserve thêm.</p>

        {results.length > 0 && (
          <div className={styles.results}>
            <h3>Kết quả gần nhất</h3>
            {results.map((result) => (
              <div key={result.orderId} className={result.status === 'CREATED' ? styles.success : styles.failure}>
                {result.status === 'CREATED' ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
                <div>
                  <strong>{result.issueCode || result.orderId}</strong>
                  <span>{result.status === 'CREATED' ? 'Đã tạo phiếu xuất' : result.message}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </aside>

      {detailOrderId && (
        <OrderDetailPreviewModal
          orderId={detailOrderId}
          giftItems={giftItemsByOrderId[detailOrderId] || []}
          onClose={() => setDetailOrderId(null)}
        />
      )}
      {giftPickerOrder && (
        <OrderGiftProductPickerModal
          order={giftPickerOrder}
          value={giftItemsByOrderId[giftPickerOrder.orderId] || []}
          onConfirm={(giftItems) => saveGifts(giftPickerOrder, giftItems)}
          onClose={() => setGiftPickerOrder(null)}
        />
      )}
    </div>
  );
}
