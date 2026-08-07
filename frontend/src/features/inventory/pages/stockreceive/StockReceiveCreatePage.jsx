import { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import { ArrowLeft, Plus, Trash2, Search, X, FileSpreadsheet, PackagePlus, AlertCircle, Loader2, Package } from 'lucide-react';
import * as XLSX from 'xlsx';
import warehouseService from '../../services/warehouseService';
import supplierService from '../../services/supplierService';
import stockReceiveService from '../../services/stockReceiveService';
import inventoryApi from '../../../../api/inventoryApi';
import purchaseOrderApi from '../../../../api/purchaseOrderApi';
import { ROUTES } from '../../../../app/router/routes';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import styles from '../CreatePage.module.css';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);
const getResponseData = (response) => response?.data?.data ?? response?.data ?? response ?? {};
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

const formatPlatforms = (item) => {
  const platforms = itemPlatforms(item);
  return platforms.length === 0 ? 'Ứng dụng' : platforms.map((platform) => PLATFORM_LABELS[platform] ?? platform).join(', ');
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

const normalizeWarehouseVariant = (item) => {
  const variantId = item.variantId ?? item.id;
  // internalVariantSku = raw variant.sku from DB — ALWAYS the value used in the Excel template
  // display name: "{productName} - {variantName} [{internalVariantSku}]"
  // The backend now returns this as a separate field so it's never overridden by marketplaceSku.
  const internalVariantSku = item.internalVariantSku ?? item.variantSku ?? item.sku ?? '';
  const marketplaceSku = item.marketplaceSku ?? '';
  // Primary SKU for dedup and grouping: use internalVariantSku so it matches the template.
  const sku = internalVariantSku || marketplaceSku;
  const salePrice = item.salePrice ?? item.currentSalePrice ?? item.price ?? item.unitPrice ?? 0;
  const unitPrice = item.unitPrice ?? item.price ?? 0;
  // variantIds from the API covers ALL variants grouped under this SKU (both channels).
  // This is essential for the dedup check — a purchase-order item may reference any of these variant IDs.
  const variantIds = uniqueValues([
    ...(item.variantIds ?? []),
    variantId,
  ]).filter(Boolean);
  return {
    id: variantId,
    variantId,
    variantIds,
    sku,                          // primary SKU = internalVariantSku (matches template display name)
    variantSku: internalVariantSku, // always internal SKU for lookup key building
    marketplaceSku,               // marketplace/external SKU (for secondary lookup)
    productName: item.productName ?? item.product?.name ?? item.variantName ?? sku,
    name: item.variantName ?? item.name ?? '',
    unitPrice,
    salePrice,
    currentSalePrice: item.currentSalePrice ?? salePrice,
    availableQuantity: item.availableQuantity ?? item.quantityOnHand ?? 0,
    channelId: item.channelId ?? null,
    channelName: item.channelName ?? '',
    platform: item.platform ?? null,
    channelIds: uniqueValues(item.channelIds ?? [item.channelId]),
    channelNames: uniqueValues(item.channelNames ?? [item.channelName]),
    platforms: itemPlatforms(item),
    mergedVariantCount: item.mergedVariantCount ?? 1,
  };
};

const aggregateWarehouseVariantsBySku = (variants) => {
  const groups = new Map();
  variants.forEach((item) => {
    // Group by internal SKU (variantSku) — the primary stable identifier.
    // item.sku is already set to variantSku by normalizeWarehouseVariant.
    const skuKey = String(item.variantSku ?? item.sku ?? '').trim().toLowerCase();
    const key = skuKey || `variant:${item.variantId ?? item.id}`;
    if (!groups.has(key)) {
      groups.set(key, {
        ...item,
        id: item.id ?? item.variantId,
        variantId: item.variantId ?? item.id,
        variantIds: uniqueValues(item.variantIds ?? [item.variantId ?? item.id]),
        platforms: itemPlatforms(item),
        channelNames: uniqueValues(item.channelNames ?? [item.channelName]),
        channelIds: uniqueValues(item.channelIds ?? [item.channelId]),
        availableQuantity: Number(item.availableQuantity ?? 0),
        mergedVariantCount: Number(item.mergedVariantCount ?? 1),
      });
      return;
    }

    const existing = groups.get(key);
    existing.productName = existing.productName || item.productName;
    existing.name = existing.name || item.name;
    existing.unitPrice = Number(existing.unitPrice ?? 0) > 0 ? existing.unitPrice : item.unitPrice;
    existing.salePrice = Number(existing.salePrice ?? 0) > 0 ? existing.salePrice : item.salePrice;
    existing.currentSalePrice = Number(existing.currentSalePrice ?? 0) > 0 ? existing.currentSalePrice : item.currentSalePrice;
    existing.availableQuantity = Math.max(Number(existing.availableQuantity ?? 0), Number(item.availableQuantity ?? 0));
    existing.platforms = uniqueValues([...itemPlatforms(existing), ...itemPlatforms(item)]);
    existing.channelNames = uniqueValues([...(existing.channelNames ?? []), ...(item.channelNames ?? []), item.channelName]);
    existing.channelIds = uniqueValues([...(existing.channelIds ?? []), ...(item.channelIds ?? []), item.channelId]);
    existing.variantIds = uniqueValues([...(existing.variantIds ?? []), ...(item.variantIds ?? []), item.variantId]);
    existing.mergedVariantCount = Number(existing.mergedVariantCount ?? 1) + Number(item.mergedVariantCount ?? 1);
  });
  return [...groups.values()];
};

const groupPurchaseOrderItems = (orderItems = []) => {
  const groups = new Map();
  orderItems.forEach((item) => {
    const sku = item.marketplaceSku || item.sku || '';
    const groupKey = String(sku).trim().toLowerCase() || `variant:${item.variantId}`;
    // Use actualQuantity (set during inspection) if available; for surplus cap at ordered qty
    // (surplus items go to a separate surplus order, not this receipt)
    const receiptQty = item.actualQuantity != null
      ? Math.min(item.actualQuantity, item.quantity)
      : item.quantity;
    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        groupKey,
        variantId: item.variantId,
        variantIds: [item.variantId],
        sku,
        productName: item.productName,
        variantName: item.variantName,
        quantity: receiptQty,
        unitPrice: item.unitCost ?? 0,
        salePrice: item.salePrice ?? 0,
        platforms: uniqueValues(item.platforms),
        mergedVariantCount: 1,
        fromPurchaseOrder: true,
      });
      return;
    }
    const existing = groups.get(groupKey);
    existing.variantIds = uniqueValues([...existing.variantIds, item.variantId]);
    existing.platforms = uniqueValues([...existing.platforms, ...(item.platforms ?? [])]);
    existing.salePrice = existing.salePrice || item.salePrice || 0;
    existing.mergedVariantCount += 1;
  });
  return [...groups.values()];
};

