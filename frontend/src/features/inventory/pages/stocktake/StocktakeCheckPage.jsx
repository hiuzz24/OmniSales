import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ClipboardList,
  Loader2,
  LockKeyhole,
  Package,
  PackageCheck,
  Plus,
  RefreshCw,
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
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import useDebounce from '../../../../shared/hooks/useDebounce';
import {
  formatNumber,
  formatVND,
  getResponseData,
} from '../components/inventoryDocumentListUtils';
import {
  confirmStocktakeComplete,
  confirmSyncMarketplaceNow,
  getDiffSummary,
} from './stocktakeCompletion';
import styles from '../CreatePage.module.css';

const hasActualQuantity = (item) => item.actualQuantity !== '' && item.actualQuantity !== null && item.actualQuantity !== undefined;
const WAREHOUSE_PAGE_SIZE = 50;
const getItemDiff = (item) => Number(item.actualQuantity || 0) - Number(item.systemQuantity || 0);
const getItemCost = (item) => Number(item.averageCost ?? item.costPrice ?? item.unitCost ?? item.unitPrice ?? item.price ?? 0);

const toStocktakeItem = (item) => ({
  variantId: item.variantId,
  variantSku: item.variantSku,
  variantName: item.variantName,
  productName: item.productName,
  systemQuantity: Number(item.quantityOnHand ?? item.availableQuantity ?? 0),
  actualQuantity: '',
  costPrice: getItemCost(item),
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

  // The backend already filters by keyword; filtering again here can hide valid
  // results when display fields differ from the backend's searchable fields.
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
    setKeyword('');
    setSelected({});
    onKeywordChange?.('');
    onClose();
  };
  const confirmSelected = () => {
    onAdd(Object.values(selected));
    setKeyword('');
    setSelected({});
    onKeywordChange?.('');
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

export default function StocktakeCheckPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const [stocktake, setStocktake] = useState(null);
  const [items, setItems] = useState([]);
  const [notes, setNotes] = useState('');
  const [warehouseItems, setWarehouseItems] = useState([]);
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingItems, setLoadingItems] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMoreItems, setHasMoreItems] = useState(false);
  const [totalItems, setTotalItems] = useState(0);
  const [searchKeyword, setSearchKeyword] = useState('');
  const debouncedKeyword = useDebounce(searchKeyword, 300);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const itemsPageRef = useRef(0);

  const markDirty = () => setDirty(true);

  useEffect(() => {
    let ignore = false;
    stocktakeService.getById(id)
      .then((response) => {
        if (ignore) return;
        const data = getResponseData(response);
        setStocktake(data);
        setItems(data?.items ?? []);
        setNotes(data?.notes ?? '');
      })
      .catch(() => {
        if (!ignore) toast.error('Không thể tải phiếu kiểm kho.');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => { ignore = true; };
  }, [id]);

  useEffect(() => {
    if (!stocktake) return;
    if (stocktake.status === 'COMPLETED' || stocktake.status === 'CANCELLED') {
      navigate(ROUTES.STOCKTAKE_DETAIL.replace(':id', stocktake.id), { replace: true });
    }
  }, [stocktake, navigate, id]);

  const { runWithoutGuard } = useUnsavedChangesGuard({ when: dirty, confirm });
  const goBack = () => {
    navigate(ROUTES.STOCKTAKES);
  };

  const updateActual = (variantId, value) => {
    setItems((current) => current.map((item) => (item.variantId === variantId ? { ...item, actualQuantity: value } : item)));
    markDirty();
  };

  const updateNotes = (variantId, value) => {
    setItems((current) => current.map((item) => (item.variantId === variantId ? { ...item, notes: value } : item)));
    markDirty();
  };

  const removeItem = (variantId) => {
    setItems((current) => current.filter((item) => item.variantId !== variantId));
    markDirty();
  };

  const addItems = (newItems) => {
    setItems((current) => {
      const ids = new Set(current.map((item) => item.variantId));
      const added = newItems
        .map(toStocktakeItem)
        .filter((item) => !ids.has(item.variantId));
      return added.length > 0 ? [...current, ...added] : current;
    });
    markDirty();
  };

  const fillActualWithSystem = () => {
    setItems((prev) => prev.map((item) => ({ ...item, actualQuantity: String(item.systemQuantity) })));
    markDirty();
  };

  const loadWarehouseItems = async (reset = false, keyword = debouncedKeyword) => {
    if (!stocktake?.warehouseId) return;
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
      toast.error(error?.response?.data?.message || 'Không thể tải sản phẩm của kho.');
    } finally {
      setLoadingMore(false);
      setLoadingItems(false);
    }
  };

  useEffect(() => {
    if (addModalOpen) loadWarehouseItems(true, debouncedKeyword);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  const closeAddModal = () => {
    setSearchKeyword('');
    setWarehouseItems([]);
    setTotalItems(0);
    itemsPageRef.current = 0;
    setAddModalOpen(false);
  };

  const openAddModal = async () => {
    if (!stocktake?.warehouseId) return;
    setAddModalOpen(true);
    setSearchKeyword('');
    itemsPageRef.current = 0;
    setHasMoreItems(false);
    await loadWarehouseItems(true, '');
  };

  const buildPayload = () => ({
    warehouseId: stocktake.warehouseId,
    sessionCode: stocktake.sessionCode,
    scheduledDate: stocktake.scheduledDate,
    notes: notes || null,
    items: items.map((item) => ({
      variantId: item.variantId,
      systemQuantity: Number(item.systemQuantity || 0),
      actualQuantity: hasActualQuantity(item) ? Number(item.actualQuantity) : null,
      notes: item.notes || null,
    })),
  });

  const validate = () => {
    if (!items.length) {
      toast.error('Vui lòng thêm ít nhất một sản phẩm kiểm.');
      return false;
    }
    const invalid = items.find((item) => hasActualQuantity(item) && Number(item.actualQuantity) < 0);
    if (invalid) {
      toast.error(`Tồn thực tế của "${invalid.productName}" không được âm.`);
      return false;
    }
    return true;
  };

  const saveProgress = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      await stocktakeService.update(stocktake.id, buildPayload());
      toast.success('Đã lưu tiến độ kiểm kho.');
      setDirty(false);
      runWithoutGuard(() => navigate(ROUTES.STOCKTAKES));
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể lưu tiến độ kiểm kho.');
    } finally {
      setSaving(false);
    }
  };

  const complete = async () => {
    if (!validate()) return;
    const missing = items.find((item) => !hasActualQuantity(item));
    if (missing) {
      toast.error('Cần nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.');
      return;
    }
    const summary = getDiffSummary(items);
    const ok = await confirmStocktakeComplete({ confirm, summary });
    if (!ok) return;

    setSaving(true);
    try {
      await stocktakeService.update(stocktake.id, buildPayload());
      await stocktakeService.changeStatus(stocktake.id, 'COMPLETED');
      toast.success('Hoàn thành phiếu kiểm kho thành công.');
      setDirty(false);
      if (summary.hasDiff) {
        const shouldSync = await confirmSyncMarketplaceNow({ confirm });
        if (shouldSync) {
          try {
            await stocktakeService.syncStocktakeMarketplaceInventory(stocktake.id);
            toast.success('Đã đồng bộ tồn kho lên các sàn liên quan.');
          } catch (syncError) {
            toast.error(syncError?.response?.data?.message || 'Hoàn thành kiểm kho nhưng đồng bộ sàn thất bại.');
          }
        }
      }
      runWithoutGuard(() => navigate(ROUTES.STOCKTAKE_DETAIL.replace(':id', stocktake.id), { replace: true }));
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể hoàn thành phiếu kiểm kho.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className={styles.loading}>
        <Loader2 size={20} className="spin-icon" /> Đang tải phiếu kiểm kho...
      </div>
    );
  }

  if (!stocktake) {
    return (
      <div className={styles.emptyState}>
        <AlertCircle size={28} />
        <p>Không tìm thấy phiếu kiểm kho.</p>
        <button type="button" onClick={() => navigate(ROUTES.STOCKTAKES)} className={`${styles.actionBtn} ${styles.backBtn}`}>
          <ArrowLeft size={14} /> Quay lại danh sách
        </button>
      </div>
    );
  }

  const checkedItems = items.filter(hasActualQuantity);
  const totals = {
    systemQty: items.reduce((sum, item) => sum + Number(item.systemQuantity || 0), 0),
    actualQty: checkedItems.reduce((sum, item) => sum + Number(item.actualQuantity || 0), 0),
    diffQty: checkedItems.reduce((sum, item) => sum + getItemDiff(item), 0),
    diffValue: checkedItems.reduce((sum, item) => sum + getItemDiff(item) * getItemCost(item), 0),
    checkedCount: checkedItems.length,
    matchedCount: checkedItems.filter((item) => getItemDiff(item) === 0).length,
    surplusCount: checkedItems.filter((item) => getItemDiff(item) > 0).length,
    shortageCount: checkedItems.filter((item) => getItemDiff(item) < 0).length,
  };
  const progressPct = items.length > 0 ? Math.round((totals.checkedCount / items.length) * 100) : 0;

  return (
    <div className={styles.page}>
      <header className={styles.pageHeader}>
        <button type="button" className={styles.backBtn} onClick={goBack}>
          <ArrowLeft size={15} /> Quay lại
        </button>
        <div className={styles.headerIcon} style={{ background: '#f0fdfa' }}>
          <ClipboardList size={18} color="#0d9488" />
        </div>
        <div>
          <h1 className={styles.headerTitle}>Kiểm tra phiếu kiểm kho</h1>
          <p className={styles.headerSubtitle}>
            {stocktake.warehouseName} · {stocktake.sessionCode}
          </p>
          {dirty && (
            <div className={styles.warningPill}>
              <AlertCircle size={12} /> Có thay đổi chưa lưu
            </div>
          )}
        </div>
      </header>

      <div className={styles.twoCol}>
        <section className={styles.leftCol}>
          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionHeaderIcon}>
                <ClipboardList size={15} color="#0d9488" />
              </div>
              <div>
                <div className={styles.sectionTitle}>Thông tin phiếu kiểm</div>
                <div className={styles.sectionSubtitle}>Thời điểm kiểm, kho kiểm</div>
              </div>
            </div>
            <div className={styles.stocktakeInfoGrid}>
              <label>
                <span className={styles.fieldLabel}>Mã phiếu</span>
                <input
                  readOnly
                  value={stocktake.sessionCode}
                  className={`${styles.fieldInput} ${styles.readonlyInput}`}
                  onClick={(event) => event.target.select()}
                />
              </label>
              <label>
                <span className={styles.fieldLabel}>Kho kiểm</span>
                <input
                  readOnly
                  value={stocktake.warehouseName ?? ''}
                  className={`${styles.fieldInput} ${styles.readonlyInput}`}
                />
              </label>
              <label>
                <span className={styles.fieldLabel}>Ngày kiểm kho</span>
                <input
                  readOnly
                  value={stocktake.scheduledDate ?? ''}
                  className={`${styles.fieldInput} ${styles.readonlyInput}`}
                />
              </label>
            </div>
          </div>

          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionHeaderIcon}>
                <PackageCheck size={15} color="#0d9488" />
              </div>
              <div>
                <div className={styles.sectionTitle}>Tổng quan kiểm kê</div>
                <div className={styles.sectionSubtitle} />
              </div>
            </div>

            <div className={styles.summaryGrid}>
              <div className={styles.summaryStat} style={{ background: '#eff6ff', borderColor: '#bfdbfe' }}>
                <div className={styles.summaryStatLabel}>Đã kiểm</div>
                <div className={styles.summaryStatValue} style={{ color: '#2563eb' }}>
                  {totals.checkedCount} / {items.length}
                </div>
              </div>
              <div className={styles.summaryStat} style={{ background: '#f8fafc', borderColor: '#e2e8f0' }}>
                <div className={styles.summaryStatLabel}>Tồn hệ thống</div>
                <div className={styles.summaryStatValue}>{formatNumber(totals.systemQty)}</div>
              </div>
              <div className={styles.summaryStat} style={{ background: '#f8fafc', borderColor: '#e2e8f0' }}>
                <div className={styles.summaryStatLabel}>Tồn thực tế</div>
                <div className={styles.summaryStatValue}>{formatNumber(totals.actualQty)}</div>
              </div>
              <div className={styles.summaryStat} style={{ background: '#f8fafc', borderColor: '#e2e8f0' }}>
                <div className={styles.summaryStatLabel}>Chênh lệch</div>
                <div className={styles.summaryStatValue} style={{ color: totals.diffQty === 0 ? '#334155' : totals.diffQty > 0 ? '#0d9488' : '#dc2626' }}>
                  {totals.diffQty > 0 ? `+${formatNumber(totals.diffQty)}` : formatNumber(totals.diffQty)}
                </div>
              </div>
              <div className={styles.summaryStat} style={{ background: '#ecfdf5', borderColor: '#a7f3d0' }}>
                <div className={styles.summaryStatLabel}>Khớp</div>
                <div className={styles.summaryStatValue} style={{ color: '#059669' }}>{totals.matchedCount}</div>
              </div>
              <div className={styles.summaryStat} style={{ background: '#f0fdfa', borderColor: '#99f6e4' }}>
                <div className={styles.summaryStatLabel}>Thừa (SP)</div>
                <div className={styles.summaryStatValue} style={{ color: '#0d9488' }}>{totals.surplusCount}</div>
              </div>
              <div className={styles.summaryStat} style={{ background: '#fff1f2', borderColor: '#fecdd3' }}>
                <div className={styles.summaryStatLabel}>Thiếu (SP)</div>
                <div className={styles.summaryStatValue} style={{ color: '#e11d48' }}>{totals.shortageCount}</div>
              </div>
            </div>

            <div className={styles.progressSection}>
              <div className={styles.progressHeader}>
                <span className={styles.progressLabel}>Tiến độ kiểm kê</span>
                <span className={styles.progressValue}>{progressPct}%</span>
              </div>
              <div className={styles.progressBar}>
                <div className={styles.progressFill} style={{ width: `${progressPct}%` }} />
              </div>
              <div className={styles.progressStats}>
                <span>{totals.checkedCount}/{items.length} SP</span>
                <span>Khớp: {totals.matchedCount}</span>
                <span>Thừa: {totals.surplusCount}</span>
                <span>Thiếu: {totals.shortageCount}</span>
              </div>
            </div>
          </div>

          <div className={styles.tableCard}>
            <div className={styles.tableCardHeader}>
              <div>
                <div className={styles.tableCardTitle}>Danh sách sản phẩm kiểm</div>
                <div className={styles.tableCardSubtitle}>Nhập số lượng tồn thực tế sau khi kiểm đếm</div>
              </div>
              <div className={styles.tableCardActions}>
                <button
                  type="button"
                  onClick={fillActualWithSystem}
                  className={`${styles.actionBtn} ${styles.secondaryBtn}`}
                >
                  <RefreshCw size={14} className={styles.secondaryIcon} /> Điền theo HT
                </button>
                <button type="button" onClick={openAddModal} className={`${styles.actionBtn} ${styles.tealBtn}`}>
                  <Plus className={styles.tealIcon} /> Thêm sản phẩm
                </button>
              </div>
            </div>
            {items.length === 0 ? (
              <div className={styles.emptyState}>
                <Package size={44} />
                <p className={styles.emptyTitle}>Chưa có sản phẩm nào</p>
                <p className={styles.emptySubtitle}>Bấm "Thêm sản phẩm" để bắt đầu thêm SKU cần kiểm</p>
              </div>
            ) : (
              <div className={styles.tableScrollX}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>STT</th>
                      <th>SKU</th>
                      <th>Biến thể</th>
                      <th className={styles.thRight}>ĐVT</th>
                      <th className={styles.thRight}>Tồn HT</th>
                      <th className={styles.thRight}>Tồn thực tế</th>
                      <th className={styles.thRight}>Chênh lệch</th>
                      <th className={styles.thRight}>Giá vốn</th>
                      <th className={styles.thRight}>Giá trị lệch</th>
                      <th>Ghi chú</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, index) => {
                      const diff = hasActualQuantity(item) ? getItemDiff(item) : 0;
                      const diffValue = hasActualQuantity(item) ? (getItemDiff(item) * getItemCost(item)) : 0;
                      const isShortage = hasActualQuantity(item) && getItemDiff(item) < 0;
                      const isSurplus = hasActualQuantity(item) && getItemDiff(item) > 0;
                      return (
                        <tr key={item.variantId} className={isShortage ? styles.shortageRow : isSurplus ? styles.surplusRow : ''}>
                          <td className={styles.mutedCell}>{index + 1}</td>
                          <td className={styles.skuTag}>{String(item.variantSku || '').toUpperCase()}</td>
                          <td>
                            <div className={styles.productNameCell}>
                              <div className={styles.productNameText}>{item.productName}</div>
                            </div>
                          </td>
                          <td className={`${styles.tdRight} ${styles.mutedCell}`}>{item.unit || 'Cái'}</td>
                          <td className={styles.tdRight}>
                            <div className={styles.systemQty}>
                              <span>{formatNumber(item.systemQuantity)}</span>
                              <button
                                type="button"
                                className={styles.syncNowBtn}
                                onClick={() => updateActual(item.variantId, String(item.systemQuantity))}
                                title="Sao chép số lượng hệ thống"
                              >
                                <RefreshCw size={11} />
                              </button>
                            </div>
                          </td>
                          <td className={styles.tdRight}>
                            <input
                              type="number"
                              min="0"
                              value={item.actualQuantity ?? ''}
                              onChange={(event) => updateActual(item.variantId, event.target.value)}
                              className={`${styles.stocktakeQuantityInput} ${isShortage ? styles.shortageInput : ''}`}
                              placeholder="—"
                            />
                          </td>
                          <td className={styles.tdRight}>
                            <DiffValue diff={diff} checked={hasActualQuantity(item)} />
                            {!hasActualQuantity(item) && <div className={styles.fieldHint}>Chưa kiểm</div>}
                          </td>
                          <td className={`${styles.tdRight} ${styles.mutedCell}`}>{formatVND(item.costPrice)}</td>
                          <td className={styles.tdRight}>
                            <span className={diffValue === 0 ? styles.mutedDash : diffValue > 0 ? styles.diffPositive : styles.diffNegative}>
                              {diffValue === 0 ? '—' : diffValue > 0 ? `+${formatVND(diffValue)}` : `-${formatVND(Math.abs(diffValue))}`}
                            </span>
                          </td>
                          <td className={styles.notesCell}>
                            <input
                              type="text"
                              value={item.notes ?? ''}
                              onChange={(event) => updateNotes(item.variantId, event.target.value)}
                              placeholder="Ghi chú"
                              className={styles.stocktakeNotesInput}
                            />
                          </td>
                          <td>
                            <button
                              type="button"
                              className={styles.removeBtn}
                              onClick={() => removeItem(item.variantId)}
                              aria-label={`Xóa ${item.productName}`}
                            >
                              <X size={15} />
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
        </section>

        <aside className={styles.rightCol}>
          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sidebarCardTitle}>
              <Warehouse size={15} color="#0d9488" /> Kho đang kiểm
            </div>
            <div className={styles.sidebarWarehouseBox}>
              <div className={styles.sidebarWarehouseName}>{stocktake.warehouseName}</div>
              <div className={styles.sidebarWarehouseAddress}>{stocktake.warehouseAddress || '—'}</div>
            </div>
            <div className={styles.lockedPill}>
              <LockKeyhole size={12} /> Khóa tại kho: {stocktake.warehouseName}
            </div>
          </div>

          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sidebarCardTitle}>Trạng thái kiểm kê</div>
            <div className={styles.statusBreakdown}>
              <div className={styles.statusRow}>
                <span className={styles.statusLabel}>Đã kiểm</span>
                <span className={styles.statusValue}>{totals.checkedCount}/{items.length} SP</span>
              </div>
              <div className={styles.statusRow}>
                <span className={styles.statusLabel}>Khớp</span>
                <span className={styles.statusValue}>{totals.matchedCount}</span>
              </div>
              <div className={styles.statusRow}>
                <span className={styles.statusLabel}>Thừa</span>
                <span className={styles.statusValue}>{totals.surplusCount}</span>
              </div>
              <div className={styles.statusRow}>
                <span className={styles.statusLabel}>Thiếu</span>
                <span className={styles.statusValue}>{totals.shortageCount}</span>
              </div>
              <div className={styles.statusRow} style={{ border: 'none' }}>
                <span className={styles.statusLabel}>Chênh lệch SL</span>
                <span className={styles.statusValue} style={{ color: totals.diffQty === 0 ? '#334155' : totals.diffQty > 0 ? '#0d9488' : '#dc2626' }}>
                  {totals.diffQty > 0 ? `+${formatNumber(totals.diffQty)}` : formatNumber(totals.diffQty)}
                </span>
              </div>
            </div>
          </div>

          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.noteHeader}>
              <div className={styles.sidebarCardTitle}>Ghi chú phiếu</div>
              <button type="button" className={styles.clearNotesBtn} onClick={() => { setNotes(''); markDirty(); }}>Xóa</button>
            </div>
            <textarea
              value={notes}
              onChange={(event) => { setNotes(event.target.value); markDirty(); }}
              placeholder="Thêm ghi chú cho phiếu kiểm..."
              className={styles.fieldTextarea}
            />
          </div>
        </aside>
      </div>

      <div className={styles.formActionsRight}>
        <button
          type="button"
          onClick={saveProgress}
          disabled={saving || loadingItems}
          className={`${styles.actionBtn} ${styles.primaryBtn}`}
        >
          <Save className={styles.primaryIcon} /> Lưu thay đổi
        </button>
        <button
          type="button"
          onClick={complete}
          disabled={saving || loadingItems}
          className={`${styles.actionBtn} ${styles.tealBtn}`}
        >
          <CheckCircle2 className={styles.tealIcon} /> Hoàn thành kiểm kho
        </button>
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
        onClose={closeAddModal}
        onAdd={addItems}
      />
      {ConfirmDialog}
    </div>
  );
}
