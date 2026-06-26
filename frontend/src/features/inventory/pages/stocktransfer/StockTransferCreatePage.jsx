import { useState, useEffect, useRef, useCallback, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../../../auth/context/AuthContext';
import {
  ArrowLeft, ArrowRightLeft, Plus, Trash2,
  Search, X, AlertCircle, Loader2, PackageOpen,
  CheckCircle2, Save, Ban,
} from 'lucide-react';
import { toast } from 'react-toastify';

import transferApi from '../../../../api/transferApi';
import warehouseService from '../../services/warehouseService';
import { ROUTES } from '../../../../app/router/routes';
import { ROLES } from '../../../auth/constants/roles';

// ─── Helpers ────────────────────────────────────────────────────────────────
const today = new Date();
const formatDateValue = (d) => d.toISOString().split('T')[0];
const formatTimeValue = (d) =>
  d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false });

const genTransferCode = () => {
  const now = new Date();
  const yy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const seq = String(Math.floor(Math.random() * 900) + 100);
  return `TRANS-${yy}-${mm}${dd}-${seq}`;
};

const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

// ─── Styles helpers ─────────────────────────────────────────────────────────
const inputStyle = (hasErr = false) => ({
  width: '100%',
  padding: '9px 12px',
  borderRadius: 8,
  border: `1.5px solid ${hasErr ? '#fca5a5' : '#e2e8f0'}`,
  fontSize: 13,
  outline: 'none',
  boxSizing: 'border-box',
  backgroundColor: hasErr ? '#fff5f5' : '#fff',
  color: '#0f172a',
  transition: 'border-color 0.15s',
});

const selectStyle = (hasErr = false) => ({
  ...inputStyle(hasErr),
  appearance: 'none',
  backgroundImage:
    "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E\")",
  backgroundRepeat: 'no-repeat',
  backgroundPosition: 'right 10px center',
  paddingRight: 36,
  cursor: 'pointer',
});

const labelStyle = {
  display: 'block',
  fontSize: 12,
  fontWeight: 600,
  color: '#374151',
  marginBottom: 5,
};

const btnPrimary = (disabled = false) => ({
  display: 'flex', alignItems: 'center', gap: 6,
  padding: '9px 20px', borderRadius: 8, border: 'none',
  backgroundColor: disabled ? '#c4b5fd' : '#7c3aed',
  color: '#fff', fontSize: 13, fontWeight: 600,
  cursor: disabled ? 'not-allowed' : 'pointer',
  transition: 'background-color 0.15s',
});

