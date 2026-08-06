import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  RefreshCw, Search, Calendar, ArrowRightLeft,
  TrendingUp, TrendingDown, Sliders, RotateCcw, Loader2,
  Download, SlidersHorizontal, ClipboardList,
  ArrowUp, ArrowDown,
} from 'lucide-react';
import { toast } from 'react-toastify';
import styles from './InventoryLogPage.module.css';
import Pagination from '../../../../shared/components/Pagination';
import inventoryService from '../../services/inventoryService';
import warehouseService from '../../services/warehouseService';
import { formatDateTime } from '../components/inventoryDocumentListUtils';

const PAGE_SIZE = 15;

const TXN_TYPE_CONFIG = {
  IMPORT:       { label: 'Nhập kho',    color: '#16a34a', bg: '#dcfce7', icon: TrendingUp,      refPrefix: 'PN' },
  OUTBOUND:     { label: 'Xuất kho',    color: '#dc2626', bg: '#fee2e2', icon: TrendingDown,    refPrefix: 'PX' },
  EXPORT:       { label: 'Xuất kho',    color: '#dc2626', bg: '#fee2e2', icon: TrendingDown,    refPrefix: 'PX' },
  ADJUSTMENT:   { label: 'Kiểm kho',   color: '#d97706', bg: '#fef3c7', icon: Sliders,         refPrefix: 'PKK' },
  TRANSFER_OUT: { label: 'Chuyển đi',  color: '#7c3aed', bg: '#f5f3ff', icon: ArrowRightLeft,  refPrefix: 'PCT' },
  TRANSFER_IN:  { label: 'Chuyển đến', color: '#7c3aed', bg: '#f5f3ff', icon: ArrowRightLeft,  refPrefix: 'PCT' },
  ORDER_DEDUCT: { label: 'Xuất đơn',   color: '#dc2626', bg: '#fee2e2', icon: TrendingDown,    refPrefix: 'DH' },
  ORDER_CANCEL: { label: 'Trả hàng',   color: '#0891b2', bg: '#e0f2fe', icon: TrendingUp,      refPrefix: 'TH' },
};

const TYPE_OPTIONS = [
  { value: 'all', label: 'Tất cả loại' },
  { value: 'IMPORT',       label: '→ Nhập kho' },
  { value: 'EXPORT',       label: '← Xuất kho' },
  { value: 'ADJUSTMENT',   label: '⚖ Kiểm kho' },
  { value: 'TRANSFER_OUT', label: '↑ Chuyển đi' },
  { value: 'TRANSFER_IN',  label: '↓ Chuyển đến' },
  { value: 'ORDER_DEDUCT', label: '← Xuất đơn hàng' },
  { value: 'ORDER_CANCEL', label: '→ Trả hàng' },
];

// Format a reference ID into a short code
function buildRefCode(log) {
  const cfg = TXN_TYPE_CONFIG[log.type];
  const prefix = cfg?.refPrefix ?? (log.referenceType ?? 'TXN').slice(0, 3).toUpperCase();
  if (!log.referenceId) return prefix;
  const shortId = String(log.referenceId).replace(/-/g, '').slice(0, 8).toUpperCase();
  return `${prefix}-${shortId}`;
}

// Build auto-description when note is missing
function buildDesc(log) {
  if (log.note) return log.note;
  switch (log.type) {
    case 'IMPORT': case 'INBOUND': return 'Nhập hàng từ nhà cung cấp';
    case 'EXPORT': case 'OUTBOUND': return 'Xuất kho';
    case 'ADJUSTMENT': return 'Điều chỉnh do kiểm kê';
    case 'TRANSFER_IN': return 'Nhận chuyển kho';
    case 'TRANSFER_OUT': return 'Chuyển kho';
    case 'ORDER_DEDUCT': return 'Xuất kho cho đơn hàng';
    case 'ORDER_CANCEL': return 'Hoàn trả đơn hàng';
    default: return 'Biến động kho hàng';
  }
}

const fmtMoney = (v) => v != null
  ? new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(v)
  : '—';

