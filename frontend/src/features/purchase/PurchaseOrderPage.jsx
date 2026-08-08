import { useCallback, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { ChevronDown, Clock3, Eye, PackageCheck, PackagePlus, Plus, Search, Send, ShoppingBag, Truck, FileEdit, X, XCircle, ImagePlus } from 'lucide-react';
import { toast } from 'react-toastify';
import purchaseOrderApi from '../../api/purchaseOrderApi';
import { ROUTES } from '../../app/router/routes';
import { ROLES } from '../auth/constants/roles';
import useAuth from '../auth/hooks/useAuth';
import useConfirmDialog from '../inventory/hooks/useConfirmDialog';
import styles from './PurchaseOrderPage.module.css';

function useTooltip() {
  const [tip, setTip] = useState(null);

  const show = (e, text) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const below = rect.bottom + 46 <= window.innerHeight;
    setTip({
      text,
      x: Math.min(Math.max(rect.left + rect.width / 2, 96), window.innerWidth - 96),
      y: below ? rect.bottom + 9 : rect.top - 9,
      above: !below,
    });
  };

  const hide = () => setTip(null);

  useEffect(() => {
    if (!tip) return undefined;
    const clear = () => setTip(null);
    window.addEventListener('scroll', clear, true);
    window.addEventListener('resize', clear);
    return () => {
      window.removeEventListener('scroll', clear, true);
      window.removeEventListener('resize', clear);
    };
  }, [tip]);

  return { tip, show, hide };
}

