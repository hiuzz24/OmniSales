import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  AlertTriangle,
  Calendar,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Loader2,
  MapPin,
  PackageCheck,
  PlayCircle,
  StickyNote,
  TrendingDown,
  TrendingUp,
  User,
  Warehouse,
  XCircle,
} from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../../app/router/routes';
import stocktakeService from '../../services/stocktakeService';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import {
  formatDate,
  formatDateTime,
  formatNumber,
  formatVND,
  getResponseData,
} from '../components/inventoryDocumentListUtils';

const STATUS_CFG = {
  DRAFT: { label: 'Nháp', icon: ClipboardList, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' },
  IN_PROGRESS: { label: 'Đang kiểm', icon: Clock3, color: '#2563eb', bg: '#eff6ff', border: '#bfdbfe' },
  COMPLETED: { label: 'Hoàn thành', icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
  CANCELLED: { label: 'Đã hủy', icon: XCircle, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const cardStyle = {
  background: '#ffffff',
  border: '1px solid #e2e8f0',
  borderRadius: 14,
  padding: 18,
};

const sectionTitleStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  fontSize: 13,
  fontWeight: 700,
  color: '#0f172a',
  marginBottom: 12,
};

const StatusBadge = ({ status }) => {
  const cfg = STATUS_CFG[status] ?? STATUS_CFG.DRAFT;
  const Icon = cfg.icon;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '5px 14px', borderRadius: 999, fontSize: 13, fontWeight: 700, color: cfg.color, backgroundColor: cfg.bg, border: `1px solid ${cfg.border}` }}>
      <Icon size={14} /> {cfg.label}
    </span>
  );
};

const InfoRow = ({ icon: Icon, label, value, color = '#0f172a' }) => (
  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '9px 0', borderBottom: '1px solid #f1f5f9' }}>
    <div style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: '#f8fafc', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      <Icon size={16} color="#64748b" />
    </div>
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 11, color: '#94a3b8', marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 500, color, wordBreak: 'break-word' }}>{value || '—'}</div>
    </div>
  </div>
);

const StatCard = ({ label, value, color = '#0f172a', bg = '#f8fafc', border = '#e2e8f0' }) => (
  <div style={{ background: bg, border: `1px solid ${border}`, borderRadius: 12, padding: '14px 16px', minWidth: 0 }}>
    <div style={{ fontSize: 11, color: '#64748b', marginBottom: 6 }}>{label}</div>
    <div style={{ fontSize: 20, fontWeight: 800, color, whiteSpace: 'nowrap' }}>{value}</div>
  </div>
);

const DiffCell = ({ diff, checked }) => {
  if (!checked) return <span style={{ color: '#cbd5e1' }}>Chưa kiểm</span>;
  if (diff === 0) return <span style={{ color: '#059669', fontWeight: 700 }}>Khớp</span>;
  if (diff > 0) {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#0d9488', fontWeight: 700 }}>
        <TrendingUp size={13} /> +{formatNumber(diff)}
      </span>
    );
  }
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#dc2626', fontWeight: 700 }}>
      <TrendingDown size={13} /> -{formatNumber(Math.abs(diff))}
    </span>
  );
};

const actionBtnStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '8px 14px',
  borderRadius: 9,
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
  border: '1px solid transparent',
};

