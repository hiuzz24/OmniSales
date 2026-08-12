import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import * as XLSX from 'xlsx';
import {
  ArrowLeft, Save, Loader2, AlertCircle, Edit3, Trash2, Plus, Search, X, FileSpreadsheet, Download,
} from 'lucide-react';

import warehouseService from '../../services/warehouseService';
import supplierService from '../../services/supplierService';
import stockReceiveService from '../../services/stockReceiveService';
import inventoryApi from '../../../../api/inventoryApi';
import { ROUTES } from '../../../../app/router/routes';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import usePagedVariants from '../../hooks/usePagedVariants';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

const uniqueValues = (values) => [...new Set((values ?? []).filter(Boolean))];

const PLATFORM_LABELS = { LAZADA: 'Lazada', SHOPIFY: 'Shopify', TIKTOK: 'TikTok Shop' };
const PLATFORM_BADGE_STYLES = {
  LAZADA: { backgroundColor: '#eef2ff', color: '#3730a3', borderColor: '#c7d2fe' },
  SHOPIFY: { backgroundColor: '#ecfdf5', color: '#047857', borderColor: '#a7f3d0' },
  TIKTOK: { backgroundColor: '#f8fafc', color: '#0f172a', borderColor: '#cbd5e1' },
  LOCAL:   { backgroundColor: '#f1f5f9', color: '#475569', borderColor: '#e2e8f0' },
};
const platformBadgeBaseStyle = {
  display: 'inline-flex', alignItems: 'center', minHeight: 20, padding: '2px 7px',
  borderRadius: 6, border: '1px solid transparent', fontSize: 11, fontWeight: 700, whiteSpace: 'nowrap',
};

const normalizePlatform = (v) => {
  const t = String(v ?? '').trim().toUpperCase();
  if (t.includes('LAZADA')) return 'LAZADA';
  if (t.includes('SHOPIFY')) return 'SHOPIFY';
  if (t.includes('TIKTOK')) return 'TIKTOK';
  return null;
};
const extractPlatforms = (value) => {
  if (Array.isArray(value)) return value.flatMap(extractPlatforms);
  const text = String(value ?? '').trim().toUpperCase();
  const matches = [];
  if (text.includes('LAZADA')) matches.push('LAZADA');
  if (text.includes('SHOPIFY')) matches.push('SHOPIFY');
  if (text.includes('TIKTOK')) matches.push('TIKTOK');
  if (matches.length) return matches;
  const p = normalizePlatform(text);
  return p ? [p] : [];
};
const itemPlatforms = (item) => uniqueValues([
  ...extractPlatforms(item?.platforms), ...extractPlatforms(item?.platform),
  ...extractPlatforms(item?.channelName), ...extractPlatforms(item?.channelNames),
]);
const renderPlatformBadges = (item) => {
  const platforms = itemPlatforms(item);
  const display = platforms.length > 0 ? platforms : ['LOCAL'];
  return display.map((p) => (
    <span key={p} style={{ ...platformBadgeBaseStyle, ...(PLATFORM_BADGE_STYLES[p] ?? PLATFORM_BADGE_STYLES.LOCAL) }}>
      {p === 'LOCAL' ? 'Ứng dụng' : PLATFORM_LABELS[p] ?? p}
    </span>
  ));
};

const normalizeWarehouseVariant = (item) => {
  const variantId = item.variantId ?? item.id;
  const internalSku = item.internalVariantSku ?? item.variantSku ?? item.sku ?? '';
  const marketplaceSku = item.marketplaceSku ?? '';
  const sku = internalSku || marketplaceSku;
  return {
    id: variantId, variantId,
    variantIds: uniqueValues([...(item.variantIds ?? []), variantId]),
    sku, variantSku: internalSku, marketplaceSku,
    productName: item.productName ?? item.product?.name ?? sku,
    name: item.variantName ?? item.name ?? '',
    unitPrice: item.unitPrice ?? item.price ?? 0,
    salePrice: item.salePrice ?? item.currentSalePrice ?? item.price ?? 0,
    availableQuantity: item.availableQuantity ?? 0,
    platforms: itemPlatforms(item),
    channelNames: uniqueValues(item.channelNames ?? [item.channelName]),
    mergedVariantCount: item.mergedVariantCount ?? 1,
  };
};

