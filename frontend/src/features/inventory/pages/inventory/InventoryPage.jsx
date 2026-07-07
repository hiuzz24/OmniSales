import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search,
  ClipboardList,
  Eye,
  AlertTriangle,
  AlertCircle,
  ChevronDown,
  Loader2,
  ArrowUpAZ,
  ArrowDownAZ,
  ArrowUp01,
  ArrowDown10,
  Layers,
  Box,
  FileText,
  PackagePlus,
  ArrowRightLeft,
  Warehouse,
  PackageMinus
} from 'lucide-react';
import styles from './InventoryPage.module.css';
import PageHeader from '../../../../shared/components/PageHeader';
import Pagination from '../../../../shared/components/Pagination';
import { ROUTES } from '../../../../app/router/routes';
import categoryApi from '../../../../api/categoryApi';
import inventoryService from '../../services/inventoryService';
import InventoryExportModal from '../components/InventoryExportModal';
import { getStatusLabel } from '../components/inventoryExcelExport';
import MarketplaceSyncButton from '../components/MarketplaceSyncButton';
import channelSyncService from '../../services/channelSyncService';
import warehouseService from '../../services/warehouseService';

// ─── Constants ────────────────────────────────────────────────────────────────
const PAGE_SIZE = 10;
const INVENTORY_FETCH_SIZE = 500;

const STATUS_OPTIONS = [
  { value: 'all', label: 'Tất cả trạng thái' },
  { value: 'in-stock', label: 'Đủ hàng' },
  { value: 'low-stock', label: 'Sắp hết' },
  { value: 'out-of-stock', label: 'Hết hàng' },
  { value: 'negative', label: 'Tồn âm' },
];

const LOCAL_CHANNEL_VALUE = '__LOCAL__';

const PLATFORM_LABELS = {
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
};

const PLATFORM_BADGE_CLASSES = {
  LAZADA: styles.channelLazada,
  SHOPIFY: styles.channelShopify,
};

const INVENTORY_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'variantSku', label: 'Mã SKU', width: 18, defaultChecked: true, getValue: (item) => item.variantSku ?? '' },
  { key: 'variantName', label: 'Tên sản phẩm', width: 34, defaultChecked: true, getValue: (item) => item.variantName ?? '' },
  { key: 'warehouseName', label: 'Kho hàng', width: 24, defaultChecked: true, getValue: (item) => item.warehouseName ?? '' },
  { key: 'channel', label: 'Kênh bán', width: 18, defaultChecked: true, getValue: (item) => getChannelLabel(item) },
  { key: 'quantityOnHand', label: 'Trong kho', width: 12, type: 'number', defaultChecked: true, getValue: (item) => item.quantityOnHand ?? 0 },
  { key: 'reservedQuantity', label: 'Giữ hàng', width: 12, type: 'number', defaultChecked: true, getValue: (item) => item.reservedQuantity ?? 0 },
  { key: 'availableQuantity', label: 'Có thể bán', width: 12, type: 'number', defaultChecked: true, getValue: (item) => item.availableQuantity ?? 0 },
  { key: 'lowStockThreshold', label: 'Tồn tối thiểu', width: 14, type: 'number', defaultChecked: true, getValue: (item) => item.lowStockThreshold ?? 0 },
  { key: 'status', label: 'Trạng thái', width: 14, defaultChecked: true, getValue: (item) => getStatusLabel(deriveStatus(item)) },
  { key: 'categoryName', label: 'Danh mục', width: 20, defaultChecked: false, getValue: (item) => item.categoryName ?? '' },
  { key: 'updatedAt', label: 'Cập nhật lần cuối', width: 20, defaultChecked: false, getValue: (item) => item.updatedAt ? new Date(item.updatedAt).toLocaleString('vi-VN') : '' },
];

