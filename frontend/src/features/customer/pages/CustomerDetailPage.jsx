import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../app/router/routes';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import {
  ArrowLeft, Pencil, Trash2, Phone, Mail, MapPin,
  Calendar, User, UserCheck, UserX, Clock, FileText,
  ShoppingBag, ChevronRight, Package,
} from 'lucide-react';
import customerService from '../services/customerService';
import orderService from '../../order/services/orderService';
import styles from './CustomerDetailPage.module.css';

const GENDER_MAP = { Nam: 'MALE', Nữ: 'FEMALE', Khác: 'OTHER' };

const ORDER_STATUS_LABEL = {
  PENDING: 'Chờ xử lý',
  CONFIRMED: 'Đã xác nhận',
  PROCESSING: 'Đang xử lý',
  SHIPPED: 'Sẵn sàng giao',
  IN_TRANSIT: 'Đang vận chuyển',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};

const ORDER_STATUS_COLORS = {
  PENDING:    { bg: '#fff7ed', color: '#ea580c', border: '#fed7aa' },
  CONFIRMED:  { bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe' },
  PROCESSING: { bg: '#f5f3ff', color: '#7c3aed', border: '#ddd6fe' },
  SHIPPED:    { bg: '#f0fdfa', color: '#0d9488', border: '#99f6e4' },
  IN_TRANSIT: { bg: '#f0f9ff', color: '#0284c7', border: '#bae6fd' },
  DELIVERED:  { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
  CANCELLED:  { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
};

const PAGE_SIZE = 5;

const CustomerDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role;

  const [customer, setCustomer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersTotalPages, setOrdersTotalPages] = useState(0);
  const [ordersTotalElements, setOrdersTotalElements] = useState(0);

  const canEdit = role === ROLES.OWNER || role === ROLES.SALES;
  const canDelete = role === ROLES.OWNER;

  useEffect(() => {
    const fetchCustomer = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await customerService.getById(id);
        setCustomer(data);
      } catch (err) {
        setError('Không thể tải thông tin khách hàng');
      } finally {
        setLoading(false);
      }
    };
    fetchCustomer();
  }, [id]);

  const fetchOrders = useCallback(async () => {
    setOrdersLoading(true);
    try {
      const data = await orderService.getAll({
        customerId: id,
        page: ordersPage,
        size: PAGE_SIZE,
      });
      setOrders(data.content || []);
      setOrdersTotalPages(data.totalPages || 0);
      setOrdersTotalElements(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to fetch customer orders:', err);
      setOrders([]);
    } finally {
      setOrdersLoading(false);
    }
  }, [id, ordersPage]);

  useEffect(() => {
    if (customer) fetchOrders();
  }, [customer, fetchOrders]);

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await customerService.delete(deleteId);
      toast.success('Xóa khách hàng thành công');
      navigate(ROUTES.CUSTOMER_LIST);
    } catch (err) {
      toast.error('Xóa khách hàng thất bại');
    } finally {
      setDeleting(false);
      setDeleteId(null);
    }
  };

  const formatAddress = (address) => {
    if (!address) return '-';
    if (typeof address === 'string') return address;
    const parts = [address.detail, address.ward, address.district, address.province].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : '-';
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  };

  const getInitials = (name) => {
    if (!name) return '?';
    return name.trim().split(' ').pop().charAt(0).toUpperCase();
  };

  const formatCurrency = (amount) => {
    if (amount == null) return '-';
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0,
    }).format(amount);
  };

  const formatOrderDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric',
    });
  };

  const getOrderItemsSummary = (order) => {
    const items = order.items || [];
    if (items.length === 0) return 'Không có sản phẩm';
    if (items.length === 1) return `${items[0].name} ×${items[0].quantity || 1}`;
    return `${items[0].name} ×${items[0].quantity || 1} (+${items.length - 1} SP)`;
  };

  if (loading) {
    return (
      <div className={`${styles.page} product-workspace`}>
        <div className={styles.pageHeader}>
          <button className={styles.backBtn} onClick={() => navigate(ROUTES.CUSTOMER_LIST)}>
            <ArrowLeft size={16} />
            Quay lại
          </button>
          <div className={styles.headerCenter}>
            <h1 className={styles.pageTitle}>Chi tiết khách hàng</h1>
          </div>
          <div style={{ width: 110 }} />
        </div>
        <div className={styles.loadingText}>Đang tải...</div>
      </div>
    );
  }

  if (error || !customer) {
    return (
      <div className={`${styles.page} product-workspace`}>
        <div className={styles.pageHeader}>
          <button className={styles.backBtn} onClick={() => navigate(ROUTES.CUSTOMER_LIST)}>
            <ArrowLeft size={16} />
            Quay lại
          </button>
          <div className={styles.headerCenter}>
            <h1 className={styles.pageTitle}>Chi tiết khách hàng</h1>
          </div>
          <div style={{ width: 110 }} />
        </div>
        <div className={styles.errorText}>{error || 'Không tìm thấy khách hàng'}</div>
      </div>
    );
  }

  return (
    <div className={`${styles.page} product-workspace`}>
      {/* Page Header */}
      <div className={styles.pageHeader}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.CUSTOMER_LIST)}>
          <ArrowLeft size={16} />
          Quay lại
        </button>
        <div className={styles.headerCenter}>
          <h1 className={styles.pageTitle}>Chi tiết khách hàng</h1>
          <p className={styles.pageSubtitle}>Xem thông tin chi tiết của khách hàng</p>
        </div>
        <div className={styles.headerActions}>
          {canEdit && (
            <button
              className={styles.editBtn}
              onClick={() => navigate(ROUTES.CUSTOMER_EDIT.replace(':id', id))}
            >
              <Pencil size={14} />
              Sửa
            </button>
          )}
          {canDelete && (
            <button className={styles.deleteBtn} onClick={() => setDeleteId(id)}>
              <Trash2 size={14} />
              Xóa
            </button>
          )}
        </div>
      </div>

      {/* Hero Card */}
      <div className={styles.heroCard}>
        <div className={styles.heroLeft}>
          <div className={styles.avatar}>
            {getInitials(customer.fullName)}
          </div>
          <div className={styles.heroInfo}>
            <h2 className={styles.customerName}>{customer.fullName || 'Không có tên'}</h2>
            <div className={styles.heroBadges}>
              {customer.gender && (
                <span className={`${styles.badge} ${styles[`badge_${(customer.gender === 'Nam' ? 'male' : customer.gender === 'Nữ' ? 'female' : 'other')}`]}`}>
                  {customer.gender}
                </span>
              )}
              {customer.isActive !== undefined && (
                <span className={`${styles.badge} ${customer.isActive ? styles.badgeActive : styles.badgeInactive}`}>
                  {customer.isActive ? (
                    <><UserCheck size={11} /> Hoạt động</>
                  ) : (
                    <><UserX size={11} /> Không HĐ</>
                  )}
                </span>
              )}
            </div>
          </div>
        </div>
        <div className={styles.heroStats}>
          <div className={styles.heroStat}>
            <span className={styles.heroStatValue}>{customer.orderCount || 0}</span>
            <span className={styles.heroStatLabel}>Đơn hàng</span>
          </div>
          <div className={styles.heroStatDivider} />
          <div className={styles.heroStat}>
            <span className={styles.heroStatValue}>
              {customer.totalSpent != null
                ? new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', minimumFractionDigits: 0 }).format(customer.totalSpent)
                : '-'}
            </span>
            <span className={styles.heroStatLabel}>Tổng chi tiêu</span>
          </div>
        </div>
      </div>

      {/* Info Grid */}
      <div className={styles.infoGrid}>
        {/* Thông tin liên hệ */}
        <div className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardIcon}>
              <Phone size={15} />
            </div>
            <h3 className={styles.cardTitle}>Thông tin liên hệ</h3>
          </div>
          <div className={styles.infoRows}>
            <div className={styles.infoRow}>
              <span className={styles.infoLabel}>
                <Phone size={13} />
                Số điện thoại
              </span>
              <span className={styles.infoValue}>{customer.phone || '-'}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.infoLabel}>
                <Mail size={13} />
                Email
              </span>
              <span className={styles.infoValue}>{customer.email || '-'}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.infoLabel}>
                <MapPin size={13} />
                Địa chỉ
              </span>
              <span className={styles.infoValue}>{formatAddress(customer.address)}</span>
            </div>
          </div>
        </div>

        {/* Thông tin cá nhân */}
        <div className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardIcon}>
              <User size={15} />
            </div>
            <h3 className={styles.cardTitle}>Thông tin cá nhân</h3>
          </div>
          <div className={styles.infoRows}>
            <div className={styles.infoRow}>
              <span className={styles.infoLabel}>
                <User size={13} />
                Giới tính
              </span>
              <span className={styles.infoValue}>{customer.gender || '-'}</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.infoLabel}>
                <Calendar size={13} />
                Ngày sinh
              </span>
              <span className={styles.infoValue}>{formatDate(customer.birth)}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Ghi chú */}
      {customer.notes && (
        <div className={styles.card}>
          <div className={styles.cardHeader}>
            <div className={styles.cardIcon}>
              <FileText size={15} />
            </div>
            <h3 className={styles.cardTitle}>Ghi chú</h3>
          </div>
          <p className={styles.notes}>{customer.notes}</p>
        </div>
      )}

      {/* Lịch sử đơn hàng */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.cardIcon}>
            <ShoppingBag size={15} />
          </div>
          <h3 className={styles.cardTitle}>Đơn hàng đã mua</h3>
          {ordersTotalElements > 0 && (
            <span className={styles.orderCountBadge}>
              {ordersTotalElements} đơn
            </span>
          )}
        </div>

        {ordersLoading ? (
          <div className={styles.ordersLoading}>Đang tải danh sách đơn...</div>
        ) : orders.length === 0 ? (
          <div className={styles.ordersEmpty}>
            <Package size={32} />
            <span>Khách hàng chưa có đơn hàng nào.</span>
          </div>
        ) : (
          <>
            <div className={styles.ordersList}>
              {orders.map((order) => {
                const statusCfg = ORDER_STATUS_COLORS[order.status] || ORDER_STATUS_COLORS.PENDING;
                return (
                  <button
                    key={order.id}
                    type="button"
                    className={styles.orderItem}
                    onClick={() => navigate(ROUTES.ORDER_DETAIL.replace(':id', order.id))}
                  >
                    <div className={styles.orderItemTop}>
                      <div className={styles.orderItemLeft}>
                        <span className={styles.orderExternalId}>
                          #{order.externalOrderId || order.id?.substring(0, 8)}
                        </span>
                        <span className={styles.orderChannel}>
                          {order.channelName || order.platform || 'Manual'}
                        </span>
                      </div>
                      <span
                        className={styles.orderStatus}
                        style={{
                          background: statusCfg.bg,
                          color: statusCfg.color,
                          borderColor: statusCfg.border,
                        }}
                      >
                        {ORDER_STATUS_LABEL[order.status] || order.status}
                      </span>
                    </div>
                    <div className={styles.orderItemBottom}>
                      <div className={styles.orderItemSummary}>
                        {getOrderItemsSummary(order)}
                      </div>
                      <div className={styles.orderItemRight}>
                        <span className={styles.orderItemDate}>{formatOrderDate(order.createdAt)}</span>
                        <span className={styles.orderItemTotal}>
                          {formatCurrency(order.totalAmount)}
                        </span>
                        <ChevronRight size={16} className={styles.orderChevron} />
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>

            {ordersTotalPages > 1 && (
              <div className={styles.ordersPagination}>
                <button
                  type="button"
                  className={styles.pageNavBtn}
                  disabled={ordersPage === 0}
                  onClick={() => setOrdersPage((p) => Math.max(0, p - 1))}
                >
                  ‹ Trước
                </button>
                <span className={styles.pageInfo}>
                  Trang {ordersPage + 1} / {ordersTotalPages}
                </span>
                <button
                  type="button"
                  className={styles.pageNavBtn}
                  disabled={ordersPage + 1 >= ordersTotalPages}
                  onClick={() => setOrdersPage((p) => p + 1)}
                >
                  Sau ›
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {/* Thông tin hệ thống */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <div className={styles.cardIcon}>
            <Clock size={15} />
          </div>
          <h3 className={styles.cardTitle}>Thông tin hệ thống</h3>
        </div>
        <div className={styles.infoRows}>
          <div className={styles.infoRow}>
            <span className={styles.infoLabel}>
              <Clock size={13} />
              Ngày tạo
            </span>
            <span className={styles.infoValue}>{formatDate(customer.createdAt)}</span>
          </div>
          <div className={styles.infoRow}>
            <span className={styles.infoLabel}>
              <Clock size={13} />
              Cập nhật lần cuối
            </span>
            <span className={styles.infoValue}>{formatDate(customer.updatedAt)}</span>
          </div>
        </div>
      </div>

      {/* Delete Modal */}
      {deleteId && (
        <div className={styles.modalOverlay} onClick={() => setDeleteId(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Xác nhận xóa</h3>
            <p className={styles.modalBody}>
              Bạn có chắc chắn muốn xóa khách hàng này? Hành động này không thể hoàn tác.
            </p>
            <div className={styles.modalActions}>
              <button
                className={styles.cancelBtn}
                onClick={() => setDeleteId(null)}
                disabled={deleting}
              >
                Hủy
              </button>
              <button
                className={styles.confirmDeleteBtn}
                onClick={handleDelete}
                disabled={deleting}
              >
                {deleting ? 'Đang xóa...' : 'Xóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CustomerDetailPage;
