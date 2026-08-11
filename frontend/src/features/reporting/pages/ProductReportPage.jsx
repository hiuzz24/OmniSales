import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowDownToLine, ArrowUpRight, Box, CalendarDays, CircleDollarSign,
  ChevronLeft, ChevronRight, RefreshCw, Search, ShoppingBag, TrendingUp,
} from 'lucide-react';
import {
  Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import ReportTabs from '../components/ReportTabs';
import reportService from '../services/reportService';
import styles from './ProductReportPage.module.css';

const toLocalDate = (date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
};
const initialDateRange = () => {
  const to = new Date(); const from = new Date(); from.setDate(from.getDate() - 29);
  return { from: toLocalDate(from), to: toLocalDate(to) };
};
const number = (value) => Number(value || 0).toLocaleString('vi-VN');
const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value || 0));
const compactMoney = (value) => {
  const amount = Number(value || 0);
  if (amount >= 1_000_000_000) return `${(amount / 1_000_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}B`;
  if (amount >= 1_000_000) return `${(amount / 1_000_000).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}M`;
  return money(amount);
};
const escapeCsv = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
const growthText = (value) => `${Number(value || 0) >= 0 ? '+' : ''}${Number(value || 0).toLocaleString('vi-VN')}%`;
const PAGE_SIZE = 10;

const MetricCard = ({ label, value, helper, icon: Icon, tone, textValue = false }) => (
  <article className={styles.metricCard}>
    <div className={styles.metricTop}><span>{label}</span><i className={styles[tone]}><Icon size={18} /></i></div>
    <strong className={textValue ? styles.textMetric : ''}>{value}</strong>
    <small>{helper}</small>
  </article>
);

