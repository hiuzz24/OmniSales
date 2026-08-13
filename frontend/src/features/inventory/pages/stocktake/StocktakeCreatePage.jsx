import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ClipboardList,
  Loader2,
  LockKeyhole,
  PackageCheck,
  Plus,
  Save,
  Search,
  TrendingDown,
  TrendingUp,
  Warehouse,
  X,
} from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../../app/router/routes';
import inventoryApi from '../../../../api/inventoryApi';
import stocktakeService from '../../services/stocktakeService';
import warehouseService from '../../services/warehouseService';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import useDebounce from '../../../../shared/hooks/useDebounce';
import { formatNumber, formatVND, getResponseData } from '../components/inventoryDocumentListUtils';
import {
  confirmStocktakeComplete,
  confirmSyncMarketplaceNow,
  getDiffSummary,
} from './stocktakeCompletion';
import styles from '../CreatePage.module.css';

const today = new Date().toISOString().slice(0, 10);
const WAREHOUSE_PAGE_SIZE = 50;

const makeSessionCode = () => {
  const now = new Date();
  const ymd = now.toISOString().slice(0, 10).replaceAll('-', '');
  return `KK-${ymd}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
};

const hasActualQuantity = (item) => item.actualQuantity !== '' && item.actualQuantity !== null && item.actualQuantity !== undefined;
const getItemDiff = (item) => Number(item.actualQuantity || 0) - Number(item.systemQuantity || 0);
const getItemCost = (item) => Number(item.averageCost ?? item.costPrice ?? item.unitCost ?? item.unitPrice ?? item.price ?? 0);

const toStocktakeItem = (item) => ({
  variantId: item.variantId,
  variantSku: item.variantSku,
  variantName: item.variantName,
  productName: item.productName,
  systemQuantity: Number(item.quantityOnHand ?? item.availableQuantity ?? 0),
  actualQuantity: '',
  averageCost: getItemCost(item),
});

const DiffValue = ({ diff, checked }) => {
  if (!checked || diff === 0) return <span className={styles.mutedDash}>—</span>;
  const Icon = diff > 0 ? TrendingUp : TrendingDown;
  return (
    <span className={diff > 0 ? styles.diffPositive : styles.diffNegative}>
      <Icon size={14} />
      {diff > 0 ? `+${formatNumber(diff)}` : `-${formatNumber(Math.abs(diff))}`}
    </span>
  );
};

const PLATFORM_LABELS = {
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
  TIKTOK: 'TikTok Shop',
};
const PLATFORM_KEYS = Object.keys(PLATFORM_LABELS);
const PLATFORM_BADGE_STYLES = {
  LAZADA: { backgroundColor: '#eef2ff', color: '#3730a3', borderColor: '#c7d2fe' },
  SHOPIFY: { backgroundColor: '#ecfdf5', color: '#047857', borderColor: '#a7f3d0' },
  TIKTOK: { backgroundColor: '#f8fafc', color: '#0f172a', borderColor: '#cbd5e1' },
  LOCAL: { backgroundColor: '#f1f5f9', color: '#475569', borderColor: '#e2e8f0' },
};
const platformBadgeBaseStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  minHeight: 20,
  padding: '2px 7px',
  borderRadius: 6,
  border: '1px solid transparent',
  fontSize: 11,
  fontWeight: 700,
  lineHeight: 1.2,
  whiteSpace: 'nowrap',
};

const uniqueValues = (values) => [...new Set((values ?? []).filter(Boolean))];

const normalizePlatform = (value) => {
  const text = String(value ?? '').trim().toUpperCase();
  if (!text) return null;
  if (text.includes('LAZADA')) return 'LAZADA';
  if (text.includes('SHOPIFY')) return 'SHOPIFY';
  if (text.includes('TIKTOK')) return 'TIKTOK';
  return PLATFORM_KEYS.includes(text) ? text : null;
};

const extractPlatforms = (value) => {
  if (Array.isArray(value)) return value.flatMap(extractPlatforms);
  const text = String(value ?? '').trim().toUpperCase();
  if (!text) return [];
  const matches = [];
  if (text.includes('LAZADA')) matches.push('LAZADA');
  if (text.includes('SHOPIFY')) matches.push('SHOPIFY');
  if (text.includes('TIKTOK')) matches.push('TIKTOK');
  const normalized = normalizePlatform(text);
  return matches.length > 0 ? matches : (normalized ? [normalized] : []);
};

const itemPlatforms = (item) => {
  return uniqueValues([
    ...extractPlatforms(item?.platforms),
    ...extractPlatforms(item?.platform),
    ...extractPlatforms(item?.channelPlatform),
    ...extractPlatforms(item?.salesChannelPlatform),
    ...extractPlatforms(item?.channel?.platform),
    ...extractPlatforms(item?.channelName),
    ...extractPlatforms(item?.channelNames),
  ]);
};

const renderPlatformBadges = (item) => {
  const platforms = itemPlatforms(item);
  const displayPlatforms = platforms.length > 0 ? platforms : ['LOCAL'];
  return displayPlatforms.map((platform) => (
    <span
      key={platform}
      style={{
        ...platformBadgeBaseStyle,
        ...(PLATFORM_BADGE_STYLES[platform] ?? PLATFORM_BADGE_STYLES.LOCAL),
      }}
    >
      {platform === 'LOCAL' ? 'Ứng dụng' : PLATFORM_LABELS[platform] ?? platform}
    </span>
  ));
};

function AddProductModal({ open, products, totalItems = 0, existingVariantIds = [], loading = false, loadingMore = false, hasMore = false, onClose, onAdd, onLoadMore, onKeywordChange }) {
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState({});

  const results = products;

  const toggle = (item) => {
    const key = item.variantId ?? item.id;
    if (existingVariantIds.includes(key)) return;
    setSelected((prev) => {
      const next = { ...prev };
      if (next[key]) {
        delete next[key];
      } else {
        next[key] = item;
      }
      return next;
    });
  };

  const count = Object.keys(selected).length;
  const resetAndClose = () => {
    setKeyword(''); onKeywordChange?.('');
    setSelected({});
    onClose();
  };
  const confirmSelected = () => {
    onAdd(Object.values(selected));
    setKeyword(''); onKeywordChange?.('');
    setSelected({});
  };

  const handleScroll = (event) => {
    const el = event.currentTarget;
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 80 && hasMore && !loading && !loadingMore) {
      onLoadMore?.();
    }
  };

  if (!open) return null;
  return (
    <div onClick={(e) => e.target === e.currentTarget && resetAndClose()}
      style={{ position: 'fixed', inset: 0, zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(2px)' }}>
      <div style={{ width: '100%', maxWidth: 640, background: '#fff', borderRadius: 16, boxShadow: '0 24px 60px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', maxHeight: '85vh', overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div>
            <span style={{ fontSize: 15, fontWeight: 700, color: '#0f172a' }}>Chọn sản phẩm</span>
            <span style={{ marginLeft: 8, fontSize: 12, color: '#94a3b8' }}>{totalItems} sản phẩm</span>
          </div>
          <button onClick={resetAndClose} style={{ width: 30, height: 30, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}>
            <X size={18} />
          </button>
        </div>
        <div style={{ padding: '12px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
            <input autoFocus value={keyword} onChange={(e) => { setKeyword(e.target.value); onKeywordChange?.(e.target.value); }} placeholder="Tìm theo tên sản phẩm hoặc SKU..."
              style={{ width: '100%', padding: '8px 10px 8px 34px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, color: '#0f172a', outline: 'none', boxSizing: 'border-box' }} />
          </div>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }} onScroll={handleScroll}>
          {loading && <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '40px 0', color: '#94a3b8', fontSize: 13 }}><Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải...</div>}
          {!loading && results.length === 0 && <p style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>{keyword.trim() ? 'Không tìm thấy sản phẩm nào phù hợp.' : 'Không có sản phẩm nào.'}</p>}
          {!loading && results.map((item) => {
            const key = item.variantId ?? item.id;
            const sku = item.variantSku ?? item.sku;
            const variantName = item.variantName ?? item.name;
            const isExisting = existingVariantIds.includes(key);
            const isSelected = Boolean(selected[key]);
            return (
              <div key={key} onClick={() => toggle(item)}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 20px', cursor: isExisting ? 'not-allowed' : 'pointer', backgroundColor: isExisting ? '#f8fafc' : isSelected ? '#f0fdfa' : '#fff', borderBottom: '1px solid #f1f5f9', opacity: isExisting ? 0.55 : 1 }}>
                <input type="checkbox" checked={isSelected} disabled={isExisting} onChange={() => toggle(item)} onClick={(e) => e.stopPropagation()} style={{ width: 16, height: 16, accentColor: '#0d9488', flexShrink: 0 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>{item.productName}</span>
                    {variantName && <span style={{ fontSize: 11, color: '#475569', backgroundColor: '#f1f5f9', padding: '1px 6px', borderRadius: 4 }}>{variantName}</span>}
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 3, alignItems: 'center', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 11, fontFamily: 'monospace', backgroundColor: '#ccfbf1', color: '#0f766e', padding: '1px 6px', borderRadius: 4 }}>{sku}</span>
                    {renderPlatformBadges(item)}
                    <span style={{ fontSize: 11, backgroundColor: '#f0fdfa', color: '#0d9488', padding: '1px 6px', borderRadius: 4 }}>Tồn hệ thống: {formatNumber(item.quantityOnHand ?? item.availableQuantity ?? 0)}</span>
                    {isExisting && <span style={{ fontSize: 11, backgroundColor: '#fffbeb', color: '#d97706', padding: '1px 6px', borderRadius: 4 }}>Đã có</span>}
                  </div>
                </div>
              </div>
            );
          })}
          {loadingMore && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '16px 0', color: '#94a3b8', fontSize: 12 }}>
              <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải thêm...
            </div>
          )}
        </div>
        <div style={{ padding: '14px 20px', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>{count > 0 ? `Đã chọn ${count} sản phẩm` : 'Chưa chọn sản phẩm nào'}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={resetAndClose} style={{ padding: '7px 14px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>Hủy</button>
            <button onClick={confirmSelected} disabled={count === 0}
              style={{ padding: '7px 16px', borderRadius: 8, border: 'none', backgroundColor: count === 0 ? '#99f6e4' : '#0d9488', color: '#fff', fontSize: 13, fontWeight: 500, cursor: count === 0 ? 'not-allowed' : 'pointer' }}>
              Thêm ({count})
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function StocktakeCreatePage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const [defaultWarehouse, setDefaultWarehouse] = useState(null);
  const [warehouseId, setWarehouseId] = useState('');
  const [loadingWarehouse, setLoadingWarehouse] = useState(true);
  const [sessionCode, setSessionCode] = useState(makeSessionCode());
  const [scheduledDate, setScheduledDate] = useState(today);
  const [notes, setNotes] = useState('');
  const [warehouseItems, setWarehouseItems] = useState([]);
  const [items, setItems] = useState([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMoreItems, setHasMoreItems] = useState(false);
  const [totalItems, setTotalItems] = useState(0);
  const [searchKeyword, setSearchKeyword] = useState('');
  const debouncedKeyword = useDebounce(searchKeyword, 300);
  const [submitting, setSubmitting] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const itemsPageRef = useRef(0);

  useEffect(() => {
    let ignore = false;
    warehouseService.getMaster()
      .then((response) => {
        if (ignore) return;
        const warehouse = getResponseData(response);
        setDefaultWarehouse(warehouse || null);
        setWarehouseId(warehouse?.id ? String(warehouse.id) : '');
      })
      .catch(() => {
        if (!ignore) {
          setDefaultWarehouse(null);
          setWarehouseId('');
          toast.error('Không thể tải kho mặc định. Vui lòng cấu hình kho mặc định trước khi kiểm kho.');
        }
      })
      .finally(() => { if (!ignore) setLoadingWarehouse(false); });
    return () => { ignore = true; };
  }, []);

  const loadWarehouseItems = async (reset = false, keyword = debouncedKeyword) => {
    if (!warehouseId) return;
    const page = reset ? 0 : itemsPageRef.current;
    if (page > 0) {
      setLoadingMore(true);
    } else {
      setLoadingItems(true);
    }
    try {
      const response = await inventoryApi.getInventoryList(page, WAREHOUSE_PAGE_SIZE, 'updatedAt', 'desc', null, null, false, keyword ? { keyword } : {});
      const data = getResponseData(response);
      const list = Array.isArray(data) ? data : data.content ?? [];
      setWarehouseItems((current) => (page === 0 ? list : [...current, ...list]));
      const hasMore = Array.isArray(data)
        ? false
        : data.totalPages != null
          ? page + 1 < data.totalPages
          : list.length >= WAREHOUSE_PAGE_SIZE;
      setHasMoreItems(hasMore);
      setTotalItems(Array.isArray(data) ? list.length : data.totalElements ?? list.length);
      itemsPageRef.current = page + 1;
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể tải tồn kho của kho mặc định.');
    } finally {
      setLoadingMore(false);
      setLoadingItems(false);
    }
  };

  useEffect(() => {
    if (!addModalOpen) return;
    loadWarehouseItems(true, debouncedKeyword);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  const totals = useMemo(() => {
    const checkedItems = items.filter((item) => hasActualQuantity(item));
    const systemQty = items.reduce((sum, item) => sum + Number(item.systemQuantity || 0), 0);
    const actualQty = checkedItems.reduce((sum, item) => sum + Number(item.actualQuantity || 0), 0);
    const diffQty = checkedItems.reduce((sum, item) => sum + getItemDiff(item), 0);
    const diffValue = checkedItems.reduce((sum, item) => sum + getItemDiff(item) * getItemCost(item), 0);
    const matchedCount = checkedItems.filter((item) => getItemDiff(item) === 0).length;
    const surplusCount = checkedItems.filter((item) => getItemDiff(item) > 0).length;
    const shortageCount = checkedItems.filter((item) => getItemDiff(item) < 0).length;
    return { systemQty, actualQty, diffQty, diffValue, checkedCount: checkedItems.length, matchedCount, surplusCount, shortageCount };
  }, [items]);

  const hasUnsavedChanges = Boolean(notes.trim() || items.length > 0);
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  const progressPct = items.length > 0 ? Math.round((totals.checkedCount / items.length) * 100) : 0;

  const fillActualWithSystem = () => {
    if (!items.length) return;
    setItems((current) => current.map((item) => ({ ...item, actualQuantity: String(item.systemQuantity) })));
  };

  const addItems = (newItems) => {
    setItems((current) => {
      const ids = new Set(current.map((item) => item.variantId));
      const added = newItems
        .map(toStocktakeItem)
        .filter((item) => !ids.has(item.variantId));
      return added.length > 0 ? [...current, ...added] : current;
    });
  };

  const openAddModal = async () => {
    if (!warehouseId) {
      toast.error('Chưa có kho mặc định để kiểm kho.');
      return;
    }
    setAddModalOpen(true);
    itemsPageRef.current = 0;
    setWarehouseItems([]);
    setHasMoreItems(false);
    await loadWarehouseItems(true, '');
  };

  const updateActual = (variantId, value) => {
    setItems((current) => current.map((item) => item.variantId === variantId ? { ...item, actualQuantity: value } : item));
  };

  const removeItem = (variantId) => setItems((current) => current.filter((item) => item.variantId !== variantId));

  const buildPayload = (fillMissingWithSystem = false) => ({
    warehouseId,
    sessionCode,
    scheduledDate,
    notes: notes || null,
    items: items.map((item) => ({
      variantId: item.variantId,
      systemQuantity: Number(item.systemQuantity || 0),
      actualQuantity: item.actualQuantity === '' || item.actualQuantity === null || item.actualQuantity === undefined
        ? (fillMissingWithSystem ? Number(item.systemQuantity || 0) : null)
        : Number(item.actualQuantity),
      notes: item.notes || null,
    })),
  });

  const validateBase = () => {
    if (!warehouseId) {
      toast.error('Chưa có kho mặc định để tạo phiếu kiểm kho.');
      return false;
    }
    if (!sessionCode.trim()) {
      toast.error('Vui lòng nhập mã phiếu kiểm.');
      return false;
    }
    if (!items.length) {
      toast.error('Vui lòng thêm ít nhất một sản phẩm kiểm.');
      return false;
    }
    return true;
  };

  const submit = async (complete) => {
    if (!validateBase()) return;
    if (complete) {
      const missing = items.find((item) => !hasActualQuantity(item));
      if (missing) {
        toast.error('Cần nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.');
        return;
      }
    }
    const invalid = items.find((item) => hasActualQuantity(item) && Number(item.actualQuantity) < 0);
    if (invalid) {
      toast.error(`Tồn thực tế của "${invalid.productName}" không được âm.`);
      return;
    }
    const summary = complete ? getDiffSummary(items) : null;
    if (complete) {
      const ok = await confirmStocktakeComplete({ confirm, summary });
      if (!ok) return;
    }
    setSubmitting(true);
    try {
      const response = await stocktakeService.create(buildPayload(false), complete);
      toast.success(complete ? 'Hoàn thành phiếu kiểm kho thành công.' : 'Lưu nháp phiếu kiểm kho thành công.');
      if (complete && summary.hasDiff) {
        const shouldSync = await confirmSyncMarketplaceNow({ confirm });
        if (shouldSync) {
          const created = getResponseData(response);
          try {
            await stocktakeService.syncStocktakeMarketplaceInventory(created.id);
            toast.success('Đã đồng bộ tồn kho lên các sàn liên quan.');
          } catch (syncError) {
            toast.error(syncError?.response?.data?.message || 'Hoàn thành kiểm kho nhưng đồng bộ sàn thất bại.');
          }
        }
      }
      runWithoutGuard(() => navigate(ROUTES.STOCKTAKES));
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể tạo phiếu kiểm kho.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={`${styles.page} product-workspace`}>
      <div className={styles.pageHeader}>
        <button type="button" className={styles.backBtn} onClick={() => navigate(ROUTES.STOCKTAKES)}>
          <ArrowLeft size={15} /> Quay lại
        </button>
        <div className={styles.headerIcon} style={{ background: '#f0fdfa' }}>
          <ClipboardList size={18} color="#0d9488" />
        </div>
        <div>
          <h1 className={styles.headerTitle}>Tạo phiếu kiểm kho</h1>
          <p className={styles.headerSubtitle}>Kiểm kê tồn thực tế trên kho mặc định duy nhất của hệ thống</p>
        </div>
      </div>

      <div className={styles.twoCol}>
        <div className={styles.leftCol}>
          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionHeaderIcon} style={{ background: '#f0fdfa' }}>
                <ClipboardList size={16} color="#0d9488" />
              </div>
              <div>
                <div className={styles.sectionTitle}>Thông tin phiếu kiểm</div>
                <div className={styles.sectionSubtitle}>Kho kiểm được khóa theo kho mặc định để tránh lệch tồn giữa các sàn.</div>
              </div>
            </div>

            <div className={styles.stocktakeInfoGrid}>
              <label>
                <span className={styles.fieldLabel}>Mã phiếu</span>
                <input value={sessionCode} onChange={(event) => setSessionCode(event.target.value)} className={styles.fieldInput} />
              </label>
              <label>
                <span className={styles.fieldLabel}>Ngày kiểm kho <span>*</span></span>
                <input type="date" value={scheduledDate} onChange={(event) => setScheduledDate(event.target.value)} className={styles.fieldInput} />
              </label>
              <label>
                <span className={styles.fieldLabel}>Giờ kiểm</span>
                <input readOnly value={new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} className={`${styles.fieldInput} ${styles.readonlyInput}`} />
              </label>
            </div>

            {!warehouseId && !loadingWarehouse && (
              <div className={styles.warningBanner} style={{ marginTop: 14 }}>
                <AlertCircle size={16} />
                Chưa có kho mặc định. Vui lòng cấu hình kho mặc định trước khi tạo phiếu kiểm kho.
              </div>
            )}

            <div className={styles.summaryGrid}>
              {[
                { label: 'Tổng SL hệ thống', value: formatNumber(totals.systemQty) },
                { label: 'Tổng SL thực tế', value: formatNumber(totals.actualQty) },
                { label: 'Tổng SL chênh lệch', value: `${totals.diffQty > 0 ? '+' : ''}${formatNumber(totals.diffQty)}`, color: totals.diffQty < 0 ? '#dc2626' : totals.diffQty > 0 ? '#0d9488' : '#94a3b8' },
                { label: 'Giá trị chênh lệch', value: formatVND(totals.diffValue), color: totals.diffValue < 0 ? '#dc2626' : totals.diffValue > 0 ? '#0d9488' : '#94a3b8' },
              ].map(({ label, value, color }) => (
                <div key={label} className={styles.summaryStat}>
                  <div className={styles.summaryStatLabel}>{label}</div>
                  <div className={styles.summaryStatValue} style={{ color: color || '#0f172a' }}>{value}</div>
                </div>
              ))}
            </div>

            {items.length > 0 && (
              <div className={styles.progressSection}>
                <div className={styles.progressHeader}>
                  <span className={styles.progressLabel}>Tiến độ kiểm</span>
                  <span className={styles.progressValue}>{progressPct}% · {formatNumber(totals.checkedCount)}/{formatNumber(items.length)} đã kiểm</span>
                </div>
                <div className={styles.progressBar}><div className={styles.progressFill} style={{ width: `${progressPct}%` }} /></div>
                <div className={styles.progressStats}>
                  <span className={styles.progressStat} style={{ color: '#0d9488' }}>Khớp: {formatNumber(totals.matchedCount)}</span>
                  <span className={styles.progressStat} style={{ color: '#0d9488' }}>Thừa: {formatNumber(totals.surplusCount)}</span>
                  <span className={styles.progressStat} style={{ color: '#dc2626' }}>Thiếu: {formatNumber(totals.shortageCount)}</span>
                </div>
              </div>
            )}

            <label style={{ display: 'block', marginTop: 14 }}>
              <span className={styles.fieldLabel}>Ghi chú</span>
              <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={2} placeholder="Ghi chú về phiếu kiểm kho..." className={styles.fieldTextarea} />
            </label>

            <div className={styles.formActionsRight}>
              <button type="button" className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={() => submit(false)} disabled={submitting || loadingWarehouse}>
                <Save className={styles.primaryIcon} />{submitting ? 'Đang xử lý...' : 'Lưu nháp'}
              </button>
              <button type="button" className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={() => submit(true)} disabled={submitting || loadingWarehouse}>
                <CheckCircle2 className={styles.tealIcon} />{submitting ? 'Đang xử lý...' : 'Hoàn thành kiểm kho'}
              </button>
            </div>
          </div>

          <div className={`${styles.card} ${styles.tableCard}`}>
            <div className={styles.tableCardHeader}>
              <div>
                <div className={styles.tableCardTitle}>Danh sách sản phẩm kiểm</div>
                <div className={styles.tableCardSubtitle}>
                  {formatNumber(totals.checkedCount)}/{formatNumber(items.length)} đã kiểm
                  · {formatNumber(totals.matchedCount)} khớp · {formatNumber(totals.surplusCount)} thừa · {formatNumber(totals.shortageCount)} thiếu
                </div>
              </div>
              <div className={styles.tableCardActions}>
                {items.length > 0 && (
                  <button type="button" className={`${styles.actionBtn} ${styles.secondaryBtn}`} onClick={fillActualWithSystem} disabled={!warehouseId || loadingItems}>
                    Điền theo HT
                  </button>
                )}
                <button type="button" className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={openAddModal} disabled={!warehouseId || loadingItems}>
                  <Plus className={styles.tealIcon} />Thêm sản phẩm
                </button>
              </div>
            </div>

            {items.length === 0 ? (
              <div className={styles.emptyState}>
                <div className={styles.emptyIcon}><PackageCheck size={24} /></div>
                <p className={styles.emptyTitle}>Chưa có sản phẩm nào</p>
                <p className={styles.emptySubtitle}>
                  {warehouseId ? 'Nhấn “Thêm sản phẩm” để bắt đầu kiểm kê kho mặc định.' : 'Cần có kho mặc định trước khi thêm sản phẩm.'}
                </p>
              </div>
            ) : (
              <div className={styles.tableScrollX}>
                <table className={styles.table} style={{ minWidth: 900 }}>
                  <thead>
                    <tr>
                      {['STT', 'Mã SP', 'Tên sản phẩm', 'ĐVT', 'Tồn kho (HT)', 'Tồn kho thực tế', 'SL lệch', 'Giá trị lệch', ''].map((header, index) => (
                        <th key={header} className={index >= 4 ? styles.thRight : ''}>{header}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, index) => {
                      const checked = hasActualQuantity(item);
                      const diff = checked ? getItemDiff(item) : 0;
                      const diffValue = diff * getItemCost(item);
                      const rowClass = checked && diff < 0 ? styles.shortageRow : checked && diff > 0 ? styles.surplusRow : '';
                      return (
                        <tr key={item.variantId} className={rowClass}>
                          <td className={styles.mutedCell}>{index + 1}</td>
                          <td><span className={styles.skuTag} style={{ background: '#ccfbf1', color: '#0d9488' }}>{item.variantSku}</span></td>
                          <td className={styles.productNameCell}>{item.productName}</td>
                          <td className={styles.mutedCell}>Cái</td>
                          <td className={styles.tdRight}>{formatNumber(item.systemQuantity)}</td>
                          <td>
                            <input
                              type="number"
                              min="0"
                              value={item.actualQuantity}
                              onChange={(event) => updateActual(item.variantId, event.target.value)}
                              className={styles.stocktakeQuantityInput}
                              aria-label={`Tồn kho thực tế của ${item.productName}`}
                            />
                          </td>
                          <td className={styles.tdRight}><DiffValue diff={diff} checked={checked} /></td>
                          <td className={styles.tdRight} style={{ fontWeight: 700, color: !checked || diffValue === 0 ? '#94a3b8' : diffValue < 0 ? '#dc2626' : '#0d9488' }}>
                            {checked ? formatVND(diffValue) : '—'}
                          </td>
                          <td>
                            <button type="button" className={styles.removeBtn} onClick={() => removeItem(item.variantId)} aria-label={`Xóa ${item.productName}`}>
                              <X size={13} />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        <div className={styles.rightCol}>
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Kho đang kiểm</div>
            <div className={styles.sidebarWarehouseBox}>
              {loadingWarehouse
                ? <Loader2 size={18} className={styles.spinIcon} />
                : <Warehouse size={18} />}
              <div>
                <strong>
                  {loadingWarehouse
                    ? 'Đang tải kho mặc định...'
                    : defaultWarehouse?.name || 'Chưa cấu hình kho mặc định'}
                </strong>
                <span>
                  {defaultWarehouse?.address || 'Hệ thống chỉ dùng kho mặc định cho phiếu kiểm này.'}
                </span>
              </div>
              <span className={warehouseId ? styles.lockedPill : styles.warningPill} style={{ marginLeft: 'auto', flexShrink: 0 }}>
                <LockKeyhole size={13} />
                {warehouseId ? 'Đã khóa' : 'Cần cấu hình'}
              </span>
            </div>
          </div>

          {items.length > 0 && (
            <div className={`${styles.card} ${styles.sidebarCard}`}>
              <div className={styles.sidebarCardTitle}>Trạng thái kiểm kê</div>
              <div className={styles.statusBreakdown}>
                {[
                  { label: 'Đã kiểm', value: totals.checkedCount, color: '#0d9488', bg: '#f0fdfa' },
                  { label: 'Khớp', value: totals.matchedCount, color: '#0d9488', bg: '#f0fdfa' },
                  { label: 'Thừa', value: totals.surplusCount, color: '#0d9488', bg: '#f0fdfa' },
                  { label: 'Thiếu', value: totals.shortageCount, color: '#dc2626', bg: '#fff5f5' },
                  { label: 'Chưa kiểm', value: items.length - totals.checkedCount, color: '#94a3b8', bg: '#f8fafc' },
                ].map(({ label, value, color, bg }) => (
                  <div key={label} className={styles.statusRow} style={{ background: bg }}>
                    <span className={styles.statusLabel} style={{ color }}>{label}</span>
                    <span className={styles.statusValue} style={{ color }}>{formatNumber(value)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className={`${styles.card} ${styles.noteCard}`} style={{ background: '#f0fdfa', border: '1px solid #ccfbf1' }}>
            <div className={styles.noteHeader}>
              <AlertCircle size={14} color="#0d9488" />
              <span className={styles.noteTitle} style={{ color: '#0f766e' }}>Lưu ý khi kiểm kho</span>
            </div>
            <ul className={styles.noteList}>
              {[
                'Phiếu kiểm kho chỉ sử dụng kho mặc định để tránh lệch tồn giữa các sàn.',
                'Nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.',
                'Dùng “Điền theo hệ thống” để điền nhanh số lượng ban đầu.',
                'Chênh lệch sẽ được ghi nhận để điều chỉnh tồn kho.',
              ].map((note) => (
                <li key={note} className={styles.noteItem} style={{ color: '#0f766e' }}>{note}</li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      <AddProductModal
        open={addModalOpen}
        products={warehouseItems}
        totalItems={totalItems}
        existingVariantIds={items.map((item) => item.variantId)}
        loading={loadingItems}
        loadingMore={loadingMore}
        hasMore={hasMoreItems}
        onLoadMore={() => loadWarehouseItems(false)}
        onKeywordChange={setSearchKeyword}
        onClose={() => setAddModalOpen(false)}
        onAdd={addItems}
      />
      {ConfirmDialog}
    </div>
  );
}
