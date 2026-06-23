import { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import { ArrowLeft, Plus, Trash2, Search, X, FileSpreadsheet, PackageMinus, AlertCircle, Loader2, Package, AlertTriangle } from 'lucide-react';
import * as XLSX from 'xlsx';
import warehouseService from '../../services/warehouseService';
import stockDeliveryService from '../../services/stockDeliveryService';
import inventoryApi from '../../../../api/inventoryApi';
import axiosClient from '../../../../api/axiosClient';
import { ROUTES } from '../../../../app/router/routes';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import styles from '../CreatePage.module.css';

const formatNumber = (v) => new Intl.NumberFormat('vi-VN').format(v ?? 0);
const getResponseData = (response) => response?.data?.data ?? response?.data ?? response ?? {};
const getErrorMessage = (error) => {
  const rawMessage = error?.response?.data?.message || error?.message || error;
  if (typeof rawMessage !== 'string') return 'Không thể lưu phiếu xuất kho.';
  if (rawMessage.includes('Insufficient inventory')) return 'Số lượng xuất vượt quá tồn kho khả dụng.';
  if (rawMessage.includes('read-only')) return 'Phiếu xuất đã xác nhận không thể chỉnh sửa.';
  return rawMessage;
};
const toDateInputValue = (value, fallback) => !value ? fallback : String(value).split('T')[0];

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

  useEffect(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const params = { page: 0, size: 50 };
        if (keyword.trim()) params.search = keyword.trim();
        const res = await axiosClient.get('/catalog/variants', { params });
        const paged = res.data?.data ?? res.data ?? {};
        setResults(paged.content ?? (Array.isArray(paged) ? paged : []));
      } catch { setResults([]); }
      finally { setLoading(false); }
    }, keyword.trim() ? 300 : 0);
    return () => clearTimeout(timerRef.current);
  }, [keyword, isOpen]);

  const toggle = (item) => {
    if (existingVariantIds.includes(item.id)) return;
    setSelected((prev) => { const n = { ...prev }; n[item.id] ? delete n[item.id] : (n[item.id] = item); return n; });
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
          <button onClick={onClose} style={{ width: 30, height: 30, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}><X size={18} /></button>
        </div>
        <div style={{ padding: '12px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
            <input autoFocus value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Tìm theo tên sản phẩm hoặc SKU..."
              style={{ width: '100%', padding: '8px 10px 8px 34px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, color: '#0f172a', outline: 'none', boxSizing: 'border-box' }} />
          </div>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {loading && <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '40px 0', color: '#94a3b8', fontSize: 13 }}><Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải...</div>}
          {!loading && results.length === 0 && <p style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>{keyword.trim() ? 'Không tìm thấy sản phẩm nào phù hợp.' : 'Không có sản phẩm nào.'}</p>}
          {!loading && results.map((item) => {
            const isExisting = existingVariantIds.includes(item.id);
            const isSelected = Boolean(selected[item.id]);
            return (
              <div key={item.id} onClick={() => toggle(item)}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 20px', cursor: isExisting ? 'not-allowed' : 'pointer', backgroundColor: isExisting ? '#f8fafc' : isSelected ? '#fef2f2' : '#fff', borderBottom: '1px solid #f1f5f9', opacity: isExisting ? 0.55 : 1 }}>
                <input type="checkbox" checked={isSelected} disabled={isExisting} onChange={() => toggle(item)} onClick={(e) => e.stopPropagation()} style={{ width: 16, height: 16, accentColor: '#dc2626', flexShrink: 0 }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0f172a' }}>{item.productName}</span>
                    {item.name && <span style={{ fontSize: 11, color: '#475569', backgroundColor: '#f1f5f9', padding: '1px 6px', borderRadius: 4 }}>{item.name}</span>}
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 3, alignItems: 'center' }}>
                    <span style={{ fontSize: 11, fontFamily: 'monospace', backgroundColor: '#fee2e2', color: '#991b1b', padding: '1px 6px', borderRadius: 4 }}>{item.sku}</span>
                    {isExisting && <span style={{ fontSize: 11, backgroundColor: '#fffbeb', color: '#d97706', padding: '1px 6px', borderRadius: 4 }}>Đã có</span>}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
        <div style={{ padding: '14px 20px', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>{count > 0 ? `Đã chọn ${count} sản phẩm` : 'Chưa chọn sản phẩm nào'}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={onClose} style={{ padding: '7px 14px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>Hủy</button>
            <button onClick={() => { onConfirm(Object.values(selected).map((i) => ({ variantId: i.id, sku: i.sku, productName: i.productName, variantName: i.name, quantity: 1 }))); }} disabled={count === 0}
              style={{ padding: '7px 16px', borderRadius: 8, border: 'none', backgroundColor: count === 0 ? '#fca5a5' : '#dc2626', color: '#fff', fontSize: 13, fontWeight: 500, cursor: count === 0 ? 'not-allowed' : 'pointer' }}>
              Thêm ({count})
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function StockDeliveryCreatePage({ mode = 'create' }) {
  const navigate = useNavigate();
  const { id } = useParams();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const fileRef = useRef(null);
  const [items, setItems] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [warehouses, setWarehouses] = useState([]);
  const [activeTab, setActiveTab] = useState('MANUAL'); // MANUAL or BY_ORDER
  const [loadingDelivery, setLoadingDelivery] = useState(mode === 'edit');
  const isEdit = mode === 'edit';

  const today = new Date().toISOString().split('T')[0];
  const { register, handleSubmit, watch, reset, formState: { errors, isSubmitting, isDirty } } = useForm({
    resolver: zodResolver(z.object({
      warehouseId: z.string().min(1, 'Vui lòng chọn kho xuất.'),
      issuedDate: z.string().min(1, 'Vui lòng chọn ngày xuất.'),
      recipient: z.string().min(1, 'Vui lòng nhập người nhận.').max(255),
      notes: z.string().optional(),
    })),
    defaultValues: { warehouseId: '', issuedDate: today, recipient: '', notes: '' },
  });
  const selectedWarehouseId = watch('warehouseId');
  const itemVariantKey = useMemo(() => items.map((item) => item.variantId).join('|'), [items]);
  const totalQuantity = useMemo(() => items.reduce((s, i) => s + (Number(i.quantity) || 0), 0), [items]);
  const hasUnsavedChanges = !loadingDelivery && (isDirty || (!isEdit && items.length > 0));
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });
  const overAvailableItem = useMemo(() => items.find((item) => item.availableQuantity != null && Number(item.quantity || 0) > Number(item.availableQuantity)), [items]);

  useEffect(() => {
    warehouseService.getAll()
      .then((wRes) => { const extract = (r) => { const d = r?.data?.data ?? r?.data; if (Array.isArray(d)) return d; if (d?.content && Array.isArray(d.content)) return d.content; return []; }; setWarehouses(extract(wRes)); })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!isEdit || !id) return;
    let ignore = false;
    setLoadingDelivery(true);
    stockDeliveryService.getStockDeliveryById(id)
      .then((response) => {
        if (ignore) return;
        const delivery = getResponseData(response);
        if (delivery.status !== 'DRAFT') { toast.info('Phiếu xuất đã xác nhận không thể chỉnh sửa.'); navigate(ROUTES.STOCK_DELIVERY_DETAIL.replace(':id', id), { replace: true }); return; }
        reset({ warehouseId: delivery.warehouseId ? String(delivery.warehouseId) : '', issuedDate: toDateInputValue(delivery.issuedAt ?? delivery.createdAt, today), recipient: delivery.recipient ?? '', notes: delivery.note ?? delivery.notes ?? '' });
        setItems((delivery.items ?? []).map((item) => ({ variantId: item.productVariantId, sku: item.sku, productName: item.productName ?? item.productVariantName ?? item.sku, variantName: item.productVariantName, quantity: item.quantity ?? 1 })));
      })
      .catch((error) => { if (!ignore) { toast.error(getErrorMessage(error)); navigate(ROUTES.STOCK_DELIVERIES, { replace: true }); } })
      .finally(() => { if (!ignore) setLoadingDelivery(false); });
    return () => { ignore = true; };
  }, [id, isEdit, navigate, reset, today]);

  useEffect(() => {
    if (!selectedWarehouseId || items.length === 0) return;
    let ignore = false;
    Promise.all(items.map(async (item) => {
      if (!item.variantId || String(item.variantId).startsWith('excel-')) return item;
      try {
        const response = await inventoryApi.getByVariant(selectedWarehouseId, item.variantId);
        const inventoryItem = getResponseData(response);
        return { ...item, quantityOnHand: inventoryItem.quantityOnHand ?? 0, reservedQuantity: inventoryItem.reservedQuantity ?? 0, availableQuantity: inventoryItem.availableQuantity ?? 0 };
      } catch { return { ...item, quantityOnHand: 0, reservedQuantity: 0, availableQuantity: 0 }; }
    })).then((nextItems) => { if (!ignore) setItems(nextItems); });
    return () => { ignore = true; };
  }, [selectedWarehouseId, itemVariantKey]);

  const onQtyChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, quantity: v } : it));
  const onRemove = (i) => setItems((p) => p.filter((_, idx) => idx !== i));
  const onAddProducts = (newItems) => { setItems((p) => { const ids = new Set(p.map((it) => it.variantId)); return [...p, ...newItems.filter((it) => !ids.has(it.variantId))]; }); setModalOpen(false); };

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

  const submitDelivery = (submitAction) => handleSubmit(async (data) => {
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }
    const invalidQty = items.find((it) => !it.quantity || Number(it.quantity) <= 0);
    if (invalidQty) { toast.error(`Sản phẩm "${invalidQty.productName}" phải có số lượng lớn hơn 0.`); return; }
    if (overAvailableItem) { toast.error('Số lượng xuất vượt quá tồn kho khả dụng. Vui lòng kiểm tra lại.'); return; }
    const payload = { warehouseId: data.warehouseId, issuedDate: data.issuedDate, recipient: data.recipient.trim(), notes: data.notes || null, deliveryType: 'ADJUSTMENT', items: items.map((it) => ({ productVariantId: it.variantId, quantity: Number(it.quantity), note: null })) };
    try {
      const savedResponse = isEdit ? await stockDeliveryService.updateStockDelivery(id, payload) : await stockDeliveryService.createStockDelivery(payload);
      const savedDelivery = getResponseData(savedResponse);
      if (submitAction === 'complete') { await stockDeliveryService.confirmStockDelivery(savedDelivery.id); toast.success('Hoàn thành phiếu xuất kho thành công.'); }
      else { toast.success(isEdit ? 'Cập nhật phiếu xuất kho thành công.' : 'Lưu tạm phiếu xuất kho thành công.'); }
      runWithoutGuard(() => navigate(ROUTES.STOCK_DELIVERIES));
    } catch (error) {
      if (error?.response?.data?.data && typeof error.response.data.data === 'object') { toast.error(Object.values(error.response.data.data)[0] || 'Có lỗi xảy ra.'); }
      else { toast.error(getErrorMessage(error)); }
    }
  });

  if (loadingDelivery) {
    return <div className={styles.loading}><Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải dữ liệu phiếu xuất...</div>;
  }

  return (
    <div className={styles.page}>

      {/* Page Header */}
      <div className={styles.pageHeader}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.STOCK_DELIVERIES)}><ArrowLeft size={15} /> Quay lại</button>
        <div className={styles.headerIcon} style={{ background: '#fef2f2' }}>
          <PackageMinus size={18} color="#dc2626" />
        </div>
        <div>
          <h1 className={styles.headerTitle}>{isEdit ? 'Sửa phiếu xuất kho' : 'Tạo phiếu xuất kho'}</h1>
          <p className={styles.headerSubtitle}>Xuất hàng hóa từ kho</p>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 6 }}>
        <button
          disabled
          style={{
            padding: '7px 16px', borderRadius: 8, border: '1px solid #e2e8f0',
            backgroundColor: '#f8fafc', color: '#94a3b8', fontSize: 13, fontWeight: 500,
            cursor: 'not-allowed', opacity: 0.7,
          }}
        >
          Xuất theo đơn hàng
        </button>
        <button
          onClick={() => setActiveTab('MANUAL')}
          className={`${styles.actionBtn} ${activeTab === 'MANUAL' ? styles.primaryBtn : styles.secondaryBtn}`}
          style={{ padding: '7px 16px' }}
        >
          Xuất thủ công
        </button>
      </div>

      {/* Info for BY_ORDER */}
      {activeTab === 'BY_ORDER' && (
        <div style={{ background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 10, padding: '12px 16px', display: 'flex', alignItems: 'start', gap: 10 }}>
          <AlertCircle size={16} color="#2563eb" style={{ flexShrink: 0, marginTop: 1 }} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#1e40af', marginBottom: 2 }}>Tính năng đang phát triển</div>
            <div style={{ fontSize: 12, color: '#3b82f6' }}>Xuất kho theo đơn hàng sẽ được cập nhật trong phiên bản tiếp theo. Vui lòng sử dụng tab "Xuất thủ công".</div>
          </div>
        </div>
      )}

      {/* Warning */}
      {overAvailableItem && (
        <div className={styles.warningBanner}>
          <AlertTriangle size={16} color="#dc2626" />
          <span>Sản phẩm <strong>"{overAvailableItem.productName}"</strong> có số lượng xuất vượt quá tồn kho khả dụng ({formatNumber(overAvailableItem.availableQuantity)}).</span>
        </div>
      )}

      {/* Two-column layout */}
      <div className={styles.twoCol}>

        {/* LEFT */}
        <div className={styles.leftCol}>

          {/* Card: Thông tin phiếu xuất */}
          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionHeaderIcon} style={{ background: '#fef2f2' }}><Package size={16} color="#dc2626" /></div>
              <div className={styles.sectionTitle}>Thông tin phiếu xuất</div>
            </div>
            <div className={`${styles.formGrid} ${styles.formGrid2}`} style={{ gridTemplateColumns: '1fr 1fr 1fr' }}>
              <div>
                <label className={styles.fieldLabel}>Kho xuất <span>*</span></label>
                <select {...register('warehouseId')} className={`${styles.fieldSelect} ${errors.warehouseId ? styles.fieldError : ''}`}>
                  <option value="">Chọn kho</option>
                  {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}{w.address ? ` — ${w.address}` : ''}</option>)}
                </select>
                {errors.warehouseId && <p className={styles.fieldErrorMsg}>{errors.warehouseId.message}</p>}
              </div>
              <div>
                <label className={styles.fieldLabel}>Ngày xuất <span>*</span></label>
                <input type="date" max={today} {...register('issuedDate')} className={`${styles.fieldInput} ${errors.issuedDate ? styles.fieldError : ''}`} />
                {errors.issuedDate && <p className={styles.fieldErrorMsg}>{errors.issuedDate.message}</p>}
              </div>
              <div>
                <label className={styles.fieldLabel}>Người nhận <span>*</span></label>
                <input {...register('recipient')} placeholder="Nhập tên người nhận" className={`${styles.fieldInput} ${errors.recipient ? styles.fieldError : ''}`} />
                {errors.recipient && <p className={styles.fieldErrorMsg}>{errors.recipient.message}</p>}
              </div>
            </div>
            <div style={{ marginTop: 14 }}>
              <label className={styles.fieldLabel}>Ghi chú</label>
              <textarea {...register('notes')} rows={2} placeholder="Ghi chú về phiếu xuất..." className={styles.fieldTextarea} />
            </div>
          </div>

          {/* Card: Danh sách sản phẩm xuất */}
          <div className={`${styles.card} ${styles.tableCard}`}>
            <div className={styles.tableCardHeader}>
              <div>
                <div className={styles.tableCardTitle}>Danh sách sản phẩm xuất</div>
                <div className={styles.tableCardSubtitle}>Thêm sản phẩm và điền số lượng xuất</div>
              </div>
              <div className={styles.tableCardActions}>
                <button className={`${styles.actionBtn} ${styles.importBtn}`} onClick={() => fileRef.current?.click()}><FileSpreadsheet className={styles.importIcon} /> Import Excel</button>
                <input ref={fileRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleExcel} />
                <button className={`${styles.actionBtn} ${styles.dangerBtn}`} onClick={() => setModalOpen(true)}><Plus className={styles.dangerIcon} /> Thêm sản phẩm</button>
              </div>
            </div>

            {items.length === 0 ? (
              <div className={styles.emptyState}>
                <div className={styles.emptyIcon}><Package size={22} /></div>
                <p className={styles.emptyTitle}>Chưa có sản phẩm nào</p>
                <p className={styles.emptySubtitle}>Nhấn "Thêm sản phẩm" hoặc Import từ Excel</p>
              </div>
            ) : (
              <div className={styles.tableWrapper}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      {['Tên sản phẩm', 'SKU', 'Tồn khả dụng', 'Số lượng xuất', ''].map((h, i) => (
                        <th key={h} className={i >= 3 ? styles.thRight : ''}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, i) => {
                      const exceedsAvailable = item.availableQuantity != null && Number(item.quantity || 0) > Number(item.availableQuantity);
                      const availableDisplay = item.availableQuantity == null ? '—' : formatNumber(item.availableQuantity);
                      return (
                        <tr key={i}>
                          <td>
                            <div style={{ fontWeight: 600, fontSize: 12 }}>{item.productName}</div>
                            {item.variantName && <div style={{ fontSize: 11, color: '#94a3b8' }}>{item.variantName}</div>}
                          </td>
                          <td><span className={styles.skuTag} style={{ background: '#fee2e2', color: '#991b1b' }}>{item.sku}</span></td>
                          <td style={{ fontWeight: 600, color: exceedsAvailable ? '#dc2626' : '#0f172a', fontSize: 12 }}>{availableDisplay}</td>
                          <td className={styles.tdRight}>
                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3 }}>
                              <input type="number" min="1" value={item.quantity} onChange={(e) => onQtyChange(i, e.target.value)}
                                className={`${styles.tableInput} ${exceedsAvailable ? styles.inputError : ''}`}
                                style={exceedsAvailable ? { borderColor: '#dc2626', background: '#fff5f5', color: '#dc2626' } : {}} />
                              {exceedsAvailable && <span style={{ fontSize: 10, color: '#dc2626' }}>Vượt quá tồn kho</span>}
                            </div>
                          </td>
                          <td><button className={styles.removeBtn} onClick={() => onRemove(i)}><Trash2 size={13} /></button></td>
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
                  <span>Tổng SL: <strong style={{ color: '#dc2626' }}>{formatNumber(totalQuantity)}</strong></span>
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
              <button className={`${styles.actionBtn} ${styles.backBtn}`} onClick={() => navigate(ROUTES.STOCK_DELIVERIES)}><ArrowLeft size={14} /> Hủy</button>
              <button className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={submitDelivery('draft')} disabled={isSubmitting}><PackageMinus className={styles.tealIcon} />{isSubmitting ? 'Đang xử lý...' : 'Lưu tạm'}</button>
              <button className={`${styles.actionBtn} ${styles.dangerBtn}`} onClick={submitDelivery('complete')} disabled={isSubmitting}><PackageMinus className={styles.dangerIcon} />{isSubmitting ? 'Đang xử lý...' : 'Hoàn thành xuất kho'}</button>
            </div>
          </div>

          {/* Summary */}
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Tổng quan</div>
            <div className={styles.sidebarRow}><span className={styles.sidebarLabel}>Số sản phẩm</span><span className={styles.sidebarValue}>{items.length}</span></div>
            <div className={styles.sidebarRow}><span className={styles.sidebarLabel}>Tổng số lượng xuất</span><span className={styles.sidebarValue} style={{ color: '#dc2626' }}>{formatNumber(totalQuantity)}</span></div>
          </div>

          {/* Notes */}
          <div className={`${styles.card} ${styles.noteCard}`} style={{ background: '#fffbeb', border: '1px solid #fde68a' }}>
            <div className={styles.noteHeader}>
              <AlertCircle size={14} color="#d97706" />
              <span className={styles.noteTitle} style={{ color: '#92400e' }}>Lưu ý quan trọng</span>
            </div>
            <ul className={styles.noteList}>
              {['Số lượng xuất phải nhỏ hơn hoặc bằng tồn kho khả dụng.', 'Kho xuất phải ở trạng thái hoạt động.', 'Sau khi xuất kho, số lượng tồn sẽ tự động giảm.', 'Lưu tạm để tiếp tục chỉnh sửa sau.'].map((note) => (
                <li key={note} className={styles.noteItem} style={{ color: '#78350f' }}>{note}</li>
              ))}
            </ul>
          </div>

          {/* Excel guide */}
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Định dạng file Excel</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 5, fontSize: 11, color: '#475569', lineHeight: 1.5 }}>
              <div><strong style={{ color: '#0f172a' }}>Cột A:</strong> SKU sản phẩm</div>
              <div><strong style={{ color: '#0f172a' }}>Cột B:</strong> Số lượng xuất</div>
            </div>
          </div>
        </div>
      </div>

      <AddProductModal isOpen={modalOpen} onClose={() => setModalOpen(false)} onConfirm={onAddProducts} existingVariantIds={items.map((it) => it.variantId)} />
      {ConfirmDialog}
    </div>
  );
}
