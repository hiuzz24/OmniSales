import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
  ClipboardList,
  Loader2,
  LockKeyhole,
  MapPin,
  Package,
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

function AddProductModal({ open, products, selectedIds, loading, onClose, onAdd, warehouseName }) {
  const [keyword, setKeyword] = useState('');
  if (!open) return null;

  const filtered = products.filter((item) => {
    const k = keyword.trim().toLowerCase();
    if (!k) return true;
    return [item.productName, item.variantName, item.variantSku]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(k));
  });

  return (
    <div className={styles.modalBackdrop} onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div className={styles.stocktakeModal}>
        <div className={styles.modalHeader}>
          <div>
            <div className={styles.modalTitle}>Thêm sản phẩm kiểm kê</div>
            <div className={styles.modalSubtitle}>Chỉ hiển thị SKU thuộc kho mặc định: {warehouseName || '—'}</div>
          </div>
          <button type="button" onClick={onClose} className={styles.modalCloseBtn} aria-label="Đóng">
            <X size={18} />
          </button>
        </div>

        <div className={styles.modalSearchRow}>
          <Search size={16} className={styles.modalSearchIcon} />
          <input
            autoFocus
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Tìm theo tên, biến thể hoặc SKU..."
            className={styles.modalSearchInput}
          />
        </div>

        <div className={styles.modalList}>
          {loading ? (
            <div className={styles.modalLoading}>
              <Loader2 size={18} className={styles.spinIcon} />
              Đang tải sản phẩm trong kho mặc định...
            </div>
          ) : filtered.length === 0 ? (
            <div className={styles.modalEmpty}>Không tìm thấy sản phẩm phù hợp.</div>
          ) : filtered.map((item, index) => {
            const isSelected = selectedIds.includes(item.variantId);
            return (
              <button
                type="button"
                key={item.variantId}
                disabled={isSelected}
                className={`${styles.modalProductRow} ${isSelected ? styles.modalProductRowDisabled : ''}`}
                onClick={() => onAdd(item)}
              >
                <span className={styles.modalProductIndex}>{String(index + 1).padStart(2, '0')}</span>
                <span className={styles.modalProductInfo}>
                  <strong>{item.productName}{item.variantName ? ` — ${item.variantName}` : ''}</strong>
                  <small>{item.variantSku} · Tồn hệ thống: {formatNumber(item.quantityOnHand ?? item.availableQuantity ?? 0)}</small>
                </span>
                {isSelected ? <span className={styles.modalAdded}>Đã thêm</span> : <span className={styles.modalAddIcon}><Plus size={16} /></span>}
              </button>
            );
          })}
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
  const [submitting, setSubmitting] = useState(false);
  const [addModalOpen, setAddModalOpen] = useState(false);

  useEffect(() => {
    let ignore = false;
    setLoadingWarehouse(true);
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

  useEffect(() => {
    if (!warehouseId) {
      setWarehouseItems([]);
      return undefined;
    }
    let ignore = false;
    setLoadingItems(true);
    setWarehouseItems([]);
    inventoryApi.getByWarehouse(warehouseId, { page: 0, size: 500 })
      .then((response) => {
        if (ignore) return;
        const data = getResponseData(response);
        setWarehouseItems(Array.isArray(data) ? data : data.content ?? []);
      })
      .catch(() => {
        if (!ignore) {
          setWarehouseItems([]);
          toast.error('Không thể tải tồn kho của kho mặc định.');
        }
      })
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

  const hasUnsavedChanges = Boolean(notes.trim() || items.length > 0);
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  const progressPct = items.length > 0 ? Math.round((totals.checkedCount / items.length) * 100) : 0;

  const fillActualWithSystem = () => {
    if (!items.length) return;
    setItems((current) => current.map((item) => ({ ...item, actualQuantity: String(item.systemQuantity) })));
  };

  const addItem = (item) => {
    setItems((current) => current.some((existing) => existing.variantId === item.variantId)
      ? current
      : [...current, toStocktakeItem(item)]);
  };

  const openAddModal = () => {
    if (!warehouseId) {
      toast.error('Chưa có kho mặc định để kiểm kho.');
      return;
    }
    setAddModalOpen(true);
  };

  const updateActual = (variantId, value) => {
    setItems((current) => current.map((item) => item.variantId === variantId ? { ...item, actualQuantity: value } : item));
  };

  const removeItem = (variantId) => setItems((current) => current.filter((item) => item.variantId !== variantId));

  const buildPayload = (fillMissingWithSystem = false) => ({
    warehouseId,
    sessionCode,
    scheduledDate,
    items: items.map((item) => ({
      variantId: item.variantId,
      systemQuantity: Number(item.systemQuantity || 0),
      actualQuantity: fillMissingWithSystem && item.actualQuantity === '' ? Number(item.systemQuantity || 0) : Number(item.actualQuantity),
      notes: item.notes || notes || null,
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
    setSubmitting(true);
    try {
      await stocktakeService.create(buildPayload(!complete), complete);
      toast.success(complete ? 'Hoàn thành phiếu kiểm kho thành công.' : 'Lưu tạm phiếu kiểm kho thành công.');
      runWithoutGuard(() => navigate(ROUTES.STOCKTAKES));
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể tạo phiếu kiểm kho.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.page}>
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
              <div className={styles.defaultWarehouseCard}>
                <div className={styles.defaultWarehouseIcon}>
                  {loadingWarehouse ? <Loader2 size={18} className={styles.spinIcon} /> : <Warehouse size={18} />}
                </div>
                <div className={styles.defaultWarehouseContent}>
                  <span className={styles.defaultWarehouseLabel}>Kho kiểm mặc định</span>
                  <strong>{loadingWarehouse ? 'Đang tải kho mặc định...' : defaultWarehouse?.name || 'Chưa cấu hình kho mặc định'}</strong>
                  {defaultWarehouse?.address && <small><MapPin size={13} /> {defaultWarehouse.address}</small>}
                </div>
                <span className={warehouseId ? styles.lockedPill : styles.warningPill}>
                  <LockKeyhole size={13} />
                  {warehouseId ? 'Đã khóa' : 'Cần cấu hình'}
                </span>
              </div>
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
              <button type="button" className={`${styles.actionBtn} ${styles.backBtn}`} onClick={() => navigate(ROUTES.STOCKTAKES)}>
                <ArrowLeft size={14} /> Hủy
              </button>
              <button type="button" className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={() => submit(false)} disabled={submitting || loadingWarehouse}>
                <Save className={styles.primaryIcon} />{submitting ? 'Đang xử lý...' : 'Lưu tạm'}
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
                          <td className={styles.productNameCell}>{item.productName}{item.variantName ? ` — ${item.variantName}` : ''}</td>
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
              <Warehouse size={18} />
              <div>
                <strong>{defaultWarehouse?.name || 'Kho mặc định'}</strong>
                <span>{defaultWarehouse?.address || 'Hệ thống chỉ dùng kho mặc định cho phiếu kiểm này.'}</span>
              </div>
            </div>
          </div>

          <div className={`${styles.card} ${styles.sidebarCard}`}>
            <div className={styles.sidebarCardTitle}>Thao tác nhanh</div>
            <div className={styles.sidebarActionStack}>
              <button type="button" className={`${styles.actionBtn} ${styles.tealBtn}`} onClick={openAddModal} disabled={!warehouseId || loadingItems}>
                <Plus className={styles.tealIcon} />Thêm sản phẩm kiểm
              </button>
              {items.length > 0 && (
                <button type="button" className={`${styles.actionBtn} ${styles.secondaryBtn}`} onClick={fillActualWithSystem} disabled={!warehouseId || loadingItems}>
                  <Package className={styles.secondaryIcon} />Điền SL theo hệ thống
                </button>
              )}
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
        selectedIds={items.map((item) => item.variantId)}
        loading={loadingItems}
        onClose={() => setAddModalOpen(false)}
        onAdd={addItem}
        warehouseName={defaultWarehouse?.name}
      />
      {ConfirmDialog}
    </div>
  );
}
