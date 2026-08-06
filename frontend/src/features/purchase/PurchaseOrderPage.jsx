import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck, Clock3, PackageCheck, Plus, Search, ShoppingBag, Truck, FileEdit, ClipboardList, XCircle, TrendingUp, TrendingDown } from 'lucide-react';
import { toast } from 'react-toastify';
import purchaseOrderApi from '../../api/purchaseOrderApi';
import { ROUTES } from '../../app/router/routes';
import { ROLES } from '../auth/constants/roles';
import useAuth from '../auth/hooks/useAuth';
import useConfirmDialog from '../inventory/hooks/useConfirmDialog';
import styles from './PurchaseOrderPage.module.css';

const STATUS = {
  DRAFT: { label: 'Nháp', className: styles.draft },
  SENT_TO_SUPPLIER: { label: 'Đã gửi NCC', className: styles.sent },
  RECEIVING: { label: 'Đang giao hàng', className: styles.receiving },
  INSPECTING: { label: 'Đang kiểm tra', className: styles.inspecting },
  INSPECTED: { label: 'Đã kiểm tra', className: styles.inspected },
  COMPLETED: { label: 'Hoàn thành', className: styles.completed },
  CANCELLED: { label: 'Đã hủy', className: styles.cancelled },
};

const money = (value) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value ?? 0);

const dateTime = (value) =>
  value
    ? new Date(value).toLocaleString('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
      })
    : '—';