const aggregateVariantsBySku = (variants) => {
  const groups = new Map();
  variants.forEach((item) => {
    const key = String(item.variantSku ?? item.sku ?? '').trim().toLowerCase() || `variant:${item.variantId}`;
    if (!groups.has(key)) { groups.set(key, { ...item, variantIds: uniqueValues(item.variantIds ?? [item.variantId]) }); return; }
    const g = groups.get(key);
    g.variantIds = uniqueValues([...g.variantIds, ...(item.variantIds ?? []), item.variantId]);
    g.platforms = uniqueValues([...itemPlatforms(g), ...itemPlatforms(item)]);
    g.channelNames = uniqueValues([...(g.channelNames ?? []), ...(item.channelNames ?? [])]);
    g.availableQuantity = Math.max(Number(g.availableQuantity ?? 0), Number(item.availableQuantity ?? 0));
    g.mergedVariantCount = (g.mergedVariantCount ?? 1) + (item.mergedVariantCount ?? 1);
  });
  return [...groups.values()];
};

const groupReceiptItems = (receiptItems = [], fromPurchaseOrder = false) => {
  const groups = new Map();
  receiptItems.forEach((item) => {
    const groupKey = String(item.marketplaceSku || item.sku || item.variantSku || item.variantId)
      .trim().toLowerCase();
    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        groupKey, variantId: item.variantId, variantIds: [item.variantId],
        sku: item.marketplaceSku || item.sku || item.variantSku,
        productName: item.productName, variantName: item.variantName || '',
        quantity: item.quantity || 0, unitPrice: item.unitCost ?? item.unitPrice ?? 0,
        platforms: uniqueValues(item.platforms), fromPurchaseOrder,
      });
      return;
    }
    const existing = groups.get(groupKey);
    existing.variantIds = uniqueValues([...existing.variantIds, item.variantId]);
    existing.platforms = uniqueValues([...existing.platforms, ...(item.platforms ?? [])]);
  });
  return [...groups.values()];
};

const expandReceiptItems = (items) => items.flatMap((item) =>
  uniqueValues(item.variantIds?.length ? item.variantIds : [item.variantId]).map((variantId) => ({
    variantId,
    quantity: item.quantity ? Number(item.quantity) : null,
    unitCost: item.unitPrice !== '' && item.unitPrice !== null && item.unitPrice !== undefined
      ? Number(item.unitPrice) : null,
  })));

// ── Excel import helpers (client-side, same as CreatePage) ───────────────────
const normalizeImportKey = (value) => String(value ?? '').trim().toLocaleLowerCase('vi-VN');

const importLookupKeys = (item) => {
  const productName = String(item?.productName ?? '').trim();
  const variantName = String(item?.name ?? '').trim();
  const sku = String(item?.sku ?? item?.variantSku ?? '').trim();
  const marketplaceSku = String(item?.marketplaceSku ?? '').trim();
  const displayNameInternal = sku ? `${productName}${variantName ? ` - ${variantName}` : ''} [${sku}]` : '';
  const displayNameMarketplace = marketplaceSku && marketplaceSku !== sku
    ? `${productName}${variantName ? ` - ${variantName}` : ''} [${marketplaceSku}]` : '';
  return uniqueValues([sku, marketplaceSku, productName, variantName,
    variantName ? `${productName} - ${variantName}` : '',
    displayNameInternal, displayNameMarketplace,
  ]).map(normalizeImportKey).filter(Boolean);
};

const importProductKey = (item) => {
  const sku = normalizeImportKey(item?.variantSku ?? item?.sku ?? item?.marketplaceSku ?? '');
  if (sku) return `sku:${sku}`;
  return uniqueValues(item?.variantIds ?? [item?.id ?? item?.variantId]).map(String).sort().join('|') || '';
};