const ProductReportPage = () => {
  const [range, setRange] = useState(initialDateRange);
  const [appliedRange, setAppliedRange] = useState(initialDateRange);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState({ key: 'unitsSold', direction: 'desc' });
  const [page, setPage] = useState(0);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setReport(await reportService.getProducts(appliedRange)); }
    catch (requestError) { setError(requestError.response?.data?.message || 'Không thể tải báo cáo sản phẩm.'); }
    finally { setLoading(false); }
  }, [appliedRange]);
  useEffect(() => { const id = window.setTimeout(load, 0); return () => window.clearTimeout(id); }, [load]);

  const products = useMemo(() => {
    const keyword = query.trim().toLocaleLowerCase('vi-VN');
    const rows = (report?.products || []).filter((item) => !keyword
      || item.sku?.toLocaleLowerCase('vi-VN').includes(keyword)
      || item.productName?.toLocaleLowerCase('vi-VN').includes(keyword)
      || item.variantName?.toLocaleLowerCase('vi-VN').includes(keyword));
    return [...rows].sort((a, b) => {
      const left = sort.key === 'revenue' ? Number(a.revenue) : sort.key === 'returnedUnits' ? Number(a.returnedUnits) : Number(a.unitsSold);
      const right = sort.key === 'revenue' ? Number(b.revenue) : sort.key === 'returnedUnits' ? Number(b.returnedUnits) : Number(b.unitsSold);
      return sort.direction === 'desc' ? right - left : left - right;
    });
  }, [query, report, sort]);
  const chartData = useMemo(() => (report?.products || []).filter((item) => Number(item.unitsSold) > 0).slice(0, 8).map((item) => ({
    sku: item.sku || item.productName, sold: Number(item.unitsSold || 0), returned: Number(item.returnedUnits || 0), name: item.productName,
  })), [report]);
  const totalPages = Math.max(1, Math.ceil(products.length / PAGE_SIZE));
  const pagedProducts = useMemo(() => products.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE), [page, products]);
  const visiblePages = useMemo(() => {
    const first = Math.max(0, Math.min(page - 2, totalPages - 5));
    return Array.from({ length: Math.min(5, totalPages) }, (_, index) => first + index);
  }, [page, totalPages]);
  const changeSort = (key) => {
    setSort((old) => ({ key, direction: old.key === key && old.direction === 'desc' ? 'asc' : 'desc' }));
    setPage(0);
  };
  const applyFilter = (event) => {
    event.preventDefault();
    if (range.from > range.to) { setError('Ngày bắt đầu không được sau ngày kết thúc.'); return; }
    setPage(0);
    setAppliedRange({ ...range });
  };
  const exportCsv = () => {
    if (!products.length) return;
    const rows = [['SKU', 'Tên sản phẩm', 'Biến thể', 'Đã bán', 'Trả hàng', 'Tỷ lệ trả', 'Doanh thu'], ...products.map((item) => [item.sku, item.productName, item.variantName, item.unitsSold, item.returnedUnits, `${item.returnRate}%`, item.revenue])];
    const csv = `\uFEFF${rows.map((row) => row.map(escapeCsv).join(',')).join('\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a');
    link.href = url; link.download = `bao-cao-san-pham-${appliedRange.from}-${appliedRange.to}.csv`; link.click(); URL.revokeObjectURL(url);
  };

  const unitsGrowth = Number(report?.unitsGrowthRate || 0);
  const revenueGrowth = Number(report?.revenueGrowthRate || 0);
  const topGrowth = report?.topGrowthProduct;

  return (
    <div className={styles.page}>
      <div className={styles.headingRow}>
        <div><p className={styles.eyebrow}>BÁO CÁO</p><h1>Báo cáo sản phẩm</h1><p className={styles.subtitle}>Phân tích sản lượng bán, trả hàng và doanh thu theo từng SKU.</p></div>
        <button className={styles.exportButton} type="button" onClick={exportCsv} disabled={!products.length}><ArrowDownToLine size={17} /> Xuất báo cáo</button>
      </div>
      <ReportTabs />

      <form className={styles.filterBar} onSubmit={applyFilter}>
        <div className={styles.filterTitle}><CalendarDays size={18} /><span>Khoảng thời gian</span></div>
        <label><span>Từ ngày</span><input type="date" value={range.from} max={range.to} onChange={(e) => setRange((old) => ({ ...old, from: e.target.value }))} /></label>
        <label><span>Đến ngày</span><input type="date" value={range.to} min={range.from} onChange={(e) => setRange((old) => ({ ...old, to: e.target.value }))} /></label>
        <button type="submit" disabled={loading}>{loading ? <RefreshCw className={styles.spin} size={16} /> : <TrendingUp size={16} />} Xem báo cáo</button>
      </form>
      {error && <div className={styles.error} role="alert">{error}</div>}

      <section className={styles.metricGrid}>
        <MetricCard label="Sản phẩm đang hoạt động" value={loading ? '—' : number(report?.activeProducts)} helper="Trên tất cả kênh" icon={Box} tone="slate" />
        <MetricCard label="Tổng sản phẩm đã bán" value={loading ? '—' : number(report?.totalUnitsSold)} helper={`${unitsGrowth >= 0 ? '↗' : '↘'} ${growthText(unitsGrowth)} so với kỳ trước`} icon={TrendingUp} tone="violet" />
        <MetricCard label="Tổng doanh thu sản phẩm" value={loading ? '—' : compactMoney(report?.totalRevenue)} helper={`${revenueGrowth >= 0 ? '↗' : '↘'} ${growthText(revenueGrowth)} so với kỳ trước`} icon={CircleDollarSign} tone="green" />
        <MetricCard label="Sản phẩm tăng trưởng nhất" value={loading ? '—' : topGrowth?.productName || 'Chưa có dữ liệu'} helper={topGrowth ? `${growthText(topGrowth.growthRate)} trong kỳ` : 'Cần dữ liệu từ hai kỳ'} icon={ArrowUpRight} tone="blue" textValue />
      </section>

      <section className={styles.chartPanel}>
        <header><div><h2>Top 8 sản phẩm — Đã bán & Trả hàng</h2><p>Xếp hạng theo số lượng bán trong khoảng thời gian đã chọn</p></div><ShoppingBag size={19} /></header>
        <div className={styles.chartBox}>
          {loading ? <div className={styles.skeletonBlock} /> : chartData.length ? (
            <ResponsiveContainer width="100%" height="100%"><BarChart data={chartData} margin={{ top: 12, right: 12, left: -10, bottom: 0 }} barGap={3}>
              <CartesianGrid strokeDasharray="3 3" vertical stroke="#e8eef6" /><XAxis dataKey="sku" tick={{ fontSize: 11, fill: '#7c8ba1' }} axisLine={false} tickLine={false} /><YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#7c8ba1' }} axisLine={false} tickLine={false} /><Tooltip formatter={(value, key) => [`${number(value)} sản phẩm`, key === 'sold' ? 'Đã bán' : 'Trả hàng']} labelFormatter={(sku) => chartData.find((item) => item.sku === sku)?.name || sku} /><Legend formatter={(value) => value === 'sold' ? 'Đã bán' : 'Trả hàng'} /><Bar dataKey="sold" fill="#7c3aed" radius={[5,5,0,0]} maxBarSize={42} /><Bar dataKey="returned" fill="#fb7185" radius={[5,5,0,0]} maxBarSize={42} />
            </BarChart></ResponsiveContainer>
          ) : <div className={styles.empty}>Chưa có sản phẩm bán trong khoảng thời gian này.</div>}
        </div>
      </section>

      <section className={styles.tablePanel}>
        <div className={styles.tableHeader}><div><h2>Danh sách sản phẩm</h2><p>{number(products.length)} SKU trong báo cáo</p></div><label className={styles.search}><Search size={16} /><input value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} placeholder="Tìm SKU, tên sản phẩm..." aria-label="Tìm sản phẩm" /></label></div>
        <div className={styles.tableWrap}><table><thead><tr><th>#</th><th>SKU</th><th>Tên sản phẩm</th><th><button type="button" onClick={() => changeSort('unitsSold')}>Đã bán</button></th><th><button type="button" onClick={() => changeSort('returnedUnits')}>Trả hàng</button></th><th><button type="button" onClick={() => changeSort('revenue')}>Doanh thu</button></th></tr></thead>
          <tbody>
            {loading ? [1,2,3,4].map((item) => <tr key={item}><td colSpan="6"><div className={styles.skeletonRow} /></td></tr>) : pagedProducts.map((item, index) => <tr key={`${item.variantId || item.sku}-${index}`}><td className={styles.rank}>{page * PAGE_SIZE + index + 1}</td><td><span className={styles.sku}>{item.sku || '-'}</span></td><td><strong className={styles.productName}>{item.productName}</strong>{item.variantName && item.variantName !== item.productName && <small>{item.variantName}</small>}</td><td><strong>{number(item.unitsSold)}</strong></td><td><span className={Number(item.returnedUnits) ? styles.returned : ''}>{number(item.returnedUnits)}</span><i className={styles.rate}>{Number(item.returnRate || 0).toLocaleString('vi-VN')}%</i></td><td className={styles.revenue}>{money(item.revenue)}</td></tr>)}
            {!loading && !products.length && <tr><td colSpan="6"><div className={styles.empty}>Không tìm thấy sản phẩm phù hợp.</div></td></tr>}
          </tbody>
        </table></div>
        {!loading && products.length > 0 && (
          <div className={styles.pagination}>
            <p>Hiển thị <strong>{page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, products.length)}</strong> trong {number(products.length)} sản phẩm</p>
            <nav aria-label="Phân trang danh sách sản phẩm">
              <button type="button" onClick={() => setPage((old) => Math.max(0, old - 1))} disabled={page === 0} aria-label="Trang trước"><ChevronLeft size={17} /></button>
              {visiblePages.map((pageIndex) => <button className={pageIndex === page ? styles.activePage : ''} type="button" key={pageIndex} onClick={() => setPage(pageIndex)} aria-current={pageIndex === page ? 'page' : undefined}>{pageIndex + 1}</button>)}
              <button type="button" onClick={() => setPage((old) => Math.min(totalPages - 1, old + 1))} disabled={page === totalPages - 1} aria-label="Trang sau"><ChevronRight size={17} /></button>
            </nav>
          </div>
        )}
      </section>
    </div>
  );
};

export default ProductReportPage;
