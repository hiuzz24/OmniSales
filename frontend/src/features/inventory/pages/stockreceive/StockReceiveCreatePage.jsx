import { useState, useEffect, useMemo, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import { ArrowLeft, Plus, Trash2, Search, X, FileSpreadsheet, PackagePlus, AlertCircle, Loader2, Package } from 'lucide-react';
import * as XLSX from 'xlsx';
import warehouseService from '../../services/warehouseService';
import supplierService from '../../services/supplierService';
import stockReceiveService from '../../services/stockReceiveService';
import axiosClient from '../../../../api/axiosClient';
import { ROUTES } from '../../../../app/router/routes';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import styles from '../CreatePage.module.css';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

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
            const isExisting = existingVariantIds.includes(item.id);
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
            <button onClick={() => { onConfirm(Object.values(selected).map((i) => ({ variantId: i.id, sku: i.sku, productName: i.productName, variantName: i.name, quantity: 1, unitPrice: 0 }))); }} disabled={count === 0}
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

  const { register, handleSubmit, setValue, formState: { errors, isSubmitting, isDirty } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { warehouseId: '', supplierId: '', invoiceNumber: '', receivedAt: new Date().toISOString().split('T')[0], notes: '' },
  });

  const totalAmount = useMemo(() => items.reduce((s, i) => s + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0), [items]);
  const totalQty = useMemo(() => items.reduce((s, i) => s + (Number(i.quantity) || 0), 0), [items]);
  const hasUnsavedChanges = isDirty || items.length > 0;
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  useEffect(() => {
    Promise.all([warehouseService.getAll(), supplierService.getAll()])
      .then(([wRes, sRes]) => {
        const extract = (r) => { const d = r?.data?.data ?? r?.data; if (Array.isArray(d)) return d; if (d?.content && Array.isArray(d.content)) return d.content; return []; };
        setWarehouses(extract(wRes));
        setSuppliers(extract(sRes));
      })
      .catch(() => {});
  }, []);

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
      setItems([{
        variantId,
        sku: searchParams.get('sku') || '',
        productName: searchParams.get('productName') || searchParams.get('sku') || 'Sản phẩm',
        variantName: '',
        quantity: 1,
        unitPrice: searchParams.get('unitCost') || 0,
      }]);
    }
  }, [searchParams, setValue]);

  // ── Item handlers ─────────────────────────────────────────────────────────
  const onQtyChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, quantity: v } : it));
  const onPriceChange = (i, v) => setItems((p) => p.map((it, idx) => idx === i ? { ...it, unitPrice: v } : it));
  const onRemove = (i) => setItems((p) => p.filter((_, idx) => idx !== i));

  const onAddProducts = (newItems) => {
    setItems((p) => { const ids = new Set(p.map((it) => it.variantId)); return [...p, ...newItems.filter((it) => !ids.has(it.variantId))]; });
    setModalOpen(false);
  };

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

  const onSubmit = handleSubmit(async (data) => {
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }
    const invalidQty = items.find((it) => !it.quantity || Number(it.quantity) <= 0);
    if (invalidQty) { toast.error(`Sản phẩm "${invalidQty.productName}" phải có số lượng lớn hơn 0.`); return; }
    const invalidPrice = items.find((it) => { const price = Number(it.unitPrice); return it.unitPrice === '' || it.unitPrice === null || it.unitPrice === undefined || isNaN(price) || price < 0; });
    if (invalidPrice) { toast.error(`Đơn giá của sản phẩm "${invalidPrice.productName}" phải lớn hơn hoặc bằng 0.`); return; }
    if (!data.invoiceNumber || data.invoiceNumber.trim() === '') { toast.error('Số hóa đơn là bắt buộc khi xác nhận phiếu nhập.'); return; }
    try {
      await stockReceiveService.createReceipt({
        warehouseId: data.warehouseId, supplierId: data.supplierId || null, invoiceNumber: data.invoiceNumber || null,
        receivedAt: data.receivedAt, notes: data.notes || null,
        items: items.map((it) => ({ variantId: it.variantId, quantity: Number(it.quantity), unitCost: Number(it.unitPrice) })), isDraft: false,
      });
      toast.success('Tạo phiếu nhập thành công.');
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
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return; }
    try {
      await stockReceiveService.createReceipt({
        warehouseId: data.warehouseId, supplierId: data.supplierId || null, invoiceNumber: data.invoiceNumber || null,
        receivedAt: data.receivedAt, notes: data.notes || null,
        items: items.map((it) => ({ variantId: it.variantId, quantity: it.quantity ? Number(it.quantity) : null, unitCost: it.unitPrice !== '' && it.unitPrice !== null && it.unitPrice !== undefined ? Number(it.unitPrice) : null })), isDraft: true,
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
    <div className={styles.page}>

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
            <div className={`${styles.formGrid} ${styles.formGrid2}`} style={{ gridTemplateColumns: '1fr 1fr 1fr 1fr' }}>
              <div>
                <label className={styles.fieldLabel}>Kho nhập <span>*</span></label>
                <select {...register('warehouseId')} className={`${styles.fieldSelect} ${errors.warehouseId ? styles.fieldError : ''}`}>
                  <option value="">Chọn kho</option>
                  {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}{w.address ? ` — ${w.address}` : ''}</option>)}
                </select>
                {errors.warehouseId && <p className={styles.fieldErrorMsg}>{errors.warehouseId.message}</p>}
              </div>
              <div>
                <label className={styles.fieldLabel}>Nhà cung cấp</label>
                <select {...register('supplierId')} className={styles.fieldSelect}>
                  <option value="">Chọn nhà cung cấp</option>
                  {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div>
                <label className={styles.fieldLabel}>Số hóa đơn <span>*</span></label>
                <input {...register('invoiceNumber')} maxLength={100} placeholder="INV-2026-001" className={styles.fieldInput} />
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
                <div className={styles.tableCardSubtitle}>Thêm sản phẩm và điền số lượng, đơn giá</div>
              </div>
              <div className={styles.tableCardActions}>
                <button className={`${styles.actionBtn} ${styles.importBtn}`} onClick={() => fileRef.current?.click()}>
                  <FileSpreadsheet className={styles.importIcon} /> Import Excel
                </button>
                <input ref={fileRef} type="file" accept=".xlsx,.xls" style={{ display: 'none' }} onChange={handleExcel} />
                <button className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={() => setModalOpen(true)}>
                  <Plus className={styles.primaryIcon} /> Thêm sản phẩm
                </button>
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
                          </td>
                          <td><span className={styles.skuTag} style={{ background: '#e0f2fe', color: '#0369a1' }}>{item.sku}</span></td>
                          <td>
                            <input type="number" min="1" step="1" value={item.quantity} onChange={(e) => onQtyChange(idx, e.target.value)}
                              className={`${styles.tableInput} ${qtyBad ? styles.inputError : ''}`} />
                          </td>
                          <td>
                            <input type="number" min="0" step="1000" value={item.unitPrice} onChange={(e) => onPriceChange(idx, e.target.value)}
                              className={`${styles.tableInput} ${priceBad ? styles.inputError : ''}`} />
                          </td>
                          <td className={styles.tdRight} style={{ fontWeight: 700, color: line > 0 ? '#2563eb' : '#94a3b8', fontSize: 12, whiteSpace: 'nowrap' }}>
                            {line > 0 ? formatVND(line) : '—'}
                          </td>
                          <td>
                            <button className={styles.removeBtn} onClick={() => onRemove(idx)}>
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

      <AddProductModal isOpen={modalOpen} onClose={() => setModalOpen(false)} onConfirm={onAddProducts} existingVariantIds={items.map((i) => i.variantId)} />
      {ConfirmDialog}
    </div>
  );
}
