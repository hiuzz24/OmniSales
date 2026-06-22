import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search,
  Download,
  Upload,
  ClipboardList,
  Eye,
  AlertTriangle,
  AlertCircle,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Loader2,
  ArrowUpAZ,
  ArrowDownAZ,
  ArrowUp01,
  ArrowDown10,
  Layers,
  Box,
} from 'lucide-react';
import styles from './InventoryPage.module.css';
import { ROUTES } from '../../../../app/router/routes';
import categoryApi from '../../../../api/categoryApi';
import inventoryService from '../../services/inventoryService';

// ─── Constants ────────────────────────────────────────────────────────────────
const PAGE_SIZE = 10;

const STATUS_OPTIONS = [
  { value: 'all', label: 'Tất cả trạng thái' },
  { value: 'in-stock', label: 'Đủ hàng' },
  { value: 'low-stock', label: 'Sắp hết' },
  { value: 'out-of-stock', label: 'Hết hàng' },
  { value: 'negative', label: 'Tồn âm' },
];

// Cycle: none → asc → desc → none
const nextSort = (current) =>
  current === 'none' ? 'asc' : current === 'asc' ? 'desc' : 'none';

// ─── Helpers ──────────────────────────────────────────────────────────────────
/**
 * Derive a status string from BE fields for display purposes.
 * BE trả về: quantityOnHand, availableQuantity, lowStockThreshold, isLowStock
 */
const deriveStatus = (item) => {
  if (item.availableQuantity < 0) return 'negative';
  if (item.availableQuantity === 0) return 'out-of-stock';
  if (item.isLowStock) return 'low-stock';
  return 'in-stock';
};

// ─── Sub-components ───────────────────────────────────────────────────────────
const StatusBadge = ({ status }) => {
  const map = {
    'in-stock': { label: 'Đủ hàng', cls: styles.badgeGreen },
    'low-stock': { label: 'Sắp hết', cls: styles.badgeOrange },
    'out-of-stock': { label: 'Hết hàng', cls: styles.badgeGray },
    'negative': { label: 'Tồn âm', cls: styles.badgeRed },
  };
  const { label, cls } = map[status] ?? { label: status, cls: '' };
  return <span className={`${styles.badge} ${cls}`}>{label}</span>;
};

const Select = ({ value, onChange, options }) => (
  <div className={styles.selectWrapper}>
    <select className={styles.select} value={value} onChange={e => onChange(e.target.value)}>
      {options.map(o => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
    <ChevronDown className={styles.selectIcon} size={14} />
  </div>
);

// ─── Pagination Component ─────────────────────────────────────────────────────
const Pagination = ({ currentPage, totalPages, totalElements, pageSize, onPageChange }) => {
  if (totalPages <= 1) return null;

  // currentPage is 0-indexed from BE; display as 1-indexed
  const displayPage = currentPage + 1;
  const startItem = currentPage * pageSize + 1;
  const endItem = Math.min((currentPage + 1) * pageSize, totalElements);

  const getPages = () => {
    const pages = [];
    if (totalPages <= 7) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
    } else {
      pages.push(1);
      if (displayPage > 3) pages.push('...');
      for (
        let i = Math.max(2, displayPage - 1);
        i <= Math.min(totalPages - 1, displayPage + 1);
        i++
      ) pages.push(i);
      if (displayPage < totalPages - 2) pages.push('...');
      pages.push(totalPages);
    }
    return pages;
  };

  return (
    <div className={styles.pagination}>
      <span className={styles.paginationInfo}>
        Hiển thị {startItem}–{endItem} / {totalElements} mục
      </span>
      <div className={styles.paginationControls}>
        <button
          id="btn-page-prev"
          className={`${styles.pageBtn} ${currentPage === 0 ? styles.pageBtnDisabled : ''}`}
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 0}
          aria-label="Trang trước"
        >
          <ChevronLeft size={15} />
        </button>

        {getPages().map((p, i) =>
          p === '...' ? (
            <span key={`ellipsis-${i}`} className={styles.pageEllipsis}>…</span>
          ) : (
            <button
              key={p}
              id={`btn-page-${p}`}
              className={`${styles.pageBtn} ${p === displayPage ? styles.pageBtnActive : ''}`}
              onClick={() => onPageChange(p - 1)} // convert back to 0-indexed
            >
              {p}
            </button>
          )
        )}

        <button
          id="btn-page-next"
          className={`${styles.pageBtn} ${currentPage === totalPages - 1 ? styles.pageBtnDisabled : ''}`}
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages - 1}
          aria-label="Trang sau"
        >
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  );
};

