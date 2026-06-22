import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, ClipboardList, PackageCheck, Plus, Save, Search, TrendingDown, TrendingUp, X, AlertCircle, Package, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../../app/router/routes';
import inventoryApi from '../../../../api/inventoryApi';
import stocktakeService from '../../services/stocktakeService';
import warehouseService from '../../services/warehouseService';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import useUnsavedChangesGuard from '../../hooks/useUnsavedChangesGuard';
import { formatNumber, formatVND, getResponseData } from '../components/inventoryDocumentListUtils';
import styles from '../CreatePage.module.css';

const today = new Date().toISOString().slice(0, 10);
const makeSessionCode = () => {
  const now = new Date();
  const ymd = now.toISOString().slice(0, 10).replaceAll('-', '');
  return `KK-${ymd}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
};

const hasActualQuantity = (item) => item.actualQuantity !== '' && item.actualQuantity !== null && item.actualQuantity !== undefined;
const getItemDiff = (item) => Number(item.actualQuantity || 0) - Number(item.systemQuantity || 0);
const getItemCost = (item) => Number(item.averageCost ?? item.costPrice ?? item.unitCost ?? item.unitPrice ?? item.price ?? 0);
const toStocktakeItem = (item) => ({
  variantId: item.variantId, variantSku: item.variantSku, variantName: item.variantName,
  productName: item.productName,
  systemQuantity: Number(item.quantityOnHand ?? item.availableQuantity ?? 0),
  actualQuantity: '',
  averageCost: getItemCost(item),
});

// ── DiffValue ────────────────────────────────────────────────────────────────
const DiffValue = ({ diff, checked }) => {
  if (!checked || diff === 0) return <span style={{ color: '#94a3b8' }}>—</span>;
  const Icon = diff > 0 ? TrendingUp : TrendingDown;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, color: diff > 0 ? '#0d9488' : '#dc2626', fontWeight: 800 }}>
      <Icon size={14} />
      {diff > 0 ? `+${formatNumber(diff)}` : `-${formatNumber(Math.abs(diff))}`}
    </span>
  );
};

// ── Add Product Modal ─────────────────────────────────────────────────────────
function AddProductModal({ open, products, selectedIds, loading, onClose, onAdd }) {
  const [keyword, setKeyword] = useState('');
  if (!open) return null;

  const filtered = products.filter((item) => {
    const k = keyword.trim().toLowerCase();
    if (!k) return true;
    return [item.productName, item.variantName, item.variantSku].filter(Boolean).some((v) => String(v).toLowerCase().includes(k));
  });

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 100, background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(2px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}
      onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div style={{ width: '100%', maxWidth: 520, background: '#fff', borderRadius: 16, boxShadow: '0 24px 60px rgba(0,0,0,0.18)', overflow: 'hidden', display: 'flex', flexDirection: 'column', maxHeight: '85vh' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '18px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, color: '#0f172a' }}>Thêm sản phẩm kiểm kê</div>
            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>Tìm và chọn sản phẩm cần thêm</div>
          </div>
          <button onClick={onClose} style={{ width: 30, height: 30, borderRadius: 8, border: 'none', background: 'transparent', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#94a3b8' }}><X size={18} /></button>
        </div>
        <div style={{ padding: '12px 20px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
            <input autoFocus value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Tìm theo tên hoặc mã sản phẩm..."
              style={{ width: '100%', padding: '8px 10px 8px 34px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 13, color: '#0f172a', outline: 'none', boxSizing: 'border-box' }} />
          </div>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {loading ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>
              <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Đang tải sản phẩm...
            </div>
          ) : filtered.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#94a3b8', fontSize: 13 }}>Không tìm thấy sản phẩm phù hợp.</div>
          ) : filtered.map((item) => {
            const isSelected = selectedIds.includes(item.variantId);
            return (
              <div key={item.variantId} onClick={() => !isSelected && onAdd(item)}
                style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 20px', cursor: isSelected ? 'default' : 'pointer', background: isSelected ? '#f8fafc' : '#fff', borderBottom: '1px solid #f1f5f9', opacity: isSelected ? 0.55 : 1 }}>
                <div style={{ width: 36, height: 36, borderRadius: 10, background: '#f0fdfa', color: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'monospace', fontWeight: 800, fontSize: 11, flexShrink: 0 }}>
                  {String(filtered.indexOf(item) + 1).padStart(2, '0')}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: '#0f172a', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.productName}{item.variantName ? ` — ${item.variantName}` : ''}</div>
                  <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>{item.variantSku} · Tồn HT: {formatNumber(item.quantityOnHand ?? item.availableQuantity ?? 0)}</div>
                </div>
                {isSelected ? (
                  <span style={{ fontSize: 12, fontWeight: 700, color: '#94a3b8' }}>Đã thêm</span>
                ) : (
                  <div style={{ width: 28, height: 28, borderRadius: 8, background: '#f0fdfa', color: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><Plus size={16} /></div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function StocktakeCreatePage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const [warehouses, setWarehouses] = useState([]);
  const [warehouseId, setWarehouseId] = useState('');
  const [sessionCode, setSessionCode] = useState(makeSessionCode());
  const [scheduledDate, setScheduledDate] = useState(today);
  const [notes, setNotes] = useState('');
  const [warehouseItems, setWarehouseItems] = useState([]);
  const [items, setItems] = useState([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);

  useEffect(() => {
    warehouseService.getAll()
      .then((response) => { const data = getResponseData(response); setWarehouses(Array.isArray(data) ? data : data.content ?? []); })
      .catch(() => setWarehouses([]));
  }, []);

  useEffect(() => {
    if (!warehouseId) return undefined;
    let ignore = false;
    inventoryApi.getByWarehouse(warehouseId, { page: 0, size: 500 })
      .then((response) => { if (!ignore) setWarehouseItems(Array.isArray(getResponseData(response)) ? getResponseData(response) : getResponseData(response).content ?? []); })
      .catch(() => { if (!ignore) { setWarehouseItems([]); toast.error('Không thể tải tồn kho của kho đã chọn.'); } })
      .finally(() => { if (!ignore) setLoadingItems(false); });
    return () => { ignore = true; };
  }, [warehouseId]);

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

  const hasUnsavedChanges = Boolean(warehouseId || notes.trim() || items.length > 0);
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  const fillActualWithSystem = () => { if (!items.length) return; setItems((current) => current.map((item) => ({ ...item, actualQuantity: String(item.systemQuantity) }))); };
  const addItem = (item) => { setItems((current) => current.some((e) => e.variantId === item.variantId) ? current : [...current, toStocktakeItem(item)]); };
  const openAddModal = () => { if (!warehouseId) { toast.error('Vui lòng chọn kho kiểm trước.'); return; } setAddModalOpen(true); };
  const updateActual = (variantId, value) => setItems((current) => current.map((item) => item.variantId === variantId ? { ...item, actualQuantity: value } : item));
  const removeItem = (variantId) => setItems((current) => current.filter((item) => item.variantId !== variantId));

  const buildPayload = (fillMissingWithSystem = false) => ({
    warehouseId, sessionCode, scheduledDate,
    items: items.map((item) => ({
      variantId: item.variantId,
      systemQuantity: Number(item.systemQuantity || 0),
      actualQuantity: fillMissingWithSystem && item.actualQuantity === '' ? Number(item.systemQuantity || 0) : Number(item.actualQuantity),
      notes: item.notes || notes || null,
    })),
  });

  const validateBase = () => {
    if (!warehouseId) { toast.error('Vui lòng chọn kho kiểm.'); return false; }
    if (!sessionCode.trim()) { toast.error('Vui lòng nhập mã phiếu kiểm.'); return false; }
    if (!items.length) { toast.error('Vui lòng thêm ít nhất một sản phẩm kiểm.'); return false; }
    return true;
  };

  const submit = async (complete) => {
    if (!validateBase()) return;
    if (complete) {
      const missing = items.find((item) => !hasActualQuantity(item));
      if (missing) { toast.error('Cần nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.'); return; }
    }
    const invalid = items.find((item) => hasActualQuantity(item) && Number(item.actualQuantity) < 0);
    if (invalid) { toast.error(`Tồn thực tế của "${invalid.productName}" không được âm.`); return; }
    setSubmitting(true);
    try {
      await stocktakeService.create(buildPayload(!complete), complete);
      toast.success(complete ? 'Hoàn thành phiếu kiểm kho thành công.' : 'Lưu tạm phiếu kiểm kho thành công.');
      runWithoutGuard(() => navigate(ROUTES.STOCKTAKES));
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể tạo phiếu kiểm kho.');
    } finally { setSubmitting(false); }
  };

  const progressPct = items.length > 0 ? Math.round((totals.checkedCount / items.length) * 100) : 0;

  return (
    <div className={styles.page}>

      {/* Page Header */}
      <div className={styles.pageHeader}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.STOCKTAKES)}><ArrowLeft size={15} /> Quay lại</button>
        <div className={styles.headerIcon} style={{ background: '#f0fdfa' }}>
          <ClipboardList size={18} color="#0d9488" />
        </div>
        <div>
          <h1 className={styles.headerTitle}>Tạo phiếu kiểm kho</h1>
          <p className={styles.headerSubtitle}>Kiểm kê và đối chiếu số lượng tồn kho thực tế</p>
        </div>
      </div>

      {/* Two-column layout */}
      <div className={styles.twoCol}>

        {/* LEFT */}
        <div className={styles.leftCol}>

          {/* Card: Thông tin phiếu kiểm */}
          <div className={`${styles.card} ${styles.cardPad}`}>
            <div className={styles.sectionHeader}>
              <div className={styles.sectionHeaderIcon} style={{ background: '#f0fdfa' }}><ClipboardList size={16} color="#0d9488" /></div>
              <div className={styles.sectionTitle}>Thông tin phiếu kiểm</div>
            </div>

            <div className={styles.formGrid} style={{ gridTemplateColumns: 'repeat(4, 1fr)' }}>
              <div>
                <label className={styles.fieldLabel}>Mã phiếu</label>
                <input value={sessionCode} onChange={(e) => setSessionCode(e.target.value)} className={styles.fieldInput} />
              </div>
              <div>
                <label className={styles.fieldLabel}>Ngày kiểm kho <span>*</span></label>
                <input type="date" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} className={styles.fieldInput} />
              </div>
              <div>
                <label className={styles.fieldLabel}>Giờ kiểm</label>
                <input readOnly value={new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} className={styles.fieldInput} style={{ background: '#f8fafc', color: '#475569' }} />
              </div>
              <div>
                <label className={styles.fieldLabel}>Kho kiểm <span>*</span></label>
                <select value={warehouseId} onChange={(e) => { setWarehouseId(e.target.value); setItems([]); setWarehouseItems([]); setAddModalOpen(false); setLoadingItems(Boolean(e.target.value)); }} className={styles.fieldSelect}>
                  <option value="">Chọn kho</option>
                  {warehouses.filter((w) => w.isActive !== false).map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
                </select>
              </div>
            </div>

            {/* Summary stats */}
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

            {/* Progress bar */}
            {items.length > 0 && (
              <div className={styles.progressSection}>
                <div className={styles.progressHeader}>
                  <span className={styles.progressLabel}>Tiến độ kiểm</span>
                  <span className={styles.progressValue}>{progressPct}% · {formatNumber(totals.checkedCount)}/{formatNumber(items.length)} đã kiểm</span>
                </div>
                <div className={styles.progressBar}><div className={styles.progressFill} style={{ width: `${progressPct}%` }} /></div>
                <div className={styles.progressStats}>
                  <span className={styles.progressStat} style={{ color: '#0d9488' }}>✓ Khớp: {formatNumber(totals.matchedCount)}</span>
                  <span className={styles.progressStat} style={{ color: '#0d9488' }}>↑ Thừa: {formatNumber(totals.surplusCount)}</span>
                  <span className={styles.progressStat} style={{ color: '#dc2626' }}>↓ Thiếu: {formatNumber(totals.shortageCount)}</span>
                </div>
              </div>
            )}

            <div style={{ marginTop: 14 }}>
              <label className={styles.fieldLabel}>Ghi chú</label>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} placeholder="Ghi chú về phiếu kiểm kho..." className={styles.fieldTextarea} />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button className={`${styles.actionBtn} ${styles.backBtn}`} onClick={() => navigate(ROUTES.STOCKTAKES)}><ArrowLeft size={14} /> Hủy</button>
              <button className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={() => submit(false)} disabled={submitting}><Save className={styles.primaryIcon} />{submitting ? 'Đang xử lý...' : 'Lưu tạm'}</button>
              <button className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={() => submit(true)} disabled={submitting}><CheckCircle2 className={styles.tealIcon} />{submitting ? 'Đang xử lý...' : 'Hoàn thành kiểm kho'}</button>
            </div>
          </div>

          {/* Card: Danh sách sản phẩm kiểm */}
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
                  <button className={`${styles.actionBtn} ${styles.secondaryBtn}`} onClick={fillActualWithSystem} disabled={!warehouseId || loadingItems}>Điền theo HT</button>
                )}
                <button className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={openAddModal} disabled={loadingItems}><Plus className={styles.tealIcon} />Thêm sản phẩm</button>
              </div>
            </div>

            {items.length === 0 ? (
              <div className={styles.emptyState}>
                <div className={styles.emptyIcon}><PackageCheck size={24} /></div>
                <p className={styles.emptyTitle}>Chưa có sản phẩm nào</p>
                <p className={styles.emptySubtitle}>Nhấn "Thêm sản phẩm" để bắt đầu kiểm kê</p>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className={styles.table} style={{ minWidth: 900 }}>
                  <thead>
                    <tr>
                      {['STT', 'Mã SP', 'Tên sản phẩm', 'ĐVT', 'Tồn kho (HT)', 'Tồn kho thực tế', 'SL lệch', 'Giá trị lệch', ''].map((h, i) => (
                        <th key={h} className={i >= 4 ? styles.thRight : ''}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, index) => {
                      const checked = hasActualQuantity(item);
                      const diff = checked ? getItemDiff(item) : 0;
                      const diffValue = diff * getItemCost(item);
                      return (
                        <tr key={item.variantId} style={{ background: checked && diff < 0 ? '#fff5f5' : checked && diff > 0 ? '#f0fdfa' : '#fff', borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ color: '#94a3b8', fontSize: 11 }}>{index + 1}</td>
                          <td><span className={styles.skuTag} style={{ background: '#ccfbf1', color: '#0d9488' }}>{item.variantSku}</span></td>
                          <td style={{ fontWeight: 600, color: '#0f172a' }}>{item.productName}{item.variantName ? ` — ${item.variantName}` : ''}</td>
                          <td style={{ color: '#94a3b8', fontSize: 11 }}>Cái</td>
                          <td className={styles.tdRight} style={{ fontWeight: 600, color: '#0f172a' }}>{formatNumber(item.systemQuantity)}</td>
                          <td>
                            <input type="number" min="0" value={item.actualQuantity} onChange={(e) => updateActual(item.variantId, e.target.value)}
                              className={styles.fieldInput} style={{ width: 100, height: 34, textAlign: 'center', fontSize: 12 }} />
                          </td>
                          <td className={styles.tdRight}><DiffValue diff={diff} checked={checked} /></td>
                          <td className={styles.tdRight} style={{ fontWeight: 700, color: !checked || diffValue === 0 ? '#94a3b8' : diffValue < 0 ? '#dc2626' : '#0d9488' }}>
                            {checked ? formatVND(diffValue) : '—'}
                          </td>
                          <td>
                            <button className={styles.removeBtn} onClick={() => removeItem(item.variantId)}>
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

        {/* RIGHT */}
        <div className={styles.rightCol}>

          {/* Quick actions */}
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Thao tác nhanh</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={openAddModal} disabled={loadingItems}><Plus className={styles.tealIcon} />Thêm sản phẩm kiểm</button>
              {items.length > 0 && (
                <button className={`${styles.actionBtn} ${styles.secondaryBtn}`} onClick={fillActualWithSystem} disabled={!warehouseId || loadingItems}><Package className={styles.secondaryIcon} />Điền SL theo hệ thống</button>
              )}
            </div>
          </div>

          {/* Status breakdown */}
          {items.length > 0 && (
            <div className={`${styles.card} ${styles.sidebarCard}`}>
              <div className={styles.sidebarCardTitle}>Trạng thái kiểm kê</div>
              <div className={styles.statusBreakdown}>
                {[
                  { label: 'Đã kiểm', value: totals.checkedCount, color: '#0d9488', bg: '#f0fdfa' },
                  { label: 'Khớp (đúng)', value: totals.matchedCount, color: '#0d9488', bg: '#f0fdfa' },
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

          {/* Notes */}
          <div className={`${styles.card} ${styles.noteCard}`} style={{ background: '#f0fdfa', border: '1px solid #ccfbf1' }}>
            <div className={styles.noteHeader}>
              <AlertCircle size={14} color="#0d9488" />
              <span className={styles.noteTitle} style={{ color: '#0f766e' }}>Lưu ý khi kiểm kho</span>
            </div>
            <ul className={styles.noteList}>
              {['Nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.', 'Dùng "Điền theo hệ thống" để điền nhanh số lượng ban đầu.', 'Lưu tạm để tiếp tục kiểm kho sau.', 'Chênh lệch sẽ được ghi nhận để điều chỉnh tồn kho.'].map((note) => (
                <li key={note} className={styles.noteItem} style={{ color: '#0f766e' }}>{note}</li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      <AddProductModal open={addModalOpen} products={warehouseItems} selectedIds={items.map((item) => item.variantId)} loading={loadingItems} onClose={() => setAddModalOpen(false)} onAdd={addItem} />
      {ConfirmDialog}
    </div>
  );
}
