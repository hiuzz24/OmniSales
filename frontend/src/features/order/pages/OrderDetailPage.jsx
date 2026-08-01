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
import stockDeliveryService from '../../inventory/services/stockDeliveryService';
import styles from './OrderDetailPage.module.css';

const STATUS_CONFIG = {
  PENDING:    { label: 'Chờ xử lý',   icon: Clock,       color: '#ea580c', bg: '#fff7ed', border: '#fed7aa' },
  CONFIRMED:  { label: 'Đã xác nhận', icon: CheckCircle, color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
  PROCESSING: { label: 'Đang xử lý',  icon: Package,     color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe' },
  SHIPPED:    { label: 'Sẵn sàng giao', icon: Truck,       color: '#0d9488', bg: '#f0fdfa', border: '#99f6e4' },
  IN_TRANSIT: { label: 'Đang vận chuyển', icon: Truck,    color: '#0284c7', bg: '#f0f9ff', border: '#bae6fd' },
  DELIVERED:  { label: 'Đã giao',      icon: CheckCircle, color: '#16a34a', bg: '#f0fdf4', border: '#bbf7d0' },
  CANCELLED:  { label: 'Đã hủy',       icon: XCircle,     color: '#dc2626', bg: '#fef2f2', border: '#fecaca' },
};

const STATUS_FLOW = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED'];

const SHOPIFY_CANCEL_REASONS = [
  { value: 'CUSTOMER', label: 'Khách hàng yêu cầu hủy' },
  { value: 'FRAUD', label: 'Nghi ngờ gian lận' },
  { value: 'INVENTORY', label: 'Hết hàng / tồn kho không đủ' },
  { value: 'STAFF', label: 'Nhân viên hủy đơn' },
  { value: 'DECLINED', label: 'Thanh toán bị từ chối' },
  { value: 'OTHER', label: 'Khác' },
];

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
  const [cancelReasonId, setCancelReasonId] = useState('');
  const [tikTokReason, setTikTokReason] = useState('');
  const [shopifyReason, setShopifyReason] = useState('');
  const [cancelReasons, setCancelReasons] = useState([]);
  const [cancelReasonsLoading, setCancelReasonsLoading] = useState(false);
  const [cancelReasonsError, setCancelReasonsError] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [readinessLoading, setReadinessLoading] = useState(true);
  const [readinessError, setReadinessError] = useState(null);

  const fetchOrder = async ({ silent = false } = {}) => {
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const data = await orderService.getById(id);
      setOrder(data);
    } catch {
      if (!silent) {
        setError('Không thể tải thông tin đơn hàng');
      }
    } finally {
      if (!silent) {
        setLoading(false);
      }
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

  const fetchReadiness = async ({ silent = false } = {}) => {
    if (!silent) {
      setReadinessLoading(true);
      setReadinessError(null);
    }
    try {
      const response = await stockDeliveryService.getOrderReadiness(id);
      setReadiness(response?.data ?? response);
      setReadinessError(null);
    } catch {
      setReadiness(null);
      setReadinessError('Không thể kiểm tra phiếu xuất kho');
    } finally {
      if (!silent) {
        setReadinessLoading(false);
      }
    }
  };

  useEffect(() => {
    fetchOrder();
    fetchHistory();
    fetchReadiness();

    const refreshInterval = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        fetchOrder({ silent: true });
        fetchReadiness({ silent: true });
      }
    }, 15_000);

    return () => window.clearInterval(refreshInterval);
  }, [id]);

  const handleUpdateStatus = async (newStatus) => {
    setShowStatusMenu(false);
    setConfirmStatus(newStatus);
  };

  const handleConfirmStatus = async () => {
    if (!confirmStatus) return;
    const targetStatus = confirmStatus;
    setUpdating(true);
    try {
      const updated = await orderService.updateStatus(id, targetStatus);
      setOrder(updated);
      if (targetStatus !== 'PROCESSING') {
        toast.success('Cập nhật trạng thái thành công');
      }
      fetchHistory();
      fetchReadiness({ silent: true });
    } catch (requestError) {
      toast.error(requestError?.response?.data?.message || 'Cập nhật trạng thái thất bại');
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

  const isLazadaOrder = (value) => value?.platform === 'LAZADA';
  const isShopifyOrder = (value) => value?.platform === 'SHOPIFY';
  const isTikTokOrder = (value) => value?.platform === 'TIKTOK';

  const handleOpenCancelModal = async () => {
    setShowCancelModal(true);
    setCancelReasonId('');
    setTikTokReason('');
    setShopifyReason('');
    setCancelReasons([]);
    setCancelReasonsError(null);

    if (!isLazadaOrder(order) && !isTikTokOrder(order)) {
      return;
    }

    setCancelReasonsLoading(true);
    try {
      const reasons = await orderService.getCancelReasons(id);
      setCancelReasons(reasons || []);
      if (!reasons || reasons.length === 0) {
        const platformName = isTikTokOrder(order) ? 'TikTok' : 'Lazada';
        setCancelReasonsError(`Không có lý do hủy hợp lệ từ ${platformName} cho đơn hàng này.`);
        toast.error(`Không có lý do hủy hợp lệ từ ${platformName}`);
      }
    } catch (requestError) {
      const platformName = isTikTokOrder(order) ? 'TikTok' : 'Lazada';
      const message = requestError?.response?.data?.message || `Không thể tải lý do hủy từ ${platformName}.`;
      setCancelReasonsError(message);
      toast.error(message);
    } finally {
      setCancelReasonsLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!isLazadaOrder(order) && !isShopifyOrder(order) && !isTikTokOrder(order) && !cancelReason.trim()) {
      toast.error('Vui lòng nhập lý do hủy');
      return;
    }
    if (isLazadaOrder(order) && !cancelReasonId) {
      toast.error('Vui lòng chọn lý do hủy Lazada');
      return;
    }
    if (isShopifyOrder(order) && !shopifyReason) {
      toast.error('Vui lòng chọn lý do hủy Shopify');
      return;
    }
    if (isTikTokOrder(order) && !tikTokReason) {
      toast.error('Vui lòng chọn lý do hủy TikTok');
      return;
    }
    setUpdating(true);
    try {
      await orderService.cancel(id, {
        reason: cancelReason,
        reasonId: cancelReasonId,
        tikTokReason: isTikTokOrder(order) ? tikTokReason : undefined,
        shopifyReason: isShopifyOrder(order) ? shopifyReason : undefined,
        email: isShopifyOrder(order) ? true : undefined,
        restock: isShopifyOrder(order) ? true : undefined,
        refund: isShopifyOrder(order) ? true : undefined,
      });
      toast.success(isTikTokOrder(order)
        ? 'Đã gửi yêu cầu hủy, đang chờ TikTok xác nhận'
        : 'Hủy đơn hàng thành công');
      setShowCancelModal(false);
      setCancelReason('');
      setCancelReasonId('');
      setTikTokReason('');
      setShopifyReason('');
      setCancelReasons([]);
      fetchOrder();
      fetchHistory();
    } catch (requestError) {
      toast.error(requestError?.response?.data?.message || 'Hủy đơn hàng thất bại');
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

  const compact = (parts) => parts.filter(Boolean).join(', ');

  const toNumber = (value) => {
    const number = Number(value);
    return Number.isFinite(number) ? number : 0;
  };

  const itemLineTotal = (item) => {
    if (item?.totalPrice != null) {
      return toNumber(item.totalPrice);
    }
    return toNumber(item?.unitPrice) * toNumber(item?.quantity || 1) - toNumber(item?.discountAmount);
  };

  const groupOrderItems = (items = []) => {
    const grouped = new Map();

    items.forEach((item) => {
      const quantity = toNumber(item.quantity || 1);
      const key = [
        item.channelVariantId || item.variantId || '',
        item.sku || '',
        item.name || '',
        item.unitPrice ?? '',
      ].join('|');

      if (!grouped.has(key)) {
        grouped.set(key, {
          ...item,
          quantity: 0,
          discountAmount: 0,
          totalPrice: 0,
          sourceIds: [],
        });
      }

      const current = grouped.get(key);
      current.quantity += quantity;
      current.discountAmount += toNumber(item.discountAmount);
      current.totalPrice += itemLineTotal(item);
      current.sourceIds.push(item.id);
    });

    return Array.from(grouped.values());
  };

  const fullNameFromAddress = (address) => {
    if (!address || typeof address === 'string') return null;
    const fullName = compact([address.first_name, address.last_name]);
    return address.name || fullName || null;
  };

  const formatAddressLines = (address) => {
    if (!address) return ['-'];
    if (typeof address === 'string') return [address];

    const recipient = fullNameFromAddress(address);
    const phone = address.phone;
    const streetLine = compact([address.detail, address.address1, address.address2]);
    const localLine = compact([address.ward, address.district, address.city]);
    const regionLine = compact([address.province, address.province_code, address.zip]);
    const countryLine = compact([address.country, address.country_code]);

    return [
      recipient,
      phone,
      streetLine,
      localLine,
      regionLine,
      countryLine,
    ].filter(Boolean);
  };

  const getCurrentStep = () => {
    const idx = STATUS_FLOW.indexOf(order?.status);
    return idx >= 0 ? idx : -1;
  };

  const isPlatformOrder = (value) => value?.platform && value.platform !== 'MANUAL';

  const getAvailableStatusOptions = () => {
    if (!order) {
      return [];
    }
    if (!isPlatformOrder(order)) {
      return STATUS_FLOW.filter(
        (status) => status !== order.status && status !== 'CANCELLED',
      );
    }

    const tikTokRawStatus = order.platformMetadata?.tiktok?.rawOrderStatus;
    if (isTikTokOrder(order)
      && order.status === 'PENDING'
      && tikTokRawStatus !== 'AWAITING_SHIPMENT') {
      return [];
    }

    const platformFlow = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED'];
    const currentIndex = platformFlow.indexOf(order.status);
    if (currentIndex < 0 || currentIndex >= platformFlow.length - 1) {
      return [];
    }
    return [platformFlow[currentIndex + 1]];
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
  const canChangeStatus = role === ROLES.OWNER || role === ROLES.SALES;
  const canCreateOrderDelivery = role === ROLES.OWNER || role === ROLES.OPERATIONS;
  const canChangePaymentStatus = role === ROLES.OWNER || role === ROLES.OPERATIONS;
  const tikTokCancelPending = isTikTokOrder(order)
    && order.platformMetadata?.tiktok?.pendingConfirmation === true;
  const tikTokAwaitingShipment = order.platformMetadata?.tiktok?.rawOrderStatus === 'AWAITING_SHIPMENT';
  const tikTokProcessingBlocked = isTikTokOrder(order)
    && order.status === 'PENDING'
    && !tikTokAwaitingShipment;
  const isCancellable = !tikTokCancelPending
    && !['IN_TRANSIT', 'DELIVERED', 'CANCELLED'].includes(order.status);
  const currentStep = getCurrentStep();
  const chStyle = getChannelStyle(order.channelName);
  const ChIcon = chStyle?.icon;

  const paymentLabels = {
    PAID:      { label: 'Đã thanh toán',   color: '#16a34a', bg: '#f0fdf4', border: '#bbf7d0' },
    UNPAID:    { label: 'Chưa thanh toán', color: '#dc2626', bg: '#fef2f2', border: '#fecaca' },
    REFUNDED:  { label: 'Đã hoàn tiền',    color: '#475569', bg: '#f8fafc', border: '#e2e8f0' },
  };
  const pc = paymentLabels[order.paymentStatus] || paymentLabels.UNPAID;
  const availableStatusOptions = getAvailableStatusOptions();
  const shipmentReady = readiness?.readyForShipment === true;
  const groupedItems = groupOrderItems(order.items || []);
  const totalItemQuantity = groupedItems.reduce((sum, item) => sum + toNumber(item.quantity), 0);

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
            {tikTokCancelPending && (
              <span className={styles.statusPill} style={{ background: '#fff7ed', color: '#c2410c', borderColor: '#fed7aa' }}>
                <Clock size={12} />
                Đang chờ TikTok xác nhận hủy
              </span>
            )}
            {tikTokProcessingBlocked && (
              <span className={styles.statusPill} style={{ background: '#fff7ed', color: '#c2410c', borderColor: '#fed7aa' }}>
                <Clock size={12} />
                Chờ TikTok chuyển sang sẵn sàng xử lý
              </span>
            )}
          </div>
        </div>

        <div className={styles.headerActions}>
          {canChangeStatus && availableStatusOptions.length > 0 && (
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
                  {availableStatusOptions.map((s) => {
                    const cfg = STATUS_CONFIG[s];
                    const Icon = cfg.icon;
                    const shipmentBlocked = s === 'SHIPPED'
                      && (readinessLoading || readinessError || !shipmentReady);
                    return (
                      <button
                        key={s}
                        className={styles.dropdownItem}
                        onClick={() => handleUpdateStatus(s)}
                        disabled={shipmentBlocked}
                        title={shipmentBlocked ? 'Cần tạo phiếu xuất kho trước' : undefined}
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
          {canChangePaymentStatus && (
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
          {canChangeStatus && isCancellable && (
            <button
              className={styles.cancelBtn}
              onClick={handleOpenCancelModal}
              disabled={updating}
            >
              Hủy đơn
            </button>
          )}
        </div>
      </div>

      {order.status === 'PROCESSING' && !shipmentReady && (
        <div className={styles.readinessNotice}>
          <AlertTriangle size={16} />
          <span>
            {readinessLoading
              ? 'Đang kiểm tra phiếu xuất kho...'
              : readinessError
                || (canCreateOrderDelivery
                  ? 'Đơn hàng chưa có phiếu xuất kho. Hãy tạo phiếu trước khi chuyển sang Sẵn sàng giao.'
                  : 'Đơn hàng đang chờ bộ phận kho tạo phiếu xuất kho.')}
          </span>
          {readinessError && (
            <button type="button" onClick={() => fetchReadiness()}>
              Thử lại
            </button>
          )}
          {!readinessLoading && !readinessError && canCreateOrderDelivery && (
            <button
              type="button"
              className={styles.readinessPrimaryAction}
              onClick={() => navigate(`${ROUTES.STOCK_DELIVERY_CREATE}?tab=BY_ORDER&orderId=${order.id}`)}
            >
              <Package size={14} />
              Tạo phiếu xuất kho
              <ArrowRight size={14} />
            </button>
          )}
        </div>
      )}

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
              <span className={styles.itemCount}>{totalItemQuantity} sản phẩm</span>
            </div>
            <div className={styles.itemsList}>
              {groupedItems.map((item, idx) => (
                <div key={item.sourceIds?.join('-') || item.id || `${item.sku}-${idx}`} className={styles.itemRow}>
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
                <div className={styles.addressText}>
                  {formatAddressLines(order.shippingAddress).map((line, index) => (
                    <span
                      key={`${line}-${index}`}
                      className={index < 2 ? styles.addressContactLine : styles.addressLine}
                    >
                      {line}
                    </span>
                  ))}
                </div>
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
              {isPlatformOrder(order) && (
                <p className={styles.confirmSubtext}>
                  Hệ thống sẽ đồng bộ trạng thái phù hợp về sàn nếu được hỗ trợ.
                  {confirmStatus === 'SHIPPED' && order.platform === 'LAZADA'
                    ? ' Với Lazada, trạng thái này tương ứng sẵn sàng giao/Ready To Ship.'
                    : confirmStatus === 'SHIPPED' && order.platform === 'TIKTOK'
                      ? ' Với TikTok, hệ thống sẽ báo kiện hàng sẵn sàng bàn giao cho đơn vị vận chuyển.'
                      : ''}
                </p>
              )}
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
            {isLazadaOrder(order) && (
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#334155', marginBottom: 6 }}>
                  Lý do hủy Lazada
                </label>
                {cancelReasonsLoading ? (
                  <div style={{ padding: '10px 12px', fontSize: 13, color: '#64748b' }}>
                    Đang tải lý do hủy từ Lazada...
                  </div>
                ) : (
                  <select
                    className={styles.cancelInput}
                    value={cancelReasonId}
                    onChange={(e) => setCancelReasonId(e.target.value)}
                    disabled={updating || !!cancelReasonsError || cancelReasons.length === 0}
                    style={{ minHeight: 42, resize: 'none' }}
                  >
                    <option value="">Chọn lý do hủy</option>
                    {cancelReasons.map((reason) => (
                      <option key={reason.id} value={reason.id}>
                        {reason.name || reason.id}
                      </option>
                    ))}
                  </select>
                )}
                {cancelReasonsError && (
                  <p style={{ marginTop: 6, fontSize: 12, color: '#dc2626' }}>{cancelReasonsError}</p>
                )}
                {!cancelReasonsError && cancelReasons[0]?.warningMessage && (
                  <p style={{ marginTop: 6, fontSize: 12, color: '#b45309', lineHeight: 1.45 }}>
                    {cancelReasons[0].warningMessage}
                  </p>
                )}
              </div>
            )}
            {isTikTokOrder(order) && (
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#334155', marginBottom: 6 }}>
                  Lý do hủy TikTok
                </label>
                {cancelReasonsLoading ? (
                  <div style={{ padding: '10px 12px', fontSize: 13, color: '#64748b' }}>
                    Đang tải lý do hủy từ TikTok...
                  </div>
                ) : (
                  <select
                    className={styles.cancelInput}
                    value={tikTokReason}
                    onChange={(e) => setTikTokReason(e.target.value)}
                    disabled={updating || !!cancelReasonsError || cancelReasons.length === 0}
                    style={{ minHeight: 42, resize: 'none' }}
                  >
                    <option value="">Chọn lý do hủy</option>
                    {cancelReasons.map((reason) => (
                      <option key={reason.id} value={reason.id}>
                        {reason.name || reason.id}
                      </option>
                    ))}
                  </select>
                )}
                {cancelReasonsError && (
                  <p style={{ marginTop: 6, fontSize: 12, color: '#dc2626' }}>{cancelReasonsError}</p>
                )}
              </div>
            )}
            {isShopifyOrder(order) && (
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#334155', marginBottom: 6 }}>
                  Lý do hủy Shopify
                </label>
                <select
                  className={styles.cancelInput}
                  value={shopifyReason}
                  onChange={(e) => setShopifyReason(e.target.value)}
                  disabled={updating}
                  style={{ minHeight: 42, resize: 'none' }}
                >
                  <option value="">Chọn lý do hủy</option>
                  {SHOPIFY_CANCEL_REASONS.map((reason) => (
                    <option key={reason.value} value={reason.value}>
                      {reason.label}
                    </option>
                  ))}
                </select>
                <div style={{
                  display: 'flex',
                  gap: 8,
                  flexWrap: 'wrap',
                  marginTop: 8,
                  fontSize: 12,
                  color: '#475569',
                }}>
                  <span>Gửi email: Có</span>
                  <span>Hoàn kho: Có</span>
                  <span>Refund: Có</span>
                </div>
              </div>
            )}
            <textarea
              className={styles.cancelInput}
              rows={3}
              placeholder={isLazadaOrder(order) || isShopifyOrder(order) || isTikTokOrder(order) ? 'Ghi chú nội bộ OSMS...' : 'VD: Khách hàng yêu cầu hủy, hết hàng...'}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              autoFocus={!isLazadaOrder(order) && !isShopifyOrder(order) && !isTikTokOrder(order)}
            />
            <div className={styles.modalActions}>
              <button className={styles.btnGhost} onClick={() => setShowCancelModal(false)} disabled={updating}>
                Đóng
              </button>
              <button
                className={styles.btnDanger}
                onClick={handleCancel}
                disabled={updating
                  || cancelReasonsLoading
                  || !!cancelReasonsError
                  || (isLazadaOrder(order)
                    ? !cancelReasonId
                    : isShopifyOrder(order)
                      ? !shopifyReason
                      : isTikTokOrder(order)
                        ? !tikTokReason
                        : !cancelReason.trim())}
              >
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