const INVENTORY_HISTORY_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'performedAt', label: 'Ngày biến động', width: 20, defaultChecked: true, getValue: (item) => item.performedAt ? new Date(item.performedAt).toLocaleString('vi-VN') : '' },
  { key: 'variantSku', label: 'Mã SKU', width: 18, defaultChecked: true, getValue: (item) => item.variantSku ?? '' },
  { key: 'variantName', label: 'Tên sản phẩm', width: 34, defaultChecked: true, getValue: (item) => item.variantName ?? '' },
  { key: 'warehouseName', label: 'Kho hàng', width: 24, defaultChecked: true, getValue: (item) => item.warehouseName ?? '' },
  { key: 'typeLabel', label: 'Loại biến động', width: 22, defaultChecked: true, getValue: (item) => item.typeLabel ?? item.type ?? '' },
  { key: 'quantityBefore', label: 'Trước biến động', width: 16, type: 'number', defaultChecked: true, getValue: (item) => item.quantityBefore ?? 0 },
  { key: 'quantityChange', label: 'Thay đổi', width: 12, type: 'number', defaultChecked: true, getValue: (item) => item.quantityChange ?? 0 },
  { key: 'quantityAfter', label: 'Sau biến động', width: 16, type: 'number', defaultChecked: true, getValue: (item) => item.quantityAfter ?? 0 },
  { key: 'referenceType', label: 'Nguồn', width: 12, defaultChecked: false, getValue: (item) => item.referenceType ?? '' },
  { key: 'note', label: 'Ghi chú', width: 36, defaultChecked: false, getValue: (item) => item.note ?? '' },
  { key: 'performedByName', label: 'Người thực hiện', width: 22, defaultChecked: false, getValue: (item) => item.performedByName ?? '' },
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

const getPlatformLabel = (platform) => PLATFORM_LABELS[platform] ?? platform ?? 'Ứng dụng';

const getChannelLabel = (item) => {
  if (!item?.platform) return 'Ứng dụng';
  return item.channelName
    ? `${getPlatformLabel(item.platform)} - ${item.channelName}`
    : getPlatformLabel(item.platform);
};

const getProductDisplayName = (item) => item?.productName || item?.variantName || 'Sản phẩm chưa đặt tên';

const buildInventoryDisplayRows = (rows) => {
  const groups = new Map();
  rows.forEach((item) => {
    const key = [
      getProductDisplayName(item),
      item.platform || 'LOCAL',
      item.channelId || item.channelName || '',
    ].join('|');
    if (!groups.has(key)) {
      groups.set(key, {
        type: 'product',
        id: `product-${key}`,
        productName: getProductDisplayName(item),
        platform: item.platform,
        channelName: item.channelName,
        channelId: item.channelId,
        items: [],
      });
    }
    groups.get(key).items.push(item);
  });

  return [...groups.values()].flatMap((group) => {
    const quantityOnHand = group.items.reduce((sum, item) => sum + Number(item.quantityOnHand ?? 0), 0);
    const reservedQuantity = group.items.reduce((sum, item) => sum + Number(item.reservedQuantity ?? 0), 0);
    const availableQuantity = group.items.reduce((sum, item) => sum + Number(item.availableQuantity ?? 0), 0);
    const lowStockThreshold = group.items.reduce((sum, item) => sum + Number(item.lowStockThreshold ?? 0), 0);
    const parentRow = {
      ...group,
      quantityOnHand,
      reservedQuantity,
      availableQuantity,
      lowStockThreshold,
      isLowStock: group.items.some((item) => item.isLowStock),
      warehouseName: group.items.length === 1 ? group.items[0].warehouseName : `${group.items.length} SKU`,
    };
    return [
      parentRow,
      ...group.items.map((item) => ({
        ...item,
        type: 'variant',
        inventoryItemId: item.id,
        id: `variant-${item.id}`,
      })),
    ];
  });
};

// ─── Sub-components ───────────────────────────────────────────────────────────
const StatusBadge = ({ status, item }) => {
  const map = {
    'in-stock':    { label: 'Đủ hàng', cls: styles.badgeGreen },
    'low-stock':   { label: 'Sắp hết', cls: styles.badgeOrange },
    'out-of-stock':{ label: 'Hết hàng', cls: styles.badgeGray },
    'negative':    { label: 'Tồn âm', cls: styles.badgeRed },
  };
  const { label, cls } = map[status] ?? { label: status, cls: '' };

  const subLabel = useMemo(() => {
    if (status === 'low-stock') return 'Dưới mức tồn';
    if (status === 'negative') {
      const absVal = Math.abs(item?.availableQuantity ?? 0);
      return `Âm ${absVal}`;
    }
    if (status === 'out-of-stock') return 'Hết hàng';
    return null;
  }, [status, item]);

  return (
    <div className={styles.statusCell}>
      <span className={`${styles.badge} ${cls}`}>{label}</span>
      {subLabel && (
        <span className={styles.statusSub}>{subLabel}</span>
      )}
    </div>
  );
};