const parseExcelImportRows = (rows, variants, existingItems) => {
  const variantsByImportKey = new Map();
  const variantsBySkuKey = new Map();
  const variantsById = new Map();
  variants.forEach((item) => {
    importLookupKeys(item).forEach((key) => variantsByImportKey.set(key, item));
    const internalSku = normalizeImportKey(item?.variantSku ?? item?.sku ?? '');
    if (internalSku) variantsBySkuKey.set(`sku:${internalSku}`, item);
    const mktSku = normalizeImportKey(item?.marketplaceSku ?? '');
    if (mktSku && mktSku !== internalSku) variantsBySkuKey.set(`sku:${mktSku}`, item);
    uniqueValues([...(item.variantIds ?? []), item.id ?? item.variantId])
      .forEach((id) => variantsById.set(String(id), item));
  });
  const valid = [];
  const errors = [];
  const usedProductKeys = new Set();
  (existingItems ?? []).forEach((item) => {
    const pk = importProductKey(item);
    if (pk) usedProductKeys.add(pk);
    const internalSku = normalizeImportKey(item?.variantSku ?? item?.sku ?? '');
    if (internalSku) usedProductKeys.add(`sku:${internalSku}`);
    const mktSku = normalizeImportKey(item?.marketplaceSku ?? '');
    if (mktSku && mktSku !== internalSku) usedProductKeys.add(`sku:${mktSku}`);
    uniqueValues([...(item.variantIds ?? []), item.variantId, item.id])
      .forEach((id) => { if (id) usedProductKeys.add(`id:${String(id)}`); });
  });
  rows.forEach((row, index) => {
    const rowNumber = index + 2;
    const [rawProduct, rawQuantity, rawUnitPrice, rawVariantId] = row;
    if (!rawProduct && !rawQuantity && !rawUnitPrice && !rawVariantId) return;
    const input = rawProduct ? String(rawProduct).trim() : '';
    const reasons = [];
    if (!input) reasons.push('Tên sản phẩm hoặc SKU không hợp lệ.');
    const variantId = rawVariantId ? String(rawVariantId).trim() : '';
    const inputSkuKey = `sku:${normalizeImportKey(input)}`;
    const variant = variantsById.get(variantId)
      ?? variantsBySkuKey.get(inputSkuKey)
      ?? variantsByImportKey.get(normalizeImportKey(input));
    if (input && !variant) reasons.push('Không tìm thấy sản phẩm trong kho.');
    const quantity = Number(rawQuantity);
    if (!rawQuantity || Number.isNaN(quantity) || quantity <= 0) reasons.push('Số lượng phải lớn hơn 0.');
    const unitPrice = rawUnitPrice === undefined || rawUnitPrice === null || rawUnitPrice === ''
      ? Number(variant?.unitPrice ?? 0) : Number(rawUnitPrice);
    if (!Number.isFinite(unitPrice) || unitPrice < 0) reasons.push('Đơn giá không hợp lệ.');
    const productKey = variant ? importProductKey(variant) : null;
    const isDuplicate = (productKey && usedProductKeys.has(productKey))
      || (variant && uniqueValues([...(variant.variantIds ?? []), variant.variantId, variant.id])
          .some((id) => id && usedProductKeys.has(`id:${String(id)}`)));
    if (isDuplicate) reasons.push('Sản phẩm đã có trong phiếu hoặc bị trùng trong file.');
    if (reasons.length > 0) {
      errors.push({ rowNumber, input, quantity: rawQuantity, unitPrice: rawUnitPrice, reason: reasons.join(' ') });
      return;
    }
    valid.push({ ...variant, quantity, unitPrice });
    if (productKey) usedProductKeys.add(productKey);
    uniqueValues([...(variant?.variantIds ?? []), variant?.variantId, variant?.id])
      .forEach((id) => { if (id) usedProductKeys.add(`id:${String(id)}`); });
  });
  return { valid, errors };
};

const downloadImportErrors = (errors) => {
  const sheet = XLSX.utils.json_to_sheet(errors.map((e) => ({
    'Dòng Excel': e.rowNumber, 'Giá trị cột A': e.input,
    'Số lượng': e.quantity ?? '', 'Đơn giá': e.unitPrice ?? '', 'Lý do lỗi': e.reason,
  })));
  sheet['!cols'] = [{ wch: 12 }, { wch: 55 }, { wch: 14 }, { wch: 16 }, { wch: 55 }];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, sheet, 'Lỗi import');
  XLSX.writeFile(wb, 'stock-in-import-errors.xlsx');
};

// ── Zod schema ────────────────────────────────────────────────────────────────
const schema = z.object({
  warehouseId: z.string().min(1, 'Vui lòng chọn kho nhập.'),
  supplierId:  z.string().optional().nullable(),
  invoiceNumber: z.string().max(100).optional(),
  receivedAt: z.string().min(1, 'Ngày nhập là bắt buộc.').refine(
    (v) => v <= new Date().toISOString().split('T')[0],
    { message: 'Ngày nhập không được lớn hơn ngày hiện tại.' }
  ),
  notes: z.string().optional(),
});