function TipButton({ label, style, showTip, hideTip, onClick, children }) {
  return (
    <button
      className={styles.iconAction}
      style={style}
      aria-label={label}
      onMouseEnter={(e) => showTip(e, label)}
      onFocus={(e) => showTip(e, label)}
      onMouseLeave={hideTip}
      onBlur={hideTip}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

const STATUS = {
  DRAFT: { label: 'Nháp', className: styles.draft },
  SENT_TO_SUPPLIER: { label: 'Đã gửi NCC', className: styles.sent },
  RECEIVING: { label: 'Đang giao hàng', className: styles.receiving },
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
  const canReceive = [ROLES.OPERATIONS, ROLES.OWNER].includes(user?.role);
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const { tip, show, hide } = useTooltip();

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
    { label: 'Hoàn thành',     value: statistics.COMPLETED          ?? 0, icon: PackageCheck,   key: 'COMPLETED' },
    { label: 'Đã hủy',         value: statistics.CANCELLED          ?? 0, icon: XCircle,        key: 'CANCELLED' },
  ];

  const goToDetail = (id) =>
    navigate(ROUTES.PURCHASE_ORDER_DETAIL.replace(':id', id));

  return (
    <main className={`${styles.page} product-workspace`}>
      <div className={styles.header}>
        <div className={styles.titleGroup}>
          <div className={styles.iconBox}><ShoppingBag size={22} /></div>
          <div>
            <h1 className={styles.title}>Đơn đặt hàng</h1>
            <p className={styles.subtitle}>Quản lý đơn đặt hàng từ nhà cung cấp</p>
          </div>
        </div>
        {canCreate && (
          <button className={styles.primaryButton} onClick={() => navigate(ROUTES.PURCHASE_ORDER_CREATE)}>
            <Plus size={18} /> Tạo đơn đặt hàng
          </button>
        )}
      </div>

      <section className={styles.stats} aria-label="Thống kê đơn đặt hàng" style={{ gridTemplateColumns: 'repeat(6, minmax(0, 1fr))' }}>
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

      <div className={styles.filterBar}>
        <div className={styles.searchWrap}>
          <Search size={18} />
          <input
            className={styles.input}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm theo mã đơn, nhà cung cấp, kho..."
            aria-label="Tìm đơn đặt hàng"
          />
        </div>
        <div className={styles.filterSelectWrap}>
          <select className={styles.select} value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Lọc theo trạng thái">
            <option value="">Tất cả trạng thái</option>
            {Object.entries(STATUS).map(([key, cfg]) => (
              <option key={key} value={key}>{cfg.label}</option>
            ))}
          </select>
          <ChevronDown size={15} />
        </div>
        {(keyword || status) && (
          <button className={styles.filterClear} onClick={() => { setKeyword(''); setStatus(''); }}>
            <X size={14} /> Xóa lọc
          </button>
        )}
        <span className={styles.filterCount}><strong>{filtered.length}</strong> đơn</span>
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
                <th>Chứng từ</th>
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
                        {order.receipts?.length ? (
                          <span className={styles.code} style={{ fontSize: 12 }}>{order.receipts.length} phiếu nhập</span>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td>
                        {order.evidenceUrl ? (
                          <a href={order.evidenceUrl} target="_blank" rel="noreferrer" title="Mở ảnh chứng từ"
                            style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, fontWeight: 600, color: '#0369a1', textDecoration: 'none' }}>
                            <ImagePlus size={14} /> Có
                          </a>
                        ) : (
                          <span style={{ fontSize: 11, color: '#cbd5e1' }}>—</span>
                        )}
                      </td>
                      <td>
                        <span className={`${styles.badge} ${config.className}`}>
                          {config.label}
                        </span>
                      </td>
                      <td style={{ maxWidth: 180 }}>
                        <span style={{ fontSize: 11, color: order.notes ? '#475569' : '#cbd5e1' }}>
                          {order.notes || '—'}
                        </span>
                      </td>
                      <td>
                        <div className={styles.actions}>
                          {canCreate && order.status === 'DRAFT' && (
                            <TipButton
                              label="Gửi NCC"
                              style={{ color: '#1d4ed8', borderColor: '#bfdbfe' }}
                              showTip={show}
                              hideTip={hide}
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
                              <Send size={15} />
                            </TipButton>
                          )}
                          {order.status === 'SENT_TO_SUPPLIER' && (
                            <TipButton
                              label="Xác nhận giao hàng"
                              style={{ color: '#0369a1', borderColor: '#bae6fd' }}
                              showTip={show}
                              hideTip={hide}
                              onClick={async () => {
                                const ok = await confirm({
                                  title: 'Xác nhận bên NCC đang giao hàng?',
                                  message: `Đơn ${order.orderCode} sẽ chuyển sang "Đang giao hàng".`,
                                  confirmLabel: 'Xác nhận',
                                  tone: 'warning',
                                });
                                if (!ok) return;
                                try { await purchaseOrderApi.confirmShipping(order.id); await load(); }
                                catch (e) { toast.error(e?.response?.data?.message || 'Không thể xác nhận.'); }
                              }}>
                              <Truck size={15} />
                            </TipButton>
                          )}
                          {canReceive && order.status === 'RECEIVING' && (
                            <TipButton
                              label="Tạo phiếu nhập"
                              style={{ color: '#047857', borderColor: '#a7f3d0' }}
                              showTip={show}
                              hideTip={hide}
                              onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?purchaseOrderId=${order.id}`)}>
                              <PackagePlus size={15} />
                            </TipButton>
                          )}
                          {order.status !== 'COMPLETED' && order.status !== 'CANCELLED' && order.status !== 'RECEIVING' && (
                            <TipButton
                              label="Hủy đơn"
                              style={{ color: '#b91c1c', borderColor: '#fecaca' }}
                              showTip={show}
                              hideTip={hide}
                              onClick={async () => {
                                const ok = await confirm({
                                  title: 'Hủy đơn đặt hàng?',
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
                              <XCircle size={15} />
                            </TipButton>
                          )}
                          <TipButton
                            label="Xem chi tiết"
                            showTip={show}
                            hideTip={hide}
                            onClick={() => goToDetail(order.id)}>
                            <Eye size={15} />
                          </TipButton>
                        </div>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
        {loading && <div className={styles.empty}>Đang tải đơn đặt hàng...</div>}
        {!loading && filtered.length === 0 && (
          <div className={styles.empty}>Chưa có đơn đặt hàng phù hợp.</div>
        )}
      </section>
      {tip &&
        createPortal(
          <span
            className={styles.tooltip}
            style={{ left: tip.x, top: tip.y, transform: tip.above ? 'translate(-50%, -100%)' : 'translate(-50%, 0)' }}
            role="tooltip"
          >
            {tip.text}
          </span>,
          document.body,
        )}
      {ConfirmDialog}
    </main>
  );
}
