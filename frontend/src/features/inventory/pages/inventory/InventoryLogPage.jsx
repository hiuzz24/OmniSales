import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  SlidersHorizontal,
  Clock,
  Search,
  Warehouse,
  User,
  Calendar,
  ArrowRightLeft,
  TrendingUp,
  TrendingDown,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Sliders,
  RotateCcw
} from 'lucide-react';
import { toast } from 'react-toastify';
import styles from './InventoryLogPage.module.css';
import { ROUTES } from '../../../../app/router/routes';
import inventoryService from '../../services/inventoryService';
import warehouseService from '../../services/warehouseService';
import userApi from '../../../../api/userApi';
import { getResponseData, formatDateTime } from '../components/inventoryDocumentListUtils';

// Constants for Page Size
const PAGE_SIZE = 15;

// InvTxnType labels and badges mappings
const TXN_TYPE_CONFIG = {
  IMPORT: { label: 'Nhập kho', color: '#16a34a', bg: '#dcfce7', icon: TrendingUp },
  INBOUND: { label: 'Nhập kho', color: '#16a34a', bg: '#dcfce7', icon: TrendingUp },
  EXPORT: { label: 'Xuất kho', color: '#2563eb', bg: '#eff6ff', icon: TrendingDown },
  OUTBOUND: { label: 'Xuất kho', color: '#2563eb', bg: '#eff6ff', icon: TrendingDown },
  ADJUSTMENT: { label: 'Điều chỉnh', color: '#d97706', bg: '#fff7ed', icon: Sliders },
  TRANSFER_OUT: { label: 'Chuyển đi', color: '#7c3aed', bg: '#f5f3ff', icon: ArrowRightLeft },
  TRANSFER_IN: { label: 'Chuyển đến', color: '#7c3aed', bg: '#f5f3ff', icon: ArrowRightLeft },
  ORDER_DEDUCT: { label: 'Xuất kho', color: '#2563eb', bg: '#eff6ff', icon: TrendingDown },
  ORDER_CANCEL: { label: 'Nhập kho', color: '#16a34a', bg: '#dcfce7', icon: TrendingUp },
};