export default function InventoryLogPage() {
  const [logs, setLogs] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showFilters, setShowFilters] = useState(false);

  // filters
  const [search, setSearch] = useState('');
  const [warehouseId, setWarehouseId] = useState('all');
  const [type, setType] = useState('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  // pagination
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    warehouseService.getAll()
      .then((res) => {
        const d = res?.data?.data ?? res?.data ?? res ?? [];
        setWarehouses(Array.isArray(d) ? d : d.content ?? []);
      })
      .catch(() => {});
  }, []);

  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size: PAGE_SIZE };
      if (search.trim()) params.productSearch = search.trim();
      if (warehouseId !== 'all') params.warehouseId = warehouseId;
      if (type !== 'all') params.type = type;
      if (fromDate) params.startDate = new Date(fromDate).toISOString();
      if (toDate) { const d = new Date(toDate); d.setHours(23, 59, 59, 999); params.endDate = d.toISOString(); }
      const data = await inventoryService.getInventoryLogs(params);
      setLogs(data?.content ?? []);
      setTotalPages(data?.totalPages ?? 1);
      setTotalElements(data?.totalElements ?? 0);
    } catch {
      toast.error('Không thể tải nhật ký kho.');
      setLogs([]);
    } finally { setLoading(false); }
  }, [search, warehouseId, type, fromDate, toDate, page]);

  useEffect(() => { fetchLogs(); }, [fetchLogs]);

  const reset = () => {
    setSearch(''); setWarehouseId('all'); setType('all');
    setFromDate(''); setToDate(''); setPage(0);
  };

  // ── Stats derived from current page ──────────────────────────────────────
  const stats = useMemo(() => {
    const totalIn  = logs.filter((l) => (l.quantityChange ?? 0) > 0).reduce((s, l) => s + (l.quantityChange ?? 0), 0);
    const totalOut = logs.filter((l) => (l.quantityChange ?? 0) < 0).reduce((s, l) => s + (l.quantityChange ?? 0), 0);
    // Use backend-computed transactionValue when available, fallback to local calc
    const getVal = (l) => {
      if (l.transactionValue != null) return Number(l.transactionValue);
      const cost = l.unitCost != null ? Number(l.unitCost) : (l.avgCostAfter != null ? Number(l.avgCostAfter) : 0);
      return cost * Math.abs(l.quantityChange ?? 0);
    };
    const valueIn  = logs.filter((l) => (l.quantityChange ?? 0) > 0).reduce((s, l) => s + getVal(l), 0);
    const valueOut = logs.filter((l) => (l.quantityChange ?? 0) < 0).reduce((s, l) => s + getVal(l), 0);
    return { totalIn, totalOut: Math.abs(totalOut), net: totalIn + totalOut, valueIn, valueOut };
  }, [logs]);

  const activeFilterCount = [
    search, warehouseId !== 'all' && warehouseId,
    type !== 'all' && type, fromDate, toDate,
  ].filter(Boolean).length;

  // ── Time formatter: 2 lines ───────────────────────────────────────────────
  const fmtTime = (v) => {
    if (!v) return '—';
    const d = new Date(v);
    const date = d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const time = d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    return { date, time };
  };

  return (
    <div className={`${styles.pageContainer} product-workspace`} style={{ gap: 16 }}>

      {/* ── Page Header ── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 40, height: 40, borderRadius: 10, background: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <ClipboardList size={20} color="#2563eb" />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#0f172a' }}>Nhật ký kho</h1>
            <p style={{ margin: 0, fontSize: 12.5, color: '#64748b' }}>Lịch sử thay đổi tồn kho theo từng sản phẩm</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <button
            onClick={() => setShowFilters((v) => !v)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '7px 14px', borderRadius: 8, border: `1px solid ${activeFilterCount > 0 ? '#2563eb' : '#e2e8f0'}`, background: activeFilterCount > 0 ? '#eff6ff' : '#fff', fontSize: 13, fontWeight: 600, color: activeFilterCount > 0 ? '#2563eb' : '#475569', cursor: 'pointer' }}
          >
            <SlidersHorizontal size={14} />
            Bộ lọc {activeFilterCount > 0 && <span style={{ background: '#2563eb', color: '#fff', borderRadius: '50%', width: 18, height: 18, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700 }}>{activeFilterCount}</span>}
          </button>
          <button
            onClick={fetchLogs}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', background: '#fff', fontSize: 13, color: '#475569', cursor: 'pointer' }}
          >
            <RefreshCw size={14} />
          </button>
        </div>
      </div>

      {/* ── Stats Cards ── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
        {[
          { label: 'Tổng giao dịch', value: totalElements, sub: `Trong khoảng đã lọc`, icon: ClipboardList, color: '#2563eb', bg: '#eff6ff', valueStr: totalElements.toLocaleString('vi-VN') },
          { label: 'Tổng nhập', value: stats.totalIn, sub: fmtMoney(stats.valueIn), icon: TrendingUp, color: '#16a34a', bg: '#dcfce7', valueStr: `+${stats.totalIn.toLocaleString('vi-VN')}`, positive: true },
          { label: 'Tổng xuất', value: stats.totalOut, sub: fmtMoney(stats.valueOut), icon: TrendingDown, color: '#dc2626', bg: '#fee2e2', valueStr: `-${stats.totalOut.toLocaleString('vi-VN')}`, negative: true },
          { label: 'Biến động ròng', value: stats.net, sub: fmtMoney(Math.abs(stats.valueIn - stats.valueOut)), icon: ArrowRightLeft, color: stats.net >= 0 ? '#7c3aed' : '#dc2626', bg: stats.net >= 0 ? '#f5f3ff' : '#fee2e2', valueStr: `${stats.net >= 0 ? '+' : ''}${stats.net.toLocaleString('vi-VN')}` },
        ].map(({ label, sub, icon: Icon, color, bg, valueStr }) => (
          <div key={label} style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12, padding: '16px 18px', display: 'flex', alignItems: 'center', gap: 14, boxShadow: '0 1px 4px rgba(0,0,0,.04)' }}>
            <div style={{ width: 44, height: 44, borderRadius: 12, background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, color }}>
              <Icon size={20} />
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 11.5, color: '#64748b', fontWeight: 600, marginBottom: 2 }}>{label}</div>
              <div style={{ fontSize: 20, fontWeight: 800, color, lineHeight: 1.1 }}>{valueStr}</div>
              <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{sub}</div>
            </div>
          </div>
        ))}
      </div>

      {/* ── Filter Bar (collapsible) ── */}
      {showFilters && (
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12, padding: '14px 16px', boxShadow: '0 1px 4px rgba(0,0,0,.04)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            {/* Search */}
            <div style={{ position: 'relative', flex: '1 1 200px', minWidth: 160 }}>
              <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94a3b8', pointerEvents: 'none' }} />
              <input
                value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
                placeholder="Tìm theo mã SP, tên SP, mã phiếu..."
                style={{ width: '100%', boxSizing: 'border-box', padding: '7px 10px 7px 28px', borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12.5, color: '#0f172a', outline: 'none' }}
              />
            </div>

            {/* Type */}
            <select value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}
              style={{ padding: '7px 28px 7px 10px', borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12.5, color: '#0f172a', outline: 'none', cursor: 'pointer', minWidth: 140 }}>
              {TYPE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>

            {/* Warehouse */}
            <select value={warehouseId} onChange={(e) => { setWarehouseId(e.target.value); setPage(0); }}
              style={{ padding: '7px 28px 7px 10px', borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12.5, color: '#0f172a', outline: 'none', cursor: 'pointer', minWidth: 140 }}>
              <option value="all">Tất cả kho</option>
              {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>

            {/* Date range */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <Calendar size={13} style={{ color: '#94a3b8', flexShrink: 0 }} />
              <input type="date" value={fromDate} onChange={(e) => { setFromDate(e.target.value); setPage(0); }}
                style={{ padding: '7px 8px', borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12.5, outline: 'none', width: 130 }} />
              <span style={{ color: '#94a3b8', fontSize: 12 }}>—</span>
              <input type="date" value={toDate} onChange={(e) => { setToDate(e.target.value); setPage(0); }}
                style={{ padding: '7px 8px', borderRadius: 7, border: '1px solid #e2e8f0', fontSize: 12.5, outline: 'none', width: 130 }} />
            </div>

            {/* Reset */}
            <button onClick={reset}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '7px 12px', borderRadius: 7, border: '1px solid #e2e8f0', background: '#f8fafc', fontSize: 12.5, color: '#64748b', cursor: 'pointer', whiteSpace: 'nowrap' }}>
              <RotateCcw size={12} /> Xóa bộ lọc
            </button>
          </div>
        </div>
      )}

      {/* ── Table Card ── */}
      <div className={styles.logsCard}>
        {/* Table header */}
        <div className={styles.cardHeader}>
          <div className={styles.headerTitle}>
            <ClipboardList size={16} className={styles.headerIcon} />
            <span>{totalElements.toLocaleString('vi-VN')} giao dịch</span>
          </div>
          <span style={{ fontSize: 12.5, color: '#94a3b8' }}>
            {loading ? 'Đang tải...' : `Trang ${page + 1} / ${totalPages}`}
          </span>
        </div>

        {loading ? (
          <div className={styles.loadingContainer}>
            <Loader2 className={styles.spinner} size={28} />
            <p>Đang tải nhật ký kho...</p>
          </div>
        ) : logs.length === 0 ? (
          <div className={styles.emptyContainer}>
            <ClipboardList size={40} className={styles.emptyIcon} />
            <p>Không có giao dịch nào phù hợp với bộ lọc.</p>
          </div>
        ) : (
          <>
            <div className={styles.tableWrapper}>
              <table className={styles.table} style={{ tableLayout: 'fixed', width: '100%' }}>
                <thead>
                  <tr>
                    <th style={{ width: 90, minWidth: 90 }}>Thời gian</th>
                    <th style={{ width: 110, minWidth: 110 }}>Mã SP</th>
                    <th style={{ minWidth: 180 }}>Tên sản phẩm</th>
                    <th style={{ width: 110, minWidth: 110 }}>Loại</th>
                    <th style={{ width: 130, minWidth: 130 }}>Mã phiếu</th>
                    <th style={{ width: 75, minWidth: 75, textAlign: 'right' }}>Số lượng</th>
                    <th style={{ width: 130, minWidth: 130, textAlign: 'right' }}>Giá trị</th>
                    <th style={{ width: 70, minWidth: 70, textAlign: 'right' }}>Tồn sau</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => {
                    const cfg = TXN_TYPE_CONFIG[log.type] ?? { label: log.type, color: '#64748b', bg: '#f1f5f9', icon: ArrowRightLeft };
                    const Icon = cfg.icon;
                    const isPos = (log.quantityChange ?? 0) > 0;
                    const time = fmtTime(log.performedAt);
                    return (
                      <tr key={log.id} className={styles.row}>
                        {/* Thời gian */}
                        <td>
                          <div style={{ fontSize: 12, fontWeight: 600, color: '#0f172a' }}>{time.date}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>{time.time}</div>
                        </td>
                        {/* Mã SP */}
                        <td>
                          <span style={{ fontFamily: 'monospace', fontSize: 11.5, fontWeight: 700, color: '#4f46e5', background: '#eef2ff', padding: '2px 6px', borderRadius: 5 }}>
                            {log.variantSku || '—'}
                          </span>
                          {log.warehouseName && (
                            <div style={{ fontSize: 10.5, color: '#94a3b8', marginTop: 2 }}>{log.warehouseName}</div>
                          )}
                        </td>
                        {/* Tên sản phẩm */}
                        <td style={{ maxWidth: 220 }}>
                          <div
                            title={log.variantName || ''}
                            style={{ fontWeight: 600, fontSize: 13, color: '#0f172a', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 210 }}
                          >
                            {log.variantName || '—'}
                          </div>
                          <div
                            title={buildDesc(log)}
                            style={{ fontSize: 11, color: '#94a3b8', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 210 }}
                          >
                            {buildDesc(log)}
                          </div>
                        </td>
                        {/* Loại */}
                        <td>
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '3px 8px', borderRadius: 20, fontSize: 11.5, fontWeight: 700, color: cfg.color, background: cfg.bg, border: `1px solid ${cfg.color}28` }}>
                            <Icon size={11} />
                            {cfg.label}
                          </span>
                        </td>
                        {/* Mã phiếu */}
                        <td>
                          <span style={{ fontFamily: 'monospace', fontSize: 11, fontWeight: 600, color: '#2563eb', background: '#eff6ff', padding: '2px 6px', borderRadius: 5 }}>
                            {buildRefCode(log)}
                          </span>
                        </td>
                        {/* Số lượng */}
                        <td style={{ textAlign: 'right', width: 75 }}>
                          <span style={{ fontWeight: 800, fontSize: 14, color: isPos ? '#16a34a' : '#dc2626' }}>
                            {isPos ? `+${log.quantityChange}` : log.quantityChange}
                          </span>
                        </td>
                        {/* Giá trị = |quantityChange| × unitCost */}
                        <td style={{ textAlign: 'right', width: 130 }}>
                          {(() => {
                            // Prefer backend-computed transactionValue, fallback to frontend calc
                            const val = log.transactionValue != null
                              ? Number(log.transactionValue)
                              : (() => {
                                  const cost = log.unitCost != null ? Number(log.unitCost)
                                    : log.avgCostAfter != null ? Number(log.avgCostAfter) : 0;
                                  return cost * Math.abs(log.quantityChange ?? 0);
                                })();
                            if (!val || val === 0) return <span style={{ color: '#94a3b8', fontSize: 12 }}>—</span>;
                            return (
                              <span style={{ fontSize: 12.5, fontWeight: 600, color: isPos ? '#16a34a' : '#dc2626' }}>
                                {(isPos ? '+' : '-') + fmtMoney(val)}
                              </span>
                            );
                          })()}
                        </td>
                        {/* Tồn sau */}
                        <td style={{ textAlign: 'right', width: 70 }}>
                          <span style={{ fontWeight: 700, fontSize: 13, color: (log.quantityAfter ?? 0) < 0 ? '#dc2626' : (log.quantityAfter ?? 0) === 0 ? '#f59e0b' : '#0f172a' }}>
                            {log.quantityAfter ?? '—'}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <Pagination
              currentPage={page}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={PAGE_SIZE}
              currentCount={logs.length}
              itemLabel="giao dịch"
              onPageChange={(p) => setPage(p)}
            />
          </>
        )}
      </div>
    </div>
  );
}
