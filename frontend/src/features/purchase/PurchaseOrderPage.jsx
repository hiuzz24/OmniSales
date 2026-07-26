import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock3, PackageCheck, Plus, Search, ShoppingBag, Truck } from 'lucide-react';
import purchaseOrderApi from '../../api/purchaseOrderApi';
import { ROUTES } from '../../app/router/routes';
import { ROLES } from '../auth/constants/roles';
import useAuth from '../auth/hooks/useAuth';
import styles from './PurchaseOrderPage.module.css';

const STATUS = {
  DRAFT: { label: 'Nháp', className: styles.draft },
  SENT_TO_SUPPLIER: { label: 'Đã gửi NCC', className: styles.sent },
  RECEIVING: { label: 'Đang giao hàng', className: styles.receiving },
  COMPLETED: { label: 'Hoàn thành', className: styles.completed },
  CANCELLED: { label: 'Đã hủy', className: styles.cancelled },
};

const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value ?? 0);
const dateTime = (value) => value ? new Date(value).toLocaleString('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
}) : '—';

export default function PurchaseOrderPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [orders, setOrders] = useState([]);
  const [statistics, setStatistics] = useState({});
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const canCreate = [ROLES.SALES, ROLES.OWNER].includes(user?.role);
  const canReceive = [ROLES.OPERATIONS, ROLES.OWNER].includes(user?.role);

  const load = useCallback(async () => {
    try {
      const [page, stats] = await Promise.all([
        purchaseOrderApi.getAll({ size: 100 }),
        purchaseOrderApi.getStatistics(),
      ]);
      setOrders(page?.content ?? []);
      setStatistics(stats ?? {});
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const id = window.setInterval(load, 3000);
    return () => window.clearInterval(id);
  }, [load]);

  const filtered = useMemo(() => orders.filter((order) => {
    const q = keyword.trim().toLowerCase();
    const matchesKeyword = !q || [order.orderCode, order.supplierName, order.warehouseName]
      .some((value) => String(value ?? '').toLowerCase().includes(q));
    return matchesKeyword && (!status || order.status === status);
  }), [orders, keyword, status]);

  const cards = [
    { label: 'Tổng đơn', value: statistics.totalCount ?? 0, icon: ShoppingBag },
    { label: 'Đã gửi NCC', value: statistics.SENT_TO_SUPPLIER ?? 0, icon: Clock3 },
    { label: 'Đang giao hàng', value: statistics.RECEIVING ?? 0, icon: Truck },
    { label: 'Hoàn thành', value: statistics.COMPLETED ?? 0, icon: PackageCheck },
  ];

  return (
    <main className={`${styles.page} product-workspace`}>
      <div className={styles.header}>
        <div className={styles.titleGroup}>
          <div className={styles.iconBox}><ShoppingBag size={22} /></div>
          <div><h1 className={styles.title}>Đơn mua hàng</h1><p className={styles.subtitle}>Quản lý đơn đặt hàng từ nhà cung cấp</p></div>
        </div>
        {canCreate && <button className={styles.primaryButton} onClick={() => navigate(ROUTES.PURCHASE_ORDER_CREATE)}><Plus size={18} /> Tạo đơn mua hàng</button>}
      </div>

      <section className={styles.stats} aria-label="Thống kê đơn mua hàng">
        {cards.map(({ label, value, icon: Icon }) => <article className={styles.stat} key={label}><span className={styles.statLabel}>{label}</span><span className={styles.statValue}>{value}</span><Icon size={18} aria-hidden="true" /></article>)}
      </section>

      <div className={styles.toolbar}>
        <div className={styles.searchWrap}><Search size={18} /><input className={styles.input} value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="Tìm theo mã đơn, nhà cung cấp..." aria-label="Tìm đơn mua hàng" /></div>
        <select className={styles.select} value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Lọc trạng thái">
          <option value="">Tất cả trạng thái</option>
          {Object.entries(STATUS).map(([value, item]) => <option value={value} key={value}>{item.label}</option>)}
        </select>
      </div>

      <section className={styles.tableCard}>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead><tr><th>Mã đơn</th><th>Nhà cung cấp</th><th>Kho nhập</th><th>Thời gian mua</th><th>Thời gian nhập</th><th className={styles.money}>Tổng tiền</th><th>Phiếu nhập</th><th>Trạng thái</th><th aria-label="Thao tác" /></tr></thead>
            <tbody>
              {!loading && filtered.map((order) => { console.log(order);
                const config = STATUS[order.status] ?? STATUS.DRAFT;
                return <tr key={order.id}>
                  <td><span className={styles.code}>{order.orderCode}</span></td>
                  <td>{order.supplierName}</td><td>{order.warehouseName}</td><td>{dateTime(order.orderDate)}</td><td>{dateTime(order.completedAt)}</td>
                  <td className={styles.money}><strong>{money(order.totalAmount)}</strong></td>
                  <td>{order.receiptCode ? <span className={styles.code}>{order.receiptCode}</span> : '—'}</td>
                  <td><span className={`${styles.badge} ${config.className}`}>{config.label}</span></td>
                  <td><div className={styles.actions}>
                    {canReceive && order.status === 'RECEIVING' && !order.receiptId && <button className={styles.actionButton} onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?purchaseOrderId=${order.id}`)}>Tạo phiếu nhập</button>}
                    {canCreate && order.status === 'DRAFT' && <button className={styles.actionButton} onClick={async () => { await purchaseOrderApi.send(order.id); await load(); }}>Gửi NCC</button>}
                  </div></td>
                </tr>;
              })}
            </tbody>
          </table>
        </div>
        {loading && <div className={styles.empty}>Đang tải đơn mua hàng...</div>}
        {!loading && filtered.length === 0 && <div className={styles.empty}>Chưa có đơn mua hàng phù hợp.</div>}
      </section>
    </main>
  );
}
