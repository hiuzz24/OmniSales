import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, Package, Warehouse, Building2, FileText,
  Calendar, User, DollarSign, Hash, AlertCircle, Loader2,
  CheckCircle2, Save, Undo2, Printer, Edit, Check,
} from 'lucide-react';
import { toast } from 'react-toastify';
import stockReceiveService from '../../services/stockReceiveService';
import { ROUTES } from '../../../../app/router/routes';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import { printStockReceiveReceipt } from './stockReceivePrintTemplate';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

const formatDate = (s) => {
  if (!s) return '—';
  return new Date(s).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const formatDateOnly = (s) => {
  if (!s) return '—';
  return new Date(s).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

const getItemProductName = (item) =>
  item?.productName ?? item?.productVariantName ?? item?.variantProductName ?? item?.variantName ?? item?.variantSku ?? item?.sku ?? '—';

const getItemSku = (item) => item?.marketplaceSku ?? item?.sku ?? item?.variantSku ?? '—';

// Status config
const STATUS_CFG = {
  CONFIRMED: {
    label: 'Hoàn thành',
    icon: CheckCircle2,
    color: '#059669',
    bg: '#ecfdf5',
    border: '#a7f3d0',
  },
  DRAFT: {
    label: 'Lưu tạm',
    icon: Save,
    color: '#d97706',
    bg: '#fffbeb',
    border: '#fcd34d',
  },
  CANCELLED: {
    label: 'Trả hàng',
    icon: Undo2,
    color: '#e11d48',
    bg: '#fff1f2',
    border: '#fecdd3',
  },
};

// ── Status badge ──────────────────────────────────────────────────────────────
const StatusBadge = ({ status }) => {
  const cfg = STATUS_CFG[status] ?? {
    label: status,
    color: '#475569',
    bg: '#f8fafc',
    border: '#e2e8f0',
  };
  const Icon = cfg.icon ?? FileText;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '6px 14px',
        borderRadius: 999,
        fontSize: 13,
        fontWeight: 700,
        color: cfg.color,
        backgroundColor: cfg.bg,
        border: `1px solid ${cfg.border}`,
      }}
    >
      <Icon size={14} />
      {cfg.label}
    </span>
  );
};

// ── Info row ──────────────────────────────────────────────────────────────────
const InfoRow = ({ icon: Icon, label, value, color = '#0f172a' }) => (
  <div
    style={{
      display: 'flex',
      alignItems: 'flex-start',
      gap: 10,
      padding: '10px 0',
      borderBottom: '1px solid #f1f5f9',
    }}
  >
    <div
      style={{
        width: 32,
        height: 32,
        borderRadius: 8,
        backgroundColor: '#f8fafc',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
      }}
    >
      <Icon size={16} color="#64748b" />
    </div>
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2 }}>{label}</div>
      <div
        style={{
          fontSize: 13,
          fontWeight: 500,
          color: color,
          wordBreak: 'break-word',
        }}
      >
        {value ?? '—'}
      </div>
    </div>
  </div>
);