// ─── Main Page ────────────────────────────────────────────────────────────────
const InventoryPage = () => {
  const navigate = useNavigate();
  // ── Filter state (client-side, applied on current page)
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [warehouseFilter, setWarehouseFilter] = useState('all');
  const [categoryFilter, setCategoryFilter] = useState('all');

  // 'none' | 'asc' | 'desc'
  const [nameSort, setNameSort] = useState('none');
  const [qtySort, setQtySort] = useState('none');

  // ── Pagination state (0-indexed, driven by BE)
  const [currentPage, setCurrentPage] = useState(0);

  // ── Data state
  const [items, setItems] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [categoryTree, setCategoryTree] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    categoryApi.getTree()
      .then(res => setCategoryTree(res || []))
      .catch(err => console.error('Lỗi tải danh mục:', err));
  }, []);

  // ── Fetch from BE whenever page changes
  const fetchInventory = useCallback(async (page, categoryId) => {
    setLoading(true);
    setError(null);
    try {
      const catIdParam = categoryId === 'all' ? null : categoryId;
      const data = await inventoryService.getInventoryList(page, PAGE_SIZE, 'updatedAt', 'desc', catIdParam);
      // BE PageResponse shape: { content, page, size, totalElements, totalPages, first, last }
      setItems(data.content ?? []);
      setTotalElements(data.totalElements ?? 0);
      setTotalPages(data.totalPages ?? 0);
    } catch (err) {
      console.error('Lỗi tải tồn kho:', err);
      setError('Không thể tải dữ liệu tồn kho. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchInventory(currentPage, categoryFilter);
  }, [currentPage, categoryFilter, fetchInventory]);

  // ── Dynamic warehouse options from loaded data
  const warehouseOptions = useMemo(() => {
    const names = [...new Set(items.map(i => i.warehouseName).filter(Boolean))];
    return [
      { value: 'all', label: 'Tất cả kho hàng' },
      ...names.map(n => ({ value: n, label: n })),
    ];
  }, [items]);

  const handlePageChange = (page) => {
    setCurrentPage(page);
    // reset client-side filters khi đổi trang
    setSearch('');
    setStatusFilter('all');
    setWarehouseFilter('all');
    setNameSort('none');
    setQtySort('none');
  };

  const renderCategoryOptions = (nodes, level = 0) => {
    return nodes.flatMap(node => {
      const indent = '\u00A0\u00A0\u00A0\u00A0'.repeat(level);
      const prefix = level > 0 ? '↳ ' : '';
      return [
        <option key={node.id} value={node.id}>
          {indent}{prefix}{node.name}
        </option>,
        ...renderCategoryOptions(node.children || [], level + 1)
      ];
    });
  };

  // ── Client-side filter + sort on current page items
  const filtered = useMemo(() => {
    let result = items.filter(item => {
      const matchSearch = !search ||
        (item.variantSku ?? '').toLowerCase().includes(search.toLowerCase()) ||
        (item.variantName ?? '').toLowerCase().includes(search.toLowerCase());
      const status = deriveStatus(item);
      const matchStatus = statusFilter === 'all' || status === statusFilter;
      const matchWarehouse = warehouseFilter === 'all' || item.warehouseName === warehouseFilter;
      return matchSearch && matchStatus && matchWarehouse;
    });

    // Sort by name (takes priority over qty sort if both active)
    if (nameSort !== 'none') {
      result = [...result].sort((a, b) => {
        const cmp = (a.variantName ?? '').localeCompare(b.variantName ?? '', 'vi');
        return nameSort === 'asc' ? cmp : -cmp;
      });
    } else if (qtySort !== 'none') {
      result = [...result].sort((a, b) =>
        qtySort === 'asc'
          ? a.quantityOnHand - b.quantityOnHand
          : b.quantityOnHand - a.quantityOnHand
      );
    }

    return result;
  }, [items, search, statusFilter, warehouseFilter, nameSort, qtySort]);

  // ── Derived alert counts from current page
  const negativeCount = items.filter(r => r.availableQuantity < 0).length;
  const lowStockCount = items.filter(r => r.isLowStock && r.availableQuantity >= 0).length;
  const totalQuantityCurrentPage = items.reduce((acc, item) => acc + item.quantityOnHand, 0);

  const quantityColor = (v) => {
    if (v < 0) return styles.cellRed;
    if (v === 0) return styles.cellOrange;
    return styles.cellDefault;
  };

  return (
    <div className={styles.page}>
      {/* ── Header ── */}
      <div className={styles.pageHeader}>
        <div className={styles.pageTitleBlock}>
          <h1 className={styles.pageTitle}>Inventory List</h1>
          <p className={styles.pageSubtitle}>
            Xem và quản lý tồn kho theo SKU, kho hàng và trạng thái
          </p>
        </div>
        <div className={styles.headerActions}>
          <button className={styles.btnOutline} id="btn-inventory-logs">
            <ClipboardList size={15} />
            Inventory Logs
          </button>
          <button className={styles.btnOutline} id="btn-export-report">
            <Download size={15} />
            Xuất báo cáo
          </button>
          <button className={styles.btnOutline} id="btn-export-stock">
            <Upload size={15} />
            Xuất kho
          </button>
          <button className={styles.btnPrimary} id="btn-import-stock">
            <Download size={15} />
            Nhập kho
          </button>
        </div>
      </div>

      {/* ── Summary Cards ── */}
      <div className={styles.summaryGrid}>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#e0f2fe', color: '#0284c7' }}>
            <Layers size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Total SKUs</span>
            <span className={styles.summaryValue}>{totalElements}</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#f3e8ff', color: '#9333ea' }}>
            <Box size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Total Quantity<br /><small>(Trang hiện tại)</small></span>
            <span className={styles.summaryValue}>{totalQuantityCurrentPage}</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#fef3c7', color: '#d97706' }}>
            <AlertTriangle size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Low Stock Items</span>
            <span className={styles.summaryValue}>{lowStockCount}</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#fee2e2', color: '#dc2626' }}>
            <AlertCircle size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={`${styles.summaryLabel} ${styles.textRed}`}>Negative Stock Items</span>
            <span className={`${styles.summaryValue} ${styles.textRed}`}>{negativeCount}</span>
          </div>
        </div>
      </div>

      {/* ── Alerts ── */}
      {!loading && negativeCount > 0 && (
        <div className={`${styles.alert} ${styles.alertError}`}>
          <AlertCircle size={16} className={styles.alertIcon} />
          <div>
            <span className={styles.alertBold}>Cảnh báo: </span>
            Có {negativeCount} SKU đang tồn kho âm trên trang này. Cần kiểm tra và điều chỉnh ngay.
          </div>
        </div>
      )}

      {!loading && lowStockCount > 0 && (
        <div className={`${styles.alert} ${styles.alertWarn}`}>
          <AlertTriangle size={16} className={styles.alertIcon} />
          <div>
            Có {lowStockCount} SKU dưới mức tồn tối thiểu trên trang này. Cần nhập hàng.
          </div>
        </div>
      )}

      {/* ── Error ── */}
      {error && (
        <div className={`${styles.alert} ${styles.alertError}`}>
          <AlertCircle size={16} className={styles.alertIcon} />
          <div>{error}</div>
        </div>
      )}

      {/* ── Filters row 1: Search + Warehouse + Status + Category ── */}
      <div className={styles.filtersRow}>
        <div className={styles.searchWrapper}>
          <Search className={styles.searchIcon} size={16} />
          <input
            id="input-inventory-search"
            className={styles.searchInput}
            placeholder="Tìm theo SKU hoặc tên sản phẩm..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
        <div className={styles.selectWrapper}>
          <select
            className={styles.select}
            value={categoryFilter}
            onChange={e => {
              setCategoryFilter(e.target.value);
              setCurrentPage(0);
            }}
          >
            <option value="all">Tất cả danh mục</option>
            {renderCategoryOptions(categoryTree)}
          </select>
          <ChevronDown className={styles.selectIcon} size={14} />
        </div>
        <Select value={warehouseFilter} onChange={setWarehouseFilter} options={warehouseOptions} />
        <Select value={statusFilter} onChange={setStatusFilter} options={STATUS_OPTIONS} />
      </div>

      {/* ── Filters row 2: Sort toggles ── */}
      <div className={styles.filtersRow2}>
        <span className={styles.sortLabel}>Sắp xếp:</span>

        {/* Sort by name */}
        <button
          id="btn-sort-name"
          className={`${styles.sortBtn} ${nameSort !== 'none' ? styles.sortBtnActive : ''}`}
          onClick={() => { setNameSort(nextSort(nameSort)); setQtySort('none'); }}
          title="Sắp xếp theo tên sản phẩm"
        >
          {nameSort === 'desc'
            ? <ArrowDownAZ size={14} />
            : <ArrowUpAZ size={14} />}
          Tên A–Z
          {nameSort !== 'none' && (
            <span className={styles.sortDirChip}>
              {nameSort === 'asc' ? '↑' : '↓'}
            </span>
          )}
        </button>

        {/* Sort by quantity */}
        <button
          id="btn-sort-qty"
          className={`${styles.sortBtn} ${qtySort !== 'none' ? styles.sortBtnActive : ''}`}
          onClick={() => { setQtySort(nextSort(qtySort)); setNameSort('none'); }}
          title="Sắp xếp theo số lượng trong kho"
        >
          {qtySort === 'desc'
            ? <ArrowDown10 size={14} />
            : <ArrowUp01 size={14} />}
          Số lượng
          {qtySort !== 'none' && (
            <span className={styles.sortDirChip}>
              {qtySort === 'asc' ? '↑' : '↓'}
            </span>
          )}
        </button>

        {/* Reset filters */}
        {(categoryFilter !== 'all' || warehouseFilter !== 'all' || statusFilter !== 'all' || nameSort !== 'none' || qtySort !== 'none' || search) && (
          <button
            id="btn-reset-filters"
            className={styles.resetBtn}
            onClick={() => {
              setSearch('');
              setStatusFilter('all');
              setWarehouseFilter('all');
              setCategoryFilter('all');
              setNameSort('none');
              setQtySort('none');
              setCurrentPage(0);
            }}
          >
            Xoá bộ lọc
          </button>
        )}
      </div>

      {/* ── Table Card ── */}
      <div className={styles.tableCard}>
        <div className={styles.tableCardHeader}>
          <div>
            <h2 className={styles.tableTitle}>
              Danh sách tồn kho ({totalElements})
            </h2>
            <p className={styles.tableSubtitle}>
              Hiển thị tồn kho theo SKU/variant/warehouse trong tenant của bạn
            </p>
          </div>
        </div>

        <div className={styles.tableWrapper}>
          {loading ? (
            <div className={styles.loadingOverlay}>
              <Loader2 size={28} className={styles.spinnerIcon} />
              <span>Đang tải dữ liệu...</span>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.th}>SKU</th>
                  <th
                    className={`${styles.th} ${styles.thSortable}`}
                    onClick={() => { setNameSort(nextSort(nameSort)); setQtySort('none'); }}
                    title="Click để sắp xếp theo tên"
                  >
                    Tên sản phẩm
                    {nameSort === 'asc' && <ArrowUpAZ size={12} style={{ marginLeft: 4 }} />}
                    {nameSort === 'desc' && <ArrowDownAZ size={12} style={{ marginLeft: 4 }} />}
                    {nameSort === 'none' && <ArrowUpAZ size={12} style={{ marginLeft: 4, opacity: 0.3 }} />}
                  </th>
                  <th className={styles.th}>Kho hàng</th>
                  <th
                    className={`${styles.th} ${styles.thRight} ${styles.thSortable}`}
                    onClick={() => { setQtySort(nextSort(qtySort)); setNameSort('none'); }}
                    title="Click để sắp xếp theo số lượng"
                  >
                    Trong kho
                    {qtySort === 'asc' && <ArrowUp01 size={12} style={{ marginLeft: 4 }} />}
                    {qtySort === 'desc' && <ArrowDown10 size={12} style={{ marginLeft: 4 }} />}
                    {qtySort === 'none' && <ArrowUp01 size={12} style={{ marginLeft: 4, opacity: 0.3 }} />}
                  </th>
                  <th className={`${styles.th} ${styles.thRight}`}>Giữ hàng</th>
                  <th className={`${styles.th} ${styles.thRight}`}>Có thể bán</th>
                  <th className={`${styles.th} ${styles.thRight}`}>Tồn tối thiểu</th>
                  <th className={styles.th}>Trạng thái</th>
                  <th className={styles.th}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((row, idx) => {
                  const status = deriveStatus(row);
                  return (
                    <tr
                      key={row.id}
                      className={`${styles.tr} ${idx % 2 === 1 ? styles.trAlt : ''}`}
                    >
                      <td className={styles.td}>
                        <span className={styles.skuChip}>{row.variantSku}</span>
                      </td>
                      <td className={styles.td}>{row.variantName}</td>
                      <td className={styles.td}>
                        <span className={styles.warehouseLink}>{row.warehouseName}</span>
                      </td>
                      <td className={`${styles.td} ${styles.tdRight} ${quantityColor(row.quantityOnHand)}`}>
                        {row.quantityOnHand}
                      </td>
                      <td className={`${styles.td} ${styles.tdRight} ${styles.cellMuted}`}>
                        {row.reservedQuantity}
                      </td>
                      <td className={`${styles.td} ${styles.tdRight} ${quantityColor(row.availableQuantity)}`}>
                        {row.availableQuantity < 0 ? (
                          <span className={styles.negativeCell}>{row.availableQuantity}</span>
                        ) : row.availableQuantity === 0 ? (
                          <span className={styles.zeroCell}>{row.availableQuantity}</span>
                        ) : (
                          row.availableQuantity
                        )}
                      </td>
                      <td className={`${styles.td} ${styles.tdRight} ${styles.cellMuted}`}>
                        {row.lowStockThreshold}
                      </td>
                      <td className={styles.td}>
                        <div className={styles.statusCell}>
                          <StatusBadge status={status} />
                        </div>
                      </td>
                      <td className={styles.td}>
                        <button
                          id={`btn-view-${row.variantSku}`}
                          className={styles.viewBtn}
                          onClick={() => navigate(`${ROUTES.INVENTORY_DETAIL.replace(':id', row.id)}?variantId=${row.variantId}`)}
                        >
                          <Eye size={14} />
                          View
                        </button>
                      </td>
                    </tr>
                  );
                })}

                {!loading && filtered.length === 0 && (
                  <tr>
                    <td colSpan={9} className={styles.emptyRow}>
                      {items.length === 0
                        ? 'Không có dữ liệu tồn kho.'
                        : 'Không tìm thấy kết quả phù hợp với bộ lọc.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {/* ── BE Pagination ── */}
        {!loading && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={PAGE_SIZE}
            onPageChange={handlePageChange}
          />
        )}
      </div>
    </div>
  );
};

export default InventoryPage;