export default function StocktakeDetailPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const [stocktake, setStocktake] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const fetchDetail = useCallback(async () => {
    setLoading(true);
    try {
      const response = await stocktakeService.getById(id);
      setStocktake(getResponseData(response));
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể tải chi tiết phiếu kiểm kho.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchDetail();
  }, [fetchDetail]);

  const changeStatus = async (nextStatus) => {
    if (!stocktake) return;
    if (nextStatus === 'COMPLETED') {
      const hasMissingActual = (stocktake.items ?? []).some((item) => !item.checked);
      if (hasMissingActual || !(stocktake.items ?? []).length) {
        toast.error('Cần nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.');
        return;
      }
    }
    const proceed = nextStatus === 'CANCELLED'
      ? await confirm({
          title: 'Hủy phiếu kiểm kho',
          message: `Bạn chắc chắn muốn hủy phiếu ${stocktake.sessionCode}?`,
          confirmText: 'Hủy phiếu',
          danger: true,
        })
      : true;
    if (!proceed) return;
    setSaving(true);
    try {
      await stocktakeService.changeStatus(stocktake.id, nextStatus);
      toast.success('Cập nhật trạng thái phiếu kiểm thành công.');
      fetchDetail();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể cập nhật trạng thái phiếu kiểm.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '50vh', gap: 10, color: '#64748b' }}>
        <Loader2 size={20} className="spin-icon" /> Đang tải phiếu kiểm kho...
      </div>
    );
  }

  if (!stocktake) {
    return (
      <div style={{ maxWidth: 720, margin: '60px auto', textAlign: 'center', color: '#64748b' }}>
        <AlertTriangle size={28} style={{ marginBottom: 10 }} />
        <p>Không tìm thấy phiếu kiểm kho.</p>
        <button type="button" style={{ ...actionBtnStyle, background: '#f1f5f9', color: '#334155', marginTop: 12 }} onClick={() => navigate(ROUTES.STOCKTAKES)}>
          <ArrowLeft size={14} /> Quay lại danh sách
        </button>
      </div>
    );
  }

  const items = stocktake.items ?? [];
  const isClosed = stocktake.status === 'COMPLETED' || stocktake.status === 'CANCELLED';
  const summary = {
    totalItems: stocktake.totalItems ?? items.length,
    checked: stocktake.checkedCount ?? 0,
    matched: stocktake.matchedCount ?? 0,
    surplus: stocktake.surplusCount ?? 0,
    shortage: stocktake.shortageCount ?? 0,
    system: stocktake.totalSystemQuantity ?? 0,
    actual: stocktake.totalActualQuantity ?? 0,
    diff: stocktake.totalDifference ?? 0,
    diffValue: stocktake.totalDifferenceValue ?? 0,
  };

  return (
    <div style={{ width: 'calc(100% + 48px)', margin: '-24px', padding: '24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
        <button type="button" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 12px', borderRadius: 9, fontSize: 13, fontWeight: 600, color: '#475569', background: '#f1f5f9', border: '1px solid #e2e8f0', cursor: 'pointer' }} onClick={() => navigate(ROUTES.STOCKTAKES)}>
          <ArrowLeft size={15} /> Quay lại
        </button>
        <div style={{ width: 40, height: 40, borderRadius: 10, background: '#f0fdfa', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ClipboardList size={20} color="#0d9488" />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <h1 style={{ margin: 0, fontSize: 19, fontWeight: 800, color: '#0f172a', fontFamily: 'monospace' }}>{stocktake.sessionCode}</h1>
            <StatusBadge status={stocktake.status} />
          </div>
          <p style={{ margin: '4px 0 0', fontSize: 13, color: '#64748b' }}>Chi tiết phiếu kiểm kho</p>
        </div>
        {!isClosed && (
          <div style={{ display: 'flex', gap: 8 }}>
            {stocktake.status !== 'IN_PROGRESS' && (
              <button type="button" disabled={saving} style={{ ...actionBtnStyle, background: '#2563eb', color: '#fff' }} onClick={() => changeStatus('IN_PROGRESS')}>
                <PlayCircle size={14} /> Bắt đầu kiểm
              </button>
            )}
            <button type="button" disabled={saving} style={{ ...actionBtnStyle, background: '#059669', color: '#fff' }} onClick={() => changeStatus('COMPLETED')}>
              <CheckCircle2 size={14} /> Hoàn thành
            </button>
            <button type="button" disabled={saving} style={{ ...actionBtnStyle, background: '#fff1f2', color: '#e11d48', border: '1px solid #fecdd3' }} onClick={() => changeStatus('CANCELLED')}>
              <XCircle size={14} /> Hủy phiếu
            </button>
          </div>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
        <div style={cardStyle}>
          <div style={sectionTitleStyle}><ClipboardList size={15} color="#0d9488" /> Thông tin phiếu</div>
          <InfoRow icon={Warehouse} label="Kho kiểm" value={stocktake.warehouseName} />
          <InfoRow icon={MapPin} label="Địa chỉ kho" value={stocktake.warehouseAddress} />
          <InfoRow icon={Calendar} label="Ngày kiểm kho" value={formatDate(stocktake.scheduledDate)} />
          <InfoRow icon={User} label="Người tạo" value={stocktake.createdByName} />
          <InfoRow icon={Clock3} label="Ngày tạo" value={formatDateTime(stocktake.createdAt)} />
          {stocktake.updatedAt && stocktake.updatedAt !== stocktake.createdAt && (
            <InfoRow icon={Clock3} label="Cập nhật lần cuối" value={formatDateTime(stocktake.updatedAt)} />
          )}
          {stocktake.notes && <InfoRow icon={StickyNote} label="Ghi chú" value={stocktake.notes} />}
        </div>

        <div style={cardStyle}>
          <div style={sectionTitleStyle}><Clock3 size={15} color="#0d9488" /> Lịch sử trạng thái</div>
          {stocktake.startedAt && (
            <InfoRow icon={PlayCircle} label="Bắt đầu kiểm" value={`${stocktake.startedByName ?? '—'} · ${formatDateTime(stocktake.startedAt)}`} />
          )}
          {stocktake.completedAt && (
            <InfoRow icon={CheckCircle2} label="Hoàn thành" value={`${stocktake.completedByName ?? '—'} · ${formatDateTime(stocktake.completedAt)}`} color="#059669" />
          )}
          {stocktake.cancelledAt && (
            <InfoRow icon={XCircle} label="Đã hủy" value={`${stocktake.cancelledByName ?? '—'} · ${formatDateTime(stocktake.cancelledAt)}`} color="#e11d48" />
          )}
          {!stocktake.startedAt && !stocktake.completedAt && !stocktake.cancelledAt && (
            <div style={{ fontSize: 13, color: '#94a3b8', padding: '8px 0' }}>Chưa có thao tác chuyển trạng thái nào.</div>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 16 }}>
        <StatCard label="Tổng SKU" value={formatNumber(summary.totalItems)} bg="#f8fafc" border="#e2e8f0" />
        <StatCard label="Đã kiểm" value={`${formatNumber(summary.checked)} / ${formatNumber(summary.totalItems)}`} bg="#eff6ff" border="#bfdbfe" color="#2563eb" />
        <StatCard label="Tổng SL hệ thống" value={formatNumber(summary.system)} bg="#f8fafc" border="#e2e8f0" />
        <StatCard label="Tổng SL thực tế" value={formatNumber(summary.actual)} bg="#f8fafc" border="#e2e8f0" />
        <StatCard label="Khớp" value={formatNumber(summary.matched)} bg="#ecfdf5" border="#a7f3d0" color="#059669" />
        <StatCard label="Thừa (SP)" value={formatNumber(summary.surplus)} bg="#f0fdfa" border="#99f6e4" color="#0d9488" />
        <StatCard label="Thiếu (SP)" value={formatNumber(summary.shortage)} bg="#fff1f2" border="#fecdd3" color="#e11d48" />
        <StatCard
          label="Giá trị chênh lệch"
          value={formatVND(summary.diffValue)}
          bg={summary.diffValue < 0 ? '#fff1f2' : summary.diffValue > 0 ? '#f0fdfa' : '#f8fafc'}
          border={summary.diffValue < 0 ? '#fecdd3' : summary.diffValue > 0 ? '#99f6e4' : '#e2e8f0'}
          color={summary.diffValue < 0 ? '#e11d48' : summary.diffValue > 0 ? '#0d9488' : '#334155'}
        />
      </div>

      <div style={cardStyle}>
        <div style={sectionTitleStyle}><PackageCheck size={15} color="#0d9488" /> Chi tiết sản phẩm kiểm</div>
        {items.length === 0 ? (
          <div style={{ textAlign: 'center', color: '#94a3b8', padding: 24 }}>Phiếu không có sản phẩm kiểm.</div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, minWidth: 960 }}>
              <thead>
                <tr style={{ textAlign: 'left', color: '#64748b', fontSize: 12 }}>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0' }}>STT</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0' }}>SKU</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0' }}>Barcode</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0' }}>Sản phẩm</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0', textAlign: 'right' }}>ĐVT</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0', textAlign: 'right' }}>Tồn HT</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0', textAlign: 'right' }}>Tồn thực tế</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0', textAlign: 'right' }}>Chênh lệch</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0', textAlign: 'right' }}>Giá vốn</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0', textAlign: 'right' }}>Giá trị lệch</th>
                  <th style={{ padding: '8px 10px', borderBottom: '1px solid #e2e8f0' }}>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item, index) => {
                  const checked = Boolean(item.checked);
                  const diff = checked ? Number(item.difference ?? 0) : 0;
                  const diffValue = checked ? Number(item.differenceValue ?? 0) : 0;
                  const rowBg = checked && diff < 0 ? '#fff7f7' : checked && diff > 0 ? '#f0fdfa' : '#ffffff';
                  return (
                    <tr key={item.id ?? item.variantId} style={{ background: rowBg }}>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', color: '#94a3b8' }}>{index + 1}</td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', fontFamily: 'monospace', fontWeight: 600, color: '#0d9488' }}>{item.variantSku}</td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', color: '#64748b' }}>{item.barcode || '—'}</td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', fontWeight: 600, color: '#0f172a' }}>
                        {item.productName}{item.variantName ? ` — ${item.variantName}` : ''}
                      </td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', textAlign: 'right', color: '#64748b' }}>{item.unit || 'Cái'}</td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', textAlign: 'right' }}>{formatNumber(item.systemQuantity)}</td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', textAlign: 'right', fontWeight: 700 }}>
                        {checked ? formatNumber(item.actualQuantity) : <span style={{ color: '#cbd5e1' }}>—</span>}
                      </td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', textAlign: 'right' }}><DiffCell diff={diff} checked={checked} /></td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', textAlign: 'right', color: '#475569' }}>{formatVND(item.costPrice)}</td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', textAlign: 'right', fontWeight: 700, color: !checked || diffValue === 0 ? '#94a3b8' : diffValue < 0 ? '#dc2626' : '#0d9488' }}>
                        {checked ? formatVND(diffValue) : '—'}
                      </td>
                      <td style={{ padding: '8px 10px', borderBottom: '1px solid #f1f5f9', color: '#64748b' }}>{item.notes || '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {ConfirmDialog}
    </div>
  );
}
