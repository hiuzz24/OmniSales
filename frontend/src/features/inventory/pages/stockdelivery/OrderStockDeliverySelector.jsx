import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, ChevronLeft, ChevronRight, Loader2, PackageCheck, Search, XCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import stockDeliveryService from '../../services/stockDeliveryService';
import styles from './OrderStockDeliverySelector.module.css';

const PAGE_SIZE = 8;

const unwrap = (response) => response?.data ?? response;

const errorText = (error) => {
  if (typeof error === 'string') return error;
  return error?.message || error?.error || 'Không thể xử lý yêu cầu';
};

export default function OrderStockDeliverySelector() {
  const [keyword, setKeyword] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [selectedIds, setSelectedIds] = useState([]);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const loadCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const response = await stockDeliveryService.getOrderCandidates({
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
  }, [page, query]);

  useEffect(() => {
    loadCandidates();
  }, [loadCandidates]);

  const orders = pageData.content || [];
  const allSelected = orders.length > 0 && orders.every((order) => selectedIds.includes(order.orderId));
  const selectedCount = selectedIds.length;
  const totalPages = Math.max(1, pageData.totalPages || 1);

  const selectedQuantity = useMemo(
    () => orders.filter((order) => selectedIds.includes(order.orderId))
      .reduce((sum, order) => sum + (order.totalQuantity || 0), 0),
    [orders, selectedIds],
  );

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

  const createDeliveries = async () => {
    if (selectedIds.length === 0) return;
    setSubmitting(true);
    try {
      const response = await stockDeliveryService.createFromOrders(selectedIds);
      const data = unwrap(response) || {};
      setResults(data.results || []);
      if (data.successCount > 0) toast.success(`Đã tạo ${data.successCount} phiếu xuất kho`);
      if (data.failCount > 0) toast.error(`${data.failCount} đơn hàng chưa tạo được phiếu`);
      setSelectedIds([]);
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
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr><td colSpan={6} className={styles.state}><Loader2 className={styles.spin} /> Đang tải đơn hàng...</td></tr>
              )}
              {!loading && orders.length === 0 && (
                <tr><td colSpan={6} className={styles.state}>Không có đơn hàng phù hợp để tạo phiếu xuất.</td></tr>
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
                  </td>
                  <td className={styles.quantityCell}>{order.totalQuantity || 0}</td>
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
          <div><span>Tổng số lượng</span><strong>{selectedQuantity}</strong></div>
        </div>
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
    </div>
  );
}
