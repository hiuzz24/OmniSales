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

// ─── Constants ────────────────────────────────────────────────────────────────
const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZE_OPTIONS = [5, 10, 15, 20];
const INVENTORY_FETCH_SIZE = 500;
const STOCK_ALERT_REFRESH_INTERVAL_MS = 5000;

const STATUS_OPTIONS = [
  { value: 'all', label: 'Tất cả trạng thái' },
  { value: 'in-stock', label: 'Đủ hàng' },
  { value: 'low-stock', label: 'Sắp hết' },
  { value: 'out-of-stock', label: 'Hết hàng' },
  { value: 'negative', label: 'Tồn âm' },
];

const PLATFORM_LABELS = {
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
  TIKTOK: 'TikTok Shop',
};
const PLATFORM_KEYS = Object.keys(PLATFORM_LABELS);
const PLATFORM_FILTER_OPTIONS = PLATFORM_KEYS.map((platform) => ({
  value: platform,
  label: PLATFORM_LABELS[platform],
}));

const PLATFORM_BADGE_CLASSES = {
  LAZADA: styles.channelLazada,
  SHOPIFY: styles.channelShopify,
  TIKTOK: styles.channelLocal,
};

const INVENTORY_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'variantSku', label: 'Mã SKU', width: 18, defaultChecked: true, getValue: (item) => item.variantSku ?? '' },
  { key: 'variantName', label: 'Tên sản phẩm', width: 34, defaultChecked: true, getValue: (item) => item.variantName ?? '' },
  { key: 'warehouseName', label: 'Kho hàng', width: 24, defaultChecked: true, getValue: (item) => item.warehouseName ?? '' },
  { key: 'channel', label: 'Kênh bán', width: 18, defaultChecked: true, getValue: (item) => getChannelLabel(item) },
  { key: 'quantityOnHand', label: 'Trong kho', width: 12, type: 'number', defaultChecked: true, getValue: (item) => item.quantityOnHand ?? 0 },
  { key: 'incomingQuantity', label: 'Hàng đang nhập', width: 16, type: 'number', defaultChecked: true, getValue: (item) => item.incomingQuantity ?? 0 },
  { key: 'outgoingQuantity', label: 'Hàng đang xuất', width: 16, type: 'number', defaultChecked: true, getValue: (item) => item.outgoingQuantity ?? 0 },
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

const uniqueValues = (values) => [...new Set((values ?? []).filter(Boolean))];

const normalizePlatform = (value) => {
  const text = String(value ?? '').trim().toUpperCase();
  if (!text) return null;
  if (text.includes('LAZADA')) return 'LAZADA';
  if (text.includes('SHOPIFY')) return 'SHOPIFY';
  if (text.includes('TIKTOK')) return 'TIKTOK';
  return PLATFORM_KEYS.includes(text) ? text : null;
};

const getItemPlatforms = (item) => {
  const platforms = Array.isArray(item?.platforms) ? item.platforms : [];
  return uniqueValues([
    ...platforms,
    item?.platform,
    item?.channelPlatform,
    item?.salesChannelPlatform,
    item?.channel?.platform,
    item?.channelName,
  ].map(normalizePlatform));
};

const getItemChannelNames = (item) => {
  const names = uniqueValues(item?.channelNames);
  if (names.length > 0) return names;
  return item?.channelName ? [item.channelName] : [];
};

const getChannelLabel = (item) => {
  const platforms = getItemPlatforms(item);
  if (platforms.length === 0) return 'Ứng dụng';
  return platforms.map(getPlatformLabel).join(', ');
};

const getProductDisplayName = (item) => item?.productName || item?.variantName || 'Sản phẩm chưa đặt tên';

const normalizeSkuKey = (value) => String(value ?? '').trim().toLowerCase();

