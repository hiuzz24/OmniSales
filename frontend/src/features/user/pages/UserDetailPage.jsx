import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  ArrowLeft, User, Mail, Phone, Calendar, Shield, Clock,
  FileText, Loader2, AlertCircle, RefreshCcw
} from 'lucide-react';
import { toast } from 'react-toastify';
import userApi from '../../../api/userApi';
import orderLogApi from '../../../api/orderLogApi';
import { ROUTES } from '../../../app/router/routes';
import Pagination from '../../../shared/components/Pagination';
import styles from './UserDetailPage.module.css';

const ROLE_LABEL = {
  SYSTEM_ADMIN: 'System Admin',
  OWNER: 'Shop Owner',
  OPERATIONS: 'Nhân viên vận hành',
  SALES: 'Nhân viên bán hàng',
};

const ROLE_COLOR = {
  SYSTEM_ADMIN: { color: '#dc2626', bg: '#fef2f2', border: '#fecaca' },
  OWNER: { color: '#7c3aed', bg: '#f5f3ff', border: '#ddd6fe' },
  OPERATIONS: { color: '#0891b2', bg: '#ecfeff', border: '#cffafe' },
  SALES: { color: '#2563eb', bg: '#eff6ff', border: '#dbeafe' },
};

const STATUS_LABEL = {
  ACTIVE: 'Hoạt động',
  INACTIVE: 'Chờ kích hoạt',
  LOCKED: 'Bị khóa',
};

const STATUS_COLOR = {
  ACTIVE: { color: '#16a34a', bg: '#f0fdf4' },
  INACTIVE: { color: '#475569', bg: '#f8fafc' },
  LOCKED: { color: '#dc2626', bg: '#fef2f2' },
};

const ACTION_MAP = {
  CREATE: { label: 'Tạo mới', color: '#16a34a', bg: '#f0fdf4' },
  UPDATE: { label: 'Cập nhật', color: '#2563eb', bg: '#eff6ff' },
  DELETE: { label: 'Xóa', color: '#dc2626', bg: '#fef2f2' },
  STATUS_CHANGE: { label: 'Đổi trạng thái', color: '#0284c7', bg: '#f0f9ff' },
  ITEM_UPDATE: { label: 'Cập nhật SP', color: '#7c3aed', bg: '#f5f3ff' },
  CUSTOMER_UPDATE: { label: 'Khách hàng', color: '#0d9488', bg: '#f0fdfa' },
  NOTES_ADD: { label: 'Ghi chú', color: '#475569', bg: '#f8fafc' },
  ORDER_CANCEL: { label: 'Hủy đơn', color: '#ea580c', bg: '#fff7ed' },
  ORDER_IMPORT: { label: 'Import đơn', color: '#0891b2', bg: '#ecfeff' },
  SYNC_UPDATE: { label: 'Đồng bộ', color: '#ca8a04', bg: '#fef9c3' },
  PAYMENT_UPDATE: { label: 'Thanh toán', color: '#d97706', bg: '#fffbeb' },
};

const UserDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  
  // States
  const [employee, setEmployee] = useState(null);
  const [logs, setLogs] = useState([]);
  const [loadingUser, setLoadingUser] = useState(true);
  const [loadingLogs, setLoadingLogs] = useState(true);
  const [error, setError] = useState(null);
  
  // Pagination
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Load basic info
  const fetchUserDetails = useCallback(async () => {
    setLoadingUser(true);
    setError(null);
    try {
      const data = await userApi.getProfileById(id);
      setEmployee(data);
    } catch (err) {
      console.error('Failed to fetch user:', err);
      setError('Không thể tải thông tin nhân viên hoặc nhân viên không tồn tại.');
      toast.error('Lỗi tải thông tin nhân viên');
    } finally {
      setLoadingUser(false);
    }
  }, [id]);

  // Load audit logs
  const fetchUserLogs = useCallback(async (pg = page) => {
    setLoadingLogs(true);
    try {
      const res = await orderLogApi.getByActor(id, { page: pg, size: 10 });
      const data = res?.data?.data || res?.data || res;
      setLogs(data?.content || []);
      setTotalPages(data?.totalPages || 0);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      console.error('Failed to fetch logs:', err);
      setLogs([]);
    } finally {
      setLoadingLogs(false);
    }
  }, [id, page]);

  useEffect(() => {
    fetchUserDetails();
  }, [fetchUserDetails]);

  useEffect(() => {
    fetchUserLogs(page);
  }, [fetchUserLogs, page]);

  const handleRefreshLogs = () => {
    setPage(0);
    fetchUserLogs(0);
  };

  const getInitials = (name) => {
    if (!name) return '?';
    const parts = name.trim().split(' ');
    if (parts.length === 1) return parts[0][0].toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  const formatDate = (dateString, includeTime = false) => {
    if (!dateString) return '-';
    const date = new Date(dateString);
    const options = {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    };
    if (includeTime) {
      options.hour = '2-digit',
      options.minute = '2-digit',
      options.second = '2-digit';
    }
    return date.toLocaleString('vi-VN', options);
  };

  const getRoleStyle = (role) => {
    return ROLE_COLOR[role] || { color: '#475569', bg: '#f1f5f9', border: '#cbd5e1' };
  };

  const getStatusStyle = (status) => {
    return STATUS_COLOR[status] || { color: '#475569', bg: '#f1f5f9' };
  };

  const getActionBadge = (action) => {
    const act = ACTION_MAP[action] || { label: action, color: '#475569', bg: '#f1f5f9' };
    return (
      <span 
        className={styles.actionBadge} 
        style={{ color: act.color, backgroundColor: act.bg }}
      >
        {act.label}
      </span>
    );
  };

  const getChangesData = (changes) => {
    if (!changes) return { oldStatus: null, newStatus: null, isPayment: false, extra: null };
    const oldStatus  = changes.oldStatus          || changes.old_status;
    const newStatus  = changes.newStatus          || changes.new_status;
    const oldPay     = changes.oldPaymentStatus;
    const newPay     = changes.newPaymentStatus;

    if (oldPay !== undefined || newPay !== undefined) {
      const extra = { ...changes };
      delete extra.oldPaymentStatus;
      delete extra.newPaymentStatus;
      return { oldStatus: oldPay, newStatus: newPay, isPayment: true, extra: extra };
    }

    if (oldStatus !== undefined || newStatus !== undefined) {
      const extra = { ...changes };
      delete extra.oldStatus;
      delete extra.newStatus;
      delete extra.old_status;
      delete extra.new_status;
      return { oldStatus, newStatus, isPayment: false, extra: extra };
    }

    return { oldStatus: null, newStatus: null, isPayment: false, extra: changes };
  };

  const formatChanges = (changes) => {
    if (!changes) return null;
    if (typeof changes !== 'object') return String(changes);
    const entries = Object.entries(changes);
    if (entries.length === 0) return null;
    return entries.map(([k, v]) => {
      // Map keys to readable Vietnamese terms
      const keyMap = {
        fullName: 'Họ tên',
        phone: 'SĐT',
        role: 'Vai trò',
        status: 'Trạng thái',
        note: 'Ghi chú',
        amount: 'Tổng tiền',
        paymentMethod: 'PT thanh toán',
        customerName: 'Khách hàng',
      };
      const keyLabel = keyMap[k] || k;
      return `${keyLabel}: ${v}`;
    }).join(', ');
  };

  if (loadingUser) {
    return (
      <div className={styles.loadingPage}>
        <Loader2 className={styles.spinner} size={40} />
        <p>Đang tải thông tin nhân viên...</p>
      </div>
    );
  }

  if (error || !employee) {
    return (
      <div className={styles.errorPage}>
        <div className={styles.errorCard}>
          <AlertCircle size={48} className={styles.errorIcon} />
          <h2>Đã xảy ra lỗi</h2>
          <p>{error || 'Không tìm thấy dữ liệu nhân viên.'}</p>
          <button className={styles.btnBack} onClick={() => navigate(ROUTES.USERS)}>
            <ArrowLeft size={16} />
            Quay lại danh sách
          </button>
        </div>
      </div>
    );
  }

  const roleStyle = getRoleStyle(employee.role);
  const statusStyle = getStatusStyle(employee.status);

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <button className={styles.btnBack} onClick={() => navigate(ROUTES.USERS)}>
          <ArrowLeft size={18} />
          <span>Quay lại</span>
        </button>
        <div className={styles.headerTitleWrap}>
          <h1>Chi tiết nhân sự</h1>
          <p>Xem thông tin cơ bản và toàn bộ lịch sử hoạt động của nhân viên trong hệ thống</p>
        </div>
      </div>

      {/* Main Grid Layout */}
      <div className={styles.grid}>
        
        {/* Left Side: Basic Info Card */}
        <div className={styles.cardInfo}>
          <div className={styles.avatarSection}>
            <div className={styles.avatar}>
              {getInitials(employee.fullName)}
            </div>
            <h2>{employee.fullName || 'Nhân sự mới'}</h2>
            <span 
              className={styles.roleTag}
              style={{ color: roleStyle.color, backgroundColor: roleStyle.bg, borderColor: roleStyle.border }}
            >
              {ROLE_LABEL[employee.role] || employee.role}
            </span>
          </div>

          <div className={styles.infoList}>
            <div className={styles.infoItem}>
              <div className={styles.infoIconWrap}>
                <Mail size={16} />
              </div>
              <div className={styles.infoContent}>
                <span className={styles.infoLabel}>Địa chỉ Email</span>
                <span className={styles.infoValue} title={employee.email}>{employee.email}</span>
              </div>
            </div>

            <div className={styles.infoItem}>
              <div className={styles.infoIconWrap}>
                <Phone size={16} />
              </div>
              <div className={styles.infoContent}>
                <span className={styles.infoLabel}>Số điện thoại</span>
                <span className={styles.infoValue}>{employee.phone || 'Chưa cung cấp'}</span>
              </div>
            </div>

            <div className={styles.infoItem}>
              <div className={styles.infoIconWrap}>
                <Shield size={16} />
              </div>
              <div className={styles.infoContent}>
                <span className={styles.infoLabel}>Trạng thái tài khoản</span>
                <span 
                  className={styles.statusBadge}
                  style={{ color: statusStyle.color, backgroundColor: statusStyle.bg }}
                >
                  {STATUS_LABEL[employee.status] || employee.status}
                </span>
              </div>
            </div>

            <div className={styles.infoItem}>
              <div className={styles.infoIconWrap}>
                <Calendar size={16} />
              </div>
              <div className={styles.infoContent}>
                <span className={styles.infoLabel}>Ngày tham gia hệ thống</span>
                <span className={styles.infoValue}>{formatDate(employee.createdAt)}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Activity Logs */}
        <div className={styles.cardLogs}>
          <div className={styles.logsHeader}>
            <div className={styles.logsTitleWrap}>
              <Clock size={20} />
              <h2>Lịch sử hoạt động</h2>
            </div>
            <button 
              className={styles.btnRefresh} 
              onClick={handleRefreshLogs}
              title="Làm mới lịch sử"
              disabled={loadingLogs}
            >
              <RefreshCcw size={16} className={loadingLogs ? styles.spinning : ''} />
              Làm mới
            </button>
          </div>

          <div className={styles.tableWrapper}>
            {loadingLogs ? (
              <div className={styles.loadingLogs}>
                <Loader2 className={styles.spinner} size={28} />
                <p>Đang tải lịch sử hoạt động...</p>
              </div>
            ) : logs.length === 0 ? (
              <div className={styles.emptyLogs}>
                <FileText size={40} className={styles.emptyIcon} />
                <h3>Chưa có hoạt động nào</h3>
                <p>Nhân viên này chưa thực hiện thao tác thay đổi dữ liệu nào ghi nhận trên hệ thống.</p>
              </div>
            ) : (
              <div className={styles.responsiveTable}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Thời gian</th>
                      <th>Thao tác</th>
                      <th>Đối tượng</th>
                      <th>Chi tiết thay đổi</th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs.map((log) => {
                      const { extra } = getChangesData(log.changes);
                      return (
                        <tr key={log.id}>
                          <td className={styles.timeCell}>
                            {formatDate(log.performedAt, true)}
                          </td>
                          <td>
                            {getActionBadge(log.action)}
                          </td>
                          <td className={styles.targetCell}>
                            <span className={styles.entityName}>{log.entityName || '-'}</span>
                            {log.entityId && (
                              <span className={styles.entityId} title={log.entityId}>
                                #{log.entityId.slice(0, 8)}
                              </span>
                            )}
                          </td>
                          <td className={styles.detailsCell}>
                            {formatChanges(extra) || <span className={styles.noDetail}>Không có chi tiết</span>}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Pagination */}
          {!loadingLogs && totalElements > 0 && (
            <div className={styles.paginationWrap}>
              <Pagination
                currentPage={page}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={10}
                currentCount={logs.length}
                itemLabel="hoạt động"
                onPageChange={setPage}
              />
            </div>
          )}
        </div>

      </div>
    </div>
  );
};

export default UserDetailPage;
