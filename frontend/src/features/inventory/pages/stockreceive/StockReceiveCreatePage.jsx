import { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import {
  ArrowLeft, Plus, Trash2, Search, X,
  FileSpreadsheet, PackagePlus, AlertCircle, Loader2,
} from 'lucide-react';
import * as XLSX from 'xlsx';

import warehouseService from '../../services/warehouseService';
import supplierService from '../../services/supplierService';
import stockReceiveService from '../../services/stockReceiveService';
import axiosClient from '../../../../api/axiosClient';
import { ROUTES } from '../../../../app/router/routes';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

// ── Zod schema ────────────────────────────────────────────────────────────────
const schema = z.object({
  warehouseId: z.string().min(1, 'Vui lòng chọn kho nhập.'),
  supplierId: z.string().optional().nullable(),
  invoiceNumber: z.string().max(100).optional(),
  receivedAt: z.string().min(1, 'Ngày nhập là bắt buộc.').refine(
    (v) => v <= new Date().toISOString().split('T')[0],
    { message: 'Ngày nhập không được lớn hơn ngày hiện tại.' }
  ),
  notes: z.string().optional(),
});

// ── Add Product Modal ─────────────────────────────────────────────────────────
function AddProductModal({ isOpen, onClose, onConfirm, existingVariantIds = [] }) {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState([]);
  const [selected, setSelected] = useState({});
  const [loading, setLoading] = useState(false);
  const timerRef = useRef(null);

  useEffect(() => {
    if (!isOpen) { setKeyword(''); setResults([]); setSelected({}); }
  }, [isOpen]);

  // Load all variants when modal opens (no keyword = show all, with keyword = search)
  useEffect(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const params = { page: 0, size: 50 };
        if (keyword.trim()) params.search = keyword.trim();
        const res = await axiosClient.get('/catalog/variants', { params });
        // Backend: ApiResponse<PageResponse<ProductVariantResponse>>
        // res.data = { success, data: { content: [...], totalPages, ... } }
        const paged = res.data?.data ?? res.data ?? {};
        const items = paged.content ?? (Array.isArray(paged) ? paged : []);
        setResults(items);
      } catch { setResults([]); }
      finally { setLoading(false); }
    }, keyword.trim() ? 300 : 0);
    return () => clearTimeout(timerRef.current);
  }, [keyword, isOpen]);

  // API trả về: { id, sku, name (variant name), productName, productId, ... }
  // Dùng item.id làm key (không phải item.variantId)
  const toggle = (item) => {
    if (existingVariantIds.includes(item.id)) return;
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
      style={{ position: 'fixed', inset: 0, zIndex: 60, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0,0,0,0.45)' }}>
      <div style={{ width: '100%', maxWidth: 540, backgroundColor: '#fff', borderRadius: 16, boxShadow: '0 24px 48px rgba(0,0,0,0.15)', display: 'flex', flexDirection: 'column', maxHeight: '88vh', overflow: 'hidden' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 24px', borderBottom: '1px solid #f1f5f9' }}>
          <div>
            <span style={{ fontSize: 16, fontWeight: 600, color: '#0f172a' }}>Chọn sản phẩm</span>
            {results.length > 0 && (
              <span style={{ marginLeft: 8, fontSize: 12, color: '#94a3b8' }}>({results.length} sản phẩm)</span>
            )}
          </div>
          <button onClick={onClose} style={{ width: 28, height: 28, borderRadius: 6, border: 'none', background: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}>
            <X size={18} />
          </button>
        </div>

        {/* Search */}
        <div style={{ padding: '12px 24px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
            <input autoFocus value={keyword} onChange={(e) => setKeyword(e.target.value)}
              placeholder="Lọc theo tên sản phẩm hoặc SKU..."
              style={{ width: '100%', padding: '8px 12px 8px 32px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, outline: 'none', boxSizing: 'border-box' }} />
          </div>
        </div>

        {/* Results */}
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {loading && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '32px 0', color: '#94a3b8', fontSize: 13 }}>
              <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải...
            </div>
          )}
          {!loading && results.length === 0 && (
            <p style={{ textAlign: 'center', padding: '32px 0', color: '#94a3b8', fontSize: 13 }}>
              {keyword.trim() ? 'Không tìm thấy sản phẩm nào phù hợp.' : 'Không có sản phẩm nào.'}
            </p>
          )}
          {!loading && results.map((item) => {
            // item.id = variantId, item.name = variant name, item.productName = product name
            const isExisting = existingVariantIds.includes(item.id);
            const isSelected = Boolean(selected[item.id]);
            return (
              <div key={item.id} onClick={() => toggle(item)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '12px 24px',
                  cursor: isExisting ? 'not-allowed' : 'pointer',
                  backgroundColor: isExisting ? '#f8fafc' : isSelected ? '#eff6ff' : '#fff',
                  borderBottom: '1px solid #f1f5f9', opacity: isExisting ? 0.6 : 1,
                }}>
                <input type="checkbox" checked={isSelected} disabled={isExisting} onChange={() => toggle(item)} onClick={(e) => e.stopPropagation()}
                  style={{ width: 16, height: 16, accentColor: '#2563eb', flexShrink: 0 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>{item.productName}</span>
                    {item.name && (
                      <span style={{ fontSize: 12, color: '#64748b', backgroundColor: '#f1f5f9', padding: '1px 6px', borderRadius: 4 }}>
                        {item.name}
                      </span>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 4, alignItems: 'center' }}>
                    <span style={{ fontSize: 11, fontFamily: 'monospace', backgroundColor: '#e0f2fe', color: '#0369a1', padding: '2px 6px', borderRadius: 4 }}>
                      {item.sku}
                    </span>
                    {isExisting && (
                      <span style={{ fontSize: 11, backgroundColor: '#fef3c7', color: '#b45309', padding: '1px 6px', borderRadius: 4 }}>Đã có</span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div style={{ padding: '14px 24px', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>
            {count > 0 ? `Đã chọn ${count} sản phẩm` : 'Chưa chọn sản phẩm nào'}
          </span>
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={onClose} style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>Hủy</button>
            <button
              onClick={() => {
                onConfirm(Object.values(selected).map((i) => ({
                  variantId: i.id,          // i.id = UUID of variant
                  sku: i.sku,
                  productName: i.productName,
                  variantName: i.name,      // i.name = variant name
                  quantity: 1,
                  unitPrice: 0,
                })));
              }}
              disabled={count === 0}
              style={{ padding: '8px 20px', borderRadius: 8, border: 'none', backgroundColor: count === 0 ? '#93c5fd' : '#2563eb', color: '#fff', fontSize: 13, fontWeight: 500, cursor: count === 0 ? 'not-allowed' : 'pointer' }}>
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
  const fileRef = useRef(null);
  const [items, setItems] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [warehouses, setWarehouses] = useState([]);
  const [suppliers, setSuppliers] = useState([]);

  const { register, handleSubmit, formState: { errors, isSubmitting, isDirty } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { warehouseId: '', supplierId: '', invoiceNumber: '', receivedAt: new Date().toISOString().split('T')[0], notes: '' },
  });

  const totalAmount = useMemo(
    () => items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0),
    [items]
  );

  useEffect(() => {
    Promise.all([warehouseService.getAll(), supplierService.getAll()])
      .then(([wRes, sRes]) => {
        const extract = (r) => {
          const d = r?.data?.data ?? r?.data;
          // Handle paginated response { content: [] } or plain array
          if (Array.isArray(d)) return d;
          if (d?.content && Array.isArray(d.content)) return d.content;
          return [];
        };
        setWarehouses(extract(wRes));
        setSuppliers(extract(sRes));
      })
      .catch(() => { });
  }, []);

  // ── Item handlers ─────────────────────────────────────────────────────────
  const onQtyChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, quantity: v } : it));
  const onPriceChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, unitPrice: v } : it));
  const onRemove = (i) => setItems((p) => p.filter((_, idx) => idx !== i));

  const onAddProducts = (newItems) => {
    setItems((p) => {
      const ids = new Set(p.map((it) => it.variantId));
      return [...p, ...newItems.filter((it) => !ids.has(it.variantId))];
    });
    setModalOpen(false);
  };

  // ── Excel import ──────────────────────────────────────────────────────────
  const handleExcel = (e) => {
    const file = e.target.files?.[0];
    if (fileRef.current) fileRef.current.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      try {
        const wb = XLSX.read(new Uint8Array(ev.target.result), { type: 'array' });
        const rows = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: undefined }).slice(1);
        const valid = []; const errs = [];
        rows.forEach((row, idx) => {
          const rn = idx + 2;
          const rawSku = row[0]; const rawQty = row[1]; const rawPrice = row[2];
          if (!rawSku && !rawQty && !rawPrice) return;
          let ok = true;
          const sku = rawSku ? String(rawSku).trim() : '';
          if (!sku) { errs.push(`Dòng ${rn}: SKU không hợp lệ.`); ok = false; }
          const qty = Number(rawQty);
          if (!rawQty || isNaN(qty) || qty <= 0) { errs.push(`Dòng ${rn}: Số lượng phải lớn hơn 0.`); ok = false; }
          const price = Number(rawPrice);
          if (rawPrice === undefined || isNaN(price) || price < 0) { errs.push(`Dòng ${rn}: Đơn giá không hợp lệ.`); ok = false; }
          if (ok) valid.push({ sku, quantity: qty, unitPrice: price });
        });
        if (valid.length === 0) { toast.error('Không có dòng hợp lệ nào trong file.'); return; }
        if (errs.length > 0) toast.warn(`Có ${errs.length} dòng lỗi. Chỉ nhập ${valid.length} dòng hợp lệ.`);
        else toast.success(`Đã nhập ${valid.length} sản phẩm từ Excel.`);
        setItems((prev) => {
          const updated = [...prev];
          valid.forEach((p) => {
            const idx2 = updated.findIndex((it) => it.sku === p.sku);
            if (idx2 >= 0) { updated[idx2] = { ...updated[idx2], quantity: p.quantity, unitPrice: p.unitPrice }; }
            else updated.push({ variantId: `excel-${p.sku}`, sku: p.sku, productName: p.sku, variantName: '', quantity: p.quantity, unitPrice: p.unitPrice });
          });
          return updated;
        });
      } catch { toast.error('Không thể đọc dữ liệu từ file Excel.'); }
    };
    reader.onerror = () => toast.error('Không thể đọc dữ liệu từ file Excel.');
    reader.readAsArrayBuffer(file);
  };

  // ── Submit ────────────────────────────────────────────────────────────────
  const onSubmit = handleSubmit(async (data) => {
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }

    // Validate quantity and unitPrice for CONFIRMED receipts
    // Số lượng bắt buộc phải lớn hơn 0
    const invalidQty = items.find((it) => !it.quantity || Number(it.quantity) <= 0);
    if (invalidQty) {
      toast.error(`Sản phẩm "${invalidQty.productName}" phải có số lượng lớn hơn 0.`);
      return;
    }

    // Đơn giá phải lớn hơn hoặc bằng 0
    const invalidPrice = items.find((it) => {
      const price = Number(it.unitPrice);
      return it.unitPrice === '' || it.unitPrice === null || it.unitPrice === undefined || isNaN(price) || price < 0;
    });
    if (invalidPrice) {
      toast.error(`Đơn giá của sản phẩm "${invalidPrice.productName}" phải lớn hơn hoặc bằng 0.`);
      return;
    }

    // Validate invoice number for CONFIRMED receipts
    if (!data.invoiceNumber || data.invoiceNumber.trim() === '') {
      toast.error('Số hóa đơn là bắt buộc khi xác nhận phiếu nhập.');
      return;
    }

    try {
      await stockReceiveService.createReceipt({
        warehouseId: data.warehouseId,
        supplierId: data.supplierId || null,
        invoiceNumber: data.invoiceNumber || null,
        receivedAt: data.receivedAt,
        notes: data.notes || null,
        items: items.map((it) => ({ variantId: it.variantId, quantity: Number(it.quantity), unitCost: Number(it.unitPrice) })),
        isDraft: false, // Confirmed receipt
      });
      toast.success('Tạo phiếu nhập thành công.');
      navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS);
    } catch (error) {
      // Handle validation errors from backend
      if (error?.response?.data?.data && typeof error.response.data.data === 'object') {
        // Validation errors: { field: message, ... }
        const validationErrors = error.response.data.data;
        const firstError = Object.values(validationErrors)[0];
        toast.error(firstError || 'Có lỗi xảy ra. Vui lòng kiểm tra lại thông tin.');
      } else {
        const errorMessage = error?.response?.data?.message || error?.message || 'Không thể tạo phiếu nhập. Vui lòng thử lại.';
        toast.error(errorMessage);
      }
    }
  });

  // ── Save as draft ─────────────────────────────────────────────────────────
  const onSaveDraft = handleSubmit(async (data) => {
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }

    // For DRAFT: quantity and unitPrice can be 0 or null, but we'll set defaults
    // No validation needed for draft

    try {
      await stockReceiveService.createReceipt({
        warehouseId: data.warehouseId,
        supplierId: data.supplierId || null,
        invoiceNumber: data.invoiceNumber || null,
        receivedAt: data.receivedAt,
        notes: data.notes || null,
        items: items.map((it) => ({
          variantId: it.variantId,
          quantity: it.quantity ? Number(it.quantity) : null,
          unitCost: it.unitPrice !== '' && it.unitPrice !== null && it.unitPrice !== undefined ? Number(it.unitPrice) : null
        })),
        isDraft: true, // Draft receipt
      });
      toast.success('Lưu tạm phiếu nhập thành công.');
      navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS);
    } catch (error) {
      // Handle validation errors from backend
      if (error?.response?.data?.data && typeof error.response.data.data === 'object') {
        // Validation errors: { field: message, ... }
        const validationErrors = error.response.data.data;
        const firstError = Object.values(validationErrors)[0];
        toast.error(firstError || 'Có lỗi xảy ra. Vui lòng kiểm tra lại thông tin.');
      } else {
        const errorMessage = error?.response?.data?.message || error?.message || 'Không thể lưu tạm phiếu nhập. Vui lòng thử lại.';
        toast.error(errorMessage);
      }
    }
  });

  const handleCancel = () => {
    if (isDirty || items.length > 0) {
      if (!window.confirm('Bạn có chắc muốn hủy? Các thay đổi chưa lưu sẽ bị mất.')) return;
    }
    navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS);
  };

  const today = new Date().toISOString().split('T')[0];

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 64px - 48px)', overflow: 'hidden' }}>
      {/* Header - Compact */}
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 6, border: '1px solid #e2e8f0', background: '#fff', fontSize: 12, color: '#374151', cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
          >
            <ArrowLeft size={14} /> Quay lại
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <PackagePlus size={14} color="#2563eb" />
            </div>
            <div>
              <h1 style={{ fontSize: 16, fontWeight: 700, color: '#0f172a', margin: 0, lineHeight: 1.2 }}>Tạo phiếu nhập kho</h1>
              <p style={{ fontSize: 11, color: '#64748b', margin: 0 }}>Nhập hàng hóa từ nhà cung cấp vào kho</p>
            </div>
          </div>
        </div>
      </div>

      {/* ═══════════════════════════════════════════════════════════ */}
      {/* CARD 1: THÔNG TIN PHIẾU NHẬP - Full Width */}
      {/* ═══════════════════════════════════════════════════════════ */}
      <div style={{ backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', padding: '14px 16px', marginBottom: 12, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>Thông tin phiếu nhập</div>
        </div>

        {/* Form Fields - Horizontal */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          {/* Kho nhập */}
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
              Kho nhập <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <select {...register('warehouseId')}
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: `1px solid ${errors.warehouseId ? '#fca5a5' : '#e2e8f0'}`, fontSize: 11, color: '#0f172a', outline: 'none', backgroundColor: '#fff', boxSizing: 'border-box' }}>
              <option value="">Chọn kho</option>
              {warehouses.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.name}{w.address ? ` - ${w.address}` : ''}
                </option>
              ))}
            </select>
            {errors.warehouseId && <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626' }}>{errors.warehouseId.message}</p>}
          </div>

          {/* Nhà cung cấp */}
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>Nhà cung cấp</label>
            <select {...register('supplierId')}
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 11, color: '#0f172a', outline: 'none', backgroundColor: '#fff', boxSizing: 'border-box' }}>
              <option value="">Chọn nhà cung cấp</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>

          {/* Số hóa đơn */}
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
              Số hóa đơn <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input {...register('invoiceNumber')} maxLength={100} placeholder="INV-2026-001"
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 11, outline: 'none', boxSizing: 'border-box' }} />
          </div>

          {/* Ngày nhập */}
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
              Ngày nhập <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input type="date" max={today} {...register('receivedAt')}
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: `1px solid ${errors.receivedAt ? '#fca5a5' : '#e2e8f0'}`, fontSize: 11, outline: 'none', boxSizing: 'border-box' }} />
            {errors.receivedAt && <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626' }}>{errors.receivedAt.message}</p>}
          </div>
        </div>

        {/* Ghi chú - Full width below */}
        <div style={{ marginTop: 12 }}>
          <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>Ghi chú</label>
          <textarea {...register('notes')} rows={2} placeholder="Ghi chú về phiếu nhập..."
            style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 11, outline: 'none', resize: 'none', boxSizing: 'border-box' }} />
        </div>

        {/* Action Buttons - Right side */}
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button onClick={handleCancel}
            style={{ padding: '6px 14px', borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: '#fff', fontSize: 12, fontWeight: 500, color: '#374151', cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
          >
            Hủy
          </button>
          <button onClick={onSaveDraft} disabled={isSubmitting}
            style={{ padding: '6px 14px', borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: isSubmitting ? '#f8fafc' : '#fff', color: isSubmitting ? '#94a3b8' : '#374151', fontSize: 12, fontWeight: 500, cursor: isSubmitting ? 'not-allowed' : 'pointer' }}
            onMouseEnter={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#f8fafc'; }}
            onMouseLeave={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#fff'; }}
          >
            {isSubmitting ? 'Đang xử lý...' : 'Lưu tạm'}
          </button>
          <button onClick={onSubmit} disabled={isSubmitting}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 16px', borderRadius: 6, border: 'none', backgroundColor: isSubmitting ? '#93c5fd' : '#2563eb', color: '#fff', fontSize: 12, fontWeight: 600, cursor: isSubmitting ? 'not-allowed' : 'pointer' }}
            onMouseEnter={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#1d4ed8'; }}
            onMouseLeave={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#2563eb'; }}
          >
            <PackagePlus size={14} /> {isSubmitting ? 'Đang xử lý...' : 'Hoàn thành nhập kho'}
          </button>
        </div>
      </div>

      {/* ═══════════════════════════════════════════════════════════ */}
      {/* 2-COLUMN LAYOUT: Danh sách sản phẩm | Lưu ý */}
      {/* ═══════════════════════════════════════════════════════════ */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 12, flex: 1, minHeight: 0 }}>

        {/* ── LEFT: Danh sách sản phẩm nhập ───────────────────────────── */}
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>

          {/* Product table card */}
          <div style={{ backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', overflow: 'hidden', display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Card header - Compact */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid #f1f5f9', flexShrink: 0 }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>Danh sách sản phẩm nhập</div>
                <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 1 }}>Thêm sản phẩm và điền thông tin số lượng, đơn giá</div>
              </div>
              <div style={{ display: 'flex', gap: 6 }}>
                {/* Excel button */}
                <button onClick={() => fileRef.current?.click()}
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 9px', borderRadius: 6, border: '1px solid #e2e8f0', background: '#fff', fontSize: 11, fontWeight: 500, color: '#374151', cursor: 'pointer' }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
                >
                  <FileSpreadsheet size={13} /> Import Excel
                </button>
                <input ref={fileRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleExcel} />

                {/* Add product button */}
                <button onClick={() => setModalOpen(true)}
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 11px', borderRadius: 6, border: 'none', backgroundColor: '#2563eb', color: '#fff', fontSize: 11, fontWeight: 500, cursor: 'pointer' }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#1d4ed8'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#2563eb'}
                >
                  <Plus size={13} /> Thêm sản phẩm
                </button>
              </div>
            </div>

            {/* Table - Scrollable */}
            {items.length === 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, gap: 6, padding: '30px 0' }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, backgroundColor: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <PackagePlus size={18} color="#94a3b8" />
                </div>
                <p style={{ fontSize: 12, color: '#64748b', fontWeight: 500, margin: 0 }}>Chưa có sản phẩm nào</p>
                <p style={{ fontSize: 11, color: '#94a3b8', margin: 0 }}>Nhấn "Thêm sản phẩm" hoặc Import từ Excel</p>
              </div>
            ) : (
              <div style={{ overflowY: 'auto', flex: 1, minHeight: 0 }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
                  <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8fafc', zIndex: 1 }}>
                    <tr style={{ borderBottom: '1px solid #e2e8f0' }}>
                      {['Tên sản phẩm', 'SKU', 'Số lượng', 'Đơn giá (₫)', 'Thành tiền', ''].map((h) => (
                        <th key={h} style={{ padding: '7px 10px', textAlign: h === 'Thành tiền' ? 'right' : 'left', fontWeight: 600, fontSize: 10, color: '#64748b', whiteSpace: 'nowrap' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, idx) => {
                      // Validation: Số lượng phải > 0
                      const qtyBad = !item.quantity || item.quantity === '' || Number(item.quantity) <= 0;
                      // Validation: Đơn giá phải >= 0
                      const price = Number(item.unitPrice);
                      const priceBad = item.unitPrice === '' || item.unitPrice === null || item.unitPrice === undefined || isNaN(price) || price < 0;
                      const line = (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0);
                      return (
                        <tr key={item.variantId ?? idx} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '7px 10px' }}>
                            <div style={{ fontWeight: 500, color: '#0f172a', fontSize: 11 }}>{item.productName}</div>
                            {item.variantName && <div style={{ fontSize: 9, color: '#94a3b8' }}>{item.variantName}</div>}
                          </td>
                          <td style={{ padding: '7px 10px' }}>
                            <span style={{ fontSize: 9, fontFamily: 'monospace', backgroundColor: '#f1f5f9', color: '#64748b', padding: '2px 4px', borderRadius: 3 }}>{item.sku}</span>
                          </td>
                          <td style={{ padding: '7px 10px', width: 90 }}>
                            <input 
                              type="number" 
                              min="1" 
                              step="1" 
                              value={item.quantity}
                              onChange={(e) => onQtyChange(idx, e.target.value)}
                              placeholder="Số lượng > 0"
                              title="Số lượng phải lớn hơn 0"
                              style={{ width: '100%', padding: '4px 5px', borderRadius: 4, border: `1px solid ${qtyBad ? '#fca5a5' : '#e2e8f0'}`, backgroundColor: qtyBad ? '#fff5f5' : '#fff', fontSize: 11, textAlign: 'right', outline: 'none', boxSizing: 'border-box' }} />
                          </td>
                          <td style={{ padding: '7px 10px', width: 120 }}>
                            <input 
                              type="number" 
                              min="0" 
                              step="1000" 
                              value={item.unitPrice}
                              onChange={(e) => onPriceChange(idx, e.target.value)}
                              placeholder="Đơn giá ≥ 0"
                              title="Đơn giá phải lớn hơn hoặc bằng 0"
                              style={{ width: '100%', padding: '4px 5px', borderRadius: 4, border: `1px solid ${priceBad ? '#fca5a5' : '#e2e8f0'}`, backgroundColor: priceBad ? '#fff5f5' : '#fff', fontSize: 11, textAlign: 'right', outline: 'none', boxSizing: 'border-box' }} />
                          </td>
                          <td style={{ padding: '7px 10px', textAlign: 'right', fontWeight: 600, color: line > 0 ? '#2563eb' : '#94a3b8', whiteSpace: 'nowrap', fontSize: 11 }}>
                            {line > 0 ? formatVND(line) : '—'}
                          </td>
                          <td style={{ padding: '7px 10px' }}>
                            <button onClick={() => onRemove(idx)} style={{ width: 22, height: 22, borderRadius: 4, border: 'none', background: 'none', cursor: 'pointer', color: '#94a3b8', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                              onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#fef2f2'; e.currentTarget.style.color = '#dc2626'; }}
                              onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.color = '#94a3b8'; }}>
                              <Trash2 size={12} />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {/* Footer summary - Compact */}
            {items.length > 0 && (
              <div style={{ borderTop: '1px solid #e2e8f0', padding: '8px 14px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#f8fafc', flexShrink: 0 }}>
                <div style={{ display: 'flex', gap: 12, fontSize: 11, color: '#64748b' }}>
                  <span><strong style={{ color: '#374151' }}>{items.length}</strong> sản phẩm</span>
                  <span>SL: <strong style={{ color: '#374151' }}>{items.reduce((s, i) => s + (Number(i.quantity) || 0), 0).toLocaleString()}</strong></span>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 9, color: '#94a3b8', marginBottom: 1 }}>Tổng giá trị</div>
                  <div style={{ fontSize: 14, fontWeight: 700, color: '#2563eb' }}>{formatVND(totalAmount)}</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* ── RIGHT: Lưu ý khi nhập kho ──────────────────────────────── */}
        <div style={{ backgroundColor: '#eff6ff', borderRadius: 8, border: '1px solid #bfdbfe', padding: '12px 14px', alignSelf: 'start' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
            <AlertCircle size={14} color="#1e40af" />
            <h3 style={{ fontSize: 12, fontWeight: 600, color: '#1e40af', margin: 0 }}>Lưu ý khi nhập kho</h3>
          </div>
          <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 6 }}>
            {[
              'Kiểm tra số lượng và đơn giá trước khi hoàn thành.',
              'Tồn kho sẽ cập nhật sau khi hoàn thành nhập kho.',
              'Lưu tạm để tiếp tục chỉnh sửa sau.',
              'Import Excel để nhập nhanh nhiều sản phẩm.',
            ].map((note) => (
              <li key={note} style={{ fontSize: 10, color: '#1e40af', lineHeight: 1.5, paddingLeft: 12, position: 'relative' }}>
                <span style={{ position: 'absolute', left: 0 }}>•</span> {note}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* Add product modal */}
      <AddProductModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onConfirm={onAddProducts}
        existingVariantIds={items.map((i) => i.variantId)}
      />
    </div>
  );
}
