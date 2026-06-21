import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  AlertCircle,
  ArrowLeft,
  Ban,
  Calendar,
  CheckCircle2,
  DollarSign,
  FileText,
  Hash,
  Loader2,
  Package,
  Printer,
  User,
  Warehouse,
} from 'lucide-react';
import { toast } from 'react-toastify';
import stockDeliveryService from '../../services/stockDeliveryService';
import useAuth from '../../../auth/hooks/useAuth';
import { ROLES } from '../../../auth/constants/roles';
import { ROUTES } from '../../../../app/router/routes';

const ISSUE_TYPES = {
  ORDER: 'Xuất bán hàng',
  ADJUSTMENT: 'Xuất dùng',
  DISPOSAL: 'Xuất hủy',
  TRANSFER: 'Trả hàng NCC',
};

const STATUS_CFG = {
  DRAFT: { label: 'Lưu tạm', color: '#d97706', bg: '#fffbeb', border: '#fcd34d' },
  CONFIRMED: { label: 'Hoàn thành', color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
  CANCELLED: { label: 'Đã hủy', color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const formatVND = (value) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value ?? 0);

const formatNumber = (value) => new Intl.NumberFormat('vi-VN').format(value ?? 0);

const formatDateTime = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

const formatDateOnly = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

const getResponseData = (response) => response?.data?.data ?? response?.data ?? response ?? {};

const StatusBadge = ({ status }) => {
  const config = STATUS_CFG[status] ?? { label: status ?? '-', color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      padding: '5px 12px',
      borderRadius: 999,
      border: `1px solid ${config.border}`,
      background: config.bg,
      color: config.color,
      fontSize: 13,
      fontWeight: 700,
      whiteSpace: 'nowrap',
    }}>
      {config.label}
    </span>
  );
};

const InfoRow = ({ icon: Icon, label, value, color = '#0f172a' }) => (
  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '11px 0', borderBottom: '1px solid #f1f5f9' }}>
    <div style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      <Icon size={16} color="#64748b" />
    </div>
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color, wordBreak: 'break-word' }}>{value ?? '-'}</div>
    </div>
  </div>
);

