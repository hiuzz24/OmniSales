import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../app/router/routes';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import {
  ArrowLeft, Clock, CheckCircle, Package, Truck, XCircle, RotateCcw,
  Phone, MapPin, User, FileText, AlertTriangle,
  ShoppingBag, Store, PenTool, ChevronRight, ArrowRight,
} from 'lucide-react';
import orderService from '../services/orderService';
import styles from './OrderDetailPage.module.css';

const STATUS_CONFIG = {
  PENDING:    { label: 'Chờ xử lý',   icon: Clock,       color: '#ea580c', bg: '#fff7ed', border: '#fed7aa' },
  CONFIRMED:  { label: 'Đã xác nhận', icon: CheckCircle, color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
  PROCESSING: { label: 'Đang xử lý',  icon: Package,     color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe' },
  SHIPPED:    { label: 'Đang giao',    icon: Truck,       color: '#0d9488', bg: '#f0fdfa', border: '#99f6e4' },
  DELIVERED:  { label: 'Đã giao',      icon: CheckCircle, color: '#16a34a', bg: '#f0fdf4', border: '#bbf7d0' },
  CANCELLED:  { label: 'Đã hủy',       icon: XCircle,     color: '#dc2626', bg: '#fef2f2', border: '#fecaca' },
};

const STATUS_FLOW = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

const CHANNEL_CONFIG = {
  Shopee:  { icon: ShoppingBag, bg: '#fff5f5', color: '#e11d48', border: '#fecdd3' },
  Lazada:  { icon: Store,       bg: '#fff7ed', color: '#f97316', border: '#fed7aa' },
  TikTok:  { icon: ShoppingBag, bg: '#fdf2f8', color: '#db2777', border: '#fbcfe8' },
  Website: { icon: PenTool,     bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
  Manual:  { icon: PenTool,     bg: '#f5f3ff', color: '#7c3aed', border: '#ddd6fe' },
};

const getChannelStyle = (name) => {
  if (!name) return null;
  const key = Object.keys(CHANNEL_CONFIG).find((k) =>
    name.toLowerCase().includes(k.toLowerCase())
  );
  return CHANNEL_CONFIG[key] || { icon: Store, bg: '#f8fafc', color: '#64748b', border: '#e2e8f0' };
};

const OrderDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role;

  const [order, setOrder] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [updating, setUpdating] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [showStatusMenu, setShowStatusMenu] = useState(false);
  const [confirmStatus, setConfirmStatus] = useState(null);
  const [showPaymentMenu, setShowPaymentMenu] = useState(false);
  const [confirmPayment, setConfirmPayment] = useState(null);
  const [cancelReason, setCancelReason] = useState('');

  const fetchOrder = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await orderService.getById(id);
      setOrder(data);
    } catch {
      setError('Không thể tải thông tin đơn hàng');
    } finally {
      setLoading(false);
    }
  };

  const fetchHistory = async () => {
    try {
      const data = await orderService.getHistory(id, 0, 50);
      setHistory(data.content || []);
    } catch {
      setHistory([]);
    }
  };

  useEffect(() => {
    fetchOrder();
    fetchHistory();
  }, [id]);

  const handleUpdateStatus = async (newStatus) => {
    setShowStatusMenu(false);
    setConfirmStatus(newStatus);
  };

  const handleConfirmStatus = async () => {
    if (!confirmStatus) return;
    setUpdating(true);
    try {
      const updated = await orderService.updateStatus(id, confirmStatus);
      setOrder(updated);
      toast.success('Cập nhật trạng thái thành công');
      fetchHistory();
    } catch {
      toast.error('Cập nhật trạng thái thất bại');
    } finally {
      setUpdating(false);
      setConfirmStatus(null);
    }
  };

  const handleUpdatePaymentStatus = (newStatus) => {
    setShowPaymentMenu(false);
    setConfirmPayment(newStatus);
  };

  const handleConfirmPaymentStatus = async () => {
    if (!confirmPayment) return;
    setUpdating(true);
    try {
      const updated = await orderService.updatePaymentStatus(id, confirmPayment);
      setOrder(updated);
      toast.success('Cập nhật trạng thái thanh toán thành công');
      fetchHistory();
    } catch {
      toast.error('Cập nhật trạng thái thanh toán thất bại');
    } finally {
      setUpdating(false);
      setConfirmPayment(null);
    }
  };

  const handleCancel = async () => {
    if (!cancelReason.trim()) {
      toast.error('Vui lòng nhập lý do hủy');
      return;
    }
    setUpdating(true);
    try {
      await orderService.cancel(id, cancelReason);
      toast.success('Hủy đơn hàng thành công');
      setShowCancelModal(false);
      setCancelReason('');
      fetchOrder();
      fetchHistory();
    } catch {
      toast.error('Hủy đơn hàng thất bại');
    } finally {
      setUpdating(false);
    }
  };

  const formatCurrency = (amount) => {
    if (amount == null) return '-';
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0,
    }).format(amount);
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  };

  const formatAddress = (address) => {
    if (!address) return '-';
    if (typeof address === 'string') return address;
    const parts = [address.detail, address.ward, address.district, address.province].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : '-';
  };

  const getCurrentStep = () => {
    const idx = STATUS_FLOW.indexOf(order?.status);
    return idx >= 0 ? idx : -1;
  };

  if (loading) {
    return (
      <div className={styles.loadingWrap}>
        <div className={styles.loadingSpinner} />
        <p>Đang tải thông tin đơn hàng...</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className={styles.errorWrap}>
        <AlertTriangle size={40} />
        <p>{error || 'Không tìm thấy đơn hàng'}</p>
        <button className={styles.btnPrimary} onClick={() => navigate(ROUTES.ORDER_LIST)}>
          Quay lại danh sách
        </button>
      </div>
    );
  }

  const sc = STATUS_CONFIG[order.status] || { label: order.status, color: '#64748b', bg: '#f1f5f9', border: '#e2e8f0' };
  const StatusIcon = sc.icon;
  const canChangeStatus = role === ROLES.OWNER || role === ROLES.OPERATIONS;
  const isCancellable = !['DELIVERED', 'CANCELLED'].includes(order.status);
  const isStatusChangeable = !['DELIVERED', 'CANCELLED'].includes(order.status);
  const currentStep = getCurrentStep();
  const chStyle = getChannelStyle(order.channelName);
  const ChIcon = chStyle?.icon;

  const paymentLabels = {
    PAID:      { label: 'Đã thanh toán',   color: '#16a34a', bg: '#f0fdf4', border: '#bbf7d0' },
    UNPAID:    { label: 'Chưa thanh toán', color: '#dc2626', bg: '#fef2f2', border: '#fecaca' },
    REFUNDED:  { label: 'Đã hoàn tiền',    color: '#475569', bg: '#f8fafc', border: '#e2e8f0' },
  };
  const pc = paymentLabels[order.paymentStatus] || paymentLabels.UNPAID;

  return (
    <div className={styles.page}>
      {/* Header */}
      <div className={styles.topBar}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.ORDER_LIST)}>
          <ArrowLeft size={16} />
          Quay lại
        </button>

        <div className={styles.headerCenter}>
          <div className={styles.headerLeft}>
            {chStyle && (
              <div className={styles.channelPill} style={{ background: chStyle.bg, color: chStyle.color, borderColor: chStyle.border }}>
                <ChIcon size={13} />
                {order.channelName}
              </div>
            )}
            <span className={styles.orderCode}>{order.externalOrderId}</span>
          </div>
          <div className={styles.headerBadges}>
            <span className={styles.statusPill} style={{ background: sc.bg, color: sc.color, borderColor: sc.border }}>
              <StatusIcon size={12} />
              {sc.label}
            </span>
            <span className={styles.payPill} style={{ background: pc.bg, color: pc.color, borderColor: pc.border }}>
              {pc.label}
            </span>
          </div>
        </div>

        <div className={styles.headerActions}>
          {canChangeStatus && (
            <div className={styles.statusDropdown}>
              <button
                className={styles.updateStatusBtn}
                style={{ '--btn-color': sc.color, '--btn-border': sc.border }}
                onClick={() => setShowStatusMenu((v) => !v)}
                disabled={updating}
              >
                <RotateCcw size={14} />
                Đổi trạng thái
              </button>
              {showStatusMenu && (
                <div className={styles.dropdownMenu}>
                  {STATUS_FLOW.map((s) => {
                    if (s === order.status) return null;
                    const cfg = STATUS_CONFIG[s];
                    const Icon = cfg.icon;
                    return (
                      <button
                        key={s}
                        className={styles.dropdownItem}
                        onClick={() => handleUpdateStatus(s)}
                      >
                        <Icon size={13} style={{ color: cfg.color }} />
                        {cfg.label}
                        <ChevronRight size={12} style={{ marginLeft: 'auto', opacity: 0.4 }} />
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}
          {canChangeStatus && (
            <div className={styles.statusDropdown}>
              <button
                className={styles.updatePaymentBtn}
                style={{ '--btn-color': pc.color, '--btn-border': pc.border }}
                onClick={() => setShowPaymentMenu((v) => !v)}
                disabled={updating}
              >
                <RotateCcw size={14} />
                Đổi TT thanh toán
              </button>
              {showPaymentMenu && (
                <div className={styles.dropdownMenu}>
                  {Object.entries(paymentLabels).map(([key, cfg]) => {
                    if (key === order.paymentStatus) return null;
                    return (
                      <button
                        key={key}
                        className={styles.dropdownItem}
                        onClick={() => handleUpdatePaymentStatus(key)}
                      >
                        <span className={styles.paymentDot} style={{ background: cfg.color }} />
                        {cfg.label}
                        <ChevronRight size={12} style={{ marginLeft: 'auto', opacity: 0.4 }} />
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}
          {isCancellable && (
            <button
              className={styles.cancelBtn}
              onClick={() => setShowCancelModal(true)}
              disabled={updating}
            >
              Hủy đơn
            </button>
          )}
        </div>
      </div>

      {/* Order Progress */}
      {!['CANCELLED'].includes(order.status) && (
        <div className={styles.progressBar}>
          {STATUS_FLOW.map((s, idx) => {
            const cfg = STATUS_CONFIG[s];
            const Icon = cfg.icon;
            const isDone = idx < currentStep;
            const isActive = idx === currentStep;
            return (
              <div key={s} className={styles.progressStep}>
                <div
                  className={`${styles.stepCircle} ${isDone ? styles.stepDone : ''} ${isActive ? styles.stepActive : ''}`}
                  style={isDone ? { background: cfg.color, borderColor: cfg.color } : isActive ? { background: cfg.bg, borderColor: cfg.color } : {}}
                >
                  {isDone ? <CheckCircle size={14} color="#fff" /> : <Icon size={14} style={isActive ? { color: cfg.color } : { color: '#94a3b8' }} />}
                </div>
                <span
                  className={styles.stepLabel}
                  style={isActive ? { color: cfg.color, fontWeight: 700 } : isDone ? { color: '#475569' } : { color: '#cbd5e1' }}
                >
                  {cfg.label}
                </span>
                {idx < STATUS_FLOW.length - 1 && (
                  <div className={`${styles.stepLine} ${idx < currentStep ? styles.stepLineDone : ''}`} />
                )}
              </div>
            );
          })}
        </div>
      )}
      {order.status === 'CANCELLED' && (
        <div className={styles.cancelledBanner}>
          <XCircle size={16} />
          Đơn hàng đã bị hủy
          {order.cancelReason && <span className={styles.cancelReasonText}>— Lý do: {order.cancelReason}</span>}
        </div>
      )}

      {/* Main 2-column grid */}
      <div className={styles.mainGrid}>
        {/* LEFT: Order details */}
        <div className={styles.leftCol}>

          {/* Meta info */}
          <div className={styles.metaCard}>
            <div className={styles.metaGrid}>
              <div className={styles.metaItem}>
                <span className={styles.metaLabel}>Ngày đặt</span>
                <span className={styles.metaValue}>{formatDate(order.createdAt)}</span>
              </div>
              {order.statusChangedAt && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Cập nhật cuối</span>
                  <span className={styles.metaValue}>{formatDate(order.statusChangedAt)}</span>
                </div>
              )}
              {order.trackingNumber && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Mã vận đơn</span>
                  <span className={styles.metaValue} style={{ fontFamily: 'monospace', color: '#2563eb' }}>
                    {order.trackingNumber}
                  </span>
                </div>
              )}
              <div className={styles.metaItem}>
                <span className={styles.metaLabel}>Platform</span>
                <span className={styles.metaValue}>{order.platform || '-'}</span>
              </div>
            </div>
            {order.note && (
              <div className={styles.noteBox}>
                <FileText size={13} />
                <span>{order.note}</span>
              </div>
            )}
          </div>

          {/* Products */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <h3 className={styles.cardTitle}>
                <Package size={16} />
                Sản phẩm
              </h3>
              <span className={styles.itemCount}>{order.items?.length || 0} sản phẩm</span>
            </div>
            <div className={styles.itemsList}>
              {(order.items || []).map((item, idx) => (
                <div key={item.id} className={styles.itemRow}>
                  <div className={styles.itemIndex}>{idx + 1}</div>
                  <div className={styles.itemInfo}>
                    <span className={styles.itemName}>{item.name}</span>
                    {item.sku && <span className={styles.itemSku}>SKU: {item.sku}</span>}
                  </div>
                  <div className={styles.itemQty}>×{item.quantity}</div>
                  <div className={styles.itemPrices}>
                    <span className={styles.itemUnit}>{formatCurrency(item.unitPrice)}</span>
                    {item.discountAmount > 0 && (
                      <span className={styles.itemDiscount}>-{formatCurrency(item.discountAmount)}</span>
                    )}
                  </div>
                  <div className={styles.itemTotal}>{formatCurrency(item.totalPrice)}</div>
                </div>
              ))}
            </div>

            {/* Totals */}
            <div className={styles.totalsSection}>
              <div className={styles.totalLine}>
                <span>Tạm tính</span>
                <span>{formatCurrency(order.subtotal)}</span>
              </div>
              {order.discountAmount > 0 && (
                <div className={`${styles.totalLine} ${styles.totalDiscount}`}>
                  <span>Giảm giá</span>
                  <span>-{formatCurrency(order.discountAmount)}</span>
                </div>
              )}
              <div className={styles.totalLine}>
                <span>Phí vận chuyển</span>
                <span>{formatCurrency(order.shippingFee)}</span>
              </div>
              <div className={`${styles.totalLine} ${styles.grandTotal}`}>
                <span>Tổng tiền</span>
                <span>{formatCurrency(order.totalAmount)}</span>
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT: Customer + History */}
        <div className={styles.rightCol}>

          {/* Customer */}
          <div className={styles.card}>
            <h3 className={styles.cardTitle}>
              <User size={16} />
              Người mua
            </h3>
            <div className={styles.customerCard}>
              <div className={styles.customerAvatar}>
                <User size={20} />
              </div>
              <div className={styles.customerMeta}>
                <span className={styles.customerName}>{order.buyerName || 'Không rõ'}</span>
                {order.buyerPhone && (
                  <a href={`tel:${order.buyerPhone}`} className={styles.customerPhone}>
                    <Phone size={12} />
                    {order.buyerPhone}
                  </a>
                )}
              </div>
            </div>
            {order.shippingAddress && (
              <div className={styles.addressBlock}>
                <div className={styles.addressLabel}>
                  <MapPin size={13} />
                  Địa chỉ giao hàng
                </div>
                <p className={styles.addressText}>{formatAddress(order.shippingAddress)}</p>
              </div>
            )}
          </div>

          {/* History */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <h3 className={styles.cardTitle}>
                <Clock size={16} />
                Lịch sử thay đổi
              </h3>
              {history.length > 0 && (
                <span className={styles.itemCount}>{history.length} thay đổi</span>
              )}
            </div>
            <div className={styles.historyList}>
              {/* Current / latest status — always shown */}
              <div className={`${styles.historyItem} ${styles.historyItemCurrent}`}>
                <div className={styles.historyDot} style={{ background: sc.color, boxShadow: `0 0 0 3px ${sc.bg}` }} />
                <div className={styles.historyContent}>
                  <div className={styles.historyTop}>
                    <span className={styles.statusBadge} style={{ background: sc.bg, color: sc.color, borderColor: sc.border }}>
                      <StatusIcon size={11} />
                      {sc.label}
                    </span>
                    <span className={styles.historyNowTag}>Hiện tại</span>
                  </div>
                  {order.statusChangedAt && (
                    <span className={styles.historyTime}>{formatDate(order.statusChangedAt)}</span>
                  )}
                </div>
              </div>
              {history.length > 0 ? [...history].reverse().map((log, idx) => {
                const isStatusChange = log.action?.startsWith('STATUS_CHANGE') || log.action?.startsWith('Trạng thái');
                const statusMatch = log.action?.match(/Trạng thái:\s*(\w+)/);
                const oldStatus = statusMatch ? statusMatch[1] : null;
                const cfg = oldStatus && STATUS_CONFIG[oldStatus] ? STATUS_CONFIG[oldStatus] : null;
                const dotColor = cfg ? cfg.color : '#94a3b8';
                return (
                  <div key={log.id} className={styles.historyItem}>
                    <div className={styles.historyDot} style={{ background: dotColor }} />
                    <div className={styles.historyContent}>
                      <div className={styles.historyTop}>
                        <span className={styles.historyAction}>{log.action}</span>
                      </div>
                      <div className={styles.historyBottom}>
                        {log.actorEmail && (
                          <span className={styles.historyActor}>
                            <span className={styles.actorDot} />
                            {log.actorEmail}
                          </span>
                        )}
                        <span className={styles.historyTime}>{formatDate(log.performedAt)}</span>
                      </div>
                      {log.changes && Object.keys(log.changes).length > 0 && (
                        <div className={styles.changeChips}>
                          {Object.entries(log.changes).map(([key, val]) => (
                            <span key={key} className={styles.changeChip}>{key}: {String(val)}</span>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                );
              }) : (
                <p className={styles.noHistory}>Chưa có lịch sử thay đổi</p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Status Confirm Modal */}
      {confirmStatus && (() => {
        const from = STATUS_CONFIG[order.status] || { label: order.status };
        const to = STATUS_CONFIG[confirmStatus] || { label: confirmStatus };
        const ToIcon = to.icon;
        return (
          <div className={styles.modalOverlay} onClick={() => setConfirmStatus(null)}>
            <div className={styles.confirmModal} onClick={(e) => e.stopPropagation()}>
              <div className={styles.confirmIconWrap} style={{ background: to.bg }}>
                <ToIcon size={26} color={to.color} />
              </div>
              <h3 className={styles.confirmTitle}>Xác nhận đổi trạng thái</h3>
              <p className={styles.confirmDesc}>
                Chuyển đơn hàng <strong>{order.externalOrderId}</strong> từ
              </p>
              <div className={styles.confirmFlow}>
                <span className={styles.confirmBadge} style={{ background: from.bg, color: from.color, borderColor: from.border }}>
                  {from.label}
                </span>
                <ArrowRight size={16} style={{ color: '#94a3b8', flexShrink: 0 }} />
                <span className={styles.confirmBadge} style={{ background: to.bg, color: to.color, borderColor: to.border }}>
                  {to.label}
                </span>
              </div>
              <p className={styles.confirmSubtext}>Hành động này sẽ được ghi nhận trong lịch sử thay đổi.</p>
              <div className={styles.confirmActions}>
                <button className={styles.btnGhost} onClick={() => setConfirmStatus(null)} disabled={updating}>
                  Hủy
                </button>
                <button className={styles.btnConfirm} style={{ background: to.color }} onClick={handleConfirmStatus} disabled={updating}>
                  {updating ? 'Đang xử lý...' : 'Xác nhận'}
                </button>
              </div>
            </div>
          </div>
        );
      })()}

      {/* Payment Status Confirm Modal */}
      {confirmPayment && (() => {
        const fromP = paymentLabels[order.paymentStatus] || { label: order.paymentStatus };
        const toP = paymentLabels[confirmPayment] || { label: confirmPayment };
        return (
          <div className={styles.modalOverlay} onClick={() => setConfirmPayment(null)}>
            <div className={styles.confirmModal} onClick={(e) => e.stopPropagation()}>
              <div className={styles.confirmIconWrap} style={{ background: toP.bg }}>
                <CheckCircle size={26} color={toP.color} />
              </div>
              <h3 className={styles.confirmTitle}>Xác nhận thanh toán</h3>
              <p className={styles.confirmDesc}>
                Cập nhật thanh toán cho đơn <strong>{order.externalOrderId}</strong>
              </p>
              <div className={styles.confirmFlow}>
                <span className={styles.confirmBadge} style={{ background: fromP.bg, color: fromP.color, borderColor: fromP.border }}>
                  {fromP.label}
                </span>
                <ArrowRight size={16} style={{ color: '#94a3b8', flexShrink: 0 }} />
                <span className={styles.confirmBadge} style={{ background: toP.bg, color: toP.color, borderColor: toP.border }}>
                  {toP.label}
                </span>
              </div>
              <p className={styles.confirmSubtext}>Hành động này sẽ được ghi nhận trong lịch sử.</p>
              <div className={styles.confirmActions}>
                <button className={styles.btnGhost} onClick={() => setConfirmPayment(null)} disabled={updating}>
                  Hủy
                </button>
                <button className={styles.btnConfirm} style={{ background: toP.color }} onClick={handleConfirmPaymentStatus} disabled={updating}>
                  {updating ? 'Đang xử lý...' : 'Xác nhận'}
                </button>
              </div>
            </div>
          </div>
        );
      })()}

      {/* Cancel Modal */}
      {showCancelModal && (
        <div className={styles.modalOverlay} onClick={() => setShowCancelModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <div className={styles.modalIcon}>
                <XCircle size={22} color="#dc2626" />
              </div>
              <div>
                <h3 className={styles.modalTitle}>Hủy đơn hàng</h3>
                <p className={styles.modalSubtitle}>{order.externalOrderId}</p>
              </div>
            </div>
            <p className={styles.modalDesc}>Vui lòng nhập lý do hủy đơn hàng. Hành động này không thể hoàn tác.</p>
            <textarea
              className={styles.cancelInput}
              rows={3}
              placeholder="VD: Khách hàng yêu cầu hủy, hết hàng..."
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              autoFocus
            />
            <div className={styles.modalActions}>
              <button className={styles.btnGhost} onClick={() => setShowCancelModal(false)} disabled={updating}>
                Đóng
              </button>
              <button className={styles.btnDanger} onClick={handleCancel} disabled={updating || !cancelReason.trim()}>
                {updating ? 'Đang xử lý...' : 'Xác nhận hủy'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default OrderDetailPage;
