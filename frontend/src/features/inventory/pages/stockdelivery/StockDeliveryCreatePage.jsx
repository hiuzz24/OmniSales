import { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import {
  ArrowLeft, Plus, Trash2, Search, X,
  FileSpreadsheet, PackageMinus, AlertCircle, Loader2, Info,
} from 'lucide-react';
import * as XLSX from 'xlsx';

import warehouseService from '../../services/warehouseService';
import stockDeliveryService from '../../services/stockDeliveryService';
import inventoryApi from '../../../../api/inventoryApi';
import axiosClient from '../../../../api/axiosClient';
import { ROUTES } from '../../../../app/router/routes';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

const formatNumber = (v) => new Intl.NumberFormat('vi-VN').format(v ?? 0);

const getResponseData = (response) => response?.data?.data ?? response?.data ?? response ?? {};

// ── Zod schema ────────────────────────────────────────────────────────────────
const schema = z.object({
  warehouseId: z.string().min(1, 'Vui lòng chọn kho xuất.'),
  issuedDate: z.string().min(1, 'Vui lòng chọn ngày xuất.'),
  recipient: z.string().min(1, 'Vui lòng nhập người nhận.').max(255, 'Người nhận tối đa 255 ký tự.'),
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

  // Load all variants when modal opens
  useEffect(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const params = { page: 0, size: 50 };
        if (keyword.trim()) params.search = keyword.trim();
        const res = await axiosClient.get('/catalog/variants', { params });
        const paged = res.data?.data ?? res.data ?? {};
        const items = paged.content ?? (Array.isArray(paged) ? paged : []);
        setResults(items);
      } catch { setResults([]); }
      finally { setLoading(false); }
    }, keyword.trim() ? 300 : 0);
    return () => clearTimeout(timerRef.current);
  }, [keyword, isOpen]);

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
            const isExisting = existingVariantIds.includes(item.id);
            const isSelected = Boolean(selected[item.id]);
            return (
              <div key={item.id} onClick={() => toggle(item)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, padding: '12px 24px',
                  cursor: isExisting ? 'not-allowed' : 'pointer',
                  backgroundColor: isExisting ? '#f8fafc' : isSelected ? '#fef2f2' : '#fff',
                  borderBottom: '1px solid #f1f5f9', opacity: isExisting ? 0.6 : 1,
                }}>
                <input type="checkbox" checked={isSelected} disabled={isExisting} onChange={() => toggle(item)} onClick={(e) => e.stopPropagation()}
                  style={{ width: 16, height: 16, accentColor: '#dc2626', flexShrink: 0 }} />
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
                    <span style={{ fontSize: 11, fontFamily: 'monospace', backgroundColor: '#fee2e2', color: '#991b1b', padding: '2px 6px', borderRadius: 4 }}>
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
                  variantId: i.id,
                  sku: i.sku,
                  productName: i.productName,
                  variantName: i.name,
                  quantity: 1,
                })));
              }}
              disabled={count === 0}
              style={{ padding: '8px 20px', borderRadius: 8, border: 'none', backgroundColor: count === 0 ? '#fca5a5' : '#dc2626', color: '#fff', fontSize: 13, fontWeight: 500, cursor: count === 0 ? 'not-allowed' : 'pointer' }}>
              Thêm ({count})
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function StockDeliveryCreatePage() {
  const navigate = useNavigate();
  const fileRef = useRef(null);
  const [items, setItems] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [warehouses, setWarehouses] = useState([]);
  const [activeTab, setActiveTab] = useState('MANUAL'); // MANUAL or BY_ORDER

  const today = new Date().toISOString().split('T')[0];
  const { register, handleSubmit, watch, formState: { errors, isSubmitting, isDirty } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { warehouseId: '', issuedDate: today, recipient: '', notes: '' },
  });
  const selectedWarehouseId = watch('warehouseId');
  const itemVariantKey = useMemo(() => items.map((item) => item.variantId).join('|'), [items]);

  const totalQuantity = useMemo(
    () => items.reduce((s, i) => s + (Number(i.quantity) || 0), 0),
    [items]
  );
  const overAvailableItem = useMemo(
    () => items.find((item) =>
      item.availableQuantity != null && Number(item.quantity || 0) > Number(item.availableQuantity)
    ),
    [items]
  );

  useEffect(() => {
    warehouseService.getAll()
      .then((wRes) => {
        const extract = (r) => {
          const d = r?.data?.data ?? r?.data;
          if (Array.isArray(d)) return d;
          if (d?.content && Array.isArray(d.content)) return d.content;
          return [];
        };
        setWarehouses(extract(wRes));
      })
      .catch(() => { });
  }, []);

  useEffect(() => {
    if (!selectedWarehouseId || items.length === 0) return;

    let ignore = false;
    Promise.all(items.map(async (item) => {
      if (!item.variantId || String(item.variantId).startsWith('excel-')) {
        return item;
      }
      try {
        const response = await inventoryApi.getByVariant(selectedWarehouseId, item.variantId);
        const inventoryItem = getResponseData(response);
        return {
          ...item,
          quantityOnHand: inventoryItem.quantityOnHand ?? 0,
          reservedQuantity: inventoryItem.reservedQuantity ?? 0,
          availableQuantity: inventoryItem.availableQuantity ?? 0,
        };
      } catch {
        return {
          ...item,
          quantityOnHand: 0,
          reservedQuantity: 0,
          availableQuantity: 0,
        };
      }
    })).then((nextItems) => {
      if (!ignore) setItems(nextItems);
    });

    return () => { ignore = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedWarehouseId, itemVariantKey]);

  // ── Item handlers ─────────────────────────────────────────────────────────
  const onQtyChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, quantity: v } : it));
  const onRemove = (i) => setItems((p) => p.filter((_, idx) => idx !== i));

  const onAddProducts = (newItems) => {
    setItems((p) => {
      const ids = new Set(p.map((it) => it.variantId));
      return [...p, ...newItems.filter((it) => !ids.has(it.variantId))];
    });
    setModalOpen(false);
  };

  // ── Excel import (SKU and Quantity only) ──────────────────────────────────
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
          const rawSku = row[0]; const rawQty = row[1];
          if (!rawSku && !rawQty) return;
          let ok = true;
          const sku = rawSku ? String(rawSku).trim() : '';
          if (!sku) { errs.push(`Dòng ${rn}: SKU không hợp lệ.`); ok = false; }
          const qty = Number(rawQty);
          if (!rawQty || isNaN(qty) || qty <= 0) { errs.push(`Dòng ${rn}: Số lượng phải lớn hơn 0.`); ok = false; }
          if (ok) valid.push({ sku, quantity: qty });
        });
        if (valid.length === 0) { toast.error('Không có dòng hợp lệ nào trong file.'); return; }
        if (errs.length > 0) toast.warn(`Có ${errs.length} dòng lỗi. Chỉ nhập ${valid.length} dòng hợp lệ.`);
        else toast.success(`Đã nhập ${valid.length} sản phẩm từ Excel.`);
        setItems((prev) => {
          const updated = [...prev];
          valid.forEach((p) => {
            const idx2 = updated.findIndex((it) => it.sku === p.sku);
            if (idx2 >= 0) { updated[idx2] = { ...updated[idx2], quantity: p.quantity }; }
            else updated.push({ variantId: `excel-${p.sku}`, sku: p.sku, productName: p.sku, variantName: '', quantity: p.quantity });
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

    // Validate quantity
    const invalidQty = items.find((it) => !it.quantity || Number(it.quantity) <= 0);
    if (invalidQty) {
      toast.error(`Sản phẩm "${invalidQty.productName}" phải có số lượng lớn hơn 0.`);
      return;
    }

    if (overAvailableItem) {
      toast.error(`Sản phẩm "${overAvailableItem.productName}" chỉ còn ${formatNumber(overAvailableItem.availableQuantity)} có thể xuất.`);
      return;
    }

    try {
      await stockDeliveryService.createStockDelivery({
        warehouseId: data.warehouseId,
        issuedDate: data.issuedDate,
        recipient: data.recipient.trim(),
        notes: data.notes || null,
        deliveryType: 'ADJUSTMENT',
        items: items.map((it) => ({ 
          productVariantId: it.variantId, 
          quantity: Number(it.quantity),
          note: null
        })),
      });
      toast.success('Tạo phiếu xuất kho thành công.');
      navigate(ROUTES.STOCK_DELIVERIES);
    } catch (error) {
      if (error?.response?.data?.data && typeof error.response.data.data === 'object') {
        const validationErrors = error.response.data.data;
        const firstError = Object.values(validationErrors)[0];
        toast.error(firstError || 'Có lỗi xảy ra. Vui lòng kiểm tra lại thông tin.');
      } else {
        const errorMessage = error?.response?.data?.message || error?.message || 'Không thể tạo phiếu xuất kho. Vui lòng thử lại.';
        toast.error(errorMessage);
      }
    }
  });

  const handleCancel = () => {
    if (isDirty || items.length > 0) {
      if (!window.confirm('Bạn có chắc muốn hủy? Các thay đổi chưa lưu sẽ bị mất.')) return;
    }
    navigate(ROUTES.STOCK_DELIVERIES);
  };

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 64px - 48px)', overflow: 'hidden' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button onClick={() => navigate(ROUTES.STOCK_DELIVERIES)}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', borderRadius: 6, border: '1px solid #e2e8f0', background: '#fff', fontSize: 12, color: '#374151', cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
          >
            <ArrowLeft size={14} /> Quay lại
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 28, height: 28, borderRadius: 8, backgroundColor: '#fef2f2', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <PackageMinus size={14} color="#dc2626" />
            </div>
            <div>
              <h1 style={{ fontSize: 16, fontWeight: 700, color: '#0f172a', margin: 0, lineHeight: 1.2 }}>Tạo phiếu xuất kho</h1>
              <p style={{ fontSize: 11, color: '#64748b', margin: 0 }}>Xuất hàng hóa từ kho theo đơn hàng hoặc thủ công</p>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexShrink: 0 }}>
        <button
          onClick={() => {}}
          disabled
          style={{
            padding: '8px 16px',
            borderRadius: 6,
            border: '1px solid #e2e8f0',
            backgroundColor: '#f8fafc',
            color: '#94a3b8',
            fontSize: 13,
            fontWeight: 500,
            cursor: 'not-allowed',
            opacity: 0.6,
          }}
        >
          Xuất theo đơn hàng
        </button>
        <button
          onClick={() => setActiveTab('MANUAL')}
          style={{
            padding: '8px 16px',
            borderRadius: 6,
            border: '1px solid #e2e8f0',
            backgroundColor: activeTab === 'MANUAL' ? '#dc2626' : '#fff',
            color: activeTab === 'MANUAL' ? '#fff' : '#374151',
            fontSize: 13,
            fontWeight: 500,
            cursor: 'pointer',
          }}
        >
          Xuất thủ công
        </button>
      </div>

      {/* Tab Content Info Message (for BY_ORDER) */}
      {activeTab === 'BY_ORDER' && (
        <div style={{ backgroundColor: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 8, padding: '12px 16px', marginBottom: 12, display: 'flex', alignItems: 'start', gap: 10 }}>
          <Info size={16} color="#2563eb" style={{ flexShrink: 0, marginTop: 2 }} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#1e40af', marginBottom: 4 }}>Tính năng đang phát triển</div>
            <div style={{ fontSize: 12, color: '#3b82f6' }}>Xuất kho theo đơn hàng sẽ được cập nhật trong phiên bản tiếp theo. Vui lòng sử dụng tab "Xuất thủ công".</div>
          </div>
        </div>
      )}

      {/* CARD 1: THÔNG TIN PHIẾU XUẤT */}
      <div style={{ backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', padding: '14px 16px', marginBottom: 12, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>Thông tin phiếu xuất</div>
        </div>

        {/* Form Fields */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
          {/* Kho xuất */}
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
              Kho xuất <span style={{ color: '#ef4444' }}>*</span>
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
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
              Ngày xuất <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input type="date" max={today} {...register('issuedDate')}
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: `1px solid ${errors.issuedDate ? '#fca5a5' : '#e2e8f0'}`, fontSize: 11, color: '#0f172a', outline: 'none', backgroundColor: '#fff', boxSizing: 'border-box' }} />
            {errors.issuedDate && <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626' }}>{errors.issuedDate.message}</p>}
          </div>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>
              Người nhận <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input {...register('recipient')} placeholder="Nhập người nhận"
              style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: `1px solid ${errors.recipient ? '#fca5a5' : '#e2e8f0'}`, fontSize: 11, color: '#0f172a', outline: 'none', backgroundColor: '#fff', boxSizing: 'border-box' }} />
            {errors.recipient && <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626' }}>{errors.recipient.message}</p>}
          </div>
        </div>

        {/* Ghi chú */}
        <div style={{ marginTop: 12 }}>
          <label style={{ display: 'block', fontSize: 11, fontWeight: 500, color: '#374151', marginBottom: 4 }}>Ghi chú</label>
          <textarea {...register('notes')} rows={2} placeholder="Ghi chú về phiếu xuất..."
            style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #e2e8f0', fontSize: 11, outline: 'none', resize: 'none', boxSizing: 'border-box' }} />
        </div>

        {/* Action Buttons */}
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 12 }}>
          <button onClick={handleCancel}
            style={{ padding: '6px 14px', borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: '#fff', fontSize: 12, fontWeight: 500, color: '#374151', cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
          >
            Hủy
          </button>
          <button onClick={onSubmit} disabled={isSubmitting}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 16px', borderRadius: 6, border: 'none', backgroundColor: isSubmitting ? '#fca5a5' : '#dc2626', color: '#fff', fontSize: 12, fontWeight: 600, cursor: isSubmitting ? 'not-allowed' : 'pointer' }}
            onMouseEnter={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#b91c1c'; }}
            onMouseLeave={(e) => { if (!isSubmitting) e.currentTarget.style.backgroundColor = '#dc2626'; }}
          >
            <PackageMinus size={14} /> {isSubmitting ? 'Đang xử lý...' : 'Hoàn thành xuất kho'}
          </button>
        </div>
      </div>

      {/* 2-COLUMN LAYOUT */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 12, flex: 1, minHeight: 0 }}>

        {/* LEFT: Danh sách sản phẩm xuất */}
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <div style={{ backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', overflow: 'hidden', display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Card header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid #f1f5f9', flexShrink: 0 }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>Danh sách sản phẩm xuất</div>
                <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 1 }}>Thêm sản phẩm và điền thông tin số lượng xuất</div>
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
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 11px', borderRadius: 6, border: 'none', backgroundColor: '#dc2626', color: '#fff', fontSize: 11, fontWeight: 500, cursor: 'pointer' }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#b91c1c'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#dc2626'}
                >
                  <Plus size={13} /> Thêm sản phẩm
                </button>
              </div>
            </div>

            {/* Table */}
            {items.length === 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, gap: 6, padding: '30px 0' }}>
                <div style={{ width: 36, height: 36, borderRadius: 8, backgroundColor: '#fef2f2', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <PackageMinus size={18} color="#dc2626" />
                </div>
                <p style={{ fontSize: 12, color: '#64748b', fontWeight: 500, margin: 0 }}>Chưa có sản phẩm nào</p>
                <p style={{ fontSize: 11, color: '#94a3b8', margin: 0 }}>Nhấn "Thêm sản phẩm" hoặc Import từ Excel</p>
              </div>
            ) : (
              <div style={{ overflowY: 'auto', flex: 1, minHeight: 0 }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
                  <thead style={{ position: 'sticky', top: 0, backgroundColor: '#f8fafc', zIndex: 1 }}>
                    <tr style={{ borderBottom: '1px solid #e2e8f0' }}>
                      {['Tên sản phẩm', 'SKU', 'Tồn khả dụng', 'Số lượng', ''].map((h) => (
                        <th key={h} style={{ padding: '7px 10px', textAlign: 'left', fontWeight: 600, fontSize: 10, color: '#64748b', whiteSpace: 'nowrap' }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, i) => {
                      const exceedsAvailable = item.availableQuantity != null && Number(item.quantity || 0) > Number(item.availableQuantity);
                      const availableDisplay = item.availableQuantity == null ? '-' : formatNumber(item.availableQuantity);

                      return (
                        <tr key={i} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          {/* Product Name */}
                          <td style={{ padding: '10px', fontSize: 11, color: '#0f172a', maxWidth: 180 }}>
                            <div style={{ fontWeight: 500, marginBottom: 2 }}>{item.productName}</div>
                            {item.variantName && (
                              <div style={{ fontSize: 10, color: '#64748b', backgroundColor: '#f1f5f9', padding: '1px 6px', borderRadius: 4, display: 'inline-block' }}>
                                {item.variantName}
                              </div>
                            )}
                          </td>

                          {/* SKU */}
                          <td style={{ padding: '10px' }}>
                            <span style={{ fontSize: 10, fontFamily: 'monospace', backgroundColor: '#fee2e2', color: '#991b1b', padding: '2px 6px', borderRadius: 4 }}>
                              {item.sku}
                            </span>
                          </td>

                          {/* Available Quantity */}
                          <td style={{ padding: '10px', fontSize: 11, color: exceedsAvailable ? '#dc2626' : '#0f172a', fontWeight: 600 }}>
                            {availableDisplay}
                          </td>

                          {/* Quantity Input */}
                          <td style={{ padding: '10px' }}>
                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3 }}>
                              <input
                                type="number"
                                min="1"
                                value={item.quantity}
                                onChange={(e) => onQtyChange(i, e.target.value)}
                                style={{
                                  width: 80,
                                  padding: '4px 6px',
                                  borderRadius: 4,
                                  border: `1px solid ${exceedsAvailable ? '#dc2626' : '#e2e8f0'}`,
                                  fontSize: 11,
                                  textAlign: 'right',
                                  outline: 'none',
                                  boxSizing: 'border-box',
                                  color: exceedsAvailable ? '#dc2626' : '#0f172a',
                                  backgroundColor: exceedsAvailable ? '#fef2f2' : '#fff',
                                }}
                              />
                              {exceedsAvailable && (
                                <span style={{ fontSize: 10, color: '#dc2626', lineHeight: 1.2, whiteSpace: 'nowrap' }}>
                                  Vượt quá tồn kho
                                </span>
                              )}
                            </div>
                          </td>

                          {/* Remove Button */}
                          <td style={{ padding: '10px', textAlign: 'center' }}>
                            <button onClick={() => onRemove(i)}
                              style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 24, height: 24, borderRadius: 4, border: 'none', backgroundColor: '#fee2e2', color: '#dc2626', cursor: 'pointer' }}
                              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#fecaca'}
                              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fee2e2'}
                            >
                              <Trash2 size={13} />
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

        {/* RIGHT: Lưu ý */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {/* Summary Card */}
          <div style={{ backgroundColor: '#fff', borderRadius: 8, border: '1px solid #e2e8f0', padding: '12px 14px' }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#0f172a', marginBottom: 8 }}>Tổng quan</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 11, color: '#64748b' }}>Số sản phẩm:</span>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#0f172a' }}>{items.length}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 11, color: '#64748b' }}>Tổng số lượng:</span>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#dc2626' }}>{totalQuantity}</span>
              </div>
            </div>
          </div>

          {/* Important Notes Card */}
          <div style={{ backgroundColor: '#fffbeb', borderRadius: 8, border: '1px solid #fde68a', padding: '12px 14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <AlertCircle size={14} color="#d97706" />
              <div style={{ fontSize: 12, fontWeight: 600, color: '#92400e' }}>Lưu ý quan trọng</div>
            </div>
            <ul style={{ margin: 0, paddingLeft: 16, fontSize: 11, color: '#78350f', lineHeight: 1.6 }}>
              <li>Số lượng xuất phải nhỏ hơn hoặc bằng số lượng tồn kho</li>
              <li>Kho xuất phải ở trạng thái hoạt động</li>
              <li>Sau khi xuất kho, số lượng tồn sẽ tự động giảm</li>
            </ul>
          </div>

          {/* Excel Format Guide */}
          <div style={{ backgroundColor: '#f0fdf4', borderRadius: 8, border: '1px solid #bbf7d0', padding: '12px 14px' }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#166534', marginBottom: 6 }}>Định dạng file Excel</div>
            <div style={{ fontSize: 11, color: '#15803d', lineHeight: 1.6 }}>
              <div style={{ marginBottom: 4 }}>Dòng đầu tiên: tiêu đề cột</div>
              <div style={{ marginBottom: 4 }}>Cột A: SKU sản phẩm</div>
              <div>Cột B: Số lượng xuất</div>
            </div>
          </div>
        </div>
      </div>

      {/* Add Product Modal */}
      <AddProductModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onConfirm={onAddProducts}
        existingVariantIds={items.map((it) => it.variantId)}
      />
    </div>
  );
}