const ChannelBadge = ({ item }) => {
  const platform = item?.platform;
  const badgeClass = PLATFORM_BADGE_CLASSES[platform] ?? styles.channelLocal;

  return (
    <div className={styles.channelCell}>
      <span className={`${styles.channelBadge} ${badgeClass}`}>
        {getPlatformLabel(platform)}
      </span>
      {item?.channelName && (
        <span className={styles.channelSub}>{item.channelName}</span>
      )}
    </div>
  );
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

// ─── Main Page ────────────────────────────────────────────────────────────────
const InventoryPage = () => {
  const navigate = useNavigate();
  // ── Filter state (sent to backend before pagination)
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [warehouseFilter, setWarehouseFilter] = useState('all');
  const [channelFilter, setChannelFilter] = useState('all');
  const [categoryFilter, setCategoryFilter] = useState('all');

  // 'none' | 'asc' | 'desc'
  const [nameSort, setNameSort] = useState('none');
  const [qtySort, setQtySort] = useState('none');

  // ── Pagination state (0-indexed, driven by BE filtered rows)
  const [currentPage, setCurrentPage] = useState(0);

  // ── Data state
  const [items, setItems] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [categoryTree, setCategoryTree] = useState([]);
  const [syncChannels, setSyncChannels] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [lowStockItems, setLowStockItems] = useState([]);
  const [summaryStats, setSummaryStats] = useState({
    totalSkus: 0,
    totalQuantity: 0,
    lowStockCount: 0,
    negativeCount: 0,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [exportOpen, setExportOpen] = useState(false);

  useEffect(() => {
    categoryApi.getTree()
      .then(res => setCategoryTree(res || []))
      .catch(err => console.error('Lỗi tải danh mục:', err));
  }, []);

  useEffect(() => {
    channelSyncService.getSyncableChannels()
      .then((channels) => setSyncChannels(Array.isArray(channels) ? channels : []))
      .catch((err) => {
        console.error('Lỗi tải danh sách kênh bán:', err);
        setSyncChannels([]);
      });
  }, []);

  useEffect(() => {
    warehouseService.getAll()
      .then((response) => {
        const data = response.data?.data ?? response.data ?? [];
        setWarehouses(Array.isArray(data) ? data : data.content ?? []);
      })
      .catch((err) => {
        console.error('Lỗi tải danh sách kho:', err);
        setWarehouses([]);
      });
  }, []);

  const fetchLowStockItems = useCallback(async () => {
    try {
      const data = await inventoryService.getLowStockItems();
      setLowStockItems(Array.isArray(data) ? data : []);
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (err) {
      console.error('Lỗi tải cảnh báo tồn kho:', err);
      setLowStockItems([]);
    }
  }, []);

  const getInventoryScope = (categoryId, channelValue) => {
    const catIdParam = categoryId === 'all' ? null : categoryId;
    const channelIdParam = channelValue && channelValue !== 'all' && channelValue !== LOCAL_CHANNEL_VALUE
      ? channelValue
      : null;
    const localOnlyParam = channelValue === LOCAL_CHANNEL_VALUE;
    return { catIdParam, channelIdParam, localOnlyParam };
  };

  const getInventoryFilters = useCallback(() => ({
    keyword: search.trim() || null,
    status: statusFilter === 'all' ? null : statusFilter,
    warehouseId: warehouseFilter === 'all' ? null : warehouseFilter,
  }), [search, statusFilter, warehouseFilter]);

  // ── Fetch from BE whenever page/filter changes
  const fetchInventory = useCallback(async (page, categoryId, channelValue, filters = {}) => {
    setLoading(true);
    setError(null);
    try {
      const { catIdParam, channelIdParam, localOnlyParam } = getInventoryScope(categoryId, channelValue);
      const data = await inventoryService.getInventoryList(
        page,
        PAGE_SIZE,
        'updatedAt',
        'desc',
        catIdParam,
        channelIdParam,
        localOnlyParam,
        filters,
      );
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

  const fetchSummaryStats = useCallback(async () => {
    try {
      const firstPage = await inventoryService.getInventoryList(0, INVENTORY_FETCH_SIZE, 'updatedAt', 'desc');
      const allRows = [...(firstPage.content ?? [])];
      const pageCount = Number(firstPage.totalPages ?? 1);

      for (let page = 1; page < pageCount; page += 1) {
        const data = await inventoryService.getInventoryList(page, INVENTORY_FETCH_SIZE, 'updatedAt', 'desc');
        allRows.push(...(data.content ?? []));
      }

      setSummaryStats({
        totalSkus: firstPage.totalElements ?? allRows.length,
        totalQuantity: allRows.reduce((sum, item) => sum + Number(item.quantityOnHand ?? 0), 0),
        lowStockCount: allRows.filter((item) => deriveStatus(item) === 'low-stock').length,
        negativeCount: allRows.filter((item) => Number(item.availableQuantity ?? 0) < 0).length,
      });
    } catch (err) {
      console.error('Lỗi tải thống kê tồn kho:', err);
      setSummaryStats({ totalSkus: 0, totalQuantity: 0, lowStockCount: 0, negativeCount: 0 });
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      fetchInventory(currentPage, categoryFilter, channelFilter, getInventoryFilters());
    }, search.trim() ? 250 : 0);

    return () => window.clearTimeout(timer);
  }, [currentPage, categoryFilter, channelFilter, getInventoryFilters, fetchInventory, search]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      fetchLowStockItems();
      fetchSummaryStats();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [fetchLowStockItems, fetchSummaryStats]);

  const warehouseOptions = useMemo(() => {
    return [
      { value: 'all', label: 'Tất cả kho hàng' },
      ...warehouses.map((warehouse) => ({ value: warehouse.id, label: warehouse.name })),
    ];
  }, [warehouses]);

  const channelOptions = useMemo(() => {
    return [
      { value: 'all', label: 'Tất cả kênh bán' },
      ...syncChannels.map((channel) => ({
        value: channel.id,
        label: channel.displayName
          ? `${getPlatformLabel(channel.platform)} - ${channel.displayName}`
          : getPlatformLabel(channel.platform),
      })),
      { value: LOCAL_CHANNEL_VALUE, label: 'Ứng dụng' },
    ];
  }, [syncChannels]);

  const handlePageChange = (page) => {
    setCurrentPage(page);
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

  const sortInventoryRows = useCallback((source) => {
    let result = source;
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
  }, [nameSort, qtySort]);

  const visibleItems = useMemo(() => sortInventoryRows(items), [items, sortInventoryRows]);
  const displayRows = useMemo(() => buildInventoryDisplayRows(visibleItems), [visibleItems]);

  useEffect(() => {
    if (currentPage > 0 && totalPages > 0 && currentPage >= totalPages) {
      setCurrentPage(Math.max(totalPages - 1, 0));
    }
  }, [currentPage, totalPages]);

  const loadInventoryExportRows = async () => {
    const { catIdParam, channelIdParam, localOnlyParam } = getInventoryScope(categoryFilter, channelFilter);
    const data = await inventoryService.getInventoryList(
      0,
      Math.max(totalElements || PAGE_SIZE, PAGE_SIZE),
      'updatedAt',
      'desc',
      catIdParam,
      channelIdParam,
      localOnlyParam,
      getInventoryFilters(),
    );
    return sortInventoryRows(data.content ?? []);
  };

  const loadInventoryExportExtraSheets = async ({ fromDate, toDate } = {}) => {
    const parseDate = (value, endOfDay = false) => {
      if (!value) return null;
      const parsed = new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    };
    const from = parseDate(fromDate);
    const to = parseDate(toDate, true);
    const data = await inventoryService.getInventoryTransactions(0, 10000, 'performedAt', 'desc');
    const historyRows = (data.content ?? []).filter((row) => {
      if (!from && !to) return true;
      const rowDate = row.performedAt ? new Date(row.performedAt) : null;
      if (!rowDate || Number.isNaN(rowDate.getTime())) return false;
      if (from && rowDate < from) return false;
      if (to && rowDate > to) return false;
      return true;
    });

    return [{
      rows: historyRows,
      columns: INVENTORY_HISTORY_EXPORT_COLUMNS,
      title: 'LỊCH SỬ BIẾN ĐỘNG TỒN KHO',
      sheetName: 'Lich su bien dong',
    }];
  };

  // ── Derived alert counts from the full inventory snapshot
  const negativeCount = summaryStats.negativeCount;
  const lowStockCount = summaryStats.lowStockCount;
  const outOfStockAlerts = lowStockItems.filter(r => Number(r.availableQuantity ?? 0) <= 0);
  const lowStockAlerts = lowStockItems.filter(r => Number(r.availableQuantity ?? 0) > 0);
  const totalQuantityAll = summaryStats.totalQuantity;

  const quantityColor = (v) => {
    if (v < 0) return styles.cellRed;
    if (v === 0) return styles.cellOrange;
    return styles.cellDefault;
  };

  const handleMarketplaceSynced = useCallback(async () => {
    await Promise.all([
      fetchInventory(currentPage, categoryFilter, channelFilter, getInventoryFilters()),
      fetchSummaryStats(),
      fetchLowStockItems(),
    ]);
  }, [categoryFilter, channelFilter, currentPage, fetchInventory, fetchLowStockItems, fetchSummaryStats, getInventoryFilters]);

  const actions = (
    <>
      <MarketplaceSyncButton
        className={styles.syncButtonWrap}
        buttonClassName={`${styles.actionBtn} ${styles.tealBtn} ${styles.syncButton}`}
        iconClassName={styles.tealIcon}
        onSynced={handleMarketplaceSynced}
      />
      <button
        className={`${styles.actionBtn} ${styles.secondaryBtn}`}
        id="btn-inventory-logs"
      >
        <ClipboardList className={styles.secondaryIcon} />
        Nhật ký kho
      </button>
      <button
        className={`${styles.actionBtn} ${styles.importBtn}`}
        id="btn-import-stock"
        type="button"
        onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}
      >
        <PackagePlus className={styles.importIcon} />
        Nhập kho
      </button>
      <button
        className={`${styles.actionBtn} ${styles.exportBtn}`}
        id="btn-export-stock"
        type="button"
        onClick={() => navigate(ROUTES.STOCK_DELIVERIES)}
      >
        <PackageMinus className={styles.exportIcon} />
        Xuất kho
      </button>
      <button
        className={`${styles.actionBtn} ${styles.transferBtn}`}
        id="btn-stock-transfer"
        onClick={() => navigate(ROUTES.STOCK_TRANSFER)}
      >
        <ArrowRightLeft className={styles.transferIcon} />
        Chuyển kho
      </button>
      <button
        className={`${styles.actionBtn} ${styles.primaryBtn}`}
        id="btn-export-report"
        type="button"
        onClick={() => setExportOpen(true)}
      >
        <FileText className={styles.primaryIcon} />
        Xuất báo cáo
      </button>
    </>
  );

  return (
    <>
    <div className={styles.page}>
      {/* ── Header ── */}
      <PageHeader
        title="Danh sách tồn kho"
        subtitle="Theo dõi và quản lý tồn kho theo SKU, kho hàng và trạng thái"
        icon={<Warehouse size={20} />}
        actions={actions}
      />

      {/* ── Summary Cards ── */}
      <div className={styles.summaryGrid}>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#e0f2fe', color: '#0284c7' }}>
            <Layers size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Tổng sản phẩm SKUs</span>
            <span className={styles.summaryValue}>{summaryStats.totalSkus}</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#f3e8ff', color: '#9333ea' }}>
            <Box size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Tổng số lượng SKUs<br /><small>(Toàn bộ)</small></span>
            <span className={styles.summaryValue}>{totalQuantityAll}</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#fef3c7', color: '#d97706' }}>
            <AlertTriangle size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Sản phẩm sắp hết hàng</span>
            <span className={styles.summaryValue}>{lowStockCount}</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryIconWrap} style={{ background: '#fee2e2', color: '#dc2626' }}>
            <AlertCircle size={20} />
          </div>
          <div className={styles.summaryInfo}>
            <span className={`${styles.summaryLabel} ${styles.textRed}`}>Sản phẩm tồn kho âm</span>
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
            Có {negativeCount} SKU đang tồn kho âm trong toàn bộ kho. Cần kiểm tra và điều chỉnh ngay.
          </div>
        </div>
      )}

      {!loading && lowStockCount > 0 && (
        <div className={`${styles.alert} ${styles.alertWarn}`}>
          <AlertTriangle size={16} className={styles.alertIcon} />
          <div>
            Có {lowStockCount} SKU dưới mức tồn tối thiểu trong toàn bộ kho. Cần nhập hàng.
          </div>
        </div>
      )}

      {lowStockItems.length > 0 && (
        <section className={styles.stockNoticePanel}>
          <div className={styles.stockNoticeHeader}>
            <div>
              <h2 className={styles.stockNoticeTitle}>Thông báo tồn kho</h2>
              <p className={styles.stockNoticeSubtitle}>
                {outOfStockAlerts.length} SKU hết hàng, {lowStockAlerts.length} SKU dưới tồn kho tối thiểu.
              </p>
            </div>
            <button
              className={styles.btnOutline}
              type="button"
              onClick={fetchLowStockItems}
            >
              Làm mới
            </button>
          </div>
          <div className={styles.stockNoticeList}>
            {lowStockItems.slice(0, 6).map((item) => {
              const available = Number(item.availableQuantity ?? 0);
              const threshold = Number(item.lowStockThreshold ?? 0);
              const urgent = available <= 0;
              return (
                <button
                  key={item.id}
                  className={styles.stockNoticeItem}
                  type="button"
                  onClick={() => navigate(`${ROUTES.INVENTORY_DETAIL.replace(':id', item.id)}?variantId=${item.variantId}`)}
                >
                  <span className={`${styles.stockNoticeDot} ${urgent ? styles.stockNoticeDotDanger : styles.stockNoticeDotWarn}`} />
                  <span className={styles.stockNoticeMain}>
                    <span className={styles.stockNoticeName}>{item.variantSku} · {item.variantName}</span>
                    <span className={styles.stockNoticeMeta}>{item.warehouseName} · Còn bán {available} / tối thiểu {threshold}</span>
                  </span>
                  <StatusBadge status={urgent ? 'out-of-stock' : 'low-stock'} />
                </button>
              );
            })}
          </div>
        </section>
      )}

      {/* ── Error ── */}
      {error && (
        <div className={`${styles.alert} ${styles.alertError}`}>
          <AlertCircle size={16} className={styles.alertIcon} />
          <div>{error}</div>
        </div>
      )}

      <div className={styles.filtersPanel}>
        {/* ── Filters row 1: Search + Warehouse + Status + Category ── */}
        <div className={styles.filtersRow}>
          <div className={styles.searchWrapper}>
            <Search className={styles.searchIcon} size={18} />
            <input
              id="input-inventory-search"
              className={styles.searchInput}
              placeholder="Tìm theo SKU hoặc tên sản phẩm..."
              value={search}
              onChange={e => {
                setSearch(e.target.value);
                setCurrentPage(0);
              }}
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
          <Select value={warehouseFilter} onChange={(value) => { setWarehouseFilter(value); setCurrentPage(0); }} options={warehouseOptions} />
          <Select value={channelFilter} onChange={(value) => { setChannelFilter(value); setCurrentPage(0); }} options={channelOptions} />
          <Select value={statusFilter} onChange={(value) => { setStatusFilter(value); setCurrentPage(0); }} options={STATUS_OPTIONS} />
        </div>

        {/* ── Filters row 2: Sort toggles ── */}
        <div className={styles.filtersRow2}>
          <span className={styles.sortLabel}>Sắp xếp:</span>

          {/* Sort by name */}
          <button
            id="btn-sort-name"
            className={`${styles.sortBtn} ${nameSort !== 'none' ? styles.sortBtnActive : ''}`}
            onClick={() => { setNameSort(nextSort(nameSort)); setQtySort('none'); setCurrentPage(0); }}
            title="Sắp xếp theo tên sản phẩm"
          >
            {nameSort === 'desc'
              ? <ArrowDownAZ size={14} />
              : <ArrowUpAZ size={14} />}
            Tên A-Z
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
            onClick={() => { setQtySort(nextSort(qtySort)); setNameSort('none'); setCurrentPage(0); }}
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
          {(categoryFilter !== 'all' || warehouseFilter !== 'all' || channelFilter !== 'all' || statusFilter !== 'all' || nameSort !== 'none' || qtySort !== 'none' || search) && (
            <button
              id="btn-reset-filters"
              className={styles.resetBtn}
              onClick={() => {
                setSearch('');
                setStatusFilter('all');
                setWarehouseFilter('all');
                setChannelFilter('all');
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
                    onClick={() => { setNameSort(nextSort(nameSort)); setQtySort('none'); setCurrentPage(0); }}
                    title="Click để sắp xếp theo tên"
                  >
                    Tên sản phẩm
                    {nameSort === 'asc' && <ArrowUpAZ size={12} style={{ marginLeft: 4 }} />}
                    {nameSort === 'desc' && <ArrowDownAZ size={12} style={{ marginLeft: 4 }} />}
                    {nameSort === 'none' && <ArrowUpAZ size={12} style={{ marginLeft: 4, opacity: 0.3 }} />}
                  </th>
                  <th className={styles.th}>Kho hàng</th>
                  <th className={styles.th}>Kênh bán</th>
                  <th
                    className={`${styles.th} ${styles.thRight} ${styles.thSortable}`}
                    onClick={() => { setQtySort(nextSort(qtySort)); setNameSort('none'); setCurrentPage(0); }}
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
                {displayRows.map((row, idx) => {
                  const status = deriveStatus(row);
                  const isProductRow = row.type === 'product';
                  return (
                    <tr
                      key={row.id}
                      className={`${styles.tr} ${idx % 2 === 1 ? styles.trAlt : ''} ${isProductRow ? styles.productRow : styles.variantRow}`}
                    >
                      <td className={styles.td}>
                        {isProductRow ? (
                          <span className={styles.productSkuSummary}>{row.items.length} SKU</span>
                        ) : (
                          <span className={styles.skuChip}>{row.variantSku}</span>
                        )}
                      </td>
                      <td className={styles.td}>
                        {isProductRow ? (
                          <div className={styles.productInfo}>
                            <span className={styles.productName}>{row.productName}</span>
                            <span className={styles.productMeta}>{row.items.length} sản phẩm con</span>
                          </div>
                        ) : (
                          <div className={styles.variantInfo}>
                            <span className={styles.variantIndent} />
                            <div>
                              <div className={styles.variantName}>{row.variantName || row.productName}</div>
                              {row.productName && row.productName !== row.variantName && (
                                <div className={styles.variantMeta}>{row.productName}</div>
                              )}
                            </div>
                          </div>
                        )}
                      </td>
                      <td className={styles.td}>
                        <span className={styles.warehouseLink}>{row.warehouseName}</span>
                      </td>
                      <td className={styles.td}>
                        <ChannelBadge item={row} />
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
                          <StatusBadge status={status} item={row} />
                        </div>
                      </td>
                      <td className={styles.td}>
                        {isProductRow ? (
                          <span className={styles.productActionHint}>Nhóm sản phẩm</span>
                        ) : (
                          <button
                            id={`btn-view-${row.variantSku}`}
                            className={styles.viewBtn}
                            onClick={() => navigate(`${ROUTES.INVENTORY_DETAIL.replace(':id', row.inventoryItemId)}?variantId=${row.variantId}`)}
                          >
                            <Eye size={14} />
                            View
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}

                {!loading && items.length === 0 && (
                  <tr>
                    <td colSpan={10} className={styles.emptyRow}>
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

        <div className={styles.tableFooter}>
          <span>
            Đang hiển thị {visibleItems.length} / {totalElements} SKU
          </span>
          <span>
            Dòng bảng: {displayRows.length.toLocaleString('vi-VN')} gồm sản phẩm cha và sản phẩm con
          </span>
          <span>
            Tổng tồn kho: {totalQuantityAll.toLocaleString('vi-VN')} đơn vị
          </span>
        </div>

        {/* ── BE Pagination ── */}
        {!loading && (
          <Pagination
            currentPage={currentPage}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={PAGE_SIZE}
            currentCount={visibleItems.length}
            itemLabel="SKU"
            onPageChange={handlePageChange}
          />
        )}
      </div>
    </div>
    <InventoryExportModal
      open={exportOpen}
      onClose={() => setExportOpen(false)}
      rows={visibleItems}
      columns={INVENTORY_EXPORT_COLUMNS}
      getDateValue={(item) => item.updatedAt ?? item.createdAt}
      loadRows={loadInventoryExportRows}
      loadExtraSheets={loadInventoryExportExtraSheets}
      title="BẢNG KÊ TỒN KHO"
      fileName="bang-ke-ton-kho"
      sheetName="tồn kho"
    />
    </>
  );
};

export default InventoryPage;
