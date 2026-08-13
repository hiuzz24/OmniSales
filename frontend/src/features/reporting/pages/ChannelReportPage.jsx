import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowDownToLine, CalendarDays, CheckCircle2, CircleDollarSign,
  PackageCheck, RefreshCw, ShoppingCart, Store, TrendingUp,
} from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import reportService from '../services/reportService';
import ReportTabs from '../components/ReportTabs';
import styles from './ChannelReportPage.module.css';

const CHART_COLORS = ['#2563eb', '#7c3aed', '#f97316', '#0d9488', '#e11d48'];
const PLATFORM_LABELS = {
  SHOPEE: 'Shopee', TIKTOK: 'TikTok Shop', LAZADA: 'Lazada',
  SHOPIFY: 'Shopify', MANUAL: 'Bán thủ công',
};
const STATUS_LABELS = {
  PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PROCESSING: 'Đang xử lý',
  SHIPPED: 'Đã gửi hàng', IN_TRANSIT: 'Đang vận chuyển', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy',
};

const toLocalDate = (date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
};

const initialDateRange = () => {
  const to = new Date();
  const from = new Date();
  from.setDate(from.getDate() - 29);
  return { from: toLocalDate(from), to: toLocalDate(to) };
};

const formatCurrency = (value) => new Intl.NumberFormat('vi-VN', {
  style: 'currency', currency: 'VND', maximumFractionDigits: 0,
}).format(Number(value || 0));
const formatNumber = (value) => Number(value || 0).toLocaleString('vi-VN');
const escapeCsv = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;

const SummaryCard = ({ label, value, helper, icon: Icon, tone }) => (
  <article className={styles.summaryCard}>
    <div className={`${styles.summaryIcon} ${styles[tone]}`}><Icon size={20} /></div>
    <div><p>{label}</p><strong>{value}</strong><span>{helper}</span></div>
  </article>
);