export default function PurchaseOrderPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [orders, setOrders] = useState([]);
  const [statistics, setStatistics] = useState({});
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const canCreate = [ROLES.SALES, ROLES.OWNER].includes(user?.role);
  const canInspect = [ROLES.SALES, ROLES.OWNER].includes(user?.role);
  const { confirm, ConfirmDialog } = useConfirmDialog();

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

  const filtered = useMemo(
    () =>
      orders.filter((order) => {
        const q = keyword.trim().toLowerCase();
        const matchesKeyword =
          !q ||
          [order.orderCode, order.supplierName, order.warehouseName].some((v) =>
            String(v ?? '').toLowerCase().includes(q),
          );
        return matchesKeyword && (!status || order.status === status);
      }),
    [orders, keyword, status],
  );

  const cards = [
    { label: 'Tổng đơn',       value: statistics.totalCount        ?? 0, icon: ShoppingBag,   key: '' },
    { label: 'Nháp',           value: statistics.DRAFT              ?? 0, icon: FileEdit,       key: 'DRAFT' },
    { label: 'Đã gửi NCC',     value: statistics.SENT_TO_SUPPLIER   ?? 0, icon: Clock3,         key: 'SENT_TO_SUPPLIER' },
    { label: 'Đang giao hàng', value: statistics.RECEIVING          ?? 0, icon: Truck,          key: 'RECEIVING' },
    { label: 'Đang kiểm tra',  value: statistics.INSPECTING         ?? 0, icon: ClipboardCheck, key: 'INSPECTING' },
    { label: 'Đã kiểm tra',    value: statistics.INSPECTED          ?? 0, icon: ClipboardList,  key: 'INSPECTED' },
    { label: 'Hoàn thành',     value: statistics.COMPLETED          ?? 0, icon: PackageCheck,   key: 'COMPLETED' },
    { label: 'Đã hủy',         value: statistics.CANCELLED          ?? 0, icon: XCircle,        key: 'CANCELLED' },
  ];

  const goToDetail = (id) =>
    navigate(ROUTES.PURCHASE_ORDER_DETAIL.replace(':id', id));

  const goToInspect = (id) =>
    navigate(`${ROUTES.PURCHASE_ORDER_DETAIL.replace(':id', id)}?inspect=true`);

  return (
    <main className={`${styles.page} product-workspace`}>
      <div className={styles.header}>
        <div className={styles.titleGroup}>
          <div className={styles.iconBox}><ShoppingBag size={22} /></div>
          <div>
            <h1 className={styles.title}>Đơn mua hàng</h1>
            <p className={styles.subtitle}>Quản lý đơn đặt hàng từ nhà cung cấp</p>
          </div>
        </div>
        {canCreate && (
          <button className={styles.primaryButton} onClick={() => navigate(ROUTES.PURCHASE_ORDER_CREATE)}>
            <Plus size={18} /> Tạo đơn mua hàng
          </button>
        )}
      </div>

      <section className={styles.stats} aria-label="Thống kê đơn mua hàng" style={{ gridTemplateColumns: 'repeat(4, minmax(0, 1fr))' }}>
        {cards.map(({ label, value, icon: Icon, key }) => {
          const isActive = status === key;
          return (
            <article
              key={label}
              className={styles.stat}
              onClick={() => setStatus(isActive ? '' : key)}
              role="button"
              tabIndex={0}
              aria-pressed={isActive}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setStatus(isActive ? '' : key)}
              style={{
                cursor: 'pointer',
                outline: isActive ? '2px solid #2563eb' : undefined,
                outlineOffset: isActive ? '2px' : undefined,
                background: isActive ? 'linear-gradient(135deg,#eff6ff,#f0fdfa)' : undefined,
                transition: 'outline 120ms ease, background 120ms ease',
              }}
            >
              <span className={styles.statLabel}>{label}</span>
              <span className={styles.statValue}>{value}</span>
              <Icon size={18} aria-hidden="true" />
            </article>
          );
        })}
      </section>

      <div className={styles.toolbar} style={{ gridTemplateColumns: '1fr' }}>
        <div className={styles.searchWrap}>
          <Search size={18} />
          <input
            className={styles.input}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm theo mã đơn, nhà cung cấp..."
            aria-label="Tìm đơn mua hàng"
          />
        </div>
      </div>

      <section className={styles.tableCard}>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Mã đơn</th>
                <th>Nhà cung cấp</th>
                <th>Kho nhập</th>
                <th>Thời gian mua</th>
                <th>Thời gian nhập</th>
                <th className={styles.money}>Tổng tiền</th>
                <th>Phiếu nhập</th>
                <th>Trạng thái</th>
                <th>Ghi chú</th>
                <th aria-label="Thao tác" />
              </tr>
            </thead>
            <tbody>
              {!loading &&
                filtered.map((order) => {
                  const config = STATUS[order.status] ?? STATUS.DRAFT;
                  return (
                    <tr key={order.id}>
                      <td>
                        <button
                          className={styles.codeLink}
                          onClick={() => goToDetail(order.id)}
                        >
                          {order.orderCode}
                        </button>
                      </td>
                      <td>{order.supplierName}</td>
                      <td>{order.warehouseName}</td>
                      <td>{dateTime(order.orderDate)}</td>
                      <td>{dateTime(order.completedAt)}</td>
                      <td className={styles.money}>
                        <strong>{money(order.totalAmount)}</strong>
                      </td>
                      <td>
                        {order.receiptCode ? (
                          <span className={styles.code}>{order.receiptCode}</span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td>
                        <span className={`${styles.badge} ${config.className}`}>
                          {config.label}
                        </span>
                      </td>
                      <td style={{ maxWidth: 180 }}>
                        {order.items?.some((item) => item?.surplusNote) ? (
                          <span style={{ fontSize: 11, color: '#d97706' }} title={order.items.filter((i) => i?.surplusNote).map((i) => `${i.productName}: ${i.surplusNote}`).join('\n')}>
                            ⚠ Có ghi chú thừa/thiếu
                          </span>
                        ) : (
                          <span style={{ fontSize: 11, color: '#cbd5e1' }}>—</span>
                        )}
                      </td>
                      <td>
                        <div className={styles.actions}>
                          {canCreate && order.status === 'DRAFT' && (
                            <button className={styles.actionButton}
                              onClick={async () => {
                                const ok = await confirm({
                                  title: 'Gửi đơn cho nhà cung cấp?',
                                  message: `Đơn ${order.orderCode} sẽ chuyển sang trạng thái "Đã gửi NCC". Bạn sẽ không thể chỉnh sửa sau khi gửi.`,
                                  confirmLabel: 'Gửi NCC',
                                  tone: 'warning',
                                });
                                if (!ok) return;
                                try { await purchaseOrderApi.send(order.id); await load(); }
                                catch (e) { toast.error(e?.response?.data?.message || 'Không thể gửi đơn.'); }
                              }}>
                              Gửi NCC
                            </button>
                          )}
                          {(canCreate || canInspect) && order.status === 'SENT_TO_SUPPLIER' && (
                            <button className={styles.actionButton}
                              style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#0369a1', borderColor: '#bae6fd' }}
                              onClick={async () => {
                                const ok = await confirm({
                                  title: 'Xác nhận đang nhận hàng?',
                                  message: `Đơn ${order.orderCode} sẽ chuyển sang "Đang giao hàng". Bắt đầu quá trình kiểm tra hàng hóa.`,
                                  confirmLabel: 'Xác nhận nhận hàng',
                                  tone: 'warning',
                                });
                                if (!ok) return;
                                try { await purchaseOrderApi.confirmReceiving(order.id); await load(); }
                                catch (e) { toast.error(e?.response?.data?.message || 'Không thể xác nhận.'); }
                              }}>
                              <Truck size={13} /> Xác nhận nhận hàng
                            </button>
                          )}
                          {canInspect && (order.status === 'RECEIVING' || order.status === 'INSPECTING') && (
                            <button className={styles.actionButton}
                              style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#7c3aed', borderColor: '#ddd6fe' }}
                              onClick={() => goToInspect(order.id)}>
                              <ClipboardCheck size={13} /> Kiểm tra
                            </button>
                          )}
                          {(canInspect || canCreate) && order.status === 'INSPECTED' && !order.receiptId && (
                            <>
                              <button className={styles.actionButton}
                                onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?purchaseOrderId=${order.id}`)}>
                                Tạo phiếu nhập
                              </button>
                              {order.hasShortage && (
                                <button className={styles.actionButton}
                                  style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#c2410c', borderColor: '#fed7aa', background: '#fff7ed' }}
                                  onClick={async () => {
                                    const shortageItems = (order.items ?? []).filter(
                                      (item) => item.actualQuantity != null && item.actualQuantity < item.quantity
                                    );
                                    const itemDesc = shortageItems.map((item) =>
                                      `• ${item.productName}: thiếu ${item.quantity - item.actualQuantity} sản phẩm`
                                    ).join('\n');
                                    const ok = await confirm({
                                      title: 'Tạo đơn bổ sung hàng thiếu?',
                                      message: `Sẽ tạo đơn mới ở trạng thái Đã kiểm tra cho số lượng còn thiếu:\n\n${itemDesc || 'Xem chi tiết đơn để biết thêm.'}\n\nGhi chú bổ sung sẽ được tự động điền.`,
                                      confirmLabel: 'Tạo đơn bổ sung',
                                      tone: 'warning',
                                    });
                                    if (!ok) return;
                                    try {
                                      const newOrder = await purchaseOrderApi.createShortageOrder(order.id);
                                      toast.success(`Đã tạo đơn bổ sung ${newOrder.orderCode}.`);
                                      await load();
                                    } catch (e) {
                                      toast.error(e?.response?.data?.message || 'Không thể tạo đơn bổ sung.');
                                    }
                                  }}>
                                  <TrendingDown size={13} /> Tạo đơn bổ sung
                                </button>
                              )}
                              {order.hasSurplus && (
                                <button className={styles.actionButton}
                                  style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#7c3aed', borderColor: '#ddd6fe' }}
                                  onClick={async () => {
                                    const surplusItems = (order.items ?? []).filter(
                                      (item) => item.actualQuantity != null && item.actualQuantity > item.quantity
                                    );
                                    const itemDesc = surplusItems.map((item) =>
                                      `• ${item.productName}: thừa ${item.actualQuantity - item.quantity} sản phẩm`
                                    ).join('\n');
                                    const ok = await confirm({
                                      title: 'Tạo đơn thặng dư?',
                                      message: `Sẽ tạo đơn mới ở trạng thái Đã kiểm tra cho số lượng thừa:\n\n${itemDesc || 'Xem chi tiết đơn để biết thêm.'}\n\nGhi chú thặng dư sẽ được tự động điền.`,
                                      confirmLabel: 'Tạo đơn thặng dư',
                                      tone: 'warning',
                                    });
                                    if (!ok) return;
                                    try {
                                      const newOrder = await purchaseOrderApi.createSurplusOrder(order.id);
                                      toast.success(`Đã tạo đơn thặng dư ${newOrder.orderCode}.`);
                                      await load();
                                    } catch (e) {
                                      toast.error(e?.response?.data?.message || 'Không thể tạo đơn thặng dư.');
                                    }
                                  }}>
                                  <TrendingUp size={13} /> Tạo đơn thặng dư
                                </button>
                              )}
                            </>
                          )}
                          {(canCreate || canInspect) && order.status !== 'COMPLETED' && order.status !== 'CANCELLED' && (
                            <button className={styles.actionButton}
                              style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#b91c1c', borderColor: '#fecaca' }}
                              onClick={async () => {
                                const ok = await confirm({
                                  title: 'Hủy đơn mua hàng?',
                                  message: `Bạn chắc chắn muốn hủy đơn ${order.orderCode}?\nThao tác này không thể hoàn tác.`,
                                  confirmLabel: 'Hủy đơn',
                                  tone: 'danger',
                                });
                                if (!ok) return;
                                try {
                                  await purchaseOrderApi.cancel(order.id);
                                  await load();
                                } catch (e) {
                                  toast.error(e?.response?.data?.message || 'Không thể hủy đơn.');
                                }
                              }}>
                              Hủy đơn
                            </button>
                          )}
                          <button className={styles.actionButton} onClick={() => goToDetail(order.id)}>
                            Xem
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
        {loading && <div className={styles.empty}>Đang tải đơn mua hàng...</div>}
        {!loading && filtered.length === 0 && (
          <div className={styles.empty}>Chưa có đơn mua hàng phù hợp.</div>
        )}
      </section>
      {ConfirmDialog}
    </main>
  );
}
