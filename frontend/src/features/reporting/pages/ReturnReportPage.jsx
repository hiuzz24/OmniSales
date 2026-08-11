import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowDownToLine, CalendarDays, CheckCircle2, ChevronDown,
  CircleDollarSign, RefreshCw, RotateCcw, Search, TrendingDown, Users, X,
} from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { ROUTES } from '../../../app/router/routes';
import ReportTabs from '../components/ReportTabs';
import reportService from '../services/reportService';
import styles from './ReturnReportPage.module.css';

const COLORS = ['#ef4444', '#f97316', '#f59e0b', '#8b5cf6', '#6366f1', '#0ea5e9', '#64748b'];
const PLATFORM_LABELS = { SHOPEE: 'Shopee', TIKTOK: 'TikTok Shop', LAZADA: 'Lazada', SHOPIFY: 'Shopify', MANUAL: 'Thủ công' };
const STATUS_LABELS = {
  PENDING_APPROVAL: 'Chờ duyệt', REJECTED: 'Từ chối', AWAITING_RETURN: 'Chờ khách gửi',
  RETURN_IN_TRANSIT: 'Đang hoàn về', INSPECTED: 'Đã kiểm hàng', PLATFORM_PROCESSING: 'Sàn xử lý',
  PENDING_STOCK: 'Chờ nhập kho', COMPLETED: 'Hoàn tất', FAILED: 'Lỗi xử lý',
};

const toLocalDate = (date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
};
const initialDateRange = () => {
  const to = new Date(); const from = new Date(); from.setDate(from.getDate() - 29);
  return { from: toLocalDate(from), to: toLocalDate(to) };
};
const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0));
const number = (value) => Number(value || 0).toLocaleString('vi-VN');
const date = (value) => value ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value)) : '-';
const initials = (name) => (name || 'K').trim().split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase();
const escapeCsv = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
const reasonLabel = (reason) => {
  const value = String(reason || '').toLowerCase();
  if (value.includes('defect') || value.includes('fault') || value.includes('damaged_product') || value.includes('lỗi')) return 'Sản phẩm lỗi';
  if (value.includes('wrong') || value.includes('incorrect') || value.includes('sai')) return 'Sai sản phẩm';
  if (value.includes('quality') || value.includes('chất lượng')) return 'Chất lượng kém';
  if (value.includes('description') || value.includes('not_as') || value.includes('mô tả')) return 'Không đúng mô tả';
  if (value.includes('mind') || value.includes('no_longer') || value.includes('đổi ý')) return 'Khách đổi ý';
  if (value.includes('transit') || value.includes('shipping') || value.includes('giao hàng')) return 'Giao hàng hư hỏng';
  if (!value || value.includes('không xác định')) return 'Không xác định';
  return reason;
};

const MetricCard = ({ label, value, helper, icon: Icon, tone }) => (
  <article className={styles.metricCard}>
    <div className={`${styles.metricIcon} ${styles[tone]}`}><Icon size={20} /></div>
    <div><p>{label}</p><strong>{value}</strong><span>{helper}</span></div>
  </article>
);

