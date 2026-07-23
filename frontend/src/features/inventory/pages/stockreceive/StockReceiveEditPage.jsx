import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import {
  ArrowLeft, Save, Loader2, AlertCircle, Edit3, Trash2, Plus, Search, X,
} from 'lucide-react';

import warehouseService from '../../services/warehouseService';
import supplierService from '../../services/supplierService';
import stockReceiveService from '../../services/stockReceiveService';
import { ROUTES } from '../../../../app/router/routes';
import axiosClient from '../../../../api/axiosClient';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

const uniqueValues = (values) => [...new Set((values ?? []).filter(Boolean))];

const groupReceiptItems = (receiptItems = []) => {
  const groups = new Map();
  receiptItems.forEach((item) => {
    const groupKey = String(item.marketplaceSku || item.sku || item.variantSku || item.variantId)
      .trim()
      .toLowerCase();
    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        groupKey,
        variantId: item.variantId,
        variantIds: [item.variantId],
        sku: item.marketplaceSku || item.sku || item.variantSku,
        productName: item.productName,
        variantName: item.variantName || '',
        quantity: item.quantity || 0,
        unitPrice: item.unitCost ?? item.unitPrice ?? 0,
        platforms: uniqueValues(item.platforms),
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
      ? Number(item.unitPrice)
      : null,
  })));

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

function AddProductModal({ open, onClose, onAdd, existingVariantIds }) {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    let ignore = false;
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const params = { page: 0, size: 50 };
        if (keyword.trim()) params.search = keyword.trim();
        const response = await axiosClient.get('/catalog/variants', { params });
        if (ignore) return;
        const data = response.data?.data ?? response.data ?? {};
        setResults(data.content ?? (Array.isArray(data) ? data : []));
      } catch {
        if (!ignore) setResults([]);
      } finally {
        if (!ignore) setLoading(false);
      }
    }, keyword.trim() ? 250 : 0);
    return () => {
      ignore = true;
      clearTimeout(timer);
    };
  }, [keyword, open]);

  if (!open) return null;

  return (
    <div style={modalBackdropStyle} onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div style={modalStyle}>
        <div style={modalHeaderStyle}>
          <div>
            <h2 style={modalTitleStyle}>Thêm sản phẩm nhập</h2>
            <p style={modalSubtitleStyle}>Tìm và chọn sản phẩm cần thêm vào phiếu lưu tạm</p>
          </div>
          <button type="button" onClick={onClose} style={modalCloseButtonStyle}><X size={18} /></button>
        </div>
        <div style={modalSearchWrapStyle}>
          <Search size={16} color="#8aa0bd" />
          <input autoFocus value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="Tìm theo tên sản phẩm hoặc SKU..." style={modalSearchInputStyle} />
        </div>
        <div style={modalListStyle}>
          {loading ? (
            <div style={modalEmptyStyle}>Đang tải...</div>
          ) : results.length ? results.map((item, index) => {
            const exists = existingVariantIds.includes(item.id);
            return (
              <button key={item.id} type="button" disabled={exists} onClick={() => onAdd(item)} style={{ ...modalRowStyle, opacity: exists ? 0.45 : 1, cursor: exists ? 'default' : 'pointer' }}>
                <span style={modalOrdinalStyle}>{String(index + 1).padStart(3, '0')}</span>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={modalProductNameStyle}>{item.productName}{item.name ? ` - ${item.name}` : ''}</span>
                  <span style={modalProductMetaStyle}>{item.sku}</span>
                </span>
                {exists ? <span style={modalAddedStyle}>Đã thêm</span> : <Plus size={18} color="#2563eb" />}
              </button>
            );
          }) : (
            <div style={modalEmptyStyle}>Không tìm thấy sản phẩm phù hợp.</div>
          )}
        </div>
      </div>
    </div>
  );
}

const modalBackdropStyle = {
  position: 'fixed',
  inset: 0,
  zIndex: 1000,
  background: 'rgba(15, 23, 42, 0.48)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 20,
};