const getProductGroupKeys = (item) => {
  const keys = [];
  const productIds = Array.isArray(item?.productIds) ? item.productIds.filter(Boolean) : [];
  if (item?.productId) productIds.push(item.productId);
  uniqueValues(productIds).forEach((productId) => keys.push(`product:${productId}`));

  const sku = normalizeSkuKey(item?.marketplaceSku ?? item?.variantSku);
  if (sku) keys.push(`sku:${sku}`);

  return keys.length > 0 ? keys : [item?.variantId ? `variant:${item.variantId}` : `name:${getProductDisplayName(item)}`];
};

const buildProductGroupKeyResolver = (rows) => {
  const parent = new Map();
  const rowKeys = [];

  const find = (key) => {
    if (!parent.has(key)) parent.set(key, key);
    const current = parent.get(key);
    if (current === key) return key;
    const root = find(current);
    parent.set(key, root);
    return root;
  };

  const union = (a, b) => {
    const rootA = find(a);
    const rootB = find(b);
    if (rootA !== rootB) parent.set(rootB, rootA);
  };

  rows.forEach((item) => {
    const keys = getProductGroupKeys(item);
    rowKeys.push(keys);
    keys.forEach(find);
    for (let index = 1; index < keys.length; index += 1) {
      union(keys[0], keys[index]);
    }
  });

  return (index) => find(rowKeys[index][0]);
};

const buildInventoryGroups = (rows) => {
  const groups = new Map();
  const resolveGroupKey = buildProductGroupKeyResolver(rows);
  rows.forEach((item, index) => {
    const key = resolveGroupKey(index);
    if (!groups.has(key)) {
      groups.set(key, {
        type: 'product',
        id: `product-${key}`,
        productId: item.productId,
        productName: getProductDisplayName(item),
        platforms: [],
        channelNames: [],
        channelIds: [],
        items: [],
      });
    }
    const group = groups.get(key);
    group.platforms = uniqueValues([...group.platforms, ...getItemPlatforms(item)]);
    group.channelNames = uniqueValues([...group.channelNames, ...getItemChannelNames(item)]);
    group.channelIds = uniqueValues([...(group.channelIds ?? []), ...(item.channelIds ?? []), item.channelId]);
    group.items.push(item);
  });

  return [...groups.values()].map((group) => {
    const quantityOnHand = group.items.reduce((sum, item) => sum + Number(item.quantityOnHand ?? 0), 0);
    const incomingQuantity = group.items.reduce((sum, item) => sum + Number(item.incomingQuantity ?? 0), 0);
    const outgoingQuantity = group.items.reduce((sum, item) => sum + Number(item.outgoingQuantity ?? 0), 0);
    const reservedQuantity = group.items.reduce((sum, item) => sum + Number(item.reservedQuantity ?? 0), 0);
    const availableQuantity = group.items.reduce((sum, item) => sum + Number(item.availableQuantity ?? 0), 0);
    const lowStockThreshold = group.items.reduce((sum, item) => sum + Number(item.lowStockThreshold ?? 0), 0);
    const parentRow = {
      ...group,
      quantityOnHand,
      incomingQuantity,
      outgoingQuantity,
      reservedQuantity,
      availableQuantity,
      lowStockThreshold,
      isLowStock: group.items.some((item) => item.isLowStock),
      warehouseName: group.items.length === 1 ? group.items[0].warehouseName : `${group.items.length} SKU`,
      platform: group.platforms.length === 1 ? group.platforms[0] : null,
      channelName: group.channelNames.join(', '),
      channelId: group.channelIds.length === 1 ? group.channelIds[0] : null,
    };
    return {
      parentRow,
      childRows: group.items.map((item) => ({
        ...item,
        type: 'variant',
        inventoryItemId: item.id,
        id: `variant-${item.id}`,
      })),
    };
  });
};

const flattenInventoryGroups = (groups) => groups.flatMap((group) => [group.parentRow, ...group.childRows]);