// ── Add Product Modal (same as CreatePage) ────────────────────────────────────
function AddProductModal({ isOpen, onClose, onConfirm, existingVariantIds = [], existingSkus = [], products = [], totalItems = 0, loading = false, loadingMore = false, hasMore = false, onLoadMore, onKeywordChange }) {
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState({});

  useEffect(() => {
    if (isOpen) return undefined;
    const timer = window.setTimeout(() => { setKeyword(''); setSelected({}); onKeywordChange?.(''); }, 0);
    return () => window.clearTimeout(timer);
  }, [isOpen, onKeywordChange]);

  const results = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    if (!q) return products;
    return products.filter((item) =>
      [item.productName, item.name, item.sku, ...itemPlatforms(item).map((p) => PLATFORM_LABELS[p] ?? p)]
        .some((v) => String(v ?? '').toLowerCase().includes(q)));
  }, [keyword, products]);

  const toggle = (item) => {
    const skuKey = String(item.sku ?? '').trim().toLowerCase();
    if (existingVariantIds.includes(item.id) || existingSkus.includes(skuKey)) return;
    setSelected((prev) => { const n = { ...prev }; n[item.id] ? delete n[item.id] : (n[item.id] = item); return n; });
  };
  const count = Object.keys(selected).length;

  const handleScroll = (event) => {
    const el = event.currentTarget;
    if (el.scrollHeight - el.scrollTop - el.clientHeight < 80 && hasMore && !loading && !loadingMore) {
      onLoadMore?.();
    }
  };

  if (!isOpen) return null;
  return (
    <div onClick={(e) => e.target === e.currentTarget && onClose()}
      style={{ position: 'fixed', inset: 0, zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(2px)' }}>
      <div style={{ width: '100%', maxWidth: 580, background: '#fff', borderRadius: 16, boxShadow: '0 24px 60px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', maxHeight: '85vh', overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div>
            <span style={{ fontSize: 15, fontWeight: 700, color: '#0f172a' }}>Chọn sản phẩm bổ sung</span>
            <span style={{ marginLeft: 8, fontSize: 12, color: '#94a3b8' }}>{totalItems} sản phẩm</span>
          </div>
          <button onClick={onClose} style={{ width: 30, height: 30, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}>
            <X size={18} />
          </button>
        </div>
        <div style={{ padding: '12px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
            <input autoFocus value={keyword} onChange={(e) => { setKeyword(e.target.value); onKeywordChange?.(e.target.value); }}
              placeholder="Tìm theo tên sản phẩm hoặc SKU..."
              style={{ width: '100%', padding: '8px 10px 8px 34px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, color: '#0f172a', outline: 'none', boxSizing: 'border-box' }} />
          </div>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }} onScroll={handleScroll}>
          {loading && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>
              <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải sản phẩm kho...
            </div>
          )}
          {!loading && results.length === 0 && (
            <p style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>
              {keyword.trim() ? 'Không tìm thấy sản phẩm phù hợp.' : 'Không có sản phẩm nào trong kho.'}
            </p>
          )}
          {!loading && results.map((item) => {
            const skuKey = String(item.sku ?? '').trim().toLowerCase();
            const isExisting = existingVariantIds.includes(item.id) || existingSkus.includes(skuKey);
            const isSelected = Boolean(selected[item.id]);
            return (
              <div key={item.id} onClick={() => toggle(item)}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 20px', cursor: isExisting ? 'not-allowed' : 'pointer', backgroundColor: isExisting ? '#f8fafc' : isSelected ? '#eff6ff' : '#fff', borderBottom: '1px solid #f1f5f9', opacity: isExisting ? 0.55 : 1 }}>
                <input type="checkbox" checked={isSelected} disabled={isExisting} onChange={() => toggle(item)} onClick={(e) => e.stopPropagation()}
                  style={{ width: 16, height: 16, accentColor: '#2563eb', flexShrink: 0 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>{item.productName}</span>
                    {item.name && <span style={{ fontSize: 11, color: '#475569', backgroundColor: '#f1f5f9', padding: '1px 6px', borderRadius: 4 }}>{item.name}</span>}
                  </div>
                  <div style={{ display: 'flex', gap: 6, marginTop: 4, alignItems: 'center', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 11, fontFamily: 'monospace', backgroundColor: '#e0f2fe', color: '#0369a1', padding: '1px 6px', borderRadius: 4 }}>{item.sku}</span>
                    {renderPlatformBadges(item)}
                    {isExisting && <span style={{ fontSize: 11, backgroundColor: '#fffbeb', color: '#d97706', padding: '1px 6px', borderRadius: 4 }}>Đã có</span>}
                  </div>
                  {Number(item.availableQuantity) > 0 && (
                    <div style={{ marginTop: 3, fontSize: 11, color: '#64748b' }}>
                      Tồn kho: <strong style={{ color: '#0f172a' }}>{Number(item.availableQuantity).toLocaleString('vi-VN')}</strong>
                    </div>
                  )}
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
            <button onClick={onClose} style={{ padding: '7px 14px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>Hủy</button>
            <button
              onClick={() => onConfirm(Object.values(selected).map((i) => ({
                variantId: i.id, variantIds: uniqueValues(i.variantIds ?? [i.id]),
                sku: i.sku, productName: i.productName, variantName: i.name,
                quantity: 1, unitPrice: i.unitPrice ?? 0, salePrice: i.salePrice ?? 0,
                platforms: itemPlatforms(i), mergedVariantCount: i.mergedVariantCount ?? 1,
                fromPurchaseOrder: false,
              })))}
              disabled={count === 0}
              style={{ padding: '7px 16px', borderRadius: 8, border: 'none', backgroundColor: count === 0 ? '#93c5fd' : '#2563eb', color: '#fff', fontSize: 13, fontWeight: 500, cursor: count === 0 ? 'not-allowed' : 'pointer' }}>
              Thêm ({count})
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function StockReceiveEditPage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const { id } = useParams();
  const [receipt, setReceipt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const importFileRef = useRef(null);

  const { register, handleSubmit, formState: { errors, isSubmitting }, setValue } = useForm({
    resolver: zodResolver(schema),
  });

  const totalAmount = useMemo(
    () => items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0),
    [items]
  );

  const fetchWarehouseVariants = useCallback(async ({ page, size, keyword }) => {
      return inventoryApi.getInventoryList(page, size, 'updatedAt', 'desc', null, null, false, {
        ...(keyword ? { keyword } : {}),
      });
  }, []);

  const pagedVariants = usePagedVariants({
    enabled: true,
    fetcher: fetchWarehouseVariants,
  });

  const warehouseVariants = useMemo(
    () => aggregateVariantsBySku(
      pagedVariants.items.map(normalizeWarehouseVariant).filter((v) => v.id),
    ),
    [pagedVariants.items],
  );
  const loadingVariants = pagedVariants.loading || pagedVariants.loadingMore;

  const closeAddModal = () => {
    setModalOpen(false);
    pagedVariants.close();
    pagedVariants.setKeyword('');
  };

  const hasUnsavedChanges = Boolean(receipt);
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  async function fetchData() {
    setLoading(true);
    try {
      const [receiptRes, wRes, sRes] = await Promise.all([
        stockReceiveService.getReceiptById(id),
        warehouseService.getMaster(),
        supplierService.getAll(),
      ]);
      
      const receiptData = receiptRes.data?.data ?? receiptRes.data;
      
      // Validate receipt status is DRAFT
      if (receiptData.status !== 'DRAFT') {
        toast.error('Chỉ có thể chỉnh sửa phiếu nhập ở trạng thái Lưu tạm');
        navigate(`/warehouse/receipts/${id}`);
        return;
      }
      
      setReceipt(receiptData);
      
      // Set form values
      const masterWarehouse = wRes.data?.data ?? wRes.data;
      setValue('warehouseId', masterWarehouse?.id ? String(masterWarehouse.id) : receiptData.warehouseId || '');
      setValue('supplierId', receiptData.supplierId || '');
      setValue('invoiceNumber', receiptData.invoiceNumber || '');
      setValue('receivedAt', receiptData.receivedAt ? new Date(receiptData.receivedAt).toISOString().split('T')[0] : new Date().toISOString().split('T')[0]);
      setValue('notes', receiptData.notes || '');
      
      // Set items — mark PO-sourced items so qty is locked
      const hasPO = Boolean(receiptData.purchaseOrderId);
      setItems(groupReceiptItems(receiptData.items || [], hasPO));

      // Extract data from responses
      const extractData = (r) => {
        const d = r?.data?.data ?? r?.data;
        if (Array.isArray(d)) return d;
        if (d?.content && Array.isArray(d.content)) return d.content;
        return [];
      };
      setWarehouses(masterWarehouse?.id ? [masterWarehouse] : extractData(wRes));
      setSuppliers(extractData(sRes));
      
    } catch (error) {
      const errorMessage =
        error?.response?.data?.message ||
        error?.message ||
        'Không thể tải thông tin phiếu nhập.';
      toast.error(errorMessage);
      navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!id) {
      toast.error('ID phiếu nhập không hợp lệ');
      navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS);
      return;
    }
    // Loading data is the external synchronization performed by this effect.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // ── Item handlers ─────────────────────────────────────────────────────────
  const onQtyChange   = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, quantity: v } : it));
  const onPriceChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, unitPrice: v } : it));
  const onRemove = (i) => setItems((p) => p.filter((_, idx) => idx !== i));
  const onAddProducts = (newItems) => {
    setItems((current) => {
      const skuKeys = new Set(current.map((it) => String(it.sku ?? '').trim().toLowerCase()).filter(Boolean));
      const ids = new Set(current.map((it) => it.variantId));
      return [
        ...current,
        ...newItems.filter((it) => {
          const skuKey = String(it.sku ?? '').trim().toLowerCase();
          return skuKey ? !skuKeys.has(skuKey) : !ids.has(it.variantId);
        }),
      ];
    });
    closeAddModal();
  };

  const downloadBlob = (blob, name) => {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click();
    URL.revokeObjectURL(url);
  };
  const onDownloadTemplate = async () => {
    try { const result = await stockReceiveService.downloadExtraItemsTemplate(id); downloadBlob(result.data, 'stock-in-extra-items-template.xlsx'); }
    catch { toast.error('Không thể tải template Excel.'); }
  };
  const onImportFile = (event) => {
    const file = event.target.files?.[0];
    if (importFileRef.current) importFileRef.current.value = '';
    if (!file) return;
    if (loadingVariants) { toast.info('Đang tải sản phẩm kho, vui lòng thử lại.'); return; }
    const reader = new FileReader();
    reader.onload = async (ev) => {
      try {
        await pagedVariants.loadAll('');
        const wb = XLSX.read(new Uint8Array(ev.target.result), { type: 'array', cellFormula: false, cellNF: false });
        const rows = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: undefined }).slice(1);
        const parsed = parseExcelImportRows(rows, warehouseVariants, items);
        if (parsed.valid.length === 0) {
          if (parsed.errors.length > 0) downloadImportErrors(parsed.errors);
          toast.error('Không có dòng hợp lệ. Đã tải file lỗi để kiểm tra.');
          return;
        }
        if (parsed.errors.length > 0) {
          downloadImportErrors(parsed.errors);
          toast.warn(`Có ${parsed.errors.length} dòng lỗi; đã nhập ${parsed.valid.length} dòng hợp lệ.`);
        } else {
          toast.success(`Đã thêm ${parsed.valid.length} sản phẩm từ Excel.`);
        }
        setItems((prev) => {
          const skuKeys = new Set(prev.map((it) => String(it.sku ?? '').trim().toLowerCase()).filter(Boolean));
          const ids = new Set(prev.map((it) => it.variantId));
          const toAdd = parsed.valid.filter((p) => {
            const skuKey = String(p.sku ?? '').trim().toLowerCase();
            return skuKey ? !skuKeys.has(skuKey) : !ids.has(p.id ?? p.variantId);
          }).map((p) => ({
            variantId: p.id ?? p.variantId,
            variantIds: uniqueValues(p.variantIds ?? [p.id ?? p.variantId]),
            sku: p.sku, productName: p.productName, variantName: p.name ?? '',
            quantity: p.quantity, unitPrice: p.unitPrice,
            platforms: itemPlatforms(p), fromPurchaseOrder: false,
          }));
          return [...prev, ...toAdd];
        });
      } catch { toast.error('Không thể đọc file Excel.'); }
    };
    reader.onerror = () => toast.error('Không thể đọc file Excel.');
    reader.readAsArrayBuffer(file);
  };

  // ── Submit - Save Draft ────────────────────────────────────────────────────
  const onSubmit = handleSubmit(async (data) => {
    if (items.length === 0) { 
      toast.error('Vui lòng có ít nhất một sản phẩm.'); 
      return; 
    }
    
    try {
      await stockReceiveService.updateReceipt(id, {
        warehouseId: data.warehouseId,
        supplierId: data.supplierId || null,
        purchaseOrderId: receipt.purchaseOrderId,
        invoiceNumber: receipt.receiptCode || null,
        receivedAt: data.receivedAt,
        notes: data.notes || null,
        items: expandReceiptItems(items),
        isDraft: true,
      });
      toast.success('Cập nhật phiếu nhập thành công.');
      runWithoutGuard(() => navigate(`/warehouse/receipts/${id}`));
    } catch (error) { 
      const errorMessage = error?.response?.data?.message || error?.message || 'Không thể cập nhật phiếu nhập. Vui lòng thử lại.';
      toast.error(errorMessage);
    }
  });

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '60vh', gap: 12, color: '#94a3b8' }}>
        <Loader2 size={32} style={{ animation: 'spin 1s linear infinite' }} />
        <p style={{ fontSize: 14 }}>Đang tải thông tin phiếu nhập...</p>
      </div>
    );
  }

  if (!receipt) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '60vh', gap: 12, color: '#94a3b8' }}>
        <AlertCircle size={32} />
        <p style={{ fontSize: 14 }}>Không tìm thấy thông tin phiếu nhập.</p>
        <button onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}
          style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>
          Quay lại danh sách
        </button>
      </div>
    );
  }

  const today = new Date().toISOString().split('T')[0];

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="product-workspace product-workspace--flow" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button onClick={() => navigate(`/warehouse/receipts/${id}`)}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', borderRadius: 7, border: '1px solid #e2e8f0', background: '#fff', fontSize: 12, color: '#374151', cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
          >
            <ArrowLeft size={14} /> Quay lại
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Edit3 size={18} color="#2563eb" />
            </div>
            <div>
              <h1 style={{ fontSize: 18, fontWeight: 700, color: '#0f172a', margin: 0 }}>Chỉnh sửa phiếu nhập: {receipt.receiptCode}</h1>
              <p style={{ fontSize: 12, color: '#64748b', margin: '1px 0 0' }}>Cập nhật thông tin phiếu nhập lưu tạm</p>
            </div>
          </div>
        </div>
      </div>

      {/* Form */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 16 }}>

        {/* Left - Items table */}
        <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: '14px 18px', borderBottom: '1px solid #f1f5f9', backgroundColor: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
            <div>
              <h3 style={{ fontSize: 14, fontWeight: 600, color: '#0f172a', margin: 0 }}>Danh sách sản phẩm</h3>
              <p style={{ fontSize: 11, color: receipt?.purchaseOrderId ? '#f97316' : '#94a3b8', margin: '2px 0 0' }}>
                {receipt?.purchaseOrderId
                  ? 'Phiếu từ đơn đặt hàng — có thể điều chỉnh số lượng (nhập hàng nhiều đợt)'
                  : 'Cập nhật số lượng và đơn giá'}
              </p>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button type="button" onClick={onDownloadTemplate} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 12px', borderRadius: 7, border: '1px solid #bfdbfe', background: '#eff6ff', color: '#1d4ed8', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}><Download size={14} /> Template</button>
              <button type="button" onClick={() => importFileRef.current?.click()} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 12px', borderRadius: 7, border: 'none', background: '#f59e0b', color: '#fff', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}><FileSpreadsheet size={14} /> Import Excel</button>
              <input ref={importFileRef} type="file" accept=".xlsx,.xls" onChange={onImportFile} style={{ display: 'none' }} />
            </div>
            <button type="button" onClick={() => { pagedVariants.open(); setModalOpen(true); }}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 12px', borderRadius: 7, border: 'none',
                background: '#009688', color: '#fff',
                fontSize: 12, fontWeight: 600,
                cursor: 'pointer', whiteSpace: 'nowrap' }}>
              <Plus size={14} /> Thêm sản phẩm
            </button>          </div>

          <div style={{ flex: 1, overflowY: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8fafc', zIndex: 1 }}>
                <tr style={{ borderBottom: '1px solid #e2e8f0' }}>
                  {['Sản phẩm', 'SKU', 'Số lượng', 'Đơn giá (₫)', 'Thành tiền', ''].map((h) => (
                    <th key={h} style={{ padding: '8px 12px', textAlign: h === 'Thành tiền' ? 'right' : 'left', fontWeight: 600, fontSize: 11, color: '#64748b', whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {items.map((item, idx) => {
                  const qtyBad = item.quantity !== '' && Number(item.quantity) <= 0;
                  const priceBad = item.unitPrice === '' || item.unitPrice === null || item.unitPrice === undefined || Number(item.unitPrice) < 0;
                  const line = (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0);
                  return (
                    <tr key={item.groupKey ?? item.variantId ?? idx} style={{ borderBottom: '1px solid #f1f5f9' }}>
                      <td style={{ padding: '10px 12px' }}>
                        <div style={{ fontWeight: 500, color: '#0f172a', fontSize: 12 }}>{item.productName}</div>
                        {item.variantName && <div style={{ fontSize: 10, color: '#94a3b8' }}>{item.variantName}</div>}
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <span style={{ fontSize: 10, fontFamily: 'monospace', backgroundColor: '#f1f5f9', color: '#64748b', padding: '2px 6px', borderRadius: 4 }}>{item.sku}</span>
                        {item.platforms?.length > 0 && (
                          <span style={{ display: 'block', marginTop: 4, fontSize: 10, color: '#64748b' }}>
                            {item.platforms.join(' · ')}
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '10px 12px', width: 100 }}>
                        <input type="number" min="0" step="1" value={item.quantity}
                          onChange={(e) => onQtyChange(idx, e.target.value)}
                          style={{ width: '100%', padding: '6px 8px', borderRadius: 6,
                            border: `1px solid ${qtyBad ? '#fca5a5' : '#e2e8f0'}`,
                            backgroundColor: qtyBad ? '#fff5f5' : '#fff',
                            fontSize: 12, textAlign: 'right', outline: 'none', boxSizing: 'border-box' }} />
                        {item.fromPurchaseOrder && (
                          <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 2 }}>Từ đơn đặt hàng</div>
                        )}
                      </td>
                      <td style={{ padding: '10px 12px', width: 130 }}>
                        <input type="number" min="0" step="1000" value={item.unitPrice} onChange={(e) => onPriceChange(idx, e.target.value)}
                          style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: `1px solid ${priceBad ? '#fca5a5' : '#e2e8f0'}`, backgroundColor: priceBad ? '#fff5f5' : '#fff', fontSize: 12, textAlign: 'right', outline: 'none', boxSizing: 'border-box' }} />
                      </td>
                      <td style={{ padding: '10px 12px', textAlign: 'right', fontWeight: 600, color: line > 0 ? '#2563eb' : '#94a3b8', whiteSpace: 'nowrap', fontSize: 12 }}>
                        {line > 0 ? formatVND(line) : '—'}
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <button onClick={() => onRemove(idx)}
                          style={{ width: 24, height: 24, borderRadius: 4, border: 'none', background: 'none', cursor: 'pointer', color: '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#fef2f2'; e.currentTarget.style.color = '#dc2626'; }}
                          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.color = '#94a3b8'; }}>
                          <Trash2 size={13} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Footer summary */}
          <div style={{ borderTop: '1px solid #e2e8f0', padding: '10px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#f8fafc' }}>
            <div style={{ display: 'flex', gap: 12, fontSize: 11, color: '#64748b' }}>
              <span><strong style={{ color: '#374151' }}>{items.length}</strong> sản phẩm</span>
              <span>SL: <strong style={{ color: '#374151' }}>{items.reduce((s, i) => s + (Number(i.quantity) || 0), 0).toLocaleString()}</strong></span>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: 9, color: '#94a3b8', marginBottom: 1 }}>Tổng giá trị</div>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#2563eb' }}>{formatVND(totalAmount)}</div>
            </div>
          </div>
        </div>

        {/* Right - Form info */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '14px 16px' }}>
            <h3 style={{ fontSize: 13, fontWeight: 600, color: '#0f172a', marginBottom: 12 }}>Thông tin phiếu nhập</h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {/* Kho nhập */}
              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
                  Kho nhập <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <select {...register('warehouseId')}
                  style={{ width: '100%', padding: '7px 9px', borderRadius: 6, border: `1px solid ${errors.warehouseId ? '#fca5a5' : '#e2e8f0'}`, fontSize: 12, color: '#0f172a', outline: 'none', backgroundColor: '#fff', boxSizing: 'border-box' }}>
                  {warehouses.length === 0 && <option value="">Chọn kho</option>}
                  {warehouses.map((w) => (
                    <option key={w.id} value={w.id}>{w.name}{w.address ? ` - ${w.address}` : ''}</option>
                  ))}
                </select>
                {errors.warehouseId && <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626' }}>{errors.warehouseId.message}</p>}
              </div>

              {/* Nhà cung cấp */}
              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>Nhà cung cấp</label>
                <select {...register('supplierId')}
                  style={{ width: '100%', padding: '7px 9px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 12, color: '#0f172a', outline: 'none', backgroundColor: '#fff', boxSizing: 'border-box' }}>
                  <option value="">Không chọn</option>
                  {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>

              {/* Mã phiếu */}
              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
                  Mã phiếu
                </label>
                <input value={receipt.receiptCode || 'Đang tạo mã...'} disabled
                  style={{ width: '100%', padding: '7px 9px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 12, outline: 'none', boxSizing: 'border-box' }} />
              </div>

              {/* Ngày nhập */}
              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
                  Ngày nhập <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input type="date" max={today} {...register('receivedAt')}
                  style={{ width: '100%', padding: '7px 9px', borderRadius: 6, border: `1px solid ${errors.receivedAt ? '#fca5a5' : '#e2e8f0'}`, fontSize: 12, outline: 'none', boxSizing: 'border-box' }} />
                {errors.receivedAt && <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626' }}>{errors.receivedAt.message}</p>}
              </div>

              {/* Ghi chú */}
              <div>
                <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>Ghi chú</label>
                <textarea {...register('notes')} rows={3} placeholder="Ghi chú về phiếu nhập..."
                  style={{ width: '100%', padding: '7px 9px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 12, outline: 'none', resize: 'vertical', boxSizing: 'border-box' }} />
              </div>
            </div>
          </div>

          {/* Actions */}
          <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button onClick={onSubmit} disabled={isSubmitting}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, padding: '10px 16px', borderRadius: 7, border: 'none', backgroundColor: isSubmitting ? '#93c5fd' : '#2563eb', color: '#fff', fontSize: 13, fontWeight: 500, cursor: isSubmitting ? 'not-allowed' : 'pointer', width: '100%' }}
              onMouseEnter={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#1d4ed8'; }}
              onMouseLeave={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#2563eb'; }}>
              {isSubmitting ? <><Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> Đang lưu...</> : <><Save size={14} /> Lưu thay đổi</>}
            </button>
            <button onClick={() => navigate(`/warehouse/receipts/${id}`)}
              style={{ padding: '9px 16px', borderRadius: 7, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer', width: '100%' }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}>
              Hủy
            </button>
          </div>
        </div>
      </div>
      <AddProductModal
        isOpen={modalOpen}
        onClose={closeAddModal}
        onConfirm={onAddProducts}
        existingVariantIds={items.flatMap((item) => item.variantIds ?? [item.variantId])}
        existingSkus={items.map((item) => String(item.sku ?? '').trim().toLowerCase()).filter(Boolean)}
        products={warehouseVariants}
        totalItems={pagedVariants.totalItems}
        loading={pagedVariants.loading}
        loadingMore={pagedVariants.loadingMore}
        hasMore={pagedVariants.hasMore}
        onLoadMore={pagedVariants.loadMore}
        onKeywordChange={pagedVariants.setKeyword}
      />
      {ConfirmDialog}
    </div>
  );
}