// ── Main page ─────────────────────────────────────────────────────────────────
export default function StockReceiveDetailPage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const { id } = useParams();
  const [receipt, setReceipt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [completing, setCompleting] = useState(false);

  const fetchReceipt = async () => {
    setLoading(true);
    try {
      const res = await stockReceiveService.getReceiptById(id);
      const data = res.data?.data ?? res.data;
      setReceipt(data);
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
  };

  const handleComplete = async () => {
    // Validate before completing
    const items = receipt.items || [];
    // Check items quantity and unitCost
    const invalidQty = items.find(item => !item.quantity || item.quantity <= 0);
    if (invalidQty) {
      toast.error(`Sản phẩm "${getItemProductName(invalidQty)}" phải có số lượng lớn hơn 0. Vui lòng chỉnh sửa phiếu.`);
      return;
    }
    
    const invalidPrice = items.find(item => !item.unitCost || item.unitCost <= 0);
    if (invalidPrice) {
      toast.error(`Sản phẩm "${getItemProductName(invalidPrice)}" phải có đơn giá lớn hơn 0. Vui lòng chỉnh sửa phiếu.`);
      return;
    }
    
    const ok = await confirm({
      title: 'Hoàn thành phiếu nhập?',
      message: `Xác nhận hoàn thành phiếu "${receipt.receiptCode}". Tồn kho sẽ được cập nhật sau khi xác nhận.`,
      confirmText: 'Hoàn thành',
    });
    if (!ok) {
      return;
    }

    setCompleting(true);
    try {
      await stockReceiveService.completeReceipt(id);
      toast.success('Hoàn thành phiếu nhập thành công.');
      fetchReceipt();
    } catch (error) {
      const errorMessage =
        error?.response?.data?.message ||
        error?.message ||
        'Không thể hoàn thành phiếu nhập. Vui lòng thử lại.';
      toast.error(errorMessage);
    } finally {
      setCompleting(false);
    }
  };

  const handlePrint = () => {
    try {
      printStockReceiveReceipt(receipt);
    } catch (error) {
      toast.error(error?.message || 'Không thể in phiếu nhập.');
    }
  };

  useEffect(() => {
    if (!id) {
      toast.error('ID phiếu nhập không hợp lệ');
      navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS);
      return;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchReceipt();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (loading) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '60vh',
          gap: 12,
          color: '#94a3b8',
        }}
      >
        <Loader2 size={32} style={{ animation: 'spin 1s linear infinite' }} />
        <p style={{ fontSize: 14 }}>Đang tải thông tin phiếu nhập...</p>
      </div>
    );
  }

  if (!receipt) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '60vh',
          gap: 12,
          color: '#94a3b8',
        }}
      >
        <AlertCircle size={32} />
        <p style={{ fontSize: 14 }}>Không tìm thấy thông tin phiếu nhập.</p>
        <button
          onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}
          style={{
            padding: '8px 16px',
            borderRadius: 8,
            border: '1px solid #e2e8f0',
            background: '#fff',
            fontSize: 13,
            fontWeight: 500,
            color: '#374151',
            cursor: 'pointer',
          }}
        >
          Quay lại danh sách
        </button>
      </div>
    );
  }

  const items = receipt.items ?? [];
  const totalSkuCount = items.length;
  const totalQuantity = items.reduce((sum, item) => sum + (item.quantity ?? 0), 0);
  const totalAmount = items.reduce(
    (sum, item) => sum + (item.quantity ?? 0) * (item.unitCost ?? 0),
    0
  );

  return (
    <>
    <div className="product-workspace product-workspace--flow" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPTS)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '7px 12px',
              borderRadius: 7,
              border: '1px solid #e2e8f0',
              background: '#fff',
              fontSize: 12,
              color: '#374151',
              cursor: 'pointer',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f8fafc')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#fff')}
          >
            <ArrowLeft size={14} /> Quay lại
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 10,
                backgroundColor: '#eff6ff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <FileText size={18} color="#2563eb" />
            </div>
            <div>
              <h1
                style={{
                  fontSize: 18,
                  fontWeight: 700,
                  color: '#0f172a',
                  margin: 0,
                }}
              >
                Chi tiết phiếu nhập: {receipt.receiptCode}
              </h1>
              <p style={{ fontSize: 12, color: '#64748b', margin: '1px 0 0' }}>
                Thông tin chi tiết và danh sách sản phẩm
              </p>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 8 }}>
          {receipt.status === 'DRAFT' && (
            <>
              <button
                onClick={() => navigate(`/warehouse/receipts/${receipt.id}/edit`)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 14px',
                  borderRadius: 7,
                  border: '1px solid #e2e8f0',
                  background: '#fff',
                  fontSize: 13,
                  fontWeight: 500,
                  color: '#374151',
                  cursor: 'pointer',
                }}
                onMouseEnter={(e) =>
                  (e.currentTarget.style.backgroundColor = '#f8fafc')
                }
                onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#fff')}
              >
                <Edit size={14} /> Chỉnh sửa
              </button>
              <button
                onClick={handleComplete}
                disabled={completing}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 14px',
                  borderRadius: 7,
                  border: 'none',
                  background: completing ? '#93c5fd' : '#2563eb',
                  color: '#fff',
                  fontSize: 13,
                  fontWeight: 500,
                  cursor: completing ? 'not-allowed' : 'pointer',
                }}
                onMouseEnter={(e) => {
                  if (!completing) e.currentTarget.style.backgroundColor = '#1d4ed8';
                }}
                onMouseLeave={(e) => {
                  if (!completing) e.currentTarget.style.backgroundColor = '#2563eb';
                }}
              >
                {completing ? (
                  <>
                    <Loader2
                      size={14}
                      style={{ animation: 'spin 1s linear infinite' }}
                    />{' '}
                    Đang xử lý...
                  </>
                ) : (
                  <>
                    <Check size={14} /> Hoàn thành nhập kho
                  </>
                )}
              </button>
            </>
          )}
          <button
            onClick={handlePrint}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '8px 14px',
              borderRadius: 7,
              border: '1px solid #e2e8f0',
              background: '#fff',
              fontSize: 13,
              fontWeight: 500,
              color: '#374151',
              cursor: 'pointer',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f8fafc')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#fff')}
          >
            <Printer size={14} /> In phiếu
          </button>
        </div>
      </div>

      {/* Two-column layout */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
        {/* Left column - Product list */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Summary cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
            <div
              style={{
                backgroundColor: '#fff',
                borderRadius: 10,
                border: '1px solid #e2e8f0',
                padding: '14px 16px',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 8,
                }}
              >
                <span style={{ fontSize: 11, color: '#64748b' }}>Số loại sản phẩm</span>
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: 7,
                    backgroundColor: '#f0f9ff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Hash size={14} color="#0284c7" />
                </div>
              </div>
              <div style={{ fontSize: 20, fontWeight: 700, color: '#0f172a' }}>
                {totalSkuCount}
              </div>
            </div>

            <div
              style={{
                backgroundColor: '#fff',
                borderRadius: 10,
                border: '1px solid #e2e8f0',
                padding: '14px 16px',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 8,
                }}
              >
                <span style={{ fontSize: 11, color: '#64748b' }}>Tổng số lượng</span>
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: 7,
                    backgroundColor: '#f0fdf4',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Package size={14} color="#16a34a" />
                </div>
              </div>
              <div style={{ fontSize: 20, fontWeight: 700, color: '#0f172a' }}>
                {totalQuantity.toLocaleString()}
              </div>
            </div>

            <div
              style={{
                backgroundColor: '#fff',
                borderRadius: 10,
                border: '1px solid #e2e8f0',
                padding: '14px 16px',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 8,
                }}
              >
                <span style={{ fontSize: 11, color: '#64748b' }}>Tổng giá trị</span>
                <div
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: 7,
                    backgroundColor: '#fef3c7',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <DollarSign size={14} color="#ca8a04" />
                </div>
              </div>
              <div style={{ fontSize: 20, fontWeight: 700, color: '#2563eb' }}>
                {formatVND(totalAmount)}
              </div>
            </div>
          </div>

          {/* Product table */}
          <div
            style={{
              backgroundColor: '#fff',
              borderRadius: 10,
              border: '1px solid #e2e8f0',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                padding: '14px 18px',
                borderBottom: '1px solid #f1f5f9',
                backgroundColor: '#f8fafc',
              }}
            >
              <h3 style={{ fontSize: 14, fontWeight: 600, color: '#0f172a', margin: 0 }}>
                Danh sách sản phẩm
              </h3>
            </div>

            {items.length === 0 ? (
              <div
                style={{
                  padding: '40px 0',
                  textAlign: 'center',
                  color: '#94a3b8',
                  fontSize: 13,
                }}
              >
                Không có sản phẩm nào
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                  <thead>
                    <tr
                      style={{
                        backgroundColor: '#f8fafc',
                        borderBottom: '1px solid #e2e8f0',
                      }}
                    >
                      {['Sản phẩm', 'SKU', 'Số lượng', 'Đơn giá', 'Thành tiền'].map(
                        (h) => (
                          <th
                            key={h}
                            style={{
                              padding: '10px 14px',
                              textAlign:
                                h === 'Số lượng' ||
                                h === 'Đơn giá' ||
                                h === 'Thành tiền'
                                  ? 'right'
                                  : 'left',
                              fontWeight: 600,
                              fontSize: 11,
                              color: '#64748b',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {h}
                          </th>
                        )
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item, idx) => {
                      const lineTotal =
                        (item.quantity ?? 0) * (item.unitCost ?? 0);
                      return (
                        <tr
                          key={item.id ?? idx}
                          style={{ borderBottom: '1px solid #f1f5f9' }}
                        >
                          <td style={{ padding: '12px 14px' }}>
                            <div
                              style={{
                                fontWeight: 500,
                                color: '#0f172a',
                                fontSize: 12,
                              }}
                            >
                              {getItemProductName(item)}
                            </div>
                            {item.variantName && (
                              <div style={{ fontSize: 10, color: '#94a3b8' }}>
                                {item.variantName}
                              </div>
                            )}
                          </td>
                          <td style={{ padding: '12px 14px' }}>
                            <span
                              style={{
                                fontSize: 10,
                                fontFamily: 'monospace',
                                backgroundColor: '#f1f5f9',
                                color: '#64748b',
                                padding: '2px 6px',
                                borderRadius: 4,
                              }}
                            >
                              {getItemSku(item)}
                            </span>
                          </td>
                          <td
                            style={{
                              padding: '12px 14px',
                              textAlign: 'right',
                              fontWeight: 500,
                              color: '#0f172a',
                            }}
                          >
                            {(item.quantity ?? 0).toLocaleString()}
                          </td>
                          <td
                            style={{
                              padding: '12px 14px',
                              textAlign: 'right',
                              color: '#475569',
                            }}
                          >
                            {formatVND(item.unitCost)}
                          </td>
                          <td
                            style={{
                              padding: '12px 14px',
                              textAlign: 'right',
                              fontWeight: 600,
                              color: '#2563eb',
                            }}
                          >
                            {formatVND(lineTotal)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                  <tfoot>
                    <tr
                      style={{
                        backgroundColor: '#f8fafc',
                        borderTop: '2px solid #e2e8f0',
                      }}
                    >
                      <td
                        colSpan={2}
                        style={{
                          padding: '12px 14px',
                          fontWeight: 600,
                          color: '#0f172a',
                          fontSize: 13,
                        }}
                      >
                        Tổng cộng
                      </td>
                      <td
                        style={{
                          padding: '12px 14px',
                          textAlign: 'right',
                          fontWeight: 600,
                          color: '#0f172a',
                          fontSize: 13,
                        }}
                      >
                        {totalQuantity.toLocaleString()}
                      </td>
                      <td></td>
                      <td
                        style={{
                          padding: '12px 14px',
                          textAlign: 'right',
                          fontWeight: 700,
                          color: '#2563eb',
                          fontSize: 15,
                        }}
                      >
                        {formatVND(totalAmount)}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* Right column - Receipt info */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Status card */}
          <div
            style={{
              backgroundColor: '#fff',
              borderRadius: 10,
              border: '1px solid #e2e8f0',
              padding: '16px 18px',
            }}
          >
            <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 8 }}>
              Trạng thái
            </div>
            <StatusBadge status={receipt.status} />
          </div>

          {/* Info card */}
          <div
            style={{
              backgroundColor: '#fff',
              borderRadius: 10,
              border: '1px solid #e2e8f0',
              padding: '16px 18px',
            }}
          >
            <h3
              style={{
                fontSize: 14,
                fontWeight: 600,
                color: '#0f172a',
                margin: '0 0 12px',
              }}
            >
              Thông tin phiếu nhập
            </h3>

            <InfoRow
              icon={FileText}
              label="Mã phiếu nhập"
              value={receipt.receiptCode}
              color="#2563eb"
            />
            <InfoRow
              icon={Warehouse}
              label="Kho nhập"
              value={receipt.warehouseName}
            />
            <InfoRow
              icon={Building2}
              label="Nhà cung cấp"
              value={receipt.supplierName ?? 'Không có'}
            />
            <InfoRow
              icon={Hash}
              label="Mã phiếu"
              value={receipt.receiptCode ?? receipt.invoiceNumber ?? 'Không có'}
            />
            <InfoRow
              icon={Calendar}
              label="Ngày nhập"
              value={formatDateOnly(receipt.receivedAt)}
            />
            <InfoRow
              icon={User}
              label="Người tạo"
              value={receipt.createdByName ?? '—'}
            />
            <InfoRow
              icon={Calendar}
              label="Ngày tạo"
              value={formatDate(receipt.createdAt)}
            />
            {receipt.status === 'CONFIRMED' && (
              <>
                <InfoRow
                  icon={User}
                  label="Người duyệt"
                  value={receipt.approvedByName ?? '—'}
                />
                <InfoRow
                  icon={Calendar}
                  label="Ngày duyệt"
                  value={formatDate(receipt.confirmedAt)}
                />
              </>
            )}

            {receipt.notes && (
              <div
                style={{
                  marginTop: 12,
                  padding: '10px 12px',
                  backgroundColor: '#f8fafc',
                  borderRadius: 8,
                  border: '1px solid #e2e8f0',
                }}
              >
                <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 4 }}>
                  Ghi chú
                </div>
                <div style={{ fontSize: 12, color: '#475569', lineHeight: 1.5 }}>
                  {receipt.notes}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
    {ConfirmDialog}
    </>
  );
}
