import { useState, useEffect, useCallback } from 'react';
import { 
  Search, Mail, UserX, Clock, CheckCircle, RefreshCw, AlertTriangle
} from 'lucide-react';
import { toast } from 'react-toastify';
import userApi from '../../../api/userApi';
import styles from './UserInviteListPage.module.css';

const UserInviteListPage = () => {
  const [invitations, setInvitations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const fetchInvitations = useCallback(async () => {
    setLoading(true);
    try {
      const data = await userApi.getInvitations();
      setInvitations(data || []);
    } catch (error) {
      console.error('Lỗi lấy danh sách lời mời:', error);
      toast.error('Không thể tải lịch sử lời mời');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchInvitations();
  }, [fetchInvitations]);

  const handleCancelInvite = async (invite) => {
    if (!window.confirm(`Bạn có chắc chắn muốn hủy lời mời cho email ${invite.email}?`)) {
      return;
    }

    setActionLoading(true);
    try {
      await userApi.cancelInviteByToken(invite.id);
      toast.success('Hủy lời mời đăng ký thành công!');
      fetchInvitations();
    } catch (error) {
      console.error('Lỗi hủy lời mời:', error);
      const msg = error?.response?.data?.message || 'Có lỗi xảy ra khi hủy lời mời';
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  const getStatusBadgeClass = (status) => {
    switch (status) {
      case 'PENDING':
        return `${styles.badge} ${styles.pending}`;
      case 'ACCEPTED':
        return `${styles.badge} ${styles.accepted}`;
      case 'CANCELLED':
        return `${styles.badge} ${styles.cancelled}`;
      case 'EXPIRED':
        return `${styles.badge} ${styles.expired}`;
      default:
        return styles.badge;
    }
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'PENDING':
        return 'Chờ xác nhận';
      case 'ACCEPTED':
        return 'Đã chấp nhận';
      case 'CANCELLED':
        return 'Đã hủy';
      case 'EXPIRED':
        return 'Hết hạn';
      default:
        return status;
    }
  };

  const getRoleLabel = (roleName) => {
    switch (roleName) {
      case 'SYSTEM_ADMIN':
        return 'Admin';
      case 'OWNER':
        return 'Owner';
      case 'OPERATIONS':
        return 'Operations Staff';
      case 'SALES':
        return 'Sales Staff';
      default:
        return roleName;
    }
  };

  const getRoleBadgeClass = (roleName) => {
    if (roleName === 'SYSTEM_ADMIN') return `${styles.roleBadge} ${styles.roleAdmin}`;
    if (roleName === 'OWNER') return `${styles.roleBadge} ${styles.roleOwner}`;
    if (roleName === 'OPERATIONS') return `${styles.roleBadge} ${styles.roleOperations}`;
    return styles.roleBadge;
  };

  const formatDateTime = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // Client-side filtering
  const filteredInvitations = invitations.filter((invite) => {
    const matchesSearch = (invite.email || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchesRole = roleFilter ? invite.roleName === roleFilter : true;
    const matchesStatus = statusFilter ? invite.status === statusFilter : true;
    return matchesSearch && matchesRole && matchesStatus;
  });

  return (
    <div className={styles.container}>
      {/* Page Header */}
      <div className={styles.header}>
        <div className={styles.titleSection}>
          <h2>Lịch sử gửi lời mời</h2>
          <p>Quản lý danh sách các email đã được mời tham gia hệ thống và trạng thái tương ứng</p>
        </div>
      </div>

      {/* Filters & Search */}
      <div className={styles.filterBar}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input
            type="text"
            className={styles.searchInput}
            placeholder="Tìm kiếm theo email..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className={styles.filterGroup}>
          <select
            className={styles.filterSelect}
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
          >
            <option value="">Tất cả vai trò</option>
            <option value="OPERATIONS">Operations Staff</option>
            <option value="SALES">Sales Staff</option>
          </select>

          <select
            className={styles.filterSelect}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Chờ xác nhận (Pending)</option>
            <option value="ACCEPTED">Đã chấp nhận (Accepted)</option>
            <option value="CANCELLED">Đã hủy (Cancelled)</option>
            <option value="EXPIRED">Hết hạn (Expired)</option>
          </select>

          <button 
            className={styles.btnInvite} 
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            onClick={fetchInvitations}
            title="Tải lại danh sách"
          >
            <RefreshCw size={16} />
            <span>Tải lại</span>
          </button>
        </div>
      </div>

      {/* Content */}
      {loading ? (
        <div className={styles.loadingContainer}>
          <div className={styles.spinner}></div>
          <p>Đang tải dữ liệu lịch sử lời mời...</p>
        </div>
      ) : filteredInvitations.length === 0 ? (
        <div className={styles.emptyContainer}>
          <Mail size={40} style={{ color: '#94a3b8' }} />
          <p>Không tìm thấy lịch sử gửi lời mời nào phù hợp.</p>
        </div>
      ) : (
        <div className={styles.tableContainer}>
          <div className={styles.tableHeader}>
            <h3>Danh sách lời mời</h3>
            <p>Hiển thị tổng số {filteredInvitations.length} lời mời</p>
          </div>

          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Email được mời</th>
                  <th>Vai trò</th>
                  <th>Thời gian gửi</th>
                  <th>Thời gian hết hạn</th>
                  <th>Trạng thái</th>
                  <th style={{ textAlign: 'center' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredInvitations.map((invite) => (
                  <tr key={invite.id}>
                    <td className={styles.emailCell}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Mail size={16} style={{ color: '#64748b' }} />
                        <span>{invite.email}</span>
                      </div>
                    </td>
                    <td>
                      <span className={getRoleBadgeClass(invite.roleName)}>
                        {getRoleLabel(invite.roleName)}
                      </span>
                    </td>
                    <td>{formatDateTime(invite.createdAt)}</td>
                    <td>{formatDateTime(invite.expiresAt)}</td>
                    <td>
                      <span className={getStatusBadgeClass(invite.status)}>
                        {getStatusLabel(invite.status)}
                      </span>
                    </td>
                    <td className={styles.actionsCell}>
                      {invite.status === 'PENDING' && (
                        <button
                          className={styles.btnActionCancelInvite}
                          onClick={() => handleCancelInvite(invite)}
                          disabled={actionLoading}
                          title="Hủy lời mời này"
                        >
                          <UserX size={15} />
                          <span>Hủy lời mời</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserInviteListPage;