const expandReceiptItems = (items) => items.map((item) => ({
    variantId: item.variantId,
    quantity: item.quantity === '' || item.quantity === null || item.quantity === undefined
      ? null
      : Number(item.quantity),
    unitCost: item.unitPrice === '' || item.unitPrice === null || item.unitPrice === undefined
      ? null
      : Number(item.unitPrice),
  }));

// ── Excel import helpers ──────────────────────────────────────────────────────
const normalizeImportKey = (value) => String(value ?? '').trim().toLocaleLowerCase('vi-VN');

const importLookupKeys = (item) => {
  const productName = String(item?.productName ?? '').trim();
  const variantName = String(item?.name ?? '').trim();
  const sku = String(item?.sku ?? item?.variantSku ?? '').trim();
  const marketplaceSku = String(item?.marketplaceSku ?? '').trim();
  const displayNameInternal = sku
    ? `${productName}${variantName ? ` - ${variantName}` : ''} [${sku}]` : '';
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

const downloadImportErrors = (errors) => {
  const sheet = XLSX.utils.json_to_sheet(errors.map((error) => ({
    'Dòng Excel': error.rowNumber,
    'Giá trị cột A': error.input,
    'Số lượng': error.quantity ?? '',
    'Đơn giá': error.unitPrice ?? '',
    'Lý do lỗi': error.reason,
  })));
  sheet['!cols'] = [{ wch: 12 }, { wch: 55 }, { wch: 14 }, { wch: 16 }, { wch: 55 }];
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'Lỗi import');
  XLSX.writeFile(workbook, 'stock-in-import-errors.xlsx');
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
    if (input && !variant) reasons.push('Không tìm thấy sản phẩm trong kho đã chọn.');
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

// ── Zod schema ────────────────────────────────────────────────────────────────
const schema = z.object({
  warehouseId: z.string().min(1, 'Vui lòng chọn kho nhập.'),
  supplierId: z.string().optional().nullable(),
  invoiceNumber: z.string().max(100).optional(),
  receivedAt: z.string().min(1, 'Ngày nhập là bắt buộc.').refine(
    (v) => v <= new Date().toISOString().split('T')[0],
    { message: 'Ngày nhập không được lớn hơn ngày hiện tại.' },
  ),
  notes: z.string().optional(),
});

// ── Add Product Modal ─────────────────────────────────────────────────────────
function AddProductModal({ isOpen, onClose, onConfirm, existingVariantIds = [], existingSkus = [], products = [], loading = false }) {
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState({});

  useEffect(() => {
    if (isOpen) return undefined;
    const timer = window.setTimeout(() => {
      setKeyword('');
      setSelected({});
    }, 0);
    return () => window.clearTimeout(timer);
  }, [isOpen]);

  const results = useMemo(() => {
    const keywordText = keyword.trim().toLowerCase();
    if (!keywordText) return products;
    return products.filter((item) => [
      item.productName,
      item.name,
      item.sku,
      formatPlatforms(item),
    ].some((value) => String(value ?? '').toLowerCase().includes(keywordText)));
  }, [keyword, products]);

  const toggle = (item) => {
    const skuKey = String(item.sku ?? '').trim().toLowerCase();
    if (existingVariantIds.includes(item.id) || existingSkus.includes(skuKey)) return;
    setSelected((prev) => {
      const n = { ...prev };
      n[item.id] ? delete n[item.id] : (n[item.id] = item);
      return n;
    });
  };

  const count = Object.keys(selected).length;

  if (!isOpen) return null;
  return (
    <div onClick={(e) => e.target === e.currentTarget && onClose()}
      style={{ position: 'fixed', inset: 0, zIndex: 100, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(2px)' }}>
      <div style={{ width: '100%', maxWidth: 560, background: '#fff', borderRadius: 16, boxShadow: '0 24px 60px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', maxHeight: '85vh', overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div>
            <span style={{ fontSize: 15, fontWeight: 700, color: '#0f172a' }}>Chọn sản phẩm</span>
            {results.length > 0 && <span style={{ marginLeft: 8, fontSize: 12, color: '#94a3b8' }}>{results.length} sản phẩm</span>}
          </div>
          <button onClick={onClose} style={{ width: 30, height: 30, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}>
            <X size={18} />
          </button>
        </div>
        <div style={{ padding: '12px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
            <input autoFocus value={keyword} onChange={(e) => setKeyword(e.target.value)}
              placeholder="Tìm theo tên sản phẩm hoặc SKU..."
              style={{ width: '100%', padding: '8px 10px 8px 34px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, color: '#0f172a', outline: 'none', boxSizing: 'border-box' }} />
          </div>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {loading && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>
              <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải...
            </div>
          )}
          {!loading && results.length === 0 && (
            <p style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>
              {keyword.trim() ? 'Không tìm thấy sản phẩm nào phù hợp.' : 'Không có sản phẩm nào.'}
            </p>
          )}
          {!loading && results.map((item) => {
            const skuKey = String(item.sku ?? '').trim().toLowerCase();
            const isExisting = existingVariantIds.includes(item.id) || existingSkus.includes(skuKey);
            const isSelected = Boolean(selected[item.id]);
            return (
              <div key={item.id} onClick={() => toggle(item)}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 20px', cursor: isExisting ? 'not-allowed' : 'pointer', backgroundColor: isExisting ? '#f8fafc' : isSelected ? '#eff6ff' : '#fff', borderBottom: '1px solid #f1f5f9', opacity: isExisting ? 0.55 : 1, transition: 'background 0.1s' }}>
                <input type="checkbox" checked={isSelected} disabled={isExisting} onChange={() => toggle(item)} onClick={(e) => e.stopPropagation()}
                  style={{ width: 16, height: 16, accentColor: '#2563eb', flexShrink: 0 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>{item.productName}</span>
                    {item.name && <span style={{ fontSize: 11, color: '#475569', backgroundColor: '#f1f5f9', padding: '1px 6px', borderRadius: 4 }}>{item.name}</span>}
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 3, alignItems: 'center' }}>
                    <span style={{ fontSize: 11, fontFamily: 'monospace', backgroundColor: '#e0f2fe', color: '#0369a1', padding: '1px 6px', borderRadius: 4 }}>{item.sku}</span>
                    {renderPlatformBadges(item)}
                    {isExisting && <span style={{ fontSize: 11, backgroundColor: '#fffbeb', color: '#d97706', padding: '1px 6px', borderRadius: 4 }}>Đã có</span>}
                  </div>
                  {Number(item.salePrice) > 0 && (
                    <div style={{ marginTop: 4, fontSize: 11, color: '#64748b' }}>
                      Giá bán hiện tại: <strong style={{ color: '#0f172a', fontWeight: 600 }}>{formatVND(Number(item.salePrice))}</strong>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
        <div style={{ padding: '14px 20px', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>{count > 0 ? `Đã chọn ${count} sản phẩm` : 'Chưa chọn sản phẩm nào'}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={onClose} style={{ padding: '7px 14px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>Hủy</button>
            <button onClick={() => { onConfirm(Object.values(selected).map((i) => ({ variantId: i.id, variantIds: uniqueValues(i.variantIds ?? [i.id]), sku: i.sku, productName: i.productName, variantName: i.name, quantity: 1, unitPrice: i.unitPrice ?? 0, salePrice: i.salePrice ?? 0, platforms: itemPlatforms(i), channelNames: uniqueValues(i.channelNames ?? [i.channelName]), mergedVariantCount: i.mergedVariantCount ?? 1, fromPurchaseOrder: false }))); }} disabled={count === 0}
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
export default function StockReceiveCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const fileRef = useRef(null);
  const prefillAppliedRef = useRef(false);
  const [items, setItems] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [warehouses, setWarehouses] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [nextReceiptCode, setNextReceiptCode] = useState('');
  const [warehouseVariants, setWarehouseVariants] = useState([]);
  const [loadingWarehouseVariants, setLoadingWarehouseVariants] = useState(false);
  const [receivingPurchaseOrders, setReceivingPurchaseOrders] = useState([]);
  const [purchaseOrderId, setPurchaseOrderId] = useState(searchParams.get('purchaseOrderId') || '');
  const [purchaseOrderError, setPurchaseOrderError] = useState('');
  const previousWarehouseIdRef = useRef('');

  const { register, handleSubmit, setValue, control, formState: { errors, isSubmitting, isDirty } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { warehouseId: '', supplierId: '', invoiceNumber: '', receivedAt: new Date().toISOString().split('T')[0], notes: '' },
  });
  const selectedWarehouseId = useWatch({ control, name: 'warehouseId' });
  const totalAmount = useMemo(() => items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0), [items]);
  const totalQty = useMemo(() => items.reduce((s, i) => s + (Number(i.quantity) || 0), 0), [items]);
  const hasUnsavedChanges = isDirty || items.length > 0;
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  useEffect(() => {
    Promise.all([
      warehouseService.getMaster(),
      supplierService.getAll(),
      stockReceiveService.getNextReceiptCode(),
      purchaseOrderApi.getAll({ size: 100, status: 'INSPECTED' }),
    ])
      .then(([wRes, sRes, codeRes, purchasePage]) => {
        const extract = (r) => { const d = r?.data?.data ?? r?.data; if (Array.isArray(d)) return d; if (d?.content && Array.isArray(d.content)) return d.content; return []; };
        const masterWarehouse = getResponseData(wRes);
        setWarehouses(masterWarehouse?.id ? [masterWarehouse] : extract(wRes));
        if (masterWarehouse?.id) {
          setValue('warehouseId', String(masterWarehouse.id), { shouldDirty: false, shouldValidate: true });
        }
        setSuppliers(extract(sRes));
        setNextReceiptCode(codeRes?.data?.data ?? codeRes?.data ?? '');
        setReceivingPurchaseOrders(
          // Exclude orders that already have a linked receipt
          (purchasePage?.content ?? []).filter((order) => !order.receiptId),
        );
      })
      .catch(() => {});
  }, [setValue]);

  useEffect(() => {
    if (!purchaseOrderId) return;
    let ignore = false;
    purchaseOrderApi.getById(purchaseOrderId)
      .then((order) => {
        if (ignore) return;
        if (order.status !== 'INSPECTED') {
          toast.error('Đơn mua hàng chưa ở trạng thái Đã kiểm tra.');
          setPurchaseOrderId('');
          return;
        }
        if (order.receiptId) {
          toast.error('Đơn mua hàng này đã có phiếu nhập kho liên kết.');
          setPurchaseOrderId('');
          return;
        }
        // Only add to dropdown if this order doesn't already have a receipt
        if (!order.receiptId) {
          setReceivingPurchaseOrders((current) => current.some((item) => item.id === order.id) ? current : [order, ...current]);
        }
        setValue('warehouseId', String(order.warehouseId), { shouldDirty: true, shouldValidate: true });
        setValue('supplierId', order.supplierId ? String(order.supplierId) : '', { shouldDirty: true });

        // Auto-generate notes from inspection surplus/shortage annotations
        const noteLines = [];
        if (order.notes) noteLines.push(order.notes);

        const allItems = order.items ?? [];
        const shortageItems = allItems.filter(
          (item) => item.actualQuantity != null && item.actualQuantity < item.quantity
        );
        const surplusItems = allItems.filter(
          (item) => item.actualQuantity != null && item.actualQuantity > item.quantity
        );

        if (shortageItems.length > 0) {
          if (noteLines.length > 0) noteLines.push('');
          noteLines.push('--- Hàng THIẾU ---');
          shortageItems.forEach((item) => {
            const name = item.variantName
              ? `${item.productName} (${item.variantName})`
              : item.productName;
            const diff = item.quantity - item.actualQuantity;
            noteLines.push(`• [THIẾU] ${name}: thiếu ${diff} sản phẩm (đặt ${item.quantity}, thực nhận ${item.actualQuantity})`);
          });
        }

        if (surplusItems.length > 0) {
          if (noteLines.length > 0) noteLines.push('');
          noteLines.push('--- Hàng THỪA ---');
          surplusItems.forEach((item) => {
            const name = item.variantName
              ? `${item.productName} (${item.variantName})`
              : item.productName;
            const diff = item.actualQuantity - item.quantity;
            noteLines.push(`• [THỪA] ${name}: thừa ${diff} sản phẩm (đặt ${item.quantity}, thực nhận ${item.actualQuantity})`);
          });
        }

        // If this is a shortage/surplus supplementary order (all items have actualQty === qty),
        // fall back to listing every item from the order's own notes context
        if (shortageItems.length === 0 && surplusItems.length === 0) {
          const itemsWithNote = allItems.filter((item) => item.surplusNote?.trim());
          if (itemsWithNote.length > 0) {
            if (noteLines.length > 0) noteLines.push('');
            noteLines.push('--- Chi tiết sản phẩm ---');
            itemsWithNote.forEach((item) => {
              const name = item.variantName
                ? `${item.productName} (${item.variantName})`
                : item.productName;
              noteLines.push(`• ${name} (SL: ${item.quantity}): ${item.surplusNote.trim()}`);
            });
          }
        }

        if (noteLines.length > 0) {
          setValue('notes', noteLines.join('\n'), { shouldDirty: true });
        }

        setItems(groupPurchaseOrderItems(order.items ?? []));
        setPurchaseOrderError('');
      })
      .catch(() => {
        if (!ignore) toast.error('Không thể tải thông tin đơn mua hàng.');
      });
    return () => { ignore = true; };
  }, [purchaseOrderId, setValue]);

  useEffect(() => {
    const warehouseId = selectedWarehouseId || '';
    if (previousWarehouseIdRef.current && previousWarehouseIdRef.current !== warehouseId) {
      setItems([]);
      setModalOpen(false);
    }
    previousWarehouseIdRef.current = warehouseId;

    if (!warehouseId) {
      const timer = window.setTimeout(() => {
        setWarehouseVariants([]);
        setLoadingWarehouseVariants(false);
      }, 0);
      return () => window.clearTimeout(timer);
    }

    let ignore = false;
    const timer = window.setTimeout(() => {
      setLoadingWarehouseVariants(true);
      inventoryApi.getInventoryList(0, 10000, 'updatedAt', 'desc', null, null, false, { warehouseId })
        .then((response) => {
          if (ignore) return;
          const data = getResponseData(response);
          const variants = Array.isArray(data) ? data : (data.content ?? []);
          const normalizedVariants = aggregateWarehouseVariantsBySku(variants
            .map(normalizeWarehouseVariant)
            .filter((item) => item.id)
          );
          setWarehouseVariants(normalizedVariants);
        })
        .catch(() => {
          if (!ignore) {
            setWarehouseVariants([]);
            toast.error('Không thể tải sản phẩm thuộc kho đã chọn.');
          }
        })
        .finally(() => { if (!ignore) setLoadingWarehouseVariants(false); });
    }, 0);

    return () => {
      ignore = true;
      window.clearTimeout(timer);
    };
  }, [selectedWarehouseId]);

  useEffect(() => {
    if (prefillAppliedRef.current) return;

    const warehouseId = searchParams.get('warehouseId');
    const variantId = searchParams.get('variantId');
    if (!warehouseId && !variantId) return;

    prefillAppliedRef.current = true;
    if (warehouseId) {
      setValue('warehouseId', warehouseId, { shouldDirty: true, shouldValidate: true });
    }

    if (variantId) {
      const timer = window.setTimeout(() => {
        setItems([{
          variantId,
          sku: searchParams.get('sku') || '',
          productName: searchParams.get('productName') || searchParams.get('sku') || 'Sản phẩm',
          variantName: '',
          quantity: 1,
          unitPrice: searchParams.get('unitCost') || 0,
          salePrice: searchParams.get('salePrice') || searchParams.get('price') || 0,
        }]);
      }, 0);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [searchParams, setValue]);

  // ── Item handlers ─────────────────────────────────────────────────────────
  const openAddProducts = () => {
    if (!purchaseOrderId) {
      setPurchaseOrderError('Vui lòng chọn đơn mua hàng trước khi thêm sản phẩm.');
      return;
    }
    if (!selectedWarehouseId) {
      toast.error('Vui lòng chọn kho nhập trước khi thêm sản phẩm.');
      return;
    }
    if (loadingWarehouseVariants) {
      toast.info('Đang tải sản phẩm thuộc kho, vui lòng thử lại sau.');
      return;
    }
    setModalOpen(true);
  };

  const onQtyChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, quantity: v } : it));
  const onPriceChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, unitPrice: v } : it));
  const onRemove = (i) => setItems((p) => p.filter((_, idx) => idx !== i));

  const onDownloadExcelTemplate = async () => {
    try {
      const response = await stockReceiveService.downloadNewReceiptExtraItemsTemplate();
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'stock-in-extra-items-template.xlsx';
      anchor.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Không thể tải template Excel. Vui lòng thử lại.');
    }
  };

  const onAddProducts = (newItems) => {
    setItems((p) => {
      const skuKeys = new Set(p.map((it) => String(it.sku ?? '').trim().toLowerCase()).filter(Boolean));
      const ids = new Set(p.map((it) => it.variantId));
      return [
        ...p,
        ...newItems.filter((it) => {
          const skuKey = String(it.sku ?? '').trim().toLowerCase();
          return skuKey ? !skuKeys.has(skuKey) : !ids.has(it.variantId);
        }),
      ];
    });
    setModalOpen(false);
  };

  const handleExcel = (e) => {
    const file = e.target.files?.[0];
    if (fileRef.current) fileRef.current.value = '';
    if (!file) return;
    if (!selectedWarehouseId) {
      toast.error('Vui lòng chọn kho nhập trước khi import Excel.');
      return;
    }
    if (loadingWarehouseVariants) {
      toast.info('Đang tải sản phẩm thuộc kho, vui lòng thử lại sau.');
      return;
    }
    const reader = new FileReader();
    reader.onload = (ev) => {
      try {
        // cellFormula:false → dùng cached VLOOKUP value để lấy variantId từ hidden column D
        const wb = XLSX.read(new Uint8Array(ev.target.result), { type: 'array', cellFormula: false, cellNF: false });
        const rows = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: undefined }).slice(1);
        const parsed = parseExcelImportRows(rows, warehouseVariants, items);
        if (parsed.valid.length === 0) {
          if (parsed.errors.length > 0) downloadImportErrors(parsed.errors);
          toast.error('Không có dòng hợp lệ trong file. Đã tải file lỗi để kiểm tra.');
          return;
        }
        if (parsed.errors.length > 0) {
          downloadImportErrors(parsed.errors);
          toast.warn(`Có ${parsed.errors.length} dòng lỗi. Đã tải file lỗi; chỉ nhập ${parsed.valid.length} dòng hợp lệ.`);
        } else {
          toast.success(`Đã nhập ${parsed.valid.length} sản phẩm từ Excel.`);
        }
        setItems((previous) => {
          const updated = [...previous];
          parsed.valid.forEach((product) => {
            updated.push({
              variantId: product.id,
              variantIds: uniqueValues(product.variantIds ?? [product.id]),
              sku: product.sku,
              productName: product.productName,
              variantName: product.name,
              quantity: product.quantity,
              unitPrice: product.unitPrice,
              salePrice: product.salePrice ?? 0,
              platforms: itemPlatforms(product),
              channelNames: uniqueValues(product.channelNames ?? [product.channelName]),
              mergedVariantCount: product.mergedVariantCount ?? 1,
              fromPurchaseOrder: false,
            });
          });
          return updated;
        });
      } catch { toast.error('Không thể đọc dữ liệu từ file Excel.'); }
    };
    reader.onerror = () => toast.error('Không thể đọc dữ liệu từ file Excel.');
    reader.readAsArrayBuffer(file);
  };

  const onSubmit = handleSubmit(async (data) => {
    if (!purchaseOrderId) {
      setPurchaseOrderError('Đơn mua hàng là bắt buộc.');
      toast.error('Vui lòng chọn đơn mua hàng.');
      return;
    }
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }
    const invalidQty = items.find((it) => !it.quantity || Number(it.quantity) <= 0);
    if (invalidQty) { toast.error(`Sản phẩm "${invalidQty.productName}" phải có số lượng lớn hơn 0.`); return; }
    const invalidPrice = items.find((it) => { const price = Number(it.unitPrice); return it.unitPrice === '' || it.unitPrice === null || it.unitPrice === undefined || isNaN(price) || price < 0; });
    if (invalidPrice) { toast.error(`Đơn giá của sản phẩm "${invalidPrice.productName}" phải lớn hơn hoặc bằng 0.`); return; }

    const confirmed = await confirm({
      title: 'Hoàn thành nhập kho?',
      message: `Xác nhận nhập ${items.length} sản phẩm (tổng SL: ${totalQty.toLocaleString()}) vào kho.\nTồn kho sẽ được cập nhật ngay sau khi hoàn thành.`,
      confirmLabel: 'Hoàn thành nhập kho',
      tone: 'warning',
    });
    if (!confirmed) return;

    try {
      const response = await stockReceiveService.createReceipt({
        purchaseOrderId: purchaseOrderId || null,
        warehouseId: data.warehouseId, supplierId: data.supplierId || null, invoiceNumber: null,
        receivedAt: data.receivedAt, notes: data.notes || null,
        items: expandReceiptItems(items), isDraft: false,
      });
      toast.success('Tạo phiếu nhập thành công.');
      const receipt = getResponseData(response);

      // Notify user if a shortage/surplus order was auto-created
      if (receipt.autoCreatedOrderCode) {
        const typeLabel = receipt.autoCreatedOrderType === 'SHORTAGE' ? 'bổ sung (hàng thiếu)' : 'thặng dư (hàng thừa)';
        const toastId = `auto-order-${receipt.autoCreatedOrderCode}`;
        toast.info(
          `🔔 Đã tự động tạo đơn ${typeLabel}: ${receipt.autoCreatedOrderCode}\n${receipt.autoCreatedOrderSummary || ''}`,
          { autoClose: 8000, toastId }
        );
      }
      if (receipt.marketplaceSyncAvailable) {
        const platforms = (receipt.marketplacePlatforms ?? []).map((platform) => PLATFORM_LABELS[platform] ?? platform).join(', ');
        const shouldSync = await confirm({
          title: 'Đồng bộ tồn có thể bán và giá lên sàn?',
          message: `Tồn kho và giá bán đã được cập nhật. Đồng bộ số lượng có thể bán và giá mới lên ${platforms || 'các sàn đang bán'} ngay bây giờ?`,
          confirmLabel: 'Đồng bộ ngay',
          cancelLabel: 'Để sau',
        });
        if (shouldSync) {
          try {
            await stockReceiveService.syncReceiptMarketplaceInventory(receipt.id);
            toast.success('Đã đồng bộ tồn có thể bán và giá lên các sàn liên quan.');
          } catch (syncError) {
            toast.error(syncError?.response?.data?.message || 'Nhập kho thành công nhưng đồng bộ sàn thất bại.');
          }
        }
      }
      runWithoutGuard(() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS));
    } catch (error) {
      if (error?.response?.data?.data && typeof error.response.data.data === 'object') {
        toast.error(Object.values(error.response.data.data)[0] || 'Có lỗi xảy ra.');
      } else {
        toast.error(error?.response?.data?.message || error?.message || 'Không thể tạo phiếu nhập.');
      }
    }
  });

  const onSaveDraft = handleSubmit(async (data) => {
    if (!purchaseOrderId) {
      setPurchaseOrderError('Đơn mua hàng là bắt buộc.');
      toast.error('Vui lòng chọn đơn mua hàng.');
      return;
    }
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }
    try {
      await stockReceiveService.createReceipt({
        purchaseOrderId: purchaseOrderId || null,
        warehouseId: data.warehouseId, supplierId: data.supplierId || null, invoiceNumber: null,
        receivedAt: data.receivedAt, notes: data.notes || null,
        items: expandReceiptItems(items), isDraft: true,
      });
      toast.success('Lưu tạm phiếu nhập thành công.');
      runWithoutGuard(() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS));
    } catch (error) {
      if (error?.response?.data?.data && typeof error.response.data.data === 'object') {
        toast.error(Object.values(error.response.data.data)[0] || 'Có lỗi xảy ra.');
      } else {
        toast.error(error?.response?.data?.message || error?.message || 'Không thể lưu tạm phiếu nhập.');
      }
    }
  });

  const today = new Date().toISOString().split('T')[0];

  return (
    <div className={`${styles.page} product-workspace`}>

      {/* Page Header */}
      <div className={styles.pageHeader}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}>
          <ArrowLeft size={15} /> Quay lại
        </button>
        <div className={styles.headerIcon} style={{ background: '#eff6ff' }}>
          <PackagePlus size={18} color="#2563eb" />
        </div>
        <div>
          <h1 className={styles.headerTitle}>Tạo phiếu nhập kho</h1>
          <p className={styles.headerSubtitle}>Nhập hàng hóa từ nhà cung cấp vào kho</p>
        </div>
      </div>

      {/* Tabs — giống StockDeliveryCreatePage */}
      <div style={{ display: 'flex', gap: 6 }}>
        <button
          type="button"
          className={`${styles.actionBtn} ${styles.primaryBtn}`}
          style={{ padding: '7px 16px' }}
        >
          Theo đơn mua hàng
        </button>
        <button
          type="button"
          onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE_MANUAL)}
          className={`${styles.actionBtn} ${styles.secondaryBtn}`}
          style={{ padding: '7px 16px' }}
        >
          Nhập thủ công
        </button>
      </div>

      {/* Two-column layout */}
      <div className={styles.twoCol}>

        {/* LEFT */}
        <div className={styles.leftCol}>

          {/* Card: Thông tin phiếu nhập */}
          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionHeaderIcon} style={{ background: '#eff6ff' }}><Package size={16} color="#2563eb" /></div>
              <div>
                <div className={styles.sectionTitle}>Thông tin phiếu nhập</div>
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label className={styles.fieldLabel}>Đơn mua hàng <span>*</span></label>
              <select
                value={purchaseOrderId}
                onChange={(event) => {
                  setPurchaseOrderId(event.target.value);
                  setPurchaseOrderError(event.target.value ? '' : 'Đơn mua hàng là bắt buộc.');
                  if (!event.target.value) setItems([]);
                }}
                className={`${styles.fieldSelect} ${purchaseOrderError ? styles.fieldError : ''}`}
                aria-invalid={Boolean(purchaseOrderError)}
                aria-describedby={purchaseOrderError ? 'purchase-order-error' : undefined}
              >
                <option value="">Chọn đơn mua hàng đã kiểm tra</option>
                {receivingPurchaseOrders.map((order) => (
                  <option key={order.id} value={order.id}>{order.orderCode} — {order.supplierName}</option>
                ))}
              </select>
              {purchaseOrderError && <p id="purchase-order-error" className={styles.fieldErrorMsg} role="alert">{purchaseOrderError}</p>}
              <p style={{ margin: '6px 0 0', color: '#64748b', fontSize: 11.5 }}>
              </p>
            </div>
            <div className={`${styles.formGrid} ${styles.formGrid2}`} style={{ gridTemplateColumns: '1fr 1fr 1fr 1fr' }}>
              <div>
                <label className={styles.fieldLabel}>Kho nhập <span>*</span></label>
                <select {...register('warehouseId')} aria-disabled={!!purchaseOrderId} style={purchaseOrderId ? { pointerEvents: 'none', background: '#f8fafc' } : undefined} className={`${styles.fieldSelect} ${errors.warehouseId ? styles.fieldError : ''}`}>
                  {warehouses.length === 0 && <option value="">Chọn kho</option>}
                  {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}{w.address ? ` — ${w.address}` : ''}</option>)}
                </select>
                {errors.warehouseId && <p className={styles.fieldErrorMsg}>{errors.warehouseId.message}</p>}
              </div>
              <div>
                <label className={styles.fieldLabel}>Nhà cung cấp</label>
                <select {...register('supplierId')} aria-disabled={!!purchaseOrderId} style={purchaseOrderId ? { pointerEvents: 'none', background: '#f8fafc' } : undefined} className={styles.fieldSelect}>
                  <option value="">Chọn nhà cung cấp</option>
                  {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div>
                <label className={styles.fieldLabel}>Mã phiếu</label>
                <input value={nextReceiptCode || 'Đang tạo mã...'} disabled className={styles.fieldInput} />
              </div>
              <div>
                <label className={styles.fieldLabel}>Ngày nhập <span>*</span></label>
                <input type="date" max={today} {...register('receivedAt')} className={`${styles.fieldInput} ${errors.receivedAt ? styles.fieldError : ''}`} />
                {errors.receivedAt && <p className={styles.fieldErrorMsg}>{errors.receivedAt.message}</p>}
              </div>
            </div>
            <div style={{ marginTop: 14 }}>
              <label className={styles.fieldLabel}>Ghi chú</label>
              <textarea {...register('notes')} rows={2} placeholder="Ghi chú về phiếu nhập..." className={styles.fieldTextarea} />
            </div>
          </div>

          {/* Card: Danh sách sản phẩm */}
          <div className={`${styles.card} ${styles.tableCard}`}>
            <div className={styles.tableCardHeader}>
              <div>
                <div className={styles.tableCardTitle}>Danh sách sản phẩm nhập</div>
              </div>
              <div className={styles.tableCardActions}>
                <input ref={fileRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleExcel} />
                <button className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={openAddProducts} disabled={!purchaseOrderId || !selectedWarehouseId || loadingWarehouseVariants}>
                  {loadingWarehouseVariants ? <Loader2 className={styles.primaryIcon} /> : <Plus className={styles.primaryIcon} />} Thêm sản phẩm
                </button>
              </div>
            </div>

            {items.length === 0 ? (
              <div className={styles.emptyState}>
                <div className={styles.emptyIcon}><Package size={22} /></div>
                <p className={styles.emptyTitle}>Chưa có sản phẩm nào</p>
                <p className={styles.emptySubtitle}>
                  {selectedWarehouseId
                    ? 'Nhấn "Thêm sản phẩm" hoặc Import từ Excel'
                    : 'Vui lòng chọn kho nhập trước khi thêm sản phẩm'}
                </p>
              </div>
            ) : (
              <div className={styles.tableWrapper}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      {['Tên sản phẩm', 'SKU', 'Số lượng', 'Đơn giá (₫)', 'Thành tiền', ''].map((h, i) => (
                        <th key={h} className={i === 4 ? styles.thRight : ''}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, idx) => {
                      const qtyBad = !item.quantity || item.quantity === '' || Number(item.quantity) <= 0;
                      const price = Number(item.unitPrice);
                      const priceBad = item.unitPrice === '' || item.unitPrice === null || item.unitPrice === undefined || isNaN(price) || price < 0;
                      const line = (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0);
                      return (
                        <tr key={item.variantId ?? idx}>
                          <td>
                            <div style={{ fontWeight: 600, fontSize: 12 }}>{item.productName}</div>
                            {item.variantName && <div style={{ fontSize: 11, color: '#94a3b8' }}>{item.variantName}</div>}
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 5 }}>
                              {renderPlatformBadges(item)}
                            </div>
                          </td>
                          <td><span className={styles.skuTag} style={{ background: '#e0f2fe', color: '#0369a1' }}>{item.sku}</span></td>
                          <td>
                            <input type="number" min="1" step="1" value={item.quantity} disabled={item.fromPurchaseOrder} onChange={(e) => onQtyChange(idx, e.target.value)}
                              className={`${styles.tableInput} ${qtyBad ? styles.inputError : ''}`} />
                          </td>
                          <td>
                            <input type="number" min="0" step="1000" value={item.unitPrice} disabled={item.fromPurchaseOrder} onChange={(e) => onPriceChange(idx, e.target.value)}
                              className={`${styles.tableInput} ${priceBad ? styles.inputError : ''}`} />
                            {Number(item.salePrice) > 0 && (
                              <div style={{ marginTop: 4, fontSize: 10.5, color: '#64748b', whiteSpace: 'nowrap' }}>
                                Giá bán: <strong style={{ color: '#0f172a', fontWeight: 600 }}>{formatVND(Number(item.salePrice))}</strong>
                              </div>
                            )}
                          </td>
                          <td className={styles.tdRight} style={{ fontWeight: 700, color: line > 0 ? '#2563eb' : '#94a3b8', fontSize: 12, whiteSpace: 'nowrap' }}>
                            {line > 0 ? formatVND(line) : '—'}
                          </td>
                          <td>
                            {!item.fromPurchaseOrder && <button className={styles.removeBtn} onClick={() => onRemove(idx)} aria-label={`Xóa ${item.productName}`}>
                              <Trash2 size={13} />
                            </button>}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {items.length > 0 && (
              <div className={styles.tableFooter}>
                <div className={styles.tableFooterLeft}>
                  <span><strong>{items.length}</strong> sản phẩm</span>
                  <span>Tổng SL: <strong>{totalQty.toLocaleString()}</strong></span>
                </div>
                <div className={styles.tableFooterRight}>
                  <div className={styles.tableFooterRightLabel}>Tổng giá trị</div>
                  <div className={styles.tableFooterRightValue}>{formatVND(totalAmount)}</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* RIGHT */}
        <div className={styles.rightCol}>

          {/* Actions */}
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button className={`${styles.actionBtn} ${styles.backBtn}`} onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}>
                <ArrowLeft size={14} /> Hủy
              </button>
              <button className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={onSaveDraft} disabled={isSubmitting}>
                <PackagePlus className={styles.tealIcon} />
                {isSubmitting ? 'Đang xử lý...' : 'Lưu tạm'}
              </button>
              <button className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={onSubmit} disabled={isSubmitting}>
                <PackagePlus className={styles.primaryIcon} />
                {isSubmitting ? 'Đang xử lý...' : 'Hoàn thành nhập kho'}
              </button>
            </div>
          </div>

          {/* Summary */}
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Tổng quan</div>
            <div className={styles.sidebarRow}>
              <span className={styles.sidebarLabel}>Số sản phẩm</span>
              <span className={styles.sidebarValue}>{items.length}</span>
            </div>
            <div className={styles.sidebarRow}>
              <span className={styles.sidebarLabel}>Tổng số lượng</span>
              <span className={styles.sidebarValue}>{totalQty.toLocaleString()}</span>
            </div>
            <div className={styles.sidebarRow}>
              <span className={styles.sidebarLabel}>Tổng giá trị</span>
              <span className={styles.sidebarValue} style={{ color: totalAmount > 0 ? '#2563eb' : '#475569' }}>{totalAmount > 0 ? formatVND(totalAmount) : '—'}</span>
            </div>
          </div>

          {/* Notes */}
          <div className={`${styles.card} ${styles.noteCard}`} style={{ background: '#eff6ff', border: '1px solid #bfdbfe' }}>
            <div className={styles.noteHeader}>
              <AlertCircle size={14} color="#2563eb" />
              <span className={styles.noteTitle} style={{ color: '#1e40af' }}>Lưu ý khi nhập kho</span>
            </div>
            <ul className={styles.noteList}>
              {['Kiểm tra số lượng và đơn giá trước khi hoàn thành.', 'Tồn kho sẽ cập nhật sau khi hoàn thành nhập kho.', 'Lưu tạm để tiếp tục chỉnh sửa sau.', 'Import Excel để nhập nhanh nhiều sản phẩm.'].map((note) => (
                <li key={note} className={styles.noteItem} style={{ color: '#1e40af' }}>{note}</li>
              ))}
            </ul>
          </div>

          {/* Excel guide */}
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Định dạng file Excel</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5, fontSize: 11, color: '#475569', lineHeight: 1.5 }}>
              <div><strong style={{ color: '#0f172a' }}>Cột A:</strong> SKU sản phẩm</div>
              <div><strong style={{ color: '#0f172a' }}>Cột B:</strong> Số lượng nhập</div>
              <div><strong style={{ color: '#0f172a' }}>Cột C:</strong> Đơn giá (₫)</div>
            </div>
          </div>
        </div>
      </div>

      <AddProductModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onConfirm={onAddProducts}
        existingVariantIds={items.map((i) => i.variantId)}
        existingSkus={items.map((i) => String(i.sku ?? '').trim().toLowerCase()).filter(Boolean)}
        products={warehouseVariants}
        loading={loadingWarehouseVariants}
      />
      {ConfirmDialog}
    </div>
  );
}
