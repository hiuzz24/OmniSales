import { useEffect, useRef, useState } from 'react';
import {
  X, Download, CheckSquare, Square, Columns3, ChevronDown,
} from 'lucide-react';
import orderApi from '../../../api/orderApi';
import Pagination from '../../../shared/components/Pagination';
import { exportOrdersToExcel, ORDER_EXPORT_COLUMNS } from '../utils/exportOrders';
import styles from './ExportOrdersModal.module.css';

const PAGE_SIZE = 20;

const getDefaultColumnKeys = () =>
  ORDER_EXPORT_COLUMNS.filter((c) => c.defaultChecked).map((c) => c.key);

const ExportOrdersModal = ({ isOpen, onClose, currentFilters = {} }) => {
  const [orders, setOrders] = useState([]);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [selectedColumnKeys, setSelectedColumnKeys] = useState(getDefaultColumnKeys);
  const [isColumnMenuOpen, setIsColumnMenuOpen] = useState(false);
  const columnMenuRef = useRef(null);

  useEffect(() => {
    if (!isOpen) {
      setOrders([]);
      setSelectedIds(new Set());
      setPage(0);
      setTotalElements(0);
      setSelectedColumnKeys(getDefaultColumnKeys());
      return;
    }
    fetchPage(0);
  }, [isOpen]);

  const fetchPage = async (pageNum) => {
    setLoading(true);
    try {
      const params = {
        page: pageNum,
        size: PAGE_SIZE,
        ...currentFilters,
      };
      const data = await orderApi.getAll(params);
      setOrders(data.content || []);
      setTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to fetch orders:', err);
    } finally {
      setLoading(false);
    }
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === orders.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(orders.map((o) => o.id)));
    }
  };

  const toggleSelect = (id) => {
    const next = new Set(selectedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setSelectedIds(next);
  };

  const handleSelectAllMatching = async () => {
    setLoading(true);
    try {
      const ids = new Set();
      const totalPages = Math.ceil(totalElements / PAGE_SIZE) || 1;
      for (let p = 0; p < totalPages; p += 1) {
        const params = { page: p, size: PAGE_SIZE, ...currentFilters };
        const data = await orderApi.getAll(params);
        (data.content || []).forEach((o) => ids.add(o.id));
      }
      setSelectedIds(ids);
    } catch (err) {
      console.error('Failed to select all:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    if (selectedIds.size === 0) return;
    setExporting(true);
    try {
      const idArray = Array.from(selectedIds);
      const detailPromises = idArray.map((id) => orderApi.getById(id).then((res) => res));
      const results = await Promise.all(detailPromises);
      const valid = results.filter(Boolean);
      exportOrdersToExcel(valid, selectedColumnKeys, 'danh-sach-don-hang');
      onClose();
    } catch (err) {
      console.error('Export failed:', err);
    } finally {
      setExporting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitle}>Xuất danh sách đơn hàng</h2>
          <button className={styles.closeBtn} onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className={styles.modalBody}>
          {/* Column picker */}
          {/* Column picker */}
          <div className={styles.columnPickerWrap}>
            <div className={styles.columnPickerLabel}>
              <Columns3 size={14} />
              Chọn cột xuất Excel
            </div>
            <div className={styles.columnPickerDropdown}>
              <button
                className={styles.columnPickerTrigger}
                onClick={() => setIsColumnMenuOpen((v) => !v)}
              >
                {selectedColumnKeys.length} / {ORDER_EXPORT_COLUMNS.length} cột đã chọn
                <ChevronDown size={14} />
              </button>
              {isColumnMenuOpen && (
                <div className={styles.columnMenu} ref={columnMenuRef}>
                  <div className={styles.columnMenuHeader}>
                    <button
                      className={styles.columnMenuAction}
                      onClick={() => setSelectedColumnKeys(ORDER_EXPORT_COLUMNS.map((c) => c.key))}
                    >
                      Chọn tất cả
                    </button>
                    <span className={styles.columnMenuDivider}></span>
                    <button
                      className={styles.columnMenuAction}
                      onClick={() => setSelectedColumnKeys([])}
                    >
                      Bỏ chọn tất cả
                    </button>
                  </div>
                  {ORDER_EXPORT_COLUMNS.map((col) => (
                    <label key={col.key} className={styles.columnMenuItem}>
                      <input
                        type="checkbox"
                        checked={selectedColumnKeys.includes(col.key)}
                        onChange={() => {
                          setSelectedColumnKeys((prev) =>
                            prev.includes(col.key)
                              ? prev.filter((k) => k !== col.key)
                              : [...prev, col.key]
                          );
                        }}
                      />
                      {col.label}
                    </label>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Selection info */}
          <div className={styles.selectionInfo}>
            <span>
              {selectedIds.size > 0
                ? `Đã chọn ${selectedIds.size} đơn hàng`
                : `Chưa chọn đơn hàng nào`}
            </span>
            <div className={styles.selectionActions}>
              <button className={styles.selectAllBtn} onClick={toggleSelectAll}>
                {selectedIds.size === orders.length ? <Square size={14} /> : <CheckSquare size={14} />}
                Chọn tất cả trang
              </button>
              {totalElements > PAGE_SIZE && (
                <button className={styles.selectAllBtn} onClick={handleSelectAllMatching}>
                  <CheckSquare size={14} />
                  Chọn tất cả ({totalElements})
                </button>
              )}
            </div>
          </div>

          {/* Order list */}
          <div className={styles.orderList}>
            <div className={styles.listHeader}>
              <div style={{ width: 32, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <input type="checkbox"
                  checked={selectedIds.size === orders.length && orders.length > 0}
                  onChange={toggleSelectAll}
                />
              </div>
              {ORDER_EXPORT_COLUMNS.filter((c) => selectedColumnKeys.includes(c.key)).map((col) => (
                <span key={col.key} style={{ flex: col.key === 'items' ? 2 : 1 }}>
                  {col.label}
                </span>
              ))}
            </div>
            {loading && orders.length === 0 ? (
              <div className={styles.loadingState}>Đang tải...</div>
            ) : orders.length === 0 ? (
              <div className={styles.emptyState}>Không có đơn hàng nào</div>
            ) : (
              orders.map((order) => (
                <div key={order.id} className={styles.orderRow}>
                  <div style={{ width: 32, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <input
                      type="checkbox"
                      checked={selectedIds.has(order.id)}
                      onChange={() => toggleSelect(order.id)}
                    />
                  </div>
                  {ORDER_EXPORT_COLUMNS.filter((c) => selectedColumnKeys.includes(c.key)).map((col) => (
                    <span
                      key={col.key}
                      style={{
                        flex: col.key === 'items' ? 2 : 1,
                        fontSize: 13,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {col.key === 'totalAmount'
                        ? (order.totalAmount != null
                          ? new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(order.totalAmount)
                          : '-')
                        : col.getValue(order) || '-'}
                    </span>
                  ))}
                </div>
              ))
            )}
          </div>

          {/* Pagination */}
          {totalElements > PAGE_SIZE && (
            <Pagination
              currentPage={page}
              totalPages={Math.ceil(totalElements / PAGE_SIZE)}
              totalElements={totalElements}
              pageSize={PAGE_SIZE}
              currentCount={orders.length}
              itemLabel="đơn hàng"
              onPageChange={(nextPage) => {
                setPage(nextPage);
                fetchPage(nextPage);
              }}
            />
          )}
          {totalElements < 0 && (
            <div className={styles.pagination}>
              <button className={styles.pageBtn} disabled={page === 0} onClick={() => { setPage(0); fetchPage(0); }}>«</button>
              <button className={styles.pageBtn} disabled={page === 0} onClick={() => { const p = page - 1; setPage(p); fetchPage(p); }}>‹</button>
              <span className={styles.pageInfo}>Trang {page + 1} / {Math.ceil(totalElements / PAGE_SIZE)}</span>
              <button
                className={styles.pageBtn}
                disabled={page >= Math.ceil(totalElements / PAGE_SIZE) - 1}
                onClick={() => { const p = page + 1; setPage(p); fetchPage(p); }}
              >›</button>
              <button
                className={styles.pageBtn}
                disabled={page >= Math.ceil(totalElements / PAGE_SIZE) - 1}
                onClick={() => { const p = Math.ceil(totalElements / PAGE_SIZE) - 1; setPage(p); fetchPage(p); }}
              >»</button>
            </div>
          )}
        </div>

        <div className={styles.modalFooter}>
          <span className={styles.footerInfo}>
            {selectedIds.size} đơn hàng sẽ được xuất
          </span>
          <div className={styles.footerActions}>
            <button className={styles.cancelBtn} onClick={onClose} disabled={exporting}>Hủy</button>
            <button
              className={styles.exportBtn}
              onClick={handleExport}
              disabled={selectedIds.size === 0 || exporting}
            >
              <Download size={15} />
              {exporting ? 'Đang xuất...' : 'Xuất Excel'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ExportOrdersModal;