export default function StockDeliveryDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const { user } = useAuth();
  const [delivery, setDelivery] = useState(null);
  const [loading, setLoading] = useState(true);
  const [completing, setCompleting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const isOwner = user?.role === ROLES.OWNER;
  const canComplete = user?.role === ROLES.OWNER || user?.role === ROLES.OPERATIONS;

  const fetchDelivery = async () => {
    setLoading(true);
    try {
      const response = await stockDeliveryService.getStockDeliveryById(id);
      setDelivery(getResponseData(response));
    } catch (error) {
      toast.error(error?.message || 'Không thể tải chi tiết phiếu xuất.');
      navigate(ROUTES.STOCK_DELIVERIES);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!id) {
      navigate(ROUTES.STOCK_DELIVERIES);
      return;
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchDelivery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const items = useMemo(() => delivery?.items ?? [], [delivery?.items]);
  const totals = useMemo(() => {
    const totalQuantity = items.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0);
    const totalCost = items.reduce((sum, item) => sum + Number(item.totalCost ?? ((item.quantity ?? 0) * (item.unitCost ?? 0))), 0);
    return { totalSku: items.length, totalQuantity, totalCost };
  }, [items]);

  const handleCancel = async () => {
    if (!delivery || delivery.status === 'CANCELLED' || !isOwner) return;
    if (!window.confirm(`Hủy phiếu xuất "${delivery.issueCode}"?\n\nTồn kho của các sản phẩm trong phiếu sẽ được khôi phục.`)) {
      return;
    }

    setCancelling(true);
    try {
      const response = await stockDeliveryService.cancelStockDelivery(delivery.id);
      setDelivery(getResponseData(response));
      toast.success('Hủy phiếu xuất thành công. Tồn kho đã được khôi phục.');
    } catch (error) {
      toast.error(error?.message || 'Không thể hủy phiếu xuất. Vui lòng thử lại.');
    } finally {
      setCancelling(false);
    }
  };

  const handleComplete = async () => {
    if (!delivery || delivery.status !== 'DRAFT' || !canComplete) return;
    if (!window.confirm(`Xác nhận hoàn thành phiếu xuất "${delivery.issueCode}"?\n\nSau khi hoàn thành sẽ không thể chuyển lại trạng thái Lưu tạm.`)) {
      return;
    }

    setCompleting(true);
    try {
      const response = await stockDeliveryService.confirmStockDelivery(delivery.id);
      setDelivery(getResponseData(response));
      toast.success('Hoàn thành phiếu xuất thành công.');
    } catch (error) {
      toast.error(error?.message || 'Không thể hoàn thành phiếu xuất. Vui lòng thử lại.');
    } finally {
      setCompleting(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '60vh', gap: 12, color: '#94a3b8' }}>
        <Loader2 size={32} style={{ animation: 'spin 1s linear infinite' }} />
        <p style={{ fontSize: 14 }}>Đang tải chi tiết phiếu xuất...</p>
      </div>
    );
  }

  if (!delivery) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '60vh', gap: 12, color: '#94a3b8' }}>
        <AlertCircle size={32} />
        <p style={{ fontSize: 14 }}>Không tìm thấy phiếu xuất.</p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button type="button" onClick={() => navigate(ROUTES.STOCK_DELIVERIES)} style={secondaryButtonStyle}>
            <ArrowLeft size={14} /> Quay lại
          </button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#fff1f2', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <FileText size={18} color="#dc2626" />
            </div>
            <div>
              <h1 style={{ fontSize: 18, fontWeight: 700, color: '#0f172a', margin: 0 }}>
                Chi tiết phiếu xuất: {delivery.issueCode}
              </h1>
              <p style={{ fontSize: 12, color: '#64748b', margin: '1px 0 0' }}>
                Thông tin phiếu xuất và danh sách sản phẩm
              </p>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          {canComplete && delivery.status === 'DRAFT' && (
            <button type="button" onClick={handleComplete} disabled={completing} style={{ ...successButtonStyle, opacity: completing ? 0.7 : 1 }}>
              {completing ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <CheckCircle2 size={14} />}
              {completing ? 'Đang hoàn thành...' : 'Hoàn thành xuất kho'}
            </button>
          )}
          {isOwner && delivery.status !== 'CANCELLED' && (
            <button type="button" onClick={handleCancel} disabled={cancelling} style={{ ...dangerButtonStyle, opacity: cancelling ? 0.7 : 1 }}>
              {cancelling ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <Ban size={14} />}
              {cancelling ? 'Đang hủy...' : 'Hủy phiếu xuất'}
            </button>
          )}
          <button type="button" onClick={() => toast.info('In phiếu đang được phát triển')} style={secondaryButtonStyle}>
            <Printer size={14} /> In phiếu
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 16 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
            <SummaryCard icon={Hash} label="Số loại sản phẩm" value={formatNumber(totals.totalSku)} color="#0284c7" bg="#f0f9ff" />
            <SummaryCard icon={Package} label="Tổng số lượng" value={formatNumber(totals.totalQuantity)} color="#16a34a" bg="#f0fdf4" />
            <SummaryCard icon={DollarSign} label="Tổng giá trị" value={formatVND(totals.totalCost)} color="#dc2626" bg="#fff1f2" />
          </div>

          <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid #f1f5f9', backgroundColor: '#f8fafc' }}>
              <h3 style={{ fontSize: 14, fontWeight: 700, color: '#0f172a', margin: 0 }}>Danh sách sản phẩm xuất</h3>
            </div>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                    {['Sản phẩm', 'SKU', 'Số lượng', 'Đơn giá', 'Thành tiền'].map((header) => (
                      <th key={header} style={{ padding: '10px 14px', textAlign: ['Số lượng', 'Đơn giá', 'Thành tiền'].includes(header) ? 'right' : 'left', fontWeight: 700, fontSize: 11, color: '#64748b', whiteSpace: 'nowrap' }}>
                        {header}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {items.length === 0 ? (
                    <tr><td colSpan={5} style={{ padding: '40px 14px', textAlign: 'center', color: '#94a3b8' }}>Không có sản phẩm nào</td></tr>
                  ) : items.map((item, index) => {
                    const lineTotal = Number(item.totalCost ?? ((item.quantity ?? 0) * (item.unitCost ?? 0)));
                    return (
                      <tr key={item.id ?? index} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '12px 14px' }}>
                          <div style={{ fontWeight: 600, color: '#0f172a' }}>{item.productName ?? '-'}</div>
                          {item.productVariantName && <div style={{ fontSize: 10, color: '#94a3b8' }}>{item.productVariantName}</div>}
                        </td>
                        <td style={{ padding: '12px 14px' }}>
                          <span style={{ fontSize: 10, fontFamily: 'monospace', backgroundColor: '#f1f5f9', color: '#64748b', padding: '2px 6px', borderRadius: 4 }}>
                            {item.sku ?? '-'}
                          </span>
                        </td>
                        <td style={{ padding: '12px 14px', textAlign: 'right', fontWeight: 700, color: '#0f172a' }}>{formatNumber(item.quantity)}</td>
                        <td style={{ padding: '12px 14px', textAlign: 'right', color: '#475569' }}>{formatVND(item.unitCost)}</td>
                        <td style={{ padding: '12px 14px', textAlign: 'right', fontWeight: 700, color: '#dc2626' }}>{formatVND(lineTotal)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '16px 18px' }}>
            <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 8 }}>Trạng thái</div>
            <StatusBadge status={delivery.status} />
          </div>

          <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '16px 18px' }}>
            <h3 style={{ fontSize: 14, fontWeight: 700, color: '#0f172a', margin: '0 0 12px' }}>Thông tin phiếu xuất</h3>
            <InfoRow icon={FileText} label="Mã phiếu xuất" value={delivery.issueCode} color="#dc2626" />
            <InfoRow icon={Warehouse} label="Kho xuất" value={delivery.warehouseName} />
            <InfoRow icon={Package} label="Loại xuất" value={delivery.issueTypeLabel ?? ISSUE_TYPES[delivery.issueType ?? delivery.deliveryType]} />
            <InfoRow icon={User} label="Người nhận" value={delivery.recipient} />
            <InfoRow icon={Calendar} label="Ngày xuất" value={formatDateOnly(delivery.issuedAt ?? delivery.createdAt)} />
            <InfoRow icon={User} label="Người tạo" value={delivery.createdByName} />
            <InfoRow icon={Calendar} label="Ngày tạo" value={formatDateTime(delivery.createdAt)} />
            {delivery.confirmedAt && <InfoRow icon={Calendar} label="Ngày xác nhận" value={formatDateTime(delivery.confirmedAt)} />}
            {delivery.note && (
              <div style={{ marginTop: 12, padding: '10px 12px', backgroundColor: '#f8fafc', borderRadius: 8, border: '1px solid #e2e8f0' }}>
                <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 4 }}>Ghi chú</div>
                <div style={{ fontSize: 12, color: '#475569', lineHeight: 1.5 }}>{delivery.note}</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function SummaryCard({ icon: Icon, label, value, color, bg }) {
  return (
    <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '14px 16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <span style={{ fontSize: 11, color: '#64748b' }}>{label}</span>
        <div style={{ width: 28, height: 28, borderRadius: 7, backgroundColor: bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon size={14} color={color} />
        </div>
      </div>
      <div style={{ fontSize: 20, fontWeight: 700, color }}>{value}</div>
    </div>
  );
}

const secondaryButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '8px 14px',
  borderRadius: 7,
  border: '1px solid #e2e8f0',
  background: '#fff',
  fontSize: 13,
  fontWeight: 600,
  color: '#374151',
  cursor: 'pointer',
};

const dangerButtonStyle = {
  ...secondaryButtonStyle,
  border: 'none',
  background: '#dc2626',
  color: '#fff',
};

const successButtonStyle = {
  ...secondaryButtonStyle,
  border: 'none',
  background: '#059669',
  color: '#fff',
};