// ─── Add Product Modal ───────────────────────────────────────────────────────
function AddProductModal({ isOpen, onClose, onAdd, warehouseId, existingVariantIds = [] }) {
  const [keyword, setKeyword] = useState('');
  const [variants, setVariants] = useState([]);
  const [loading, setLoading] = useState(false);
  const timerRef = useRef(null);

  // Reset khi modal đóng
  useEffect(() => {
    if (!isOpen) { setKeyword(''); setVariants([]); }
  }, [isOpen]);

  // Load variants của kho xuất khi modal mở
  useEffect(() => {
    if (!isOpen || !warehouseId) return;
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await transferApi.getAvailableVariants(warehouseId);
        const data = res?.data ?? [];
        setVariants(Array.isArray(data) ? data : []);
      } catch {
        setVariants([]);
        toast.error('Không thể tải danh sách sản phẩm.');
      } finally {
        setLoading(false);
      }
    }, 0);
    return () => clearTimeout(timerRef.current);
  }, [isOpen, warehouseId]);

  // Filter theo keyword
  const filtered = variants.filter((v) => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return true;
    return (
      v.variantName?.toLowerCase().includes(kw) ||
      v.sku?.toLowerCase().includes(kw)
    );
  });

  const handleAdd = (variant) => {
    if (existingVariantIds.includes(variant.variantId)) return;
    onAdd(variant);
    toast.success(`Đã thêm "${variant.variantName}" vào phiếu.`);
  };

  if (!isOpen) return null;

  return (
    <div
      onClick={(e) => e.target === e.currentTarget && onClose()}
      style={{
        position: 'fixed', inset: 0, zIndex: 60,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        backgroundColor: 'rgba(15,23,42,0.5)',
        backdropFilter: 'blur(2px)',
      }}
    >
      <div style={{
        width: '100%', maxWidth: 540,
        backgroundColor: '#fff', borderRadius: 16,
        boxShadow: '0 32px 64px rgba(0,0,0,0.18)',
        display: 'flex', flexDirection: 'column', maxHeight: '85vh',
        overflow: 'hidden', animation: 'fadeInScale 0.18s ease',
      }}>
        {/* Header */}
        <div style={{
          display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
          padding: '20px 24px 14px', borderBottom: '1px solid #f1f5f9',
        }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700, color: '#0f172a' }}>
              Thêm sản phẩm chuyển kho
            </div>
            <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>
              Tìm và chọn sản phẩm cần chuyển
            </div>
          </div>
          <button onClick={onClose} style={{
            width: 30, height: 30, borderRadius: 8, border: '1px solid #e2e8f0',
            background: '#f8fafc', cursor: 'pointer', display: 'flex',
            alignItems: 'center', justifyContent: 'center', color: '#64748b',
            flexShrink: 0,
          }}>
            <X size={16} />
          </button>
        </div>

        {/* Search */}
        <div style={{ padding: '14px 24px', borderBottom: '1px solid #f1f5f9' }}>
          <div style={{ position: 'relative' }}>
            <Search size={15} style={{
              position: 'absolute', left: 12, top: '50%',
              transform: 'translateY(-50%)', color: '#94a3b8',
            }} />
            <input
              autoFocus
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="Tìm theo tên hoặc mã sản phẩm..."
              style={{
                width: '100%', padding: '9px 12px 9px 36px',
                borderRadius: 8, border: '1.5px solid #e2e8f0',
                fontSize: 13, outline: 'none', boxSizing: 'border-box',
                backgroundColor: '#f8fafc', color: '#0f172a',
              }}
              onFocus={(e) => { e.target.style.borderColor = '#7c3aed'; e.target.style.backgroundColor = '#fff'; }}
              onBlur={(e) => { e.target.style.borderColor = '#e2e8f0'; e.target.style.backgroundColor = '#f8fafc'; }}
            />
          </div>
        </div>

        {/* List */}
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {loading && (
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              gap: 8, padding: '40px 0', color: '#94a3b8', fontSize: 13,
            }}>
              <Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} />
              Đang tải sản phẩm...
            </div>
          )}
          {!loading && filtered.length === 0 && (
            <div style={{
              textAlign: 'center', padding: '40px 0',
              color: '#94a3b8', fontSize: 13,
            }}>
              {keyword.trim() ? 'Không tìm thấy sản phẩm phù hợp.' : 'Không có sản phẩm nào trong kho.'}
            </div>
          )}
          {!loading && filtered.map((v, idx) => {
            const isExisting = existingVariantIds.includes(v.variantId);
            return (
              <div
                key={v.variantId}
                onClick={() => !isExisting && handleAdd(v)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 14,
                  padding: '13px 24px',
                  cursor: isExisting ? 'not-allowed' : 'pointer',
                  borderBottom: '1px solid #f8fafc',
                  opacity: isExisting ? 0.5 : 1,
                  transition: 'background-color 0.12s',
                }}
                onMouseEnter={(e) => { if (!isExisting) e.currentTarget.style.backgroundColor = '#faf5ff'; }}
                onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; }}
              >
                {/* Số thứ tự */}
                <div style={{
                  width: 28, height: 28, borderRadius: '50%',
                  backgroundColor: '#f5f3ff', flexShrink: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 11, fontWeight: 700, color: '#7c3aed',
                }}>
                  {String(idx + 1).padStart(3, '0')}
                </div>

                {/* Info */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: '#0f172a', marginBottom: 2 }}>
                    {v.variantName}
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <span style={{
                      fontSize: 11, fontFamily: 'monospace',
                      color: '#6d28d9', backgroundColor: '#ede9fe',
                      padding: '1px 6px', borderRadius: 4,
                    }}>
                      {v.sku}
                    </span>
                    <span style={{ fontSize: 11, color: '#94a3b8' }}>
                      Tồn: <strong style={{ color: '#0f172a' }}>{v.availableQuantity}</strong>
                    </span>
                  </div>
                </div>

                {/* Action */}
                {isExisting ? (
                  <CheckCircle2 size={18} color="#10b981" style={{ flexShrink: 0 }} />
                ) : (
                  <div style={{
                    width: 26, height: 26, borderRadius: '50%',
                    border: '2px solid #7c3aed',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0, color: '#7c3aed',
                  }}>
                    <Plus size={14} />
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div style={{
          padding: '12px 24px', borderTop: '1px solid #f1f5f9',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          backgroundColor: '#f8fafc',
        }}>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>
            {filtered.length} sản phẩm • Nhấn <strong>+</strong> để thêm vào phiếu
          </span>
          <button
            onClick={onClose}
            style={{
              padding: '7px 18px', borderRadius: 8,
              border: '1.5px solid #e2e8f0', background: '#fff',
              fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer',
            }}
          >
            Đóng
          </button>
        </div>
      </div>

      {/* Keyframes animation */}
      <style>{`
        @keyframes fadeInScale {
          from { opacity: 0; transform: scale(0.95); }
          to   { opacity: 1; transform: scale(1); }
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

// ─── Main Page ───────────────────────────────────────────────────────────────
export default function StockTransferCreatePage() {
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  const [warehouses, setWarehouses] = useState([]);
  const [fromWarehouseId, setFromWarehouseId] = useState('');
  const [toWarehouseId, setToWarehouseId] = useState('');
  const [transferDate, setTransferDate] = useState(formatDateValue(today));
  const [transferTime, setTransferTime] = useState(formatTimeValue(today));
  const [note, setNote] = useState('');
  const [items, setItems] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [transferCode, setTransferCode] = useState('');
  const [userWarehouse, setUserWarehouse] = useState(null);
  const [loadingWarehouse, setLoadingWarehouse] = useState(false);

  const isOperations = user?.role === ROLES.OPERATIONS;

  // Load danh sách kho và mã chuyển kho gợi ý từ BE
  useEffect(() => {
    warehouseService.getAll()
      .then((res) => {
        const d = res?.data?.data ?? res?.data ?? [];
        const arr = Array.isArray(d) ? d : d?.content ?? [];
        setWarehouses(arr);
      })
      .catch(() => setWarehouses([]));

    if (isOperations && user?.id) {
      setLoadingWarehouse(true);
      warehouseService.getUserWarehouse(user.id)
        .then((res) => {
          const w = res?.data?.data;
          if (w) {
            setUserWarehouse(w);
            setFromWarehouseId(w.id);
          }
        })
        .catch((err) => {
          console.error(err);
          toast.error('Không thể lấy thông tin kho của bạn.');
        })
        .finally(() => {
          setLoadingWarehouse(false);
        });
    }

    transferApi.getSuggestedCode()
      .then((code) => {
        setTransferCode(code);
      })
      .catch(() => {
        // Fallback phòng khi API lỗi
        setTransferCode(genTransferCode());
      });
  }, [isOperations, user?.id]);

  // Swap kho xuất ↔ kho nhận
  const handleSwap = () => {
    if (!fromWarehouseId && !toWarehouseId) return;
    setFromWarehouseId(toWarehouseId);
    setToWarehouseId(fromWarehouseId);
    setItems([]); // Reset items khi đổi kho
  };

  // Options kho nhận: loại trừ kho xuất đang chọn
  const toWarehouseOptions = warehouses.filter((w) => w.id !== fromWarehouseId);
  // Options kho xuất: loại trừ kho nhận đang chọn
  const fromWarehouseOptions = warehouses.filter((w) => w.id !== toWarehouseId);

  // Thêm sản phẩm từ modal (1 cái 1 lần)
  const handleAddProduct = useCallback((variant) => {
    setItems((prev) => {
      if (prev.some((it) => it.variantId === variant.variantId)) return prev;
      return [...prev, {
        variantId: variant.variantId,
        sku: variant.sku,
        variantName: variant.variantName,
        availableQuantity: variant.availableQuantity,
        unitPrice: variant.unitPrice ?? 0,
        quantity: 1,
      }];
    });
  }, []);

  const handleQtyChange = (idx, val) => {
    setItems((prev) => prev.map((it, i) => {
      if (i !== idx) return it;
      const qty = Math.max(1, Math.min(Number(val) || 1, it.availableQuantity));
      return { ...it, quantity: qty };
    }));
  };

  const handlePriceChange = (idx, val) => {
    setItems((prev) => prev.map((it, i) =>
      i !== idx ? it : { ...it, unitPrice: val === '' ? '' : Math.max(0, Number(val) || 0) }
    ));
  };

  const handleRemove = (idx) => setItems((prev) => prev.filter((_, i) => i !== idx));

  // Validate trước khi submit
  const validate = () => {
    if (!fromWarehouseId) { toast.error('Vui lòng chọn kho xuất.'); return false; }
    if (!toWarehouseId) { toast.error('Vui lòng chọn kho nhận.'); return false; }
    if (fromWarehouseId === toWarehouseId) { toast.error('Kho xuất và kho nhận phải khác nhau.'); return false; }
    if (!transferDate) { toast.error('Vui lòng chọn ngày chuyển kho.'); return false; }
    if (items.length === 0) { toast.error('Vui lòng thêm ít nhất một sản phẩm.'); return false; }
    const overQty = items.find((it) => it.quantity > it.availableQuantity);
    if (overQty) {
      toast.error(`Sản phẩm "${overQty.variantName}" vượt tồn kho hiện tại (${overQty.availableQuantity}).`);
      return false;
    }
    return true;
  };

  const handleConfirm = async () => {
    if (!validate()) return;
    setSubmitting(true);
    try {
      const timeStr = transferTime ? (transferTime.length === 5 ? `${transferTime}:00` : transferTime) : '00:00:00';
      const formattedTransferDate = `${transferDate}T${timeStr}+07:00`;

      const payload = {
        transferCode,
        fromWarehouseId,
        toWarehouseId,
        transferTime: formattedTransferDate,
        createdById: user?.id,
        note: note || null,
        status: 'IN_TRANSIT',
        items: items.map((it) => ({
          variantId: it.variantId,
          quantity: it.quantity,
          unitCost: it.unitPrice || 0,
        })),
      };
      console.log('Dữ liệu gửi lên BE (Xác nhận chuyển kho):', payload);
      await transferApi.createTransfer(payload);
      toast.success('Tạo phiếu chuyển kho thành công!');
      navigate(ROUTES.STOCK_TRANSFER);
    } catch (err) {
      const msg = err?.response?.data?.message || 'Không thể tạo phiếu chuyển kho. Vui lòng thử lại.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveDraft = async () => {
    if (!fromWarehouseId) { toast.error('Vui lòng chọn kho xuất.'); return; }
    if (!toWarehouseId) { toast.error('Vui lòng chọn kho nhận.'); return; }
    if (fromWarehouseId === toWarehouseId) { toast.error('Kho xuất và kho nhận phải khác nhau.'); return; }
    setSubmitting(true);
    try {
      const timeStr = transferTime ? (transferTime.length === 5 ? `${transferTime}:00` : transferTime) : '00:00:00';
      const formattedTransferDate = `${transferDate}T${timeStr}+07:00`;

      const payload = {
        transferCode,
        fromWarehouseId,
        toWarehouseId,
        transferTime: formattedTransferDate,
        createdById: user?.id,
        note: note || null,
        status: 'DRAFT',
        items: items.map((it) => ({ 
          variantId: it.variantId, 
          quantity: it.quantity,
          unitCost: it.unitPrice || 0,
        })),
      };
      console.log('Dữ liệu gửi lên BE (Lưu tạm):', payload);
      await transferApi.createTransfer(payload);
      toast.success('Đã lưu tạm phiếu chuyển kho.');
      navigate(ROUTES.STOCK_TRANSFER);
    } catch (err) {
      const msg = err?.response?.data?.message || 'Không thể lưu phiếu. Vui lòng thử lại.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const canAddProduct = Boolean(fromWarehouseId);
  const totalQty = items.reduce((s, it) => s + (Number(it.quantity) || 0), 0);
  const totalAmount = items.reduce((s, it) => s + (Number(it.quantity) || 0) * (Number(it.unitPrice) || 0), 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, paddingBottom: 32 }}>

      {/* ── Header ────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <button
          onClick={() => navigate(ROUTES.STOCK_TRANSFER)}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '7px 12px', borderRadius: 8,
            border: '1.5px solid #e2e8f0', background: '#fff',
            fontSize: 13, color: '#374151', cursor: 'pointer',
            fontWeight: 500,
          }}
          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
        >
          <ArrowLeft size={15} /> Quay lại
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 38, height: 38, borderRadius: 10,
            backgroundColor: '#f5f3ff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <ArrowRightLeft size={18} color="#7c3aed" />
          </div>
          <div>
            <h1 style={{ fontSize: 20, fontWeight: 800, color: '#0f172a', margin: 0, lineHeight: 1.2 }}>
              Tạo phiếu chuyển kho
            </h1>
            <p style={{ fontSize: 12, color: '#64748b', margin: 0, marginTop: 2 }}>
              Điều chuyển hàng hóa giữa các kho
            </p>
          </div>
        </div>
      </div>

      {/* ── Card 1: Thông tin phiếu ──────────────────────────────── */}
      <div style={{
        backgroundColor: '#fff', borderRadius: 12,
        border: '1.5px solid #e2e8f0',
        boxShadow: '0 1px 4px rgba(0,0,0,0.04)',
        overflow: 'hidden',
      }}>
        <div style={{ padding: '18px 22px', borderBottom: '1px solid #f1f5f9' }}>
          <h2 style={{ fontSize: 15, fontWeight: 700, color: '#0f172a', margin: 0 }}>
            Thông tin phiếu chuyển
          </h2>
        </div>

        <div style={{ padding: '20px 22px' }}>
          {/* Row 1: Mã phiếu | Ngày chuyển | Giờ chuyển */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16, marginBottom: 18 }}>
            {/* Mã phiếu */}
            <div>
              <label style={labelStyle}>Mã phiếu</label>
              <input
                value={transferCode}
                readOnly
                style={{
                  ...inputStyle(),
                  backgroundColor: '#f8fafc',
                  color: '#7c3aed',
                  fontFamily: 'monospace',
                  fontWeight: 700,
                  fontSize: 13,
                  cursor: 'default',
                }}
              />
            </div>

            {/* Ngày chuyển kho */}
            <div>
              <label style={labelStyle}>
                Ngày chuyển kho <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <input
                type="date"
                value={transferDate}
                onChange={(e) => setTransferDate(e.target.value)}
                style={inputStyle(!transferDate)}
                onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
                onBlur={(e) => e.target.style.borderColor = transferDate ? '#e2e8f0' : '#fca5a5'}
              />
            </div>

            {/* Giờ chuyển kho */}
            <div>
              <label style={labelStyle}>Giờ chuyển kho</label>
              <input
                type="time"
                value={transferTime}
                onChange={(e) => setTransferTime(e.target.value)}
                style={inputStyle()}
                onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              />
            </div>
          </div>

          {/* Row 2: Kho xuất ↔ Kho nhận */}
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, marginBottom: 18 }}>
            {/* Kho xuất */}
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>
                Xuất tại kho <span style={{ color: '#ef4444' }}>*</span>
              </label>
              {isOperations ? (
                <div style={{ position: 'relative' }}>
                  <input
                    value={loadingWarehouse ? 'Đang tải thông tin kho...' : (userWarehouse?.name || 'Chưa liên kết kho')}
                    readOnly
                    style={{
                      ...inputStyle(!fromWarehouseId && submitting),
                      backgroundColor: '#f1f5f9',
                      color: '#475569',
                      cursor: 'not-allowed',
                      fontWeight: 600,
                    }}
                  />
                  {loadingWarehouse && (
                    <Loader2
                      size={15}
                      style={{
                        position: 'absolute',
                        right: 12,
                        top: '50%',
                        transform: 'translateY(-50%)',
                        animation: 'spin 1s linear infinite',
                        color: '#94a3b8',
                      }}
                    />
                  )}
                </div>
              ) : (
                <select
                  value={fromWarehouseId}
                  onChange={(e) => {
                    setFromWarehouseId(e.target.value);
                    setItems([]); // Reset items khi đổi kho xuất
                  }}
                  style={selectStyle(!fromWarehouseId && submitting)}
                  onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
                  onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                >
                  <option value="">Chọn kho xuất</option>
                  {fromWarehouseOptions.map((w) => (
                    <option key={w.id} value={w.id}>{w.name}</option>
                  ))}
                </select>
              )}
            </div>

            {/* Swap button */}
            <button
              onClick={handleSwap}
              title="Hoán đổi kho xuất / kho nhận"
              disabled={isOperations}
              style={{
                width: 38, height: 38, borderRadius: 8,
                border: '1.5px solid #e2e8f0',
                background: isOperations ? '#f1f5f9' : '#f8fafc',
                cursor: isOperations ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center', justifyContent: 'center',
                color: isOperations ? '#94a3b8' : '#64748b', flexShrink: 0, marginBottom: 0,
                transition: 'all 0.15s',
                opacity: isOperations ? 0.6 : 1,
              }}
              onMouseEnter={(e) => {
                if (!isOperations) {
                  e.currentTarget.style.borderColor = '#7c3aed';
                  e.currentTarget.style.color = '#7c3aed';
                  e.currentTarget.style.backgroundColor = '#f5f3ff';
                }
              }}
              onMouseLeave={(e) => {
                if (!isOperations) {
                  e.currentTarget.style.borderColor = '#e2e8f0';
                  e.currentTarget.style.color = '#64748b';
                  e.currentTarget.style.backgroundColor = '#f8fafc';
                }
              }}
            >
              <ArrowRightLeft size={16} />
            </button>

            {/* Kho nhận */}
            <div style={{ flex: 1 }}>
              <label style={labelStyle}>
                Nhập tại kho <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <select
                value={toWarehouseId}
                onChange={(e) => setToWarehouseId(e.target.value)}
                style={selectStyle(!toWarehouseId && submitting)}
                disabled={!fromWarehouseId}
                onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
                onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
              >
                <option value="">Chọn kho nhận</option>
                {toWarehouseOptions.map((w) => (
                  <option key={w.id} value={w.id}>{w.name}</option>
                ))}
              </select>
              {!fromWarehouseId && (
                <p style={{ margin: '4px 0 0', fontSize: 11, color: '#94a3b8' }}>
                  {isOperations ? 'Vui lòng đợi tải thông tin kho' : 'Chọn kho xuất trước'}
                </p>
              )}
            </div>
          </div>

          {/* Ghi chú */}
          <div style={{ marginBottom: 20 }}>
            <label style={labelStyle}>Ghi chú</label>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              rows={3}
              placeholder="Ghi chú về phiếu chuyển kho..."
              style={{
                ...inputStyle(),
                resize: 'vertical',
                minHeight: 72,
                lineHeight: 1.5,
              }}
              onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
              onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
            />
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button
              onClick={() => navigate(ROUTES.STOCK_TRANSFER)}
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '9px 18px', borderRadius: 8,
                border: '1.5px solid #e2e8f0', background: '#fff',
                fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer',
              }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
            >
              <Ban size={14} /> Hủy
            </button>

            <button
              onClick={handleSaveDraft}
              disabled={submitting}
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '9px 18px', borderRadius: 8,
                border: '1.5px solid #e2e8f0',
                background: submitting ? '#f8fafc' : '#fff',
                color: submitting ? '#94a3b8' : '#374151',
                fontSize: 13, fontWeight: 500,
                cursor: submitting ? 'not-allowed' : 'pointer',
              }}
              onMouseEnter={(e) => { if (!submitting) e.currentTarget.style.backgroundColor = '#f8fafc'; }}
              onMouseLeave={(e) => { if (!submitting) e.currentTarget.style.backgroundColor = '#fff'; }}
            >
              <Save size={14} /> Lưu tạm
            </button>

            <button
              onClick={handleConfirm}
              disabled={submitting}
              style={btnPrimary(submitting)}
              onMouseEnter={(e) => { if (!submitting) e.currentTarget.style.backgroundColor = '#6d28d9'; }}
              onMouseLeave={(e) => { if (!submitting) e.currentTarget.style.backgroundColor = '#7c3aed'; }}
            >
              <CheckCircle2 size={15} />
              {submitting ? 'Đang xử lý...' : 'Xác nhận chuyển kho'}
            </button>
          </div>
        </div>
      </div>

      {/* ── Card 2: Danh sách sản phẩm + Lưu ý ─────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 16, alignItems: 'start' }}>

        {/* LEFT: Bảng sản phẩm */}
        <div style={{
          backgroundColor: '#fff', borderRadius: 12,
          border: '1.5px solid #e2e8f0',
          boxShadow: '0 1px 4px rgba(0,0,0,0.04)',
          overflow: 'hidden',
        }}>
          {/* Header */}
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '16px 22px', borderBottom: '1px solid #f1f5f9',
          }}>
            <div>
              <div style={{ fontSize: 15, fontWeight: 700, color: '#0f172a' }}>
                Danh sách sản phẩm chuyển
              </div>
              <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 2 }}>
                Thêm sản phẩm và điền số lượng, đơn giá
              </div>
            </div>

            <button
              onClick={() => {
                if (!canAddProduct) {
                  toast.warning('Vui lòng chọn kho xuất trước khi thêm sản phẩm.');
                  return;
                }
                setModalOpen(true);
              }}
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '9px 18px', borderRadius: 8, border: 'none',
                backgroundColor: canAddProduct ? '#7c3aed' : '#c4b5fd',
                color: '#fff', fontSize: 13, fontWeight: 600,
                cursor: canAddProduct ? 'pointer' : 'not-allowed',
                transition: 'background-color 0.15s',
              }}
              onMouseEnter={(e) => { if (canAddProduct) e.currentTarget.style.backgroundColor = '#6d28d9'; }}
              onMouseLeave={(e) => { if (canAddProduct) e.currentTarget.style.backgroundColor = '#7c3aed'; }}
            >
              <Plus size={15} /> Thêm sản phẩm
            </button>
          </div>

          {/* Content */}
          {items.length === 0 ? (
            <div style={{
              display: 'flex', flexDirection: 'column',
              alignItems: 'center', justifyContent: 'center',
              padding: '60px 0', gap: 10,
            }}>
              <div style={{
                width: 52, height: 52, borderRadius: 14,
                backgroundColor: '#f5f3ff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <ArrowRightLeft size={24} color="#c4b5fd" />
              </div>
              <p style={{ fontSize: 14, fontWeight: 600, color: '#64748b', margin: 0 }}>
                Chưa có sản phẩm nào
              </p>
              <p style={{ fontSize: 12, color: '#94a3b8', margin: 0 }}>
                {canAddProduct
                  ? 'Nhấn "Thêm sản phẩm" để bắt đầu'
                  : 'Chọn kho xuất trước để thêm sản phẩm'}
              </p>
            </div>
          ) : (
            <>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead style={{ backgroundColor: '#f8fafc' }}>
                    <tr style={{ borderBottom: '1.5px solid #e2e8f0' }}>
                      {[
                        { label: '#', align: 'center', width: 40 },
                        { label: 'Sản phẩm / Biến thể', align: 'left' },
                        { label: 'SKU', align: 'left', width: 120 },
                        { label: 'Tồn kho', align: 'right', width: 80 },
                        { label: 'Số lượng', align: 'center', width: 100 },
                        { label: 'Đơn giá (đ)', align: 'right', width: 130 },
                        { label: 'Thành tiền', align: 'right', width: 120 },
                        { label: '', width: 36 },
                      ].map((col) => (
                        <th key={col.label} style={{
                          padding: '10px 14px', textAlign: col.align ?? 'left',
                          fontWeight: 600, fontSize: 11, color: '#64748b',
                          whiteSpace: 'nowrap', width: col.width,
                        }}>
                          {col.label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, idx) => {
                      const overStock = item.quantity > item.availableQuantity;
                      return (
                        <tr
                          key={item.variantId}
                          style={{ borderBottom: '1px solid #f1f5f9', transition: 'background-color 0.1s' }}
                          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#faf5ff'}
                          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                        >
                          {/* STT */}
                          <td style={{ padding: '10px 14px', textAlign: 'center' }}>
                            <span style={{
                              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                              width: 22, height: 22, borderRadius: '50%',
                              backgroundColor: '#f5f3ff',
                              fontSize: 11, fontWeight: 700, color: '#7c3aed',
                            }}>
                              {idx + 1}
                            </span>
                          </td>

                          {/* Tên */}
                          <td style={{ padding: '10px 14px' }}>
                            <div style={{ fontWeight: 600, color: '#0f172a', fontSize: 13 }}>
                              {item.variantName}
                            </div>
                          </td>

                          {/* SKU */}
                          <td style={{ padding: '10px 14px' }}>
                            <span style={{
                              fontSize: 11, fontFamily: 'monospace',
                              color: '#6d28d9', backgroundColor: '#ede9fe',
                              padding: '2px 7px', borderRadius: 4,
                            }}>
                              {item.sku}
                            </span>
                          </td>

                          {/* Tồn kho */}
                          <td style={{
                            padding: '10px 14px', textAlign: 'right',
                            fontSize: 13, fontWeight: 500,
                            color: item.availableQuantity <= 10 ? '#dc2626' : '#0f172a',
                          }}>
                            {item.availableQuantity}
                          </td>

                          {/* Số lượng */}
                          <td style={{ padding: '6px 10px' }}>
                            <input
                              type="number"
                              min="1"
                              max={item.availableQuantity}
                              value={item.quantity}
                              onChange={(e) => handleQtyChange(idx, e.target.value)}
                              style={{
                                width: '100%', padding: '6px 8px',
                                borderRadius: 6,
                                border: `1.5px solid ${overStock ? '#fca5a5' : '#e2e8f0'}`,
                                backgroundColor: overStock ? '#fff5f5' : '#fff',
                                fontSize: 13, textAlign: 'center', outline: 'none',
                                boxSizing: 'border-box',
                              }}
                              onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
                              onBlur={(e) => e.target.style.borderColor = overStock ? '#fca5a5' : '#e2e8f0'}
                            />
                            {overStock && (
                              <p style={{ margin: '2px 0 0', fontSize: 10, color: '#dc2626', textAlign: 'center' }}>
                                Vượt tồn kho!
                              </p>
                            )}
                          </td>

                          {/* Đơn giá */}
                          <td style={{ padding: '6px 10px' }}>
                            <input
                              type="number"
                              min="0"
                              step="1000"
                              value={item.unitPrice}
                              onChange={(e) => handlePriceChange(idx, e.target.value)}
                              placeholder="0"
                              style={{
                                width: '100%', padding: '6px 8px',
                                borderRadius: 6,
                                border: '1.5px solid #e2e8f0',
                                backgroundColor: '#fff',
                                fontSize: 13, textAlign: 'right', outline: 'none',
                                boxSizing: 'border-box',
                              }}
                              onFocus={(e) => e.target.style.borderColor = '#7c3aed'}
                              onBlur={(e) => e.target.style.borderColor = '#e2e8f0'}
                            />
                          </td>

                          {/* Thành tiền */}
                          {(() => {
                            const lineTotal = (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0);
                            return (
                              <td style={{
                                padding: '6px 14px', textAlign: 'right',
                                fontWeight: 700, whiteSpace: 'nowrap',
                                color: lineTotal > 0 ? '#7c3aed' : '#94a3b8',
                                fontSize: 13,
                              }}>
                                {lineTotal > 0 ? formatVND(lineTotal) : '—'}
                              </td>
                            );
                          })()}

                          {/* Xóa */}
                          <td style={{ padding: '6px 10px' }}>
                            <button
                              onClick={() => handleRemove(idx)}
                              style={{
                                width: 28, height: 28, borderRadius: 6,
                                border: 'none', background: 'none',
                                cursor: 'pointer', color: '#94a3b8',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                transition: 'all 0.12s',
                              }}
                              onMouseEnter={(e) => {
                                e.currentTarget.style.backgroundColor = '#fef2f2';
                                e.currentTarget.style.color = '#dc2626';
                              }}
                              onMouseLeave={(e) => {
                                e.currentTarget.style.backgroundColor = 'transparent';
                                e.currentTarget.style.color = '#94a3b8';
                              }}
                            >
                              <Trash2 size={14} />
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Summary footer */}
              <div style={{
                borderTop: '1.5px solid #e2e8f0',
                padding: '12px 22px',
                backgroundColor: '#f8fafc',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                flexWrap: 'wrap', gap: 8,
              }}>
                <div style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
                  <span style={{ fontSize: 12, color: '#64748b' }}>
                    <strong style={{ color: '#0f172a' }}>{items.length}</strong> sản phẩm
                  </span>
                  <span style={{ fontSize: 12, color: '#64748b' }}>
                    Tổng SL:{' '}
                    <strong style={{ color: '#0f172a' }}>{totalQty.toLocaleString('vi-VN')}</strong>
                  </span>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 1 }}>Tổng giá trị chuyến</div>
                  <div style={{ fontSize: 17, fontWeight: 800, color: '#7c3aed', letterSpacing: '-0.3px' }}>
                    {formatVND(totalAmount)}
                  </div>
                </div>
              </div>
            </>
          )}
        </div>

        {/* RIGHT: Lưu ý */}
        <div style={{
          backgroundColor: '#f0f9ff',
          borderRadius: 12,
          border: '1.5px solid #bae6fd',
          padding: '16px 18px',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
            <AlertCircle size={16} color="#0369a1" />
            <span style={{ fontSize: 13, fontWeight: 700, color: '#0369a1' }}>
              Lưu ý khi chuyển kho
            </span>
          </div>
          <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 9 }}>
            {[
              'Số lượng chuyển không được vượt quá tồn kho hiện tại.',
              'Kho xuất và kho nhận phải khác nhau.',
              'Tồn kho sẽ cập nhật ngay sau khi xác nhận chuyển.',
              isOperations
                ? 'Sản phẩm được lấy từ kho làm việc của bạn.'
                : 'Chọn kho xuất trước để xem danh sách sản phẩm có sẵn.',
            ].map((note) => (
              <li key={note} style={{
                fontSize: 12, color: '#0369a1', lineHeight: 1.6,
                paddingLeft: 14, position: 'relative',
              }}>
                <span style={{
                  position: 'absolute', left: 0, top: 0,
                  fontWeight: 700, color: '#7c3aed',
                }}>•</span>
                {note}
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* Modal thêm sản phẩm */}
      <AddProductModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        onAdd={handleAddProduct}
        warehouseId={fromWarehouseId}
        existingVariantIds={items.map((it) => it.variantId)}
      />
    </div>
  );
}