// ─── Sub-components ───────────────────────────────────────────────────────────
const StatusBadge = ({ status, item }) => {
  const map = {
    'in-stock': { label: 'Đủ hàng', cls: styles.badgeGreen },
    'low-stock': { label: 'Sắp hết', cls: styles.badgeOrange },
    'out-of-stock': { label: 'Hết hàng', cls: styles.badgeGray },
    'negative': { label: 'Tồn âm', cls: styles.badgeRed },
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
  const platforms = getItemPlatforms(item);

  return (
    <div className={styles.channelCell}>
      {platforms.length === 0 ? (
        <span className={`${styles.channelBadge} ${styles.channelLocal}`}>Ứng dụng</span>
      ) : (
        platforms.map((platform) => (
          <span key={platform} className={`${styles.channelBadge} ${PLATFORM_BADGE_CLASSES[platform] ?? styles.channelLocal}`}>
            {getPlatformLabel(platform)}
          </span>
        ))
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
  const [selectedPlatformFilters, setSelectedPlatformFilters] = useState([]);
  const [categoryFilter, setCategoryFilter] = useState('all');

  // 'none' | 'asc' | 'desc'
  const [nameSort, setNameSort] = useState('none');
  const [qtySort, setQtySort] = useState('none');

  // ── Pagination state (0-indexed, driven by grouped products)
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  // ── Data state
  const [items, setItems] = useState([]);
  const [backendTotalProducts, setBackendTotalProducts] = useState(0);
  const [totalSkuElements, setTotalSkuElements] = useState(0);
  const [categoryTree, setCategoryTree] = useState([]);
  const [lowStockItems, setLowStockItems] = useState([]);
  const [summaryStats, setSummaryStats] = useState({
    totalProducts: 0,
    totalSkus: 0,
    totalQuantity: 0,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [exportOpen, setExportOpen] = useState(false);

  useEffect(() => {
    categoryApi.getTree()
      .then(res => setCategoryTree(res || []))
      .catch(err => console.error('Lỗi tải danh mục:', err));
  }, []);

  const fetchLowStockItems = useCallback(async () => {
    try {
      const data = await inventoryService.getLowStockItems();
      setLowStockItems(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Lỗi tải cảnh báo tồn kho:', err);
    }
  }, []);

  const getInventoryScope = (categoryId) => {
    const catIdParam = categoryId === 'all' ? null : categoryId;
    const channelIdParam = null;
    const localOnlyParam = false;
    return { catIdParam, channelIdParam, localOnlyParam };
  };

  const getInventoryFilters = useCallback(() => ({
    keyword: search.trim() || null,
    status: statusFilter === 'all' ? null : statusFilter,
    platforms: selectedPlatformFilters,
  }), [search, statusFilter, selectedPlatformFilters]);

  // Fetch the complete filtered SKU set, then paginate product groups on the client.
  // Paginating SKUs before grouping caused a 10-product page to show only 9 groups.
  const fetchInventory = useCallback(async (categoryId, filters = {}) => {
    setLoading(true);
    setError(null);
    try {
      const { catIdParam, channelIdParam, localOnlyParam } = getInventoryScope(categoryId);
      const firstPage = await inventoryService.getInventoryList(
        0,
        INVENTORY_FETCH_SIZE,
        'updatedAt',
        'desc',
        catIdParam,
        channelIdParam,
        localOnlyParam,
        filters,
      );
      const allRows = [...(firstPage.content ?? [])];
      const pageCount = Number(firstPage.totalPages ?? 1);

      for (let page = 1; page < pageCount; page += 1) {
        const data = await inventoryService.getInventoryList(
          page,
          INVENTORY_FETCH_SIZE,
          'updatedAt',
          'desc',
          catIdParam,
          channelIdParam,
          localOnlyParam,
          filters,
        );
        allRows.push(...(data.content ?? []));
      }

      setItems(allRows);
      setBackendTotalProducts(Number(firstPage.totalProducts ?? buildInventoryGroups(allRows).length));
      setTotalSkuElements(Number(firstPage.totalSkus ?? firstPage.totalElements ?? allRows.length));
    } catch (err) {
      console.error('Lỗi tải tồn kho:', err);
      setError('Không thể tải dữ liệu tồn kho. Vui lòng thử lại.');
      setItems([]);
      setBackendTotalProducts(0);
      setTotalSkuElements(0);
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchSummaryStats = useCallback(async () => {
    try {
      const firstPage = await inventoryService.getInventoryList(0, INVENTORY_FETCH_SIZE, 'updatedAt', 'desc');
      const allRows = [...(firstPage.content ?? [])];
      console.log('First page of inventory for summary stats:', firstPage);
      const pageCount = Number(firstPage.totalPages ?? 1);

      for (let page = 1; page < pageCount; page += 1) {
        const data = await inventoryService.getInventoryList(page, INVENTORY_FETCH_SIZE, 'updatedAt', 'desc');
        allRows.push(...(data.content ?? []));
      }

      setSummaryStats({
        totalProducts: Number(firstPage.totalProducts ?? buildInventoryGroups(allRows).length),
        totalSkus: Number(firstPage.totalSkus ?? firstPage.totalElements ?? allRows.length),
        totalQuantity: allRows.reduce((sum, item) => sum + Number(item.quantityOnHand ?? 0), 0),
      });
    } catch (err) {
      console.error('Lỗi tải thống kê tồn kho:', err);
      setSummaryStats({ totalProducts: 0, totalSkus: 0, totalQuantity: 0 });
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      fetchInventory(categoryFilter, getInventoryFilters());
    }, search.trim() ? 250 : 0);

    return () => window.clearTimeout(timer);
  }, [categoryFilter, getInventoryFilters, fetchInventory, search]);

  useEffect(() => {
    const timer = window.setTimeout(fetchSummaryStats, 0);
    return () => window.clearTimeout(timer);
  }, [fetchSummaryStats]);

  useEffect(() => {
    const refreshAlertsWhenVisible = () => {
      if (!document.hidden) fetchLowStockItems();
    };

    refreshAlertsWhenVisible();
    const interval = window.setInterval(refreshAlertsWhenVisible, STOCK_ALERT_REFRESH_INTERVAL_MS);
    window.addEventListener('focus', refreshAlertsWhenVisible);
    document.addEventListener('visibilitychange', refreshAlertsWhenVisible);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', refreshAlertsWhenVisible);
      document.removeEventListener('visibilitychange', refreshAlertsWhenVisible);
    };
  }, [fetchLowStockItems]);

  const togglePlatformFilter = (platform) => {
    setSelectedPlatformFilters((prev) => (
      prev.includes(platform)
        ? prev.filter((item) => item !== platform)
        : [...prev, platform]
    ));
    setCurrentPage(0);
  };

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

  const sortInventoryGroups = useCallback((source) => {
    if (nameSort !== 'none') {
      return [...source].sort((a, b) => {
        const cmp = (a.parentRow.productName ?? '').localeCompare(b.parentRow.productName ?? '', 'vi');
        return nameSort === 'asc' ? cmp : -cmp;
      });
    }

    if (qtySort !== 'none') {
      return [...source].sort((a, b) => {
        const qtyA = Number(a.parentRow.quantityOnHand ?? 0);
        const qtyB = Number(b.parentRow.quantityOnHand ?? 0);
        return qtySort === 'asc' ? qtyA - qtyB : qtyB - qtyA;
      });
    }

    return source;
  }, [nameSort, qtySort]);

  const productGroups = useMemo(() => {
    const groups = buildInventoryGroups(items);
    return sortInventoryGroups(groups);
  }, [items, sortInventoryGroups]);
  const visibleItems = useMemo(
    () => sortInventoryRows(flattenInventoryGroups(productGroups).filter((row) => row.type !== 'product')),
    [productGroups, sortInventoryRows]
  );
  const totalProducts = backendTotalProducts || productGroups.length;
  const totalPages = Math.ceil(totalProducts / pageSize);
  const paginatedProductGroups = useMemo(() => {
    const start = currentPage * pageSize;
    return productGroups.slice(start, start + pageSize);
  }, [currentPage, pageSize, productGroups]);
  const displayRows = useMemo(() => flattenInventoryGroups(paginatedProductGroups), [paginatedProductGroups]);
  const currentSkuCount = useMemo(
    () => paginatedProductGroups.reduce((sum, group) => sum + group.childRows.length, 0),
    [paginatedProductGroups]
  );

  useEffect(() => {
    if (currentPage > 0 && totalPages > 0 && currentPage >= totalPages) {
      const timer = window.setTimeout(() => setCurrentPage(Math.max(totalPages - 1, 0)), 0);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [currentPage, totalPages]);

  const loadInventoryExportRows = async () => {
    const { catIdParam, channelIdParam, localOnlyParam } = getInventoryScope(categoryFilter);
    const firstPage = await inventoryService.getInventoryList(0, INVENTORY_FETCH_SIZE, 'updatedAt', 'desc', catIdParam, channelIdParam, localOnlyParam, getInventoryFilters());
    const allRows = [...(firstPage.content ?? [])];
    const pageCount = Number(firstPage.totalPages ?? 1);
    for (let page = 1; page < pageCount; page += 1) {
      const data = await inventoryService.getInventoryList(page, INVENTORY_FETCH_SIZE, 'updatedAt', 'desc', catIdParam, channelIdParam, localOnlyParam, getInventoryFilters());
      allRows.push(...(data.content ?? []));
    }
    const groups = buildInventoryGroups(allRows);
    return sortInventoryRows(flattenInventoryGroups(groups).filter((row) => row.type !== 'product'));
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
  const negativeStockAlerts = lowStockItems.filter(r => Number(r.availableQuantity ?? 0) < 0);
  const outOfStockAlerts = lowStockItems.filter(r => Number(r.availableQuantity ?? 0) === 0);
  const lowStockAlerts = lowStockItems.filter(r => Number(r.availableQuantity ?? 0) > 0);
  const negativeCount = negativeStockAlerts.length;
  const lowStockCount = lowStockAlerts.length;
  const outOfStockCount = outOfStockAlerts.length;
  const stockNoticeItems = [...outOfStockAlerts, ...lowStockAlerts];
  const totalQuantityAll = summaryStats.totalQuantity;

  const quantityColor = (v) => {
    if (v < 0) return styles.cellRed;
    if (v === 0) return styles.cellOrange;
    return styles.cellDefault;
  };

  const handleMarketplaceSynced = useCallback(async () => {
    await Promise.all([
      fetchInventory(categoryFilter, getInventoryFilters()),
      fetchSummaryStats(),
      fetchLowStockItems(),
    ]);
    window.dispatchEvent(new Event('notifications:refresh'));
  }, [categoryFilter, fetchInventory, fetchLowStockItems, fetchSummaryStats, getInventoryFilters]);

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
        onClick={() => {navigate(ROUTES.INVENTORY_LOGS)}}
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
      <div className={`${styles.page} product-workspace`}>
        {/* ── Header ── */}
        <div className={styles.pageHeader}>
          <PageHeader
            title="Danh sách tồn kho"
            subtitle="Theo dõi và quản lý tồn kho theo SKU, kho hàng và trạng thái"
            icon={<Warehouse size={20} />}
            actions={actions}
          />
        </div>

        {/* ── Summary Cards ── */}
        <div className={styles.summaryGrid}>
          <div className={styles.summaryCard}>
            <div className={styles.summaryIconWrap} style={{ background: '#e0f2fe', color: '#0284c7' }}>
              <Layers size={20} />
            </div>
            <div className={styles.summaryInfo}>
              <span className={styles.summaryLabel}>Tổng sản phẩm</span>
              <span className={styles.summaryValue}>{summaryStats.totalProducts.toLocaleString('vi-VN')}</span>
              <span className={styles.summaryMeta}>{summaryStats.totalSkus.toLocaleString('vi-VN')} sản phẩm con (SKU)</span>
            </div>
          </div>
          <div className={styles.summaryCard}>
            <div className={styles.summaryIconWrap} style={{ background: '#f3e8ff', color: '#9333ea' }}>
              <Box size={20} />
            </div>
            <div className={styles.summaryInfo}>
              <span className={styles.summaryLabel}>Tổng đơn vị tồn kho</span>
              <span className={styles.summaryValue}>{totalQuantityAll.toLocaleString('vi-VN')}</span>
              <span className={styles.summaryMeta}>Trên toàn bộ kho hàng</span>
            </div>
          </div>
          <div className={styles.summaryCard}>
            <div className={styles.summaryIconWrap} style={{ background: '#fef3c7', color: '#d97706' }}>
              <AlertTriangle size={20} />
            </div>
            <div className={styles.summaryInfo}>
              <span className={styles.summaryLabel}>SKU sắp hết hàng</span>
              <span className={styles.summaryValue}>{lowStockCount.toLocaleString('vi-VN')}</span>
              <span className={styles.summaryMeta}>Dưới mức tồn tối thiểu</span>
            </div>
          </div>
          <div className={styles.summaryCard}>
            <div className={styles.summaryIconWrap} style={{ background: '#fee2e2', color: '#dc2626' }}>
              <AlertCircle size={20} />
            </div>
            <div className={styles.summaryInfo}>
              <span className={`${styles.summaryLabel} ${styles.textRed}`}>SKU tồn kho âm</span>
              <span className={`${styles.summaryValue} ${styles.textRed}`}>{negativeCount.toLocaleString('vi-VN')}</span>
              <span className={styles.summaryMeta}>Cần kiểm tra ngay</span>
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

        {!loading && (lowStockCount > 0 || outOfStockCount > 0) && (
          <div className={`${styles.alert} ${styles.alertWarn}`}>
            <AlertTriangle size={16} className={styles.alertIcon} />
            <div>
              Có {lowStockCount.toLocaleString('vi-VN')} SKU tồn kho thấp và {outOfStockCount.toLocaleString('vi-VN')} SKU hết hàng trong toàn bộ kho. Cần nhập hàng.
            </div>
          </div>
        )}

        {stockNoticeItems.length > 0 && (
          <section className={styles.stockNoticePanel}>
            <div className={styles.stockNoticeHeader}>
              <div>
                <h2 className={styles.stockNoticeTitle}>Thông báo tồn kho</h2>
                <p className={styles.stockNoticeSubtitle}>
                  {lowStockCount.toLocaleString('vi-VN')} SKU tồn kho thấp, {outOfStockCount.toLocaleString('vi-VN')} SKU hết hàng.
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
              {stockNoticeItems.slice(0, 6).map((item) => {
                const available = Number(item.availableQuantity ?? 0);
                const threshold = Number(item.lowStockThreshold ?? 0);
                const urgent = available <= 0;
                const alertStatus = available < 0
                  ? 'negative'
                  : available === 0 ? 'out-of-stock' : 'low-stock';
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
                    <StatusBadge status={alertStatus} />
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
          <div className={styles.filterSection}>
            <div className={styles.filterSectionHeader}>
              <span className={styles.filterSectionTitle}>Bộ lọc tồn kho</span>
              <span className={styles.filterSectionHint}>Tìm nhanh theo SKU, tên sản phẩm hoặc kết hợp nhiều sàn</span>
            </div>

            <div className={styles.filtersPrimaryRow}>
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
              <Select value={statusFilter} onChange={(value) => { setStatusFilter(value); setCurrentPage(0); }} options={STATUS_OPTIONS} />
            </div>
          </div>

          <div className={styles.filterQuickRow}>
            <div className={styles.platformFilterWrap}>
              <div className={styles.platformFilterHeader}>
                <span className={styles.platformFilterLabel}>Kênh bán</span>
                <span className={styles.platformFilterHint}>Chọn nhiều để xem sản phẩm có đủ các sàn</span>
                {selectedPlatformFilters.length > 0 && (
                  <button
                    type="button"
                    className={styles.platformFilterClear}
                    onClick={() => {
                      setSelectedPlatformFilters([]);
                      setCurrentPage(0);
                    }}
                  >
                    Bỏ chọn
                  </button>
                )}
              </div>
              <div className={styles.platformFilterGroup} aria-label="Lọc theo kênh bán">
                {PLATFORM_FILTER_OPTIONS.map((platform) => {
                  const active = selectedPlatformFilters.includes(platform.value);
                  return (
                    <button
                      key={platform.value}
                      type="button"
                      aria-pressed={active}
                      title={active ? `Bỏ lọc ${platform.label}` : `Lọc sản phẩm có ${platform.label}`}
                      className={`${styles.platformFilterTag} ${styles[`platformFilter${platform.value}`]} ${active ? styles.platformFilterTagActive : ''}`}
                      onClick={() => togglePlatformFilter(platform.value)}
                    >
                      <span className={styles.platformFilterDot} />
                      <span>{platform.label}</span>
                      {active && <span className={styles.platformFilterCheck}>✓</span>}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className={styles.sortFilterWrap}>
              <span className={styles.sortLabel}>Sắp xếp</span>
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
            </div>

            {(categoryFilter !== 'all' || selectedPlatformFilters.length > 0 || statusFilter !== 'all' || nameSort !== 'none' || qtySort !== 'none' || search) && (
              <button
                id="btn-reset-filters"
                className={styles.resetBtn}
                onClick={() => {
                  setSearch('');
                  setStatusFilter('all');
                  setSelectedPlatformFilters([]);
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
                Danh sách tồn kho
              </h2>
              <p className={styles.tableSubtitle}>
                <strong>{totalProducts.toLocaleString('vi-VN')}</strong> sản phẩm · <strong>{totalSkuElements.toLocaleString('vi-VN')}</strong> sản phẩm con (SKU)
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
                    <th className={styles.th}>Kho</th>
                    <th className={styles.th}>Kênh</th>
                    <th
                      className={`${styles.th} ${styles.thRight} ${styles.thSortable}`}
                      onClick={() => { setQtySort(nextSort(qtySort)); setNameSort('none'); setCurrentPage(0); }}
                      title="Trong kho — Click để sắp xếp"
                    >
                      Trong kho
                      {qtySort === 'asc' && <ArrowUp01 size={12} style={{ marginLeft: 4 }} />}
                      {qtySort === 'desc' && <ArrowDown10 size={12} style={{ marginLeft: 4 }} />}
                      {qtySort === 'none' && <ArrowUp01 size={12} style={{ marginLeft: 4, opacity: 0.3 }} />}
                    </th>
                    <th className={`${styles.th} ${styles.thRight}`} title="Hàng đang nhập kho (phiếu nhập chưa hoàn thành)">Đang nhập</th>
                    <th className={`${styles.th} ${styles.thRight}`} title="Hàng đang xuất kho (phiếu xuất chưa hoàn thành)">Đang xuất</th>
                    <th className={`${styles.th} ${styles.thRight}`} title="Hàng đang được giữ cho đơn hàng">Giữ hàng</th>
                    <th className={`${styles.th} ${styles.thRight}`} title="Số lượng có thể bán = Trong kho - Giữ hàng">Có thể bán</th>
                    <th className={`${styles.th} ${styles.thRight}`} title="Ngưỡng cảnh báo sắp hết hàng">Tồn kho tối thiểu</th>
                    <th className={styles.th}>Trạng thái</th>
                    <th className={styles.th}></th>
                  </tr>
                </thead>
                <tbody>
                  {displayRows.map((row, idx) => {
                    const status = deriveStatus(row);
                    const isProductRow = row.type === 'product';
                    const isOutOfStockVariant = !isProductRow
                      && Number(row.availableQuantity ?? 0) === 0;
                    return (
                      <tr
                        key={row.id}
                        className={`${styles.tr} ${idx % 2 === 1 ? styles.trAlt : ''} ${isProductRow ? styles.productRow : styles.variantRow} ${isOutOfStockVariant ? styles.outOfStockRow : ''}`}
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
                              <span className={styles.productName} title={row.productName}>{row.productName}</span>
                              <span className={styles.productMeta}>{row.items.length} sản phẩm con</span>
                            </div>
                          ) : (
                            <div className={styles.variantInfo}>
                              <span className={styles.variantIndent} />
                              <div>
                                <div className={styles.variantName} title={row.variantName || row.productName}>{row.variantName || row.productName}</div>
                                {row.productName && row.productName !== row.variantName && (
                                  <div className={styles.variantMeta} title={row.productName}>{row.productName}</div>
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
                          {row.quantityOnHand != null ? Number(row.quantityOnHand).toLocaleString('vi-VN') : '0'}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight} ${Number(row.incomingQuantity ?? 0) > 0 ? styles.qtyPositive : styles.cellMuted}`}>
                          {Number(row.incomingQuantity ?? 0).toLocaleString('vi-VN')}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight} ${Number(row.outgoingQuantity ?? 0) > 0 ? styles.qtyWarning : styles.cellMuted}`}>
                          {Number(row.outgoingQuantity ?? 0).toLocaleString('vi-VN')}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight} ${styles.cellMuted}`}>
                          {Number(row.reservedQuantity ?? 0).toLocaleString('vi-VN')}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight} ${quantityColor(row.availableQuantity ?? 0)}`}>
                          {(row.availableQuantity ?? 0) < 0 ? (
                            <span className={styles.negativeCell}>{Number(row.availableQuantity).toLocaleString('vi-VN')}</span>
                          ) : (row.availableQuantity ?? 0) === 0 ? (
                            <span className={styles.zeroCell}>0</span>
                          ) : (
                            Number(row.availableQuantity).toLocaleString('vi-VN')
                          )}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight} ${styles.cellMuted}`}>
                          {Number(row.lowStockThreshold ?? 0).toLocaleString('vi-VN')}
                        </td>
                        <td className={styles.td}>
                          <div className={styles.statusCell}>
                            <StatusBadge status={status} item={row} />
                          </div>
                        </td>
                        <td className={styles.td}>
                          {isProductRow ? (
                            <span className={styles.productActionHint}>—</span>
                          ) : (
                            <button
                              id={`btn-view-${row.variantSku}`}
                              className={styles.viewBtn}
                              title={`Xem chi tiết ${row.variantSku}`}
                              onClick={() => navigate(`${ROUTES.INVENTORY_DETAIL.replace(':id', row.inventoryItemId)}?variantId=${row.variantId}`)}
                            >
                              <Eye size={14} />
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}

                  {!loading && displayRows.length === 0 && (
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
            <span className={styles.footerMetric}>
              <span className={styles.footerMetricLabel}>Trang hiện tại</span>
              <strong>{paginatedProductGroups.length} sản phẩm</strong>
            </span>
            <span className={styles.footerMetric}>
              <span className={styles.footerMetricLabel}>Sản phẩm con trên trang</span>
              <strong>{currentSkuCount.toLocaleString('vi-VN')} SKU</strong>
            </span>
          </div>
          {/* ── Product-group Pagination ── */}
          {!loading && (
            <Pagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalElements={totalProducts}
              pageSize={pageSize}
              currentCount={paginatedProductGroups.length}
              itemLabel="sản phẩm"
              onPageChange={handlePageChange}
              showPageSizeSelector
              pageSizeOptions={PAGE_SIZE_OPTIONS}
              pageSizeLabel="Số sản phẩm mỗi trang"
              onPageSizeChange={(nextPageSize) => {
                setPageSize(nextPageSize);
                setCurrentPage(0);
              }}
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
