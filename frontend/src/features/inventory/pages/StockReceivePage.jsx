import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Plus, Search, Download, Eye, Printer,
  CheckCircle2, Save, Undo2, FileText, PackagePlus,
  ChevronLeft, ChevronRight, MoreVertical, Check,
} from 'lucide-react';
import { toast } from 'react-toastify';
import stockReceiveService from '../services/stockReceiveService';
import { ROUTES } from '../../../app/router/routes';

// ── Helpers ───────────────────────────────────────────────────────────────────
const formatVND = (v) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v ?? 0);

const formatDate = (s) => {
  if (!s) return '—';
  return new Date(s).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });
};

// Status config — matches reference project (DRAFT/COMPLETED/RETURNED)
const STATUS_CFG = {
  CONFIRMED: { label: 'Hoàn thành', icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
  DRAFT: { label: 'Lưu tạm', icon: Save, color: '#d97706', bg: '#fffbeb', border: '#fcd34d' },
  CANCELLED: { label: 'Trả hàng', icon: Undo2, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

// ── Stat card ─────────────────────────────────────────────────────────────────
const StatCard = ({ label, value, icon: Icon, color, bg }) => (
  <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '14px 16px' }}>
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
      <span style={{ fontSize: 11, color: '#64748b' }}>{label}</span>
      <div style={{ width: 28, height: 28, borderRadius: 7, backgroundColor: bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Icon size={14} color={color} />
      </div>
    </div>
    <div style={{ fontSize: 20, fontWeight: 700, color: '#0f172a' }}>{value}</div>
  </div>
);

// ── Status badge ──────────────────────────────────────────────────────────────
const StatusBadge = ({ status }) => {
  const cfg = STATUS_CFG[status] ?? { label: status, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
  const Icon = cfg.icon ?? FileText;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      padding: '2px 8px', borderRadius: 999,
      fontSize: 11, fontWeight: 500,
      color: cfg.color, backgroundColor: cfg.bg,
      border: `1px solid ${cfg.border}`,
    }}>
      <Icon size={11} />
      {cfg.label}
    </span>
  );
};

// ── Dropdown menu for actions ─────────────────────────────────────────────────
const ActionMenu = ({ receipt, onComplete, onRefresh }) => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    if (open) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [open]);

  const handleComplete = async () => {
    setOpen(false);
    if (window.confirm(`Xác nhận hoàn thành phiếu nhập "${receipt.receiptCode}"?\n\nTồn kho sẽ được cập nhật sau khi xác nhận.`)) {
      try {
        await onComplete(receipt.id);
        toast.success('Hoàn thành phiếu nhập thành công.');
        onRefresh();
      } catch (error) {
        const errorMessage = error?.response?.data?.message || error?.message || 'Không thể hoàn thành phiếu nhập. Vui lòng thử lại.';
        toast.error(errorMessage);
      }
    }
  };

  return (
    <div ref={menuRef} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen(!open)}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 26,
          height: 26,
          borderRadius: 5,
          border: '1px solid #e2e8f0',
          background: '#fff',
          cursor: 'pointer',
          color: '#64748b',
        }}
        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
      >
        <MoreVertical size={14} />
      </button>

      {open && (
        <div
          style={{
            position: 'absolute',
            right: 0,
            top: '100%',
            marginTop: 4,
            backgroundColor: '#fff',
            borderRadius: 8,
            border: '1px solid #e2e8f0',
            boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
            minWidth: 160,
            zIndex: 50,
            overflow: 'hidden',
          }}
        >
          <button
            onClick={() => {
              setOpen(false);
              // Navigate to detail page
              navigate(`/warehouse/receipts/${receipt.id}`);
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              width: '100%',
              padding: '8px 12px',
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              fontSize: 12,
              color: '#374151',
              textAlign: 'left',
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <Eye size={14} />
            Xem chi tiết
          </button>

          {receipt.status === 'DRAFT' && (
            <button
              onClick={handleComplete}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                width: '100%',
                padding: '8px 12px',
                border: 'none',
                background: 'none',
                cursor: 'pointer',
                fontSize: 12,
                color: '#059669',
                textAlign: 'left',
              }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#ecfdf5'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <Check size={14} />
              Hoàn thành nhập kho
            </button>
          )}

          <button
            onClick={() => {
              setOpen(false);
              toast.info('In phiếu đang được phát triển');
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              width: '100%',
              padding: '8px 12px',
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              fontSize: 12,
              color: '#374151',
              textAlign: 'left',
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <Printer size={14} />
            In phiếu
          </button>
        </div>
      )}
    </div>
  );
};

// ── Page ──────────────────────────────────────────────────────────────────────
export default function StockReceivePage() {
  const navigate = useNavigate();
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [pagination, setPagination] = useState({ page: 0, size: 20, totalPages: 0, totalElements: 0 });

  const fetch = async () => {
    setLoading(true);
    try {
      const res = await stockReceiveService.getReceipts({ page: pagination.page, size: pagination.size });
      const data = res.data?.data ?? res.data ?? {};
      const list = data.content ?? data ?? [];
      setReceipts(Array.isArray(list) ? list : []);
      setPagination((p) => ({ ...p, totalPages: data.totalPages ?? 1, totalElements: data.totalElements ?? list.length }));
    } catch {
      setReceipts([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagination.page]);

  const handleCompleteReceipt = async (receiptId) => {
    // Call API to complete receipt (update status from DRAFT to CONFIRMED)
    // This should be implemented in stockReceiveService
    await stockReceiveService.completeReceipt(receiptId);
  };

  const filtered = receipts.filter((r) => {
    const s = search.toLowerCase();
    const matchSearch = !s || (r.receiptCode ?? '').toLowerCase().includes(s)
      || (r.supplierName ?? '').toLowerCase().includes(s)
      || (r.invoiceNumber ?? '').toLowerCase().includes(s);
    const matchStatus = statusFilter === 'ALL' || r.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const stats = {
    total: receipts.length,
    completed: receipts.filter((r) => r.status === 'CONFIRMED').length,
    draft: receipts.filter((r) => r.status === 'DRAFT').length,
    cancelled: receipts.filter((r) => r.status === 'CANCELLED').length,
  };

  const cols = ['Mã phiếu', 'Kho nhập', 'Nhà cung cấp', 'Số HĐ', 'SL SKU', 'Tổng SL', 'Giá trị', 'Trạng thái', 'Người tạo', 'Ngày tạo', ''];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <PackagePlus size={18} color="#2563eb" />
          </div>
          <div>
            <h1 style={{ fontSize: 18, fontWeight: 700, color: '#0f172a', margin: 0 }}>Danh sách phiếu nhập kho</h1>
            <p style={{ fontSize: 12, color: '#64748b', margin: '1px 0 0' }}>Quản lý tất cả phiếu nhập hàng từ nhà cung cấp</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 12px', borderRadius: 7, border: '1px solid #e2e8f0', backgroundColor: '#fff', fontSize: 12, fontWeight: 500, color: '#374151', cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f8fafc'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#fff'}
          >
            <Download size={14} /> Xuất Excel
          </button>
          <button
            onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE)}
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 12px', borderRadius: 7, border: 'none', backgroundColor: '#2563eb', color: '#fff', fontSize: 12, fontWeight: 500, cursor: 'pointer' }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#1d4ed8'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#2563eb'}
          >
            <Plus size={14} /> Tạo phiếu nhập
          </button>
        </div>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        <StatCard label="Tổng phiếu" value={stats.total} icon={FileText} color="#475569" bg="#f8fafc" />
        <StatCard label="Hoàn thành" value={stats.completed} icon={CheckCircle2} color="#059669" bg="#ecfdf5" />
        <StatCard label="Lưu tạm" value={stats.draft} icon={Save} color="#d97706" bg="#fffbeb" />
        <StatCard label="Trả hàng" value={stats.cancelled} icon={Undo2} color="#e11d48" bg="#fff1f2" />
      </div>

      {/* Filters */}
      <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', padding: '12px 16px', display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div style={{ position: 'relative', flex: 1, minWidth: 200 }}>
          <Search size={14} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }} />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Tìm theo mã phiếu, nhà cung cấp, số hóa đơn..."
            style={{ width: '100%', paddingLeft: 30, paddingRight: 10, paddingTop: 7, paddingBottom: 7, borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12, color: '#0f172a', outline: 'none', boxSizing: 'border-box' }}
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{ padding: '7px 10px', borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12, color: '#374151', backgroundColor: '#fff', minWidth: 150, outline: 'none' }}
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="DRAFT">Lưu tạm</option>
          <option value="CONFIRMED">Hoàn thành</option>
          <option value="CANCELLED">Trả hàng</option>
        </select>
      </div>

      {/* Table */}
      <div style={{ backgroundColor: '#fff', borderRadius: 10, border: '1px solid #e2e8f0', overflow: 'hidden' }}>
        {loading ? (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '60px 0', color: '#94a3b8', fontSize: 13 }}>
            Đang tải...
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                  {cols.map((col) => (
                    <th key={col} style={{ padding: '8px 12px', textAlign: col === 'SL SKU' || col === 'Tổng SL' || col === 'Giá trị' ? 'right' : 'left', fontWeight: 600, fontSize: 11, color: '#64748b', whiteSpace: 'nowrap' }}>
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={cols.length} style={{ textAlign: 'center', padding: '50px 0', color: '#94a3b8', fontSize: 13 }}>
                      {receipts.length === 0
                        ? <span>Chưa có phiếu nhập nào. Nhấn <button onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE)} style={{ color: '#2563eb', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 500 }}>Tạo phiếu nhập</button> để bắt đầu.</span>
                        : 'Không tìm thấy phiếu nhập nào phù hợp.'}
                    </td>
                  </tr>
                ) : filtered.map((r, idx) => (
                  <tr key={r.id ?? r.receiptCode ?? idx}
                    style={{ borderBottom: '1px solid #f1f5f9', backgroundColor: idx % 2 === 0 ? '#fff' : '#f8fafc' }}
                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f1f5f9'}
                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = idx % 2 === 0 ? '#fff' : '#f8fafc'}
                  >
                    <td style={{ padding: '10px 12px', fontFamily: 'monospace', fontWeight: 600, color: '#2563eb', whiteSpace: 'nowrap' }}>{r.receiptCode ?? '—'}</td>
                    <td style={{ padding: '10px 12px', color: '#475569', whiteSpace: 'nowrap' }}>{r.warehouseName ?? '—'}</td>
                    <td style={{ padding: '10px 12px', color: '#475569', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.supplierName ?? '—'}</td>
                    <td style={{ padding: '10px 12px', color: '#94a3b8', fontFamily: 'monospace' }}>{r.invoiceNumber ?? '—'}</td>
                    <td style={{ padding: '10px 12px', textAlign: 'right', color: '#475569' }}>{r.totalSkuCount ?? 0}</td>
                    <td style={{ padding: '10px 12px', textAlign: 'right', fontWeight: 500, color: '#0f172a' }}>{(r.totalQuantity ?? 0).toLocaleString()}</td>
                    <td style={{ padding: '10px 12px', textAlign: 'right', fontWeight: 600, color: '#0f172a', whiteSpace: 'nowrap' }}>{formatVND(r.totalCost)}</td>
                    <td style={{ padding: '10px 12px' }}><StatusBadge status={r.status} /></td>
                    <td style={{ padding: '10px 12px', color: '#475569' }}>{r.createdByName ?? '—'}</td>
                    <td style={{ padding: '10px 12px', color: '#94a3b8', whiteSpace: 'nowrap' }}>{formatDate(r.createdAt)}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <ActionMenu receipt={r} onComplete={handleCompleteReceipt} onRefresh={fetch} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer */}
        <div style={{ padding: '10px 16px', borderTop: '1px solid #f1f5f9', backgroundColor: 'rgba(248,250,252,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, color: '#64748b' }}>Hiển thị {filtered.length} / {receipts.length} phiếu</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 11, color: '#94a3b8' }}>
            {[{ color: '#059669', label: 'Hoàn thành' }, { color: '#d97706', label: 'Lưu tạm' }, { color: '#e11d48', label: 'Trả hàng' }].map((s) => (
              <span key={s.label} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ width: 7, height: 7, borderRadius: '50%', backgroundColor: s.color, display: 'inline-block' }} />{s.label}
              </span>
            ))}
          </div>
        </div>

        {/* Pagination */}
        {!loading && pagination.totalPages > 1 && (
          <div style={{ padding: '10px 16px', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 12, color: '#64748b' }}>
            <span>Trang {pagination.page + 1} / {pagination.totalPages}</span>
            <div style={{ display: 'flex', gap: 6 }}>
              {[{ icon: ChevronLeft, label: 'Trước', disabled: pagination.page === 0, onClick: () => setPagination((p) => ({ ...p, page: Math.max(0, p.page - 1) })) },
              { icon: ChevronRight, label: 'Sau', disabled: pagination.page >= pagination.totalPages - 1, onClick: () => setPagination((p) => ({ ...p, page: Math.min(p.totalPages - 1, p.page + 1) })) }
              ].map((btn) => (
                <button key={btn.label} onClick={btn.onClick} disabled={btn.disabled}
                  style={{ display: 'flex', alignItems: 'center', gap: 3, padding: '5px 10px', borderRadius: 7, border: '1px solid #e2e8f0', backgroundColor: '#fff', fontSize: 12, color: btn.disabled ? '#cbd5e1' : '#374151', cursor: btn.disabled ? 'not-allowed' : 'pointer' }}
                >
                  <btn.icon size={13} /> {btn.label}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