const ReturnDetailModal = ({ customer, onClose, onOpenOrder }) => {
  useEffect(() => {
    const handleKey = (event) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  return (
    <div className={styles.modalBackdrop} role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="return-customer-title">
        <header className={styles.modalHeader}>
          <div className={styles.customerIdentity}>
            <span className={styles.largeAvatar}>{initials(customer.customerName)}</span>
            <div><h2 id="return-customer-title">{customer.customerName}</h2><p>{number(customer.returnCount)} phiếu trả hàng · {customer.customerPhone || 'Chưa có số điện thoại'}</p></div>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng"><X size={20} /></button>
        </header>
        <div className={styles.modalStats}>
          <div><span>Số phiếu trả</span><strong>{number(customer.returnCount)}</strong></div>
          <div><span>Tổng giá trị</span><strong>{money(customer.totalValue)}</strong></div>
          <div><span>Lần trả gần nhất</span><strong>{date(customer.lastReturnAt)}</strong></div>
        </div>
        <div className={styles.modalBody}>
          <h3>Danh sách phiếu trả hàng</h3>
          <div className={styles.returnCards}>
            {customer.returns.map((item) => (
              <article className={styles.returnCard} key={item.returnId}>
                <div className={styles.returnCardTop}>
                  <div><span>Mã trả hàng</span><strong>{item.externalReturnId || item.returnId}</strong></div>
                  <span className={`${styles.statusBadge} ${styles[item.status?.toLowerCase()]}`}>{STATUS_LABELS[item.status] || item.status}</span>
                </div>
                <dl className={styles.returnFacts}>
                  <div><dt>Đơn hàng</dt><dd><button type="button" onClick={() => onOpenOrder(item.orderId)}>{item.externalOrderId || '-'}</button></dd></div>
                  <div><dt>Kênh</dt><dd>{PLATFORM_LABELS[item.platform] || item.platform} · {item.channelName || '-'}</dd></div>
                  <div><dt>Lý do</dt><dd>{reasonLabel(item.reason)}</dd></div>
                  <div><dt>Giá trị</dt><dd className={styles.money}>{money(item.value)}</dd></div>
                  <div><dt>Ngày tạo</dt><dd>{date(item.createdAt)}</dd></div>
                </dl>
                <div className={styles.productList}>
                  {item.items.length ? item.items.map((product, index) => (
                    <div key={`${product.sku}-${index}`}><span>{product.name}<small>{product.sku || 'Không có SKU'}</small></span><strong>x{number(product.quantity)}</strong></div>
                  )) : <span className={styles.muted}>Chưa có dữ liệu sản phẩm.</span>}
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
};

const ReturnReportPage = () => {
  const navigate = useNavigate();
  const [range, setRange] = useState(initialDateRange);
  const [appliedRange, setAppliedRange] = useState(initialDateRange);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState({ key: 'returnCount', direction: 'desc' });
  const [selectedCustomer, setSelectedCustomer] = useState(null);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setReport(await reportService.getReturns(appliedRange)); }
    catch (requestError) { setError(requestError.response?.data?.message || 'Không thể tải báo cáo trả hàng.'); }
    finally { setLoading(false); }
  }, [appliedRange]);

  useEffect(() => { const id = window.setTimeout(load, 0); return () => window.clearTimeout(id); }, [load]);

  const reasons = useMemo(() => {
    const merged = new Map();
    (report?.reasons || []).forEach((item) => {
      const label = reasonLabel(item.reason); const old = merged.get(label) || 0; merged.set(label, old + Number(item.count || 0));
    });
    const total = [...merged.values()].reduce((sum, value) => sum + value, 0);
    return [...merged.entries()].map(([name, value]) => ({ name, value, percentage: total ? value * 100 / total : 0 })).sort((a, b) => b.value - a.value);
  }, [report]);

  const customers = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase('vi-VN');
    const rows = (report?.customers || []).filter((item) => !keyword || item.customerName?.toLocaleLowerCase('vi-VN').includes(keyword) || item.customerPhone?.includes(keyword));
    return [...rows].sort((a, b) => {
      const left = sort.key === 'totalValue' ? Number(a.totalValue) : Number(a.returnCount);
      const right = sort.key === 'totalValue' ? Number(b.totalValue) : Number(b.returnCount);
      return sort.direction === 'desc' ? right - left : left - right;
    });
  }, [query, report, sort]);

  const toggleSort = (key) => setSort((old) => ({ key, direction: old.key === key && old.direction === 'desc' ? 'asc' : 'desc' }));
  const applyFilter = (event) => { event.preventDefault(); if (range.from > range.to) { setError('Ngày bắt đầu không được sau ngày kết thúc.'); return; } setAppliedRange({ ...range }); };
  const exportCsv = () => {
    if (!customers.length) return;
    const rows = [['Khách hàng', 'Số đơn trả', 'Tổng giá trị', 'Kênh trả', 'Lần gần nhất'], ...customers.map((item) => [item.customerName, item.returnCount, item.totalValue, item.platforms.map((platform) => PLATFORM_LABELS[platform] || platform).join(' / '), date(item.lastReturnAt)])];
    const csv = `\uFEFF${rows.map((row) => row.map(escapeCsv).join(',')).join('\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a');
    link.href = url; link.download = `bao-cao-tra-hang-${appliedRange.from}-${appliedRange.to}.csv`; link.click(); URL.revokeObjectURL(url);
  };

  return (
    <div className={styles.page}>
      <div className={styles.headingRow}>
        <div><p className={styles.eyebrow}>BÁO CÁO</p><h1>Thống kê tỷ lệ trả hàng</h1><p className={styles.subtitle}>Theo dõi nguyên nhân, giá trị và khách hàng phát sinh yêu cầu trả hàng.</p></div>
        <button className={styles.exportButton} type="button" onClick={exportCsv} disabled={!customers.length}><ArrowDownToLine size={17} /> Xuất báo cáo</button>
      </div>
      <ReportTabs />

      <form className={styles.filterBar} onSubmit={applyFilter}>
        <div className={styles.filterTitle}><CalendarDays size={18} /><span>Khoảng thời gian</span></div>
        <label><span>Từ ngày</span><input type="date" value={range.from} max={range.to} onChange={(e) => setRange((old) => ({ ...old, from: e.target.value }))} /></label>
        <label><span>Đến ngày</span><input type="date" value={range.to} min={range.from} onChange={(e) => setRange((old) => ({ ...old, to: e.target.value }))} /></label>
        <button type="submit" disabled={loading}>{loading ? <RefreshCw className={styles.spin} size={16} /> : <RotateCcw size={16} />} Xem báo cáo</button>
      </form>
      {error && <div className={styles.error} role="alert">{error}</div>}

      <section className={styles.metricGrid}>
        <MetricCard label="Tổng yêu cầu trả" value={loading ? '—' : number(report?.totalRequests)} helper="Trong khoảng thời gian đã chọn" icon={RotateCcw} tone="slate" />
        <MetricCard label="Tỷ lệ trả hàng" value={loading ? '—' : `${Number(report?.returnRate || 0).toLocaleString('vi-VN')}%`} helper="Trên tổng đơn giao thành công" icon={TrendingDown} tone="red" />
        <MetricCard label="Tổng giá trị trả" value={loading ? '—' : money(report?.totalReturnValue)} helper="Theo số lượng được duyệt" icon={CircleDollarSign} tone="orange" />
        <MetricCard label="Đã chấp thuận" value={loading ? '—' : number(report?.approvedRequests)} helper="Đã chuyển sang bước xử lý" icon={CheckCircle2} tone="green" />
      </section>

      <section className={styles.reasonPanel}>
        <div className={styles.panelHeader}><div><h2>Phân bổ lý do trả hàng</h2><p>Nhóm theo lý do ghi nhận từ các sàn bán hàng</p></div><RotateCcw size={19} /></div>
        <div className={styles.reasonContent}>
          <div className={styles.chartBox}>
            {loading ? <div className={styles.skeletonCircle} /> : reasons.length ? <ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={reasons} dataKey="value" nameKey="name" innerRadius={60} outerRadius={88} paddingAngle={3} stroke="none">{reasons.map((item, index) => <Cell key={item.name} fill={COLORS[index % COLORS.length]} />)}</Pie><Tooltip formatter={(value) => [`${number(value)} yêu cầu`, 'Số lượng']} /></PieChart></ResponsiveContainer> : <div className={styles.empty}>Chưa có dữ liệu lý do trả hàng.</div>}
            {!loading && reasons.length > 0 && <div className={styles.chartTotal}><strong>{number(report?.totalRequests)}</strong><span>yêu cầu</span></div>}
          </div>
          <div className={styles.reasonLegend}>{reasons.map((item, index) => <div key={item.name}><i style={{ background: COLORS[index % COLORS.length] }} /><span>{item.name}</span><strong>{item.percentage.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%</strong><small>{number(item.value)}</small></div>)}</div>
        </div>
      </section>

      <section className={styles.tablePanel}>
        <div className={styles.tableHeader}>
          <div><h2><Users size={18} /> Trả hàng theo khách hàng <span>{number(report?.customers?.length)} KH</span></h2><p>Nhấn vào tên khách hàng để xem toàn bộ phiếu trả.</p></div>
          <label className={styles.search}><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm tên hoặc số điện thoại..." aria-label="Tìm khách hàng" /></label>
        </div>
        <div className={styles.tableWrap}>
          <table><thead><tr><th>Khách hàng</th><th><button type="button" onClick={() => toggleSort('returnCount')}>Số đơn trả <ChevronDown size={13} /></button></th><th><button type="button" onClick={() => toggleSort('totalValue')}>Tổng giá trị <ChevronDown size={13} /></button></th><th>Kênh trả</th><th>Lần gần nhất</th></tr></thead>
            <tbody>
              {loading ? [1,2,3,4].map((item) => <tr key={item}><td colSpan="5"><div className={styles.skeletonRow} /></td></tr>) : customers.map((customer) => (
                <tr key={customer.customerKey}>
                  <td><button className={styles.customerButton} type="button" onClick={() => setSelectedCustomer(customer)}><span className={styles.avatar}>{initials(customer.customerName)}</span><span><strong>{customer.customerName}</strong><small>{customer.customerPhone || 'Chưa có SĐT'}</small></span></button></td>
                  <td><strong>{number(customer.returnCount)}</strong>{customer.returnCount >= 2 && <span className={styles.frequent}>Nhiều lần</span>}</td>
                  <td className={styles.money}>{money(customer.totalValue)}</td>
                  <td><div className={styles.platforms}>{customer.platforms.map((platform) => <span key={platform} className={styles[platform.toLowerCase()]}>{PLATFORM_LABELS[platform] || platform}</span>)}</div></td>
                  <td>{date(customer.lastReturnAt)}</td>
                </tr>
              ))}
              {!loading && !customers.length && <tr><td colSpan="5"><div className={styles.empty}>Không tìm thấy khách hàng phù hợp.</div></td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      {selectedCustomer && <ReturnDetailModal customer={selectedCustomer} onClose={() => setSelectedCustomer(null)} onOpenOrder={(orderId) => navigate(ROUTES.ORDER_DETAIL.replace(':id', orderId))} />}
    </div>
  );
};

export default ReturnReportPage;