const ChannelReportPage = () => {
  const [range, setRange] = useState(initialDateRange);
  const [appliedRange, setAppliedRange] = useState(initialDateRange);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadReport = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setReport(await reportService.getOrdersByChannel(appliedRange));
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Không thể tải dữ liệu báo cáo. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, [appliedRange]);

  useEffect(() => {
    // Fetching the remote report is the external synchronization owned by this effect.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadReport();
  }, [loadReport]);

  const chartData = useMemo(() => (report?.channels || []).map((channel) => ({
    name: channel.channelName || PLATFORM_LABELS[channel.platform] || channel.platform,
    value: Number(channel.orderCount || 0),
    share: Number(channel.orderShare || 0),
  })), [report]);

  const exportCsv = () => {
    if (!report?.channels?.length) return;
    const header = ['Kênh bán', 'Gian hàng', 'Số đơn', 'Doanh thu', 'Giá trị TB', 'Đã giao', 'Đã hủy', 'Tỷ trọng'];
    const rows = report.channels.map((channel) => [
      PLATFORM_LABELS[channel.platform] || channel.platform, channel.channelName, channel.orderCount,
      channel.revenue, channel.averageOrderValue, channel.deliveredCount, channel.cancelledCount, `${channel.orderShare}%`,
    ]);
    const csv = `\uFEFF${[header, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\n')}`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `bao-cao-don-hang-theo-kenh-${appliedRange.from}-${appliedRange.to}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const submitFilter = (event) => {
    event.preventDefault();
    if (range.from > range.to) {
      setError('Ngày bắt đầu không được sau ngày kết thúc.');
      return;
    }
    setAppliedRange({ ...range });
  };

  const totalStatuses = report?.statuses?.reduce((sum, item) => sum + Number(item.orderCount || 0), 0) || 0;

  return (
    <div className={styles.page}>
      <div className={styles.headingRow}>
        <div>
          <p className={styles.eyebrow}>BÁO CÁO</p>
          <h1>Hiệu quả đơn hàng theo kênh</h1>
          <p className={styles.subtitle}>Theo dõi số lượng đơn, doanh thu và tỷ lệ hoàn tất trên từng kênh bán hàng.</p>
        </div>
        <button className={styles.exportButton} type="button" onClick={exportCsv} disabled={!report?.channels?.length}>
          <ArrowDownToLine size={17} /> Xuất báo cáo
        </button>
      </div>

      <ReportTabs />

      <form className={styles.filterBar} onSubmit={submitFilter}>
        <div className={styles.filterTitle}><CalendarDays size={18} /><span>Khoảng thời gian</span></div>
        <label><span>Từ ngày</span><input type="date" value={range.from} max={range.to} onChange={(e) => setRange((old) => ({ ...old, from: e.target.value }))} /></label>
        <label><span>Đến ngày</span><input type="date" value={range.to} min={range.from} onChange={(e) => setRange((old) => ({ ...old, to: e.target.value }))} /></label>
        <button type="submit" disabled={loading}>{loading ? <RefreshCw className={styles.spin} size={16} /> : <TrendingUp size={16} />} Xem báo cáo</button>
      </form>

      {error && <div className={styles.error} role="alert">{error}</div>}

      <section className={styles.summaryGrid} aria-label="Tổng quan báo cáo">
        <SummaryCard label="Tổng đơn hàng" value={loading ? '—' : formatNumber(report?.totalOrders)} helper="Tất cả trạng thái trong kỳ" icon={ShoppingCart} tone="blue" />
        <SummaryCard label="Tổng doanh thu" value={loading ? '—' : formatCurrency(report?.totalRevenue)} helper="Không gồm đơn đã hủy" icon={CircleDollarSign} tone="green" />
        <SummaryCard label="Giá trị trung bình" value={loading ? '—' : formatCurrency(report?.averageOrderValue)} helper="Trên mỗi đơn hợp lệ" icon={PackageCheck} tone="violet" />
        <SummaryCard label="Tỷ lệ đã giao" value={loading ? '—' : `${Number(report?.deliveredRate || 0).toLocaleString('vi-VN')}%`} helper={`${formatNumber(report?.deliveredOrders)} đơn hoàn tất`} icon={CheckCircle2} tone="orange" />
      </section>

      <section className={styles.insightGrid}>
        <article className={styles.panel}>
          <div className={styles.panelHeader}><div><h2>Phân bổ đơn hàng theo kênh</h2><p>Tỷ trọng số đơn của từng kênh trong kỳ</p></div><Store size={20} /></div>
          <div className={styles.chartContent}>
            <div className={styles.chartBox}>
              {loading ? <div className={styles.skeletonCircle} /> : chartData.length ? (
                <ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={chartData} dataKey="value" nameKey="name" innerRadius={64} outerRadius={92} paddingAngle={3} stroke="none">{chartData.map((item, index) => <Cell key={item.name} fill={CHART_COLORS[index % CHART_COLORS.length]} />)}</Pie><Tooltip formatter={(value) => [`${formatNumber(value)} đơn`, 'Số lượng']} /></PieChart></ResponsiveContainer>
              ) : <div className={styles.empty}>Chưa có đơn hàng trong khoảng thời gian này.</div>}
              {!loading && chartData.length > 0 && <div className={styles.chartTotal}><strong>{formatNumber(report?.totalOrders)}</strong><span>đơn hàng</span></div>}
            </div>
            <div className={styles.legend}>{chartData.map((item, index) => <div key={item.name}><span className={styles.dot} style={{ background: CHART_COLORS[index % CHART_COLORS.length] }} /><span>{item.name}</span><strong>{item.share.toLocaleString('vi-VN')}%</strong></div>)}</div>
          </div>
        </article>

        <article className={styles.panel}>
          <div className={styles.panelHeader}><div><h2>Trạng thái đơn hàng</h2><p>Tiến độ xử lý đơn trong kỳ báo cáo</p></div><CheckCircle2 size={20} /></div>
          <div className={styles.statusList}>
            {loading ? [1, 2, 3, 4].map((item) => <div className={styles.skeletonRow} key={item} />) : (report?.statuses || []).map((item, index) => {
              const percent = totalStatuses ? (Number(item.orderCount) / totalStatuses) * 100 : 0;
              return <div className={styles.statusRow} key={item.status}><div><span>{STATUS_LABELS[item.status] || item.status}</span><strong>{formatNumber(item.orderCount)}</strong></div><div className={styles.progress}><span style={{ width: `${percent}%`, background: CHART_COLORS[index % CHART_COLORS.length] }} /></div></div>;
            })}
            {!loading && !report?.statuses?.length && <div className={styles.empty}>Chưa có dữ liệu trạng thái.</div>}
          </div>
        </article>
      </section>

      <section className={`${styles.panel} ${styles.tablePanel}`}>
        <div className={styles.panelHeader}><div><h2>Chi tiết hiệu quả từng kênh</h2><p>Dữ liệu được tổng hợp trực tiếp từ đơn hàng trong hệ thống</p></div><span className={styles.resultCount}>{formatNumber(report?.channels?.length)} kênh</span></div>
        <div className={styles.tableWrap}>
          <table>
            <thead><tr><th>Kênh bán</th><th>Gian hàng</th><th>Số đơn</th><th>Doanh thu</th><th>Giá trị TB</th><th>Đã giao</th><th>Đã hủy</th><th>Tỷ trọng</th></tr></thead>
            <tbody>
              {loading ? [1, 2, 3].map((item) => <tr key={item}><td colSpan="8"><div className={styles.skeletonRow} /></td></tr>) : (report?.channels || []).map((channel, index) => (
                <tr key={`${channel.channelId || channel.platform}-${channel.channelName}`}>
                  <td><span className={styles.channelBadge}><i style={{ background: CHART_COLORS[index % CHART_COLORS.length] }} />{PLATFORM_LABELS[channel.platform] || channel.platform}</span></td>
                  <td className={styles.shopName}>{channel.channelName || 'Kênh mặc định'}</td><td><strong>{formatNumber(channel.orderCount)}</strong></td>
                  <td className={styles.revenue}>{formatCurrency(channel.revenue)}</td><td>{formatCurrency(channel.averageOrderValue)}</td>
                  <td className={styles.delivered}>{formatNumber(channel.deliveredCount)}</td><td className={Number(channel.cancelledCount) ? styles.cancelled : ''}>{formatNumber(channel.cancelledCount)}</td>
                  <td><strong>{Number(channel.orderShare || 0).toLocaleString('vi-VN')}%</strong></td>
                </tr>
              ))}
              {!loading && !report?.channels?.length && <tr><td colSpan="8"><div className={styles.empty}>Chưa có dữ liệu kênh bán trong khoảng thời gian đã chọn.</div></td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};

export default ChannelReportPage;