const modalStyle = {
  width: 'min(520px, 100%)',
  maxHeight: '76vh',
  background: '#fff',
  borderRadius: 10,
  boxShadow: '0 22px 60px rgba(15, 23, 42, 0.28)',
  border: '1px solid #e2e8f0',
  padding: 20,
  display: 'flex',
  flexDirection: 'column',
  gap: 14,
};

const modalHeaderStyle = { display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 };
const modalTitleStyle = { margin: 0, fontSize: 18, fontWeight: 700, color: '#0f172a' };
const modalSubtitleStyle = { margin: '5px 0 0', fontSize: 13, color: '#64748b' };
const modalCloseButtonStyle = { width: 28, height: 28, borderRadius: 6, border: 'none', background: 'transparent', color: '#475569', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const modalSearchWrapStyle = { display: 'flex', alignItems: 'center', gap: 9, border: '1px solid #cbd5e1', borderRadius: 8, padding: '0 12px', height: 40, background: '#f8fafc' };
const modalSearchInputStyle = { flex: 1, border: 'none', outline: 'none', background: 'transparent', fontSize: 13, color: '#0f172a' };
const modalListStyle = { border: '1px solid #e2e8f0', borderRadius: 8, overflowY: 'auto', maxHeight: 320, background: '#fff' };
const modalRowStyle = { width: '100%', border: 'none', borderBottom: '1px solid #f1f5f9', background: '#fff', padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12, textAlign: 'left' };
const modalOrdinalStyle = { width: 40, height: 34, borderRadius: 8, background: '#ecfdf5', color: '#009688', fontFamily: 'monospace', fontSize: 12, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center' };
const modalProductNameStyle = { display: 'block', fontSize: 13, fontWeight: 600, color: '#0f172a', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };
const modalProductMetaStyle = { display: 'block', marginTop: 2, fontSize: 12, color: '#8aa0bd', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };
const modalAddedStyle = { fontSize: 12, color: '#94a3b8', fontWeight: 600 };
const modalEmptyStyle = { padding: 24, textAlign: 'center', color: '#94a3b8', fontSize: 13 };

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

  const { register, handleSubmit, formState: { errors, isSubmitting }, setValue } = useForm({
    resolver: zodResolver(schema),
  });

  const totalAmount = useMemo(
    () => items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0),
    [items]
  );
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
      
      // Set items
      setItems(groupReceiptItems(receiptData.items || []));
      
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
  const onAddProduct = (item) => {
    setItems((current) => {
      if (current.some((entry) => (entry.variantIds ?? [entry.variantId]).includes(item.id))) return current;
      return [
        ...current,
        {
          groupKey: `variant:${item.id}`,
          variantId: item.id,
          variantIds: [item.id],
          sku: item.sku,
          productName: item.productName,
          variantName: item.name || '',
          quantity: 1,
          unitPrice: item.costPrice ?? item.price ?? 0,
          platforms: [],
        },
      ];
    });
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
              <p style={{ fontSize: 11, color: '#94a3b8', margin: '2px 0 0' }}>Cập nhật số lượng và đơn giá</p>
            </div>
            <button type="button" onClick={() => setModalOpen(true)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 12px', borderRadius: 7, border: 'none', background: '#009688', color: '#fff', fontSize: 12, fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap' }}>
              <Plus size={14} /> Thêm sản phẩm
            </button>
          </div>

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
                        <input type="number" min="0" step="1" value={item.quantity} onChange={(e) => onQtyChange(idx, e.target.value)}
                          style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: `1px solid ${qtyBad ? '#fca5a5' : '#e2e8f0'}`, backgroundColor: qtyBad ? '#fff5f5' : '#fff', fontSize: 12, textAlign: 'right', outline: 'none', boxSizing: 'border-box' }} />
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
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onAdd={onAddProduct}
        existingVariantIds={items.flatMap((item) => item.variantIds ?? [item.variantId])}
      />
      {ConfirmDialog}
    </div>
  );
}
