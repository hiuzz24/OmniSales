import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCircle2,
  ClipboardList,
  PackageCheck,
  Plus,
  Save,
  Search,
  TrendingDown,
  TrendingUp,
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

const today = new Date().toISOString().slice(0, 10);

const makeSessionCode = () => {
  const now = new Date();
  const ymd = now.toISOString().slice(0, 10).replaceAll('-', '');
  return `KK-${ymd}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
};

function AddProductModal({ open, products, selectedIds, loading, onClose, onAdd }) {
  const [keyword, setKeyword] = useState('');

  if (!open) return null;

  const filteredProducts = products
    .filter((item) => {
      const search = keyword.trim().toLowerCase();
      if (!search) return true;
      return [item.productName, item.variantName, item.variantSku]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(search));
    });

  return (
    <div style={modalBackdropStyle} onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div style={modalStyle}>
        <div style={modalHeaderStyle}>
          <div>
            <h2 style={modalTitleStyle}>Thêm sản phẩm kiểm kê</h2>
            <p style={modalSubtitleStyle}>Tìm và chọn sản phẩm cần thêm vào phiếu kiểm kho</p>
          </div>
          <button type="button" onClick={onClose} style={modalCloseButtonStyle}>
            <X size={18} />
          </button>
        </div>

        <div style={modalSearchWrapStyle}>
          <Search size={18} style={{ color: '#8aa0bd', flexShrink: 0 }} />
          <input
            autoFocus
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Tìm theo tên hoặc mã sản phẩm..."
            style={modalSearchInputStyle}
          />
        </div>

        <div style={modalListStyle}>
          {loading ? (
            <div style={modalEmptyStyle}>Đang tải sản phẩm...</div>
          ) : filteredProducts.length ? (
            filteredProducts.map((item, index) => {
              const isSelected = selectedIds.includes(item.variantId);
              return (
              <button
                key={item.variantId}
                type="button"
                disabled={isSelected}
                onClick={() => onAdd(item)}
                style={{
                  ...modalProductRowStyle,
                  opacity: isSelected ? 0.42 : 1,
                  cursor: isSelected ? 'default' : 'pointer',
                }}
              >
                <span style={modalOrdinalStyle}>{String(index + 1).padStart(3, '0')}</span>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={modalProductNameStyle}>{item.productName}{item.variantName ? ` - ${item.variantName}` : ''}</div>
                  <div style={modalProductMetaStyle}>
                    {item.variantSku} · Cái · Tồn HT: {formatNumber(item.quantityOnHand ?? item.availableQuantity ?? 0)}
                  </div>
                </div>
                {isSelected ? (
                  <span style={modalAddedTextStyle}>Đã thêm</span>
                ) : (
                  <span title="Thêm sản phẩm" style={modalAddButtonStyle}>
                    <Plus size={18} />
                  </span>
                )}
              </button>
              );
            })
          ) : (
            <div style={modalEmptyStyle}>Không còn sản phẩm phù hợp.</div>
          )}
        </div>
      </div>
    </div>
  );
}

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
      .then((response) => {
        const data = getResponseData(response);
        setWarehouses(Array.isArray(data) ? data : data.content ?? []);
      })
      .catch(() => setWarehouses([]));
  }, []);

  useEffect(() => {
    if (!warehouseId) return undefined;
    let ignore = false;
    inventoryApi.getByWarehouse(warehouseId, { page: 0, size: 500 })
      .then((response) => {
        if (ignore) return;
        const data = getResponseData(response);
        const content = Array.isArray(data) ? data : data.content ?? [];
        setWarehouseItems(content);
      })
      .catch(() => {
        if (ignore) return;
        setWarehouseItems([]);
        toast.error('Không thể tải tồn kho của kho đã chọn.');
      })
      .finally(() => {
        if (!ignore) setLoadingItems(false);
      });
    return () => {
      ignore = true;
    };
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
    return {
      systemQty,
      actualQty,
      diffQty,
      diffValue,
      checkedCount: checkedItems.length,
      matchedCount,
      surplusCount,
      shortageCount,
    };
  }, [items]);
  const hasUnsavedChanges = Boolean(warehouseId || notes.trim() || items.length > 0);
  const { runWithoutGuard } = useUnsavedChangesGuard({ when: hasUnsavedChanges, confirm });

  const fillActualWithSystem = () => {
    if (!items.length) return;
    setItems((current) => current.map((item) => ({ ...item, actualQuantity: String(item.systemQuantity) })));
  };

  const addItem = (item) => {
    setItems((current) => (
      current.some((entry) => entry.variantId === item.variantId)
        ? current
        : [...current, toStocktakeItem(item)]
    ));
  };

  const openAddModal = () => {
    if (!warehouseId) {
      toast.error('Vui lòng chọn kho kiểm.');
      return;
    }
    setAddModalOpen(true);
  };

  const updateActual = (variantId, value) => {
    setItems((current) => current.map((item) => (
      item.variantId === variantId ? { ...item, actualQuantity: value } : item
    )));
  };

  const removeItem = (variantId) => {
    setItems((current) => current.filter((item) => item.variantId !== variantId));
  };

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
      toast.error('Vui lòng chọn kho kiểm.');
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
      toast.error(`Tồn thực tế của ${invalid.productName} không được âm.`);
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
    <div style={pageStyle}>
      <div style={headerStyle}>
        <button type="button" onClick={() => navigate(ROUTES.STOCKTAKES)} style={backButtonStyle}>
          <ArrowLeft size={18} /> Quay lại
        </button>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={headerIconStyle}><ClipboardList size={20} color="#009688" /></div>
          <div>
            <h1 style={titleStyle}>Tạo phiếu kiểm kho</h1>
            <p style={subtitleStyle}>Kiểm kê và đối chiếu số lượng tồn kho thực tế</p>
          </div>
        </div>
      </div>

      <section style={panelStyle}>
        <h2 style={sectionTitleStyle}>Thông tin phiếu kiểm</h2>
        <div style={formGridStyle}>
          <Field label="Mã phiếu">
            <input value={sessionCode} onChange={(event) => setSessionCode(event.target.value)} style={inputStyle} />
          </Field>
          <Field label="Ngày kiểm kho *">
            <input type="date" value={scheduledDate} onChange={(event) => setScheduledDate(event.target.value)} style={inputStyle} />
          </Field>
          <Field label="Giờ kiểm">
            <input readOnly value={new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} style={readOnlyInputStyle} />
          </Field>
          <Field label="Kho kiểm *">
            <select
              value={warehouseId}
              onChange={(event) => {
                const nextWarehouseId = event.target.value;
                setWarehouseId(nextWarehouseId);
                setItems([]);
                setWarehouseItems([]);
                setAddModalOpen(false);
                setLoadingItems(Boolean(nextWarehouseId));
              }}
              style={inputStyle}
            >
              <option value="">Chọn kho</option>
              {warehouses.filter((warehouse) => warehouse.isActive !== false).map((warehouse) => (
                <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
              ))}
            </select>
          </Field>
        </div>

        <div style={summaryGridStyle}>
          <Summary label="Tổng SL tồn kho (hệ thống)" value={formatNumber(totals.systemQty)} />
          <Summary label="Tổng SL tồn kho thực tế" value={formatNumber(totals.actualQty)} />
          <Summary label="Tổng SL chênh lệch" value={`${totals.diffQty > 0 ? '+' : ''}${formatNumber(totals.diffQty)}`} color={totals.diffQty < 0 ? '#dc2626' : '#009688'} />
          <Summary label="Tổng giá trị chênh lệch" value={formatVND(totals.diffValue)} color={totals.diffValue < 0 ? '#dc2626' : '#009688'} />
        </div>

        <Field label="Ghi chú">
          <textarea value={notes} onChange={(event) => setNotes(event.target.value)} rows={3} placeholder="Ghi chú về phiếu kiểm kho..." style={{ ...inputStyle, height: 'auto', paddingTop: 10 }} />
        </Field>

        <div style={actionsRowStyle}>
          <button type="button" onClick={() => navigate(ROUTES.STOCKTAKES)} style={secondaryButtonStyle}>Hủy</button>
          <button type="button" onClick={() => submit(false)} disabled={submitting} style={secondaryButtonStyle}>
            <Save size={16} /> Lưu tạm
          </button>
          <button type="button" onClick={() => submit(true)} disabled={submitting} style={primaryButtonStyle}>
            <CheckCircle2 size={16} /> Hoàn thành kiểm kho
          </button>
        </div>
      </section>

      <section style={panelStyle}>
        <div style={tableHeaderStyle}>
          <div>
            <h2 style={sectionTitleStyle}>Danh sách sản phẩm kiểm</h2>
            <p style={subtitleStyle}>
              {formatNumber(totals.checkedCount)}/{formatNumber(items.length)} đã kiểm · {formatNumber(totals.matchedCount)} khớp · {formatNumber(totals.surplusCount)} thừa · {formatNumber(totals.shortageCount)} thiếu
            </p>
          </div>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {items.length > 0 && (
              <button type="button" onClick={fillActualWithSystem} disabled={!warehouseId || loadingItems} style={secondaryButtonStyle}>
                Điền theo hệ thống
              </button>
            )}
            <button type="button" onClick={openAddModal} disabled={loadingItems} style={primaryButtonStyle}>
              <Plus size={16} /> Thêm sản phẩm
            </button>
          </div>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', minWidth: 980, borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr>
                {['STT', 'Mã sản phẩm', 'Tên sản phẩm', 'ĐVT', 'Tồn kho (HT)', 'Tồn kho thực tế', 'SL lệch', 'Giá trị lệch', 'Xóa'].map((header) => (
                  <th key={header} style={thStyle}>{header}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {!items.length ? (
                <tr>
                  <td colSpan={9} style={emptyStyle}>
                    <div style={emptyBoxStyle}>
                      <span style={emptyIconStyle}><PackageCheck size={26} /></span>
                      <strong>Chưa có sản phẩm nào</strong>
                      <span>Nhấn "Thêm sản phẩm" để bắt đầu kiểm kê</span>
                    </div>
                  </td>
                </tr>
              ) : items.map((item, index) => {
                const checked = hasActualQuantity(item);
                const diff = checked ? getItemDiff(item) : 0;
                const diffValue = diff * getItemCost(item);
                return (
                  <tr key={item.variantId} style={{ background: checked && diff < 0 ? '#fff7f7' : checked && diff > 0 ? '#f0fdf9' : '#fff' }}>
                    <td style={tdStyle}>{index + 1}</td>
                    <td style={tdStyle}><span style={skuStyle}>{item.variantSku}</span></td>
                    <td style={{ ...tdStyle, color: '#020617', fontWeight: 700 }}>{item.productName}{item.variantName ? ` - ${item.variantName}` : ''}</td>
                    <td style={tdStyle}>Cái</td>
                    <td style={{ ...tdStyle, textAlign: 'right' }}>{formatNumber(item.systemQuantity)}</td>
                    <td style={tdStyle}>
                      <input
                        type="number"
                        min="0"
                        value={item.actualQuantity}
                        onChange={(event) => updateActual(item.variantId, event.target.value)}
                        placeholder="Nhập SL"
                        style={{ ...inputStyle, width: 120, textAlign: 'center' }}
                      />
                    </td>
                    <td style={{ ...tdStyle, textAlign: 'right' }}>
                      <DiffValue diff={diff} checked={checked} />
                    </td>
                    <td style={{ ...tdStyle, textAlign: 'right', color: !checked || diffValue === 0 ? '#94a3b8' : diffValue < 0 ? '#dc2626' : '#009688', fontWeight: 700 }}>
                      {checked ? formatVND(diffValue) : '—'}
                    </td>
                    <td style={tdStyle}>
                      <button type="button" onClick={() => removeItem(item.variantId)} style={deleteButtonStyle}><X size={16} /></button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
            {!!items.length && (
              <tfoot>
                <tr>
                  <td colSpan={4} style={tfootStyle}>{formatNumber(items.length)} sản phẩm</td>
                  <td style={{ ...tfootStyle, textAlign: 'right' }}>HT: {formatNumber(totals.systemQty)}</td>
                  <td style={{ ...tfootStyle, textAlign: 'right' }}>TT: {formatNumber(totals.actualQty)}</td>
                  <td colSpan={3} style={{ ...tfootStyle, textAlign: 'right', color: totals.diffValue < 0 ? '#dc2626' : '#009688' }}>{formatVND(totals.diffValue)}</td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </section>

      <AddProductModal
        open={addModalOpen}
        products={warehouseItems}
        selectedIds={items.map((item) => item.variantId)}
        loading={loadingItems}
        onClose={() => setAddModalOpen(false)}
        onAdd={addItem}
      />
      {ConfirmDialog}
    </div>
  );
}

const hasActualQuantity = (item) => item.actualQuantity !== '' && item.actualQuantity !== null && item.actualQuantity !== undefined;

const getItemDiff = (item) => Number(item.actualQuantity || 0) - Number(item.systemQuantity || 0);

const getItemCost = (item) => Number(
  item.averageCost
  ?? item.costPrice
  ?? item.unitCost
  ?? item.unitPrice
  ?? item.price
  ?? 0
);

const toStocktakeItem = (item) => ({
  variantId: item.variantId,
  variantSku: item.variantSku,
  variantName: item.variantName,
  productName: item.productName,
  systemQuantity: Number(item.quantityOnHand ?? item.availableQuantity ?? 0),
  actualQuantity: '',
  averageCost: getItemCost(item),
});

const Field = ({ label, children }) => (
  <label style={{ display: 'flex', flexDirection: 'column', gap: 8, color: '#0f172a', fontSize: 14, fontWeight: 700 }}>{label}{children}</label>
);

const Summary = ({ label, value, color = '#020617' }) => (
  <div style={summaryStyle}>
    <span style={{ color: '#8aa0bd', fontSize: 13 }}>{label}</span>
    <strong style={{ color, fontSize: 18 }}>{value}</strong>
  </div>
);

const DiffValue = ({ diff, checked }) => {
  if (!checked || diff === 0) {
    return <span style={{ color: '#cbd5e1' }}>—</span>;
  }
  const Icon = diff > 0 ? TrendingUp : TrendingDown;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'flex-end', gap: 4, color: diff > 0 ? '#009688' : '#dc2626', fontWeight: 800 }}>
      <Icon size={15} />
      {diff > 0 ? `+${formatNumber(diff)}` : `-${formatNumber(Math.abs(diff))}`}
    </span>
  );
};

const pageStyle = { display: 'flex', flexDirection: 'column', gap: 24 };
const headerStyle = { display: 'flex', alignItems: 'center', gap: 22, flexWrap: 'wrap' };
const backButtonStyle = { display: 'inline-flex', alignItems: 'center', gap: 8, border: 'none', background: 'transparent', color: '#020617', fontWeight: 700, cursor: 'pointer' };
const headerIconStyle = { width: 40, height: 40, borderRadius: 12, background: '#ecfeff', display: 'flex', alignItems: 'center', justifyContent: 'center' };
const titleStyle = { margin: 0, fontSize: 24, color: '#020617' };
const subtitleStyle = { margin: '4px 0 0', color: '#64748b', fontSize: 13, fontWeight: 500 };
const panelStyle = { border: '1px solid #dfe7f2', borderRadius: 10, background: '#fff', padding: 24 };
const sectionTitleStyle = { margin: 0, fontSize: 20, color: '#020617' };
const formGridStyle = { display: 'grid', gridTemplateColumns: 'repeat(4, minmax(180px, 1fr))', gap: 16, marginTop: 22 };
const inputStyle = { width: '100%', height: 40, border: '1px solid #dbe4ef', borderRadius: 8, background: '#fff', color: '#020617', fontSize: 14, outline: 'none', padding: '0 12px', boxSizing: 'border-box' };
const readOnlyInputStyle = { ...inputStyle, background: '#f8fafc', color: '#64748b' };
const summaryGridStyle = { display: 'grid', gridTemplateColumns: 'repeat(4, minmax(160px, 1fr))', gap: 16, margin: '18px 0' };
const summaryStyle = { minHeight: 70, borderRadius: 10, background: '#f8fafc', padding: '14px 16px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 8 };
const actionsRowStyle = { display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18, flexWrap: 'wrap' };
const primaryButtonStyle = { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, border: 'none', borderRadius: 8, background: '#009688', color: '#fff', fontSize: 14, fontWeight: 700, padding: '10px 16px', cursor: 'pointer' };
const secondaryButtonStyle = { ...primaryButtonStyle, border: '1px solid #dbe4ef', background: '#fff', color: '#020617' };
const tableHeaderStyle = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, marginBottom: 18, flexWrap: 'wrap' };
const thStyle = { padding: '12px 10px', textAlign: 'left', background: '#f8fafc', borderBottom: '1px solid #dfe7f2', whiteSpace: 'nowrap', color: '#020617' };
const tdStyle = { padding: '12px 10px', borderBottom: '1px solid #eef2f7', color: '#33476a', verticalAlign: 'middle', whiteSpace: 'nowrap' };
const emptyStyle = { padding: 64, textAlign: 'center', color: '#8aa0bd', borderBottom: '1px solid #eef2f7' };
const emptyBoxStyle = { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 };
const emptyIconStyle = { width: 56, height: 56, borderRadius: 14, background: '#f1f5f9', color: '#94a3b8', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' };
const skuStyle = { display: 'inline-flex', borderRadius: 5, background: '#ccfbf1', color: '#00796b', padding: '3px 8px', fontFamily: 'monospace', fontWeight: 700, fontSize: 12 };
const deleteButtonStyle = { width: 30, height: 30, border: 'none', borderRadius: 8, background: 'transparent', color: '#94a3b8', cursor: 'pointer' };
const tfootStyle = { padding: '13px 10px', background: '#f8fafc', color: '#33476a', fontWeight: 700, borderTop: '1px solid #dfe7f2' };

const modalBackdropStyle = {
  position: 'fixed',
  inset: 0,
  zIndex: 80,
  background: 'rgba(15, 23, 42, 0.48)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 20,
};

const modalStyle = {
  width: 'min(512px, 100%)',
  borderRadius: 10,
  background: '#fff',
  padding: 24,
  boxShadow: '0 24px 50px rgba(15, 23, 42, 0.24)',
};

const modalHeaderStyle = { display: 'flex', justifyContent: 'space-between', gap: 16, marginBottom: 18 };
const modalTitleStyle = { margin: 0, fontSize: 20, color: '#020617', fontWeight: 800 };
const modalSubtitleStyle = { margin: '6px 0 0', fontSize: 13, color: '#64748b' };
const modalCloseButtonStyle = { width: 28, height: 28, border: 'none', borderRadius: 8, background: 'transparent', color: '#475569', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' };
const modalSearchWrapStyle = { height: 42, border: '1px solid #cbd5e1', borderRadius: 9, display: 'flex', alignItems: 'center', gap: 10, padding: '0 12px', marginBottom: 12, boxShadow: '0 0 0 2px rgba(15, 23, 42, 0.08)' };
const modalSearchInputStyle = { border: 'none', outline: 'none', flex: 1, fontSize: 14, color: '#020617' };
const modalListStyle = { maxHeight: 320, overflowY: 'auto', border: '1px solid #eef2f7', borderRadius: 9 };
const modalProductRowStyle = { width: '100%', display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px', border: 'none', borderBottom: '1px solid #eef2f7', background: '#fff', textAlign: 'left' };
const modalOrdinalStyle = { width: 38, height: 38, borderRadius: 11, background: '#ecfeff', color: '#009688', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'monospace', fontWeight: 800, fontSize: 12, flexShrink: 0 };
const modalProductNameStyle = { color: '#020617', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };
const modalProductMetaStyle = { color: '#7890b2', fontSize: 12, marginTop: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' };
const modalAddButtonStyle = { width: 32, height: 32, border: 'none', borderRadius: 8, background: 'transparent', color: '#009688', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 };
const modalAddedTextStyle = { color: '#94a3b8', fontSize: 12, fontWeight: 700, flexShrink: 0 };
const modalEmptyStyle = { padding: 36, textAlign: 'center', color: '#8aa0bd' };