export default function InventoryLogPage() {
  const navigate = useNavigate();

  // State
  const [logs, setLogs] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);

  // Filters State
  const [sku, setSku] = useState('');
  const [productName, setProductName] = useState('');
  const [warehouseId, setWarehouseId] = useState('all');
  const [performedById, setPerformedById] = useState('all');
  const [type, setType] = useState('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  // Pagination State
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Fetch initial option lists (warehouses and users)
  useEffect(() => {
    const fetchOptions = async () => {
      try {
        // Fetch warehouses
        const whResponse = await warehouseService.getAll();
        const whData = getResponseData(whResponse);
        setWarehouses(Array.isArray(whData) ? whData : whData.content ?? []);

        // Fetch users
        const userRes = await userApi.getAllUsers(0, 100);
        const userData = userRes.content ?? [];
        setUsers(userData);
      } catch (error) {
        console.error('Lỗi khi tải danh sách bộ lọc:', error);
      }
    };
    fetchOptions();
  }, []);

  // Fetch Inventory Logs based on active filters and page
  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size: PAGE_SIZE,
      };

      // Map local filter variables to API query parameters
      const searchStr = sku.trim() || productName.trim();
      if (searchStr) {
        params.productSearch = searchStr;
      }
      if (warehouseId && warehouseId !== 'all') {
        params.warehouseId = warehouseId;
      }
      if (performedById && performedById !== 'all') {
        params.performedById = performedById;
      }
      if (type && type !== 'all') {
        params.type = type;
      }

      // Handle ISO timestamp for from/to dates
      if (fromDate) {
        params.startDate = new Date(fromDate).toISOString(); // e.g. 2026-06-24T00:00:00.000Z
      }
      if (toDate) {
        const end = new Date(toDate);
        end.setHours(23, 59, 59, 999);
        params.endDate = end.toISOString();
      }

      const data = await inventoryService.getInventoryLogs(params);
      setLogs(data?.content ?? []);
      setTotalPages(data?.totalPages ?? 1);
      setTotalElements(data?.totalElements ?? 0);
    } catch (error) {
      toast.error('Không thể tải lịch sử thay đổi kho.');
      setLogs([]);
      setTotalPages(1);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  }, [sku, productName, warehouseId, performedById, type, fromDate, toDate, page]);

  // Trigger logs fetch on filter changes or page changes
  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  // Reset all filters to default
  const handleResetFilters = () => {
    setSku('');
    setProductName('');
    setWarehouseId('all');
    setPerformedById('all');
    setType('all');
    setFromDate('');
    setToDate('');
    setPage(0);
  };

  // Debounced input change triggers page reset
  const handleFilterChange = () => {
    setPage(0);
  };

  // Helper: Format reason/note and reference type/code
  const renderReason = (log) => {
    // Generate description based on backend type if note is null
    let desc = log.note;
    if (!desc) {
      switch (log.type) {
        case 'IMPORT':
        case 'INBOUND':
          desc = 'Nhập hàng từ nhà cung cấp';
          break;
        case 'EXPORT':
        case 'OUTBOUND':
          desc = 'Xuất kho';
          break;
        case 'ADJUSTMENT':
          desc = 'Điều chỉnh do kiểm kê';
          break;
        case 'TRANSFER_IN':
          desc = 'Nhận chuyển kho';
          break;
        case 'TRANSFER_OUT':
          desc = 'Chuyển kho';
          break;
        case 'ORDER_DEDUCT':
          desc = `Xuất kho cho đơn hàng`;
          break;
        case 'ORDER_CANCEL':
          desc = 'Hoàn trả đơn hàng';
          break;
        default:
          desc = 'Biến động kho hàng';
      }
    }

    // Generate readable code pattern (e.g. TRF-2026-008 or IMP-A21F53B2)
    let code = 'TXN';
    const refId = log.referenceId ? log.referenceId.toString().slice(0, 8).toUpperCase() : '';
    switch (log.referenceType) {
      case 'TRANSFER':
        code = `TRF-${refId}`;
        break;
      case 'RECEIPT':
        code = `IMP-${refId}`;
        break;
      case 'DELIVERY':
        code = `EXP-${refId}`;
        break;
      case 'STOCKTAKE':
        code = `ADJ-${refId}`;
        break;
      case 'ORDER':
        code = `ORD-${refId}`;
        break;
      default:
        code = log.referenceType ? `${log.referenceType.toUpperCase().slice(0, 3)}-${refId}` : `REF-${refId}`;
    }

    return (
      <div className={styles.reasonCell}>
        <div className={styles.reasonText}>{desc}</div>
        <div className={styles.refCode}>{code}</div>
      </div>
    );
  };

  // Helper: Type badges
  const renderTypeBadge = (logType) => {
    const config = TXN_TYPE_CONFIG[logType] ?? {
      label: logType,
      color: '#64748b',
      bg: '#f1f5f9',
      icon: Clock,
    };
    const Icon = config.icon;

    return (
      <span
        className={styles.typeBadge}
        style={{ color: config.color, backgroundColor: config.bg, border: `1px solid ${config.color}25` }}
      >
        <Icon size={13} className={styles.badgeIcon} />
        {config.label}
      </span>
    );
  };

  // Page index helpers
  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  };

  const startItem = page * PAGE_SIZE + 1;
  const endItem = Math.min((page + 1) * PAGE_SIZE, totalElements);

  return (
    <div className={styles.pageContainer}>
      {/* Filters Card */}
      <div className={styles.filterCard}>
        <div className={styles.cardHeader}>
          <div className={styles.headerTitle}>
            <SlidersHorizontal size={18} className={styles.headerIcon} />
            <span>Bộ lọc</span>
          </div>
          <button onClick={handleResetFilters} className={styles.resetBtn} title="Thiết lập lại bộ lọc">
            <RotateCcw size={14} />
            Làm mới
          </button>
        </div>

        <div className={styles.filtersGrid}>
          {/* SKU */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>SKU</label>
            <div className={styles.inputWrapper}>
              <Search size={15} className={styles.inputIcon} />
              <input
                type="text"
                className={styles.input}
                placeholder="Tìm theo SKU..."
                value={sku}
                onChange={(e) => {
                  setSku(e.target.value);
                  handleFilterChange();
                }}
              />
            </div>
          </div>

          {/* Product Name */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Tên sản phẩm</label>
            <div className={styles.inputWrapper}>
              <Search size={15} className={styles.inputIcon} />
              <input
                type="text"
                className={styles.input}
                placeholder="Tìm theo tên sản phẩm..."
                value={productName}
                onChange={(e) => {
                  setProductName(e.target.value);
                  handleFilterChange();
                }}
              />
            </div>
          </div>

          {/* Warehouse */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Kho hàng</label>
            <div className={styles.selectWrapper}>
              <select
                className={styles.select}
                value={warehouseId}
                onChange={(e) => {
                  setWarehouseId(e.target.value);
                  handleFilterChange();
                }}
              >
                <option value="all">Tất cả kho</option>
                {warehouses.map((wh) => (
                  <option key={wh.id} value={wh.id}>
                    {wh.name}
                  </option>
                ))}
              </select>
              <ChevronDown size={14} className={styles.selectArrow} />
            </div>
          </div>

          {/* Performer */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Người thực hiện</label>
            <div className={styles.selectWrapper}>
              <select
                className={styles.select}
                value={performedById}
                onChange={(e) => {
                  setPerformedById(e.target.value);
                  handleFilterChange();
                }}
              >
                <option value="all">Tất cả người dùng</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName || u.email}
                  </option>
                ))}
              </select>
              <ChevronDown size={14} className={styles.selectArrow} />
            </div>
          </div>

          {/* Type */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Loại giao dịch</label>
            <div className={styles.selectWrapper}>
              <select
                className={styles.select}
                value={type}
                onChange={(e) => {
                  setType(e.target.value);
                  handleFilterChange();
                }}
              >
                <option value="all">Tất cả loại</option>
                <option value="IMPORT">Nhập kho</option>
                <option value="EXPORT">Xuất kho</option>
                <option value="ADJUSTMENT">Điều chỉnh</option>
                <option value="TRANSFER_OUT">Chuyển đi</option>
                <option value="TRANSFER_IN">Chuyển đến</option>
              </select>
              <ChevronDown size={14} className={styles.selectArrow} />
            </div>
          </div>

          {/* From Date */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Từ ngày</label>
            <div className={styles.inputWrapper}>
              <Calendar size={15} className={styles.inputIcon} />
              <input
                type="date"
                className={styles.input}
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  handleFilterChange();
                }}
              />
            </div>
          </div>

          {/* To Date */}
          <div className={styles.filterGroup}>
            <label className={styles.filterLabel}>Đến ngày</label>
            <div className={styles.inputWrapper}>
              <Calendar size={15} className={styles.inputIcon} />
              <input
                type="date"
                className={styles.input}
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value);
                  handleFilterChange();
                }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Log List Card */}
      <div className={styles.logsCard}>
        <div className={styles.cardHeader}>
          <div className={styles.headerTitle}>
            <Clock size={18} className={styles.headerIcon} />
            <span>Lịch sử thay đổi</span>
          </div>
          <span className={styles.elementsCount}>
            {loading ? 'Đang tải...' : `Hiển thị ${logs.length} giao dịch`}
          </span>
        </div>

        {loading ? (
          <div className={styles.loadingContainer}>
            <Loader2 className={styles.spinner} size={28} />
            <p>Đang tải lịch sử thay đổi...</p>
          </div>
        ) : logs.length === 0 ? (
          <div className={styles.emptyContainer}>
            <Clock size={40} className={styles.emptyIcon} />
            <p>Không có dữ liệu giao dịch nào khớp với bộ lọc.</p>
          </div>
        ) : (
          <>
            <div className={styles.tableWrapper}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th style={{ width: 120 }}>Thời gian</th>
                    <th style={{ width: 130 }}>Người thực hiện</th>
                    <th style={{ width: 110 }}>Loại</th>
                    <th style={{ width: 120 }}>SKU</th>
                    <th style={{ width: 240 }}>Sản phẩm</th>
                    <th style={{ width: 130 }}>Kho</th>
                    <th style={{ width: 80, textAlign: 'center' }}>Số lượng</th>
                    <th style={{ width: 220 }}>Lý do</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => {
                    const isQtyPositive = log.quantityChange > 0;
                    return (
                      <tr key={log.id} className={styles.row}>
                        <td className={styles.timeCell}>{formatDateTime(log.performedAt)}</td>
                        <td>
                          <div className={styles.userCell}>
                            <User size={13} className={styles.cellIcon} />
                            <span>{log.performedByName}</span>
                          </div>
                        </td>
                        <td>{renderTypeBadge(log.type)}</td>
                        <td className={styles.skuCell}>{log.variantSku}</td>
                        <td className={styles.productCell}>{log.variantName}</td>
                        <td>
                          <div className={styles.warehouseCell}>
                            <Warehouse size={13} className={styles.cellIcon} />
                            <span>{log.warehouseName}</span>
                          </div>
                        </td>
                        <td
                          className={styles.quantityCell}
                          style={{ color: isQtyPositive ? '#16a34a' : '#dc2626' }}
                        >
                          {isQtyPositive ? `+${log.quantityChange}` : log.quantityChange}
                        </td>
                        <td>{renderReason(log)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Panel */}
            {totalPages > 1 && (
              <div className={styles.pagination}>
                <span className={styles.paginationInfo}>
                  Hiển thị {startItem}–{endItem} / {totalElements} mục
                </span>
                <div className={styles.paginationControls}>
                  <button
                    className={`${styles.pageBtn} ${page === 0 ? styles.pageBtnDisabled : ''}`}
                    onClick={() => handlePageChange(page - 1)}
                    disabled={page === 0}
                    aria-label="Trang trước"
                  >
                    <ChevronLeft size={16} />
                  </button>

                  {Array.from({ length: totalPages }, (_, i) => i).map((pageNum) => {
                    // Show dots or logic if there are too many pages (simple helper for display)
                    const isCurrent = pageNum === page;
                    return (
                      <button
                        key={pageNum}
                        className={`${styles.pageBtn} ${isCurrent ? styles.pageBtnActive : ''}`}
                        onClick={() => handlePageChange(pageNum)}
                      >
                        {pageNum + 1}
                      </button>
                    );
                  })}

                  <button
                    className={`${styles.pageBtn} ${page === totalPages - 1 ? styles.pageBtnDisabled : ''}`}
                    onClick={() => handlePageChange(page + 1)}
                    disabled={page === totalPages - 1}
                    aria-label="Trang sau"
                  >
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
