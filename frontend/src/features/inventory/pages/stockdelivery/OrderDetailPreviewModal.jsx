import { useCallback, useEffect, useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  AlertCircle,
  CalendarClock,
  CreditCard,
  Gift,
  Loader2,
  MapPin,
  Package,
  Phone,
  RefreshCw,
  Store,
  User,
  X,
} from 'lucide-react';
import orderApi from '../../../../api/orderApi';
import styles from './OrderDetailPreviewModal.module.css';

const STATUS_LABELS = {
  PENDING: 'Chờ xử lý',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Sẵn sàng giao',
  IN_TRANSIT: 'Đang vận chuyển',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};

const PAYMENT_LABELS = {
  UNPAID: 'Chưa thanh toán',
  PAID: 'Đã thanh toán',
  REFUNDED: 'Đã hoàn tiền',
};

const compact = (values) => values
  .filter((value) => value != null && String(value).trim() !== '')
  .map((value) => String(value).trim());

const unique = (values) => [...new Set(values)];

const formatDate = (value) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const formatCurrency = (value, currency) => {
  if (value == null) return '-';
  const amount = Number(value);
  if (!Number.isFinite(amount)) return '-';

  try {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: currency || 'VND',
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${amount.toLocaleString('vi-VN')} ${currency || 'VND'}`;
  }
};

const addressLines = (address) => {
  if (!address) return ['Chưa có địa chỉ giao hàng'];
  if (typeof address === 'string') return [address];

  const recipient = address.name
    || address.fullName
    || compact([address.first_name, address.last_name]).join(' ');
  const phone = address.phone || address.phoneNumber;
  const street = compact([
    address.detail,
    address.address,
    address.address1,
    address.address2,
  ]).join(', ');
  const locality = compact([
    address.ward,
    address.district,
    address.city,
    address.province,
    address.zip || address.postalCode,
    address.country,
  ]).join(', ');

  const lines = unique(compact([recipient, phone, street, locality]));
  return lines.length > 0 ? lines : ['Chưa có địa chỉ giao hàng'];
};

const itemTotal = (item) => {
  if (item?.totalPrice != null) return item.totalPrice;
  return (Number(item?.unitPrice) || 0) * (Number(item?.quantity) || 0)
    - (Number(item?.discountAmount) || 0);
};

export default function OrderDetailPreviewModal({ orderId, giftItems = [], onClose }) {
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadOrder = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setOrder(await orderApi.getById(orderId));
    } catch (requestError) {
      setError(requestError?.response?.data?.message || 'Không thể tải chi tiết đơn hàng.');
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    let active = true;

    orderApi.getById(orderId)
      .then((data) => {
        if (active) setOrder(data);
      })
      .catch((requestError) => {
        if (active) {
          setError(requestError?.response?.data?.message || 'Không thể tải chi tiết đơn hàng.');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [orderId]);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };

    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const shippingLines = useMemo(
    () => addressLines(order?.shippingAddress),
    [order?.shippingAddress],
  );

  return createPortal(
    <div
      className={styles.overlay}
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <section
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="order-preview-title"
      >
        <header className={styles.header}>
          <div className={styles.heading}>
            <span className={styles.headingIcon}><Package size={19} /></span>
            <div>
              <span className={styles.eyebrow}>Chi tiết đơn hàng</span>
              <h2 id="order-preview-title">{order?.externalOrderId || 'Đang tải...'}</h2>
            </div>
          </div>
          <button type="button" className={styles.closeButton} onClick={onClose} aria-label="Đóng chi tiết đơn hàng" title="Đóng">
            <X size={19} />
          </button>
        </header>

        {loading && (
          <div className={styles.state}>
            <Loader2 className={styles.spin} size={24} />
            <span>Đang tải chi tiết đơn hàng...</span>
          </div>
        )}

        {!loading && error && (
          <div className={styles.state}>
            <AlertCircle size={28} />
            <strong>Không tải được đơn hàng</strong>
            <span>{error}</span>
            <button type="button" className={styles.retryButton} onClick={loadOrder}>
              <RefreshCw size={15} />
              Thử lại
            </button>
          </div>
        )}

        {!loading && !error && order && (
          <div className={styles.content}>
            <div className={styles.statusBar}>
              <div className={styles.channel}>
                <Store size={15} />
                <strong>{order.platform || 'MANUAL'}</strong>
                <span>{order.channelName || 'Kênh thủ công'}</span>
              </div>
              <div className={styles.badges}>
                <span className={`${styles.badge} ${styles[`status${order.status}`] || ''}`}>
                  {STATUS_LABELS[order.status] || order.status || '-'}
                </span>
                <span className={`${styles.badge} ${styles.paymentBadge}`}>
                  {PAYMENT_LABELS[order.paymentStatus] || order.paymentStatus || '-'}
                </span>
              </div>
            </div>

            <div className={styles.infoGrid}>
              <section className={styles.infoSection}>
                <h3><User size={16} /> Người nhận</h3>
                <strong>{order.buyerName || order.customerName || 'Chưa có tên người nhận'}</strong>
                <span><Phone size={14} /> {order.buyerPhone || 'Chưa có số điện thoại'}</span>
                <div className={styles.address}>
                  <MapPin size={14} />
                  <div>{shippingLines.map((line) => <span key={line}>{line}</span>)}</div>
                </div>
              </section>

              <section className={styles.infoSection}>
                <h3><CalendarClock size={16} /> Thông tin đơn</h3>
                <dl className={styles.definitionList}>
                  <div><dt>Ngày tạo</dt><dd>{formatDate(order.createdAt)}</dd></div>
                  <div><dt>Mã vận đơn</dt><dd>{order.trackingNumber || '-'}</dd></div>
                  <div><dt>Ghi chú</dt><dd>{order.note || '-'}</dd></div>
                </dl>
              </section>
            </div>

            <section className={styles.itemsSection}>
              <div className={styles.sectionHeader}>
                <h3><Package size={16} /> Sản phẩm trong phiếu xuất</h3>
                <span>
                  {order.items?.length || 0} sản phẩm đặt
                  {giftItems.length > 0 ? ` · ${giftItems.length} quà tặng` : ''}
                </span>
              </div>
              <div className={styles.itemsTableWrap}>
                <table className={styles.itemsTable}>
                  <thead>
                    <tr>
                      <th>Sản phẩm</th>
                      <th>SKU</th>
                      <th className={styles.numberCell}>SL</th>
                      <th className={styles.numberCell}>Đơn giá</th>
                      <th className={styles.numberCell}>Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(order.items || []).map((item) => (
                      <tr key={item.id || `${item.sku}-${item.name}`}>
                        <td>
                          <strong>{item.name || 'Sản phẩm chưa đặt tên'}</strong>
                          {item.variantName && <span>{item.variantName}</span>}
                        </td>
                        <td>{item.sku || '-'}</td>
                        <td className={styles.numberCell}>{item.quantity || 0}</td>
                        <td className={styles.numberCell}>{formatCurrency(item.unitPrice, order.currency)}</td>
                        <td className={styles.numberCell}>{formatCurrency(itemTotal(item), order.currency)}</td>
                      </tr>
                    ))}
                    {giftItems.map((item) => (
                      <tr key={`gift-${item.productVariantId}`} className={styles.giftRow}>
                        <td>
                          <div className={styles.giftProductName}>
                            <strong>{item.productName || 'Quà tặng'}</strong>
                            <span className={styles.giftBadge}><Gift size={11} /> Quà tặng</span>
                          </div>
                          {item.variantName && <span>{item.variantName}</span>}
                        </td>
                        <td>{item.sku || '-'}</td>
                        <td className={styles.numberCell}>{item.quantity || 0}</td>
                        <td className={styles.numberCell}>-</td>
                        <td className={styles.numberCell}>-</td>
                      </tr>
                    ))}
                    {(order.items || []).length === 0 && giftItems.length === 0 && (
                      <tr><td colSpan={5} className={styles.emptyItems}>Đơn hàng chưa có sản phẩm.</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            <section className={styles.totals}>
              <div className={styles.totalTitle}><CreditCard size={16} /> Thanh toán</div>
              <dl>
                <div><dt>Tạm tính</dt><dd>{formatCurrency(order.subtotal, order.currency)}</dd></div>
                <div><dt>Giảm giá</dt><dd>-{formatCurrency(order.discountAmount || 0, order.currency)}</dd></div>
                <div><dt>Phí vận chuyển</dt><dd>{formatCurrency(order.shippingFee || 0, order.currency)}</dd></div>
                <div className={styles.grandTotal}><dt>Tổng cộng</dt><dd>{formatCurrency(order.totalAmount, order.currency)}</dd></div>
              </dl>
            </section>
          </div>
        )}
      </section>
    </div>,
    document.body,
  );
}
