import { useState, useEffect, useCallback } from 'react';
import { 
  Users, UserCheck, UserX, Lock, Plus, X, Mail, AlertTriangle, 
  Search, Download, Eye, Pencil, Key, ShieldAlert, Check, Copy, CheckCircle2
} from 'lucide-react';
import { toast } from 'react-toastify';
import PageHeader from '../../../shared/components/PageHeader';
import userApi from '../../../api/userApi';
import authService from '../../auth/services/authService';
import styles from './UserListPage.module.css';

const UserListPage = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  
  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  // Drawer States
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState('view'); // 'view' | 'edit' | 'create'
  const [selectedUser, setSelectedUser] = useState(null);

  // Form States
  const [formName, setFormName] = useState('');
  const [formPhone, setFormPhone] = useState('');
  const [formEmail, setFormEmail] = useState('');
  const [formRole, setFormRole] = useState('SALES');
  const [formStatus, setFormStatus] = useState('ACTIVE');
  const [formPassword, setFormPassword] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  // Invite Modal State
  const [isInviteOpen, setIsInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('SALES');
  const [inviteLoading, setInviteLoading] = useState(false);
  const [inviteError, setInviteError] = useState('');

  // Reset Password Password Popup State
  const [showTempPwd, setShowTempPwd] = useState(false);
  const [tempPwdData, setTempPwdData] = useState({ email: '', password: '' });

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const data = await userApi.getAllUsers(0, 100);
      setUsers(data.content || []);
    } catch (error) {
      console.error('Failed to fetch users:', error);
      toast.error('Không thể tải danh sách nhân sự');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // Statistics calculation
  const totalUsers = users.length;
  const activeUsers = users.filter(u => u.status === 'ACTIVE').length;
  const inactiveUsers = users.filter(u => u.status === 'INACTIVE').length;
  const lockedUsers = users.filter(u => u.status === 'LOCKED').length;

  // Formatter helpers
  const formatRole = (role) => {
    switch (role) {
      case 'SYSTEM_ADMIN':
        return 'System Admin';
      case 'OWNER':
        return 'Owner';
      case 'OPERATIONS':
        return 'Operations Staff';
      case 'SALES':
        return 'Sales Staff';
      default:
        return role || 'Nhân viên';
    }
  };

  const formatStatusText = (status) => {
    switch (status) {
      case 'ACTIVE':
        return 'Hoạt động';
      case 'INACTIVE':
        return 'Chờ kích hoạt';
      case 'LOCKED':
        return 'Bị khóa';
      default:
        return status;
    }
  };

  const getRoleClass = (role) => {
    if (role === 'SYSTEM_ADMIN') return `${styles.roleBadge} ${styles.roleAdmin}`;
    if (role === 'OWNER') return `${styles.roleBadge} ${styles.roleOwner}`;
    if (role === 'OPERATIONS') return `${styles.roleBadge} ${styles.roleOperations}`;
    return styles.roleBadge;
  };

  const getStatusBadge = (status) => {
    const s = String(status).toUpperCase();
    if (s === 'ACTIVE') return <span className={`${styles.badge} ${styles.active}`}>Hoạt động</span>;
    if (s === 'INACTIVE') return <span className={`${styles.badge} ${styles.inactive}`}>Chờ kích hoạt</span>;
    if (s === 'LOCKED') return <span className={`${styles.badge} ${styles.locked}`}>Bị khóa</span>;
    return <span className={styles.badge}>{status}</span>;
  };

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    });
  };

  // Client-side filtering
  const filteredUsers = users.filter(user => {
    const matchesSearch = 
      (user.fullName || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (user.email || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchesRole = roleFilter ? user.role === roleFilter : true;
    const matchesStatus = statusFilter ? user.status === statusFilter : true;
    return matchesSearch && matchesRole && matchesStatus;
  });

  // Client-side CSV export
  const handleExportUsers = () => {
    try {
      const headers = ['Họ và tên', 'Số điện thoại', 'Email', 'Vai trò', 'Trạng thái', 'Ngày tham gia'];
      const rows = filteredUsers.map(u => [
        u.fullName || 'Nhân viên mới',
        u.phone || '',
        u.email,
        formatRole(u.role),
        formatStatusText(u.status),
        formatDate(u.createdAt)
      ]);

      // Prepend BOM to force UTF-8 in Excel
      const csvContent = "data:text/csv;charset=utf-8,\uFEFF" 
        + [headers.join(','), ...rows.map(e => e.map(val => `"${val}"`).join(','))].join('\n');
      
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `Danh_sach_nhan_su_${new Date().toLocaleDateString('vi-VN')}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      toast.success('Xuất danh sách nhân viên thành công!');
    } catch (error) {
      console.error('Failed to export:', error);
      toast.error('Có lỗi xảy ra khi xuất danh sách');
    }
  };

  // Invite member actions
  const handleOpenInvite = () => {
    setInviteEmail('');
    setInviteRole('SALES');
    setInviteError('');
    setIsInviteOpen(true);
  };

  const handleCloseInvite = () => {
    setIsInviteOpen(false);
  };

  const handleSendInvite = async (e) => {
    e.preventDefault();
    setInviteError('');

    if (!inviteEmail.trim()) {
      setInviteError('Vui lòng nhập địa chỉ email');
      return;
    }

    setInviteLoading(true);
    try {
      await authService.inviteUser(inviteEmail.trim(), inviteRole);
      toast.success(`Đã gửi email mời đăng ký đến ${inviteEmail} thành công!`);
      setIsInviteOpen(false);
      fetchUsers(); // Refresh to show pending invite
    } catch (error) {
      console.error('Failed to invite user:', error);
      const errorMsg = error?.response?.data?.message || 'Có lỗi xảy ra khi gửi lời mời.';
      setInviteError(errorMsg);
      toast.error(errorMsg);
    } finally {
      setInviteLoading(false);
    }
  };

  // Drawer handlers
  const handleOpenDrawer = (mode, user = null) => {
    setDrawerMode(mode);
    setSelectedUser(user);
    if (user) {
      setFormName(user.fullName || '');
      setFormPhone(user.phone || '');
      setFormEmail(user.email || '');
      setFormRole(user.role || 'SALES');
      setFormStatus(user.status || 'ACTIVE');
      setFormPassword('');
    } else {
      // Create mode
      setFormName('');
      setFormPhone('');
      setFormEmail('');
      setFormRole('SALES');
      setFormStatus('ACTIVE');
      // Generate a nice random secure password initially
      setFormPassword(generateRandomPassword());
    }
    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setDrawerOpen(false);
    setSelectedUser(null);
  };

  const generateRandomPassword = () => {
    const uppers = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    const lowers = 'abcdefghijklmnopqrstuvwxyz';
    const numbers = '0123456789';
    const specials = '!@#$%^&*()';
    const all = uppers + lowers + numbers + specials;
    
    let pwd = '';
    // Ensure complexity requirements
    pwd += uppers[Math.floor(Math.random() * uppers.length)];
    pwd += lowers[Math.floor(Math.random() * lowers.length)];
    pwd += numbers[Math.floor(Math.random() * numbers.length)];
    pwd += specials[Math.floor(Math.random() * specials.length)];
    
    for (let i = 0; i < 6; i++) {
      pwd += all[Math.floor(Math.random() * all.length)];
    }
    // Shuffle
    return pwd.split('').sort(() => 0.5 - Math.random()).join('');
  };

  const handleSaveUser = async (e) => {
    e.preventDefault();
    if (!formName.trim()) {
      toast.error('Họ và tên không được để trống');
      return;
    }

    setActionLoading(true);
    try {
      if (drawerMode === 'create') {
        const payload = {
          email: formEmail.trim(),
          password: formPassword,
          fullName: formName.trim(),
          phone: formPhone.trim() || null,
          role: formRole
        };
        await userApi.createUser(payload);
        toast.success('Thêm nhân viên mới thành công!');
      } else if (drawerMode === 'edit') {
        const payload = {
          email: selectedUser.email,
          fullName: formName.trim(),
          phone: formPhone.trim() || null,
          role: formRole,
          status: formStatus
        };
        await userApi.updateUser(selectedUser.id, payload);
        toast.success('Cập nhật thông tin nhân sự thành công!');
      }
      setDrawerOpen(false);
      fetchUsers();
    } catch (error) {
      console.error('Failed to save user:', error);
      const msg = error?.response?.data?.message || 'Có lỗi xảy ra khi lưu thông tin';
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  const handleResetPassword = async () => {
    if (!selectedUser) return;
    if (!window.confirm(`Bạn có chắc chắn muốn đặt lại mật khẩu cho nhân viên ${selectedUser.fullName || selectedUser.email}?`)) {
      return;
    }

    setActionLoading(true);
    try {
      const response = await userApi.resetUserPassword(selectedUser.id);
      // Backend returns ResetPasswordResponse
      // Let's display the password in a popup modal
      setTempPwdData({
        email: selectedUser.email,
        password: response.tempPassword || 'Đã gửi qua email'
      });
      setShowTempPwd(true);
      toast.success('Đã đặt lại mật khẩu thành công!');
    } catch (error) {
      console.error('Failed to reset password:', error);
      const msg = error?.response?.data?.message || 'Không thể đặt lại mật khẩu';
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  const handleDisableUser = async () => {
    if (!selectedUser) return;
    if (!window.confirm(`Bạn có chắc chắn muốn vô hiệu hóa tài khoản của nhân viên ${selectedUser.fullName || selectedUser.email}?`)) {
      return;
    }

    setActionLoading(true);
    try {
      // API deleteUser soft deletes (status = INACTIVE)
      await userApi.deleteUser(selectedUser.id);
      toast.success('Đã vô hiệu hóa tài khoản nhân viên!');
      setDrawerOpen(false);
      fetchUsers();
    } catch (error) {
      console.error('Failed to disable user:', error);
      const msg = error?.response?.data?.message || 'Không thể vô hiệu hóa tài khoản';
      toast.error(msg);
    } finally {
      setActionLoading(false);
    }
  };

  // Role permissions definitions
  const ROLE_PERMISSIONS = {
    OWNER: [
      'Có toàn quyền quản lý hệ thống',
      'Xem và tạo đơn hàng',
      'Xem danh sách sản phẩm',
      'Xem báo cáo doanh số',
      'Quản lý kho hàng'
    ],
    SYSTEM_ADMIN: [
      'Có toàn quyền quản trị hệ thống',
      'Quản lý cấu hình toàn hệ thống'
    ],
    OPERATIONS: [
      'Xem và tạo đơn hàng',
      'Xem danh sách sản phẩm',
      'Quản lý kho hàng',
      'Không có quyền xem báo cáo doanh số'
    ],
    SALES: [
      'Xem và tạo đơn hàng',
      'Xem danh sách sản phẩm',
      'Xem báo cáo doanh số',
      'Không có quyền quản lý kho'
    ]
  };

  const currentPermissions = ROLE_PERMISSIONS[formRole] || [];

  return (
    <div className={styles.container}>
      <PageHeader 
        title="Quản lý nhân sự"
        subtitle="Quản lý tài khoản nội bộ, phân quyền và trạng thái trong tenant"
        icon={() => <Users size={20} />}
        actions={
          <div className={styles.headerActions}>
            <button className={styles.btnExport} onClick={handleExportUsers}>
              <Download size={18} />
              Xuất danh sách
            </button>
            <button className={styles.btnInvite} onClick={handleOpenInvite}>
              <Mail size={18} />
              Mời thành viên
            </button>
            <button className={styles.btnCreate} onClick={() => handleOpenDrawer('create')}>
              <Plus size={18} />
              Thêm User mới
            </button>
          </div>
        }
      />

      {/* ── Stats Grid ── */}
      <div className={styles.statsGrid}>
        <div className={`${styles.statCard} ${styles.statBlue}`}>
          <div className={styles.statIcon}>
            <Users size={24} />
          </div>
          <div className={styles.statInfo}>
            <h3>Tổng nhân sự</h3>
            <p>{totalUsers}</p>
          </div>
        </div>

        <div className={`${styles.statCard} ${styles.statGreen}`}>
          <div className={styles.statIcon}>
            <UserCheck size={24} />
          </div>
          <div className={styles.statInfo}>
            <h3>Hoạt động</h3>
            <p>{activeUsers}</p>
          </div>
        </div>

        <div className={`${styles.statCard} ${styles.statGray}`}>
          <div className={styles.statIcon}>
            <UserX size={24} />
          </div>
          <div className={styles.statInfo}>
            <h3>Không hoạt động</h3>
            <p>{inactiveUsers}</p>
          </div>
        </div>

        <div className={`${styles.statCard} ${styles.statRed}`}>
          <div className={styles.statIcon}>
            <Lock size={24} />
          </div>
          <div className={styles.statInfo}>
            <h3>Bị khóa</h3>
            <p>{lockedUsers}</p>
          </div>
        </div>
      </div>

      {/* ── Filter Bar ── */}
      <div className={styles.filterBar}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input 
            type="text"
            placeholder="Tìm theo tên hoặc email..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className={styles.searchInput}
          />
        </div>

        <div className={styles.filtersWrapper}>
          <select 
            value={roleFilter} 
            onChange={(e) => setRoleFilter(e.target.value)}
            className={styles.filterSelect}
          >
            <option value="">Tất cả vai trò</option>
            <option value="SYSTEM_ADMIN">System Admin</option>
            <option value="OWNER">Owner</option>
            <option value="OPERATIONS">Operations Staff (Vận hành)</option>
            <option value="SALES">Sales Staff (Kinh doanh)</option>
          </select>

          <select 
            value={statusFilter} 
            onChange={(e) => setStatusFilter(e.target.value)}
            className={styles.filterSelect}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Chờ kích hoạt</option>
            <option value="LOCKED">Bị khóa</option>
          </select>
        </div>
      </div>

      {/* ── User List Table ── */}
      <div className={styles.tableContainer}>
        <div className={styles.tableHeader}>
          <h3>Danh sách tài khoản ({filteredUsers.length})</h3>
          <p>Chỉ hiển thị tài khoản thuộc tenant của bạn. Nhấn View hoặc Edit để xem chi tiết và thay đổi vai trò.</p>
        </div>

        {loading ? (
          <div className={styles.emptyState}>Đang tải danh sách nhân viên...</div>
        ) : filteredUsers.length === 0 ? (
          <div className={styles.emptyState}>Không tìm thấy nhân viên nào khớp với tìm kiếm</div>
        ) : (
          <div className={styles.tableWrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Họ và tên</th>
                  <th>Email</th>
                  <th>Vai trò</th>
                  <th>Trạng thái</th>
                  <th>Ngày tham gia</th>
                  <th style={{ textAlign: 'right' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map((item) => (
                  <tr key={item.id}>
                    <td className={styles.nameCell}>
                      <div className={styles.fullName}>{item.fullName || 'Nhân viên mới'}</div>
                      <div className={styles.phoneNum}>{item.phone || '-'}</div>
                    </td>
                    <td className={styles.emailCell}>{item.email}</td>
                    <td>
                      <span className={getRoleClass(item.role)}>
                        {formatRole(item.role)}
                      </span>
                    </td>
                    <td>{getStatusBadge(item.status)}</td>
                    <td>{formatDate(item.createdAt)}</td>
                    <td className={styles.actionsCell}>
                      <button 
                        className={styles.btnActionView}
                        onClick={() => handleOpenDrawer('view', item)}
                        title="Xem chi tiết"
                      >
                        <Eye size={16} />
                        <span>View</span>
                      </button>
                      <button 
                        className={styles.btnActionEdit}
                        onClick={() => handleOpenDrawer('edit', item)}
                        title="Chỉnh sửa"
                      >
                        <Pencil size={16} />
                        <span>Edit</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Slide-out Edit/View Drawer ── */}
      <div 
        className={`${styles.drawerBackdrop} ${drawerOpen ? styles.drawerBackdropOpen : ''}`} 
        onClick={handleCloseDrawer}
      />
      
      <div className={`${styles.drawer} ${drawerOpen ? styles.drawerOpen : ''}`}>
        <div className={styles.drawerHeader}>
          <h2>
            {drawerMode === 'create' && 'Thêm User mới'}
            {drawerMode === 'edit' && 'Chỉnh sửa tài khoản'}
            {drawerMode === 'view' && 'Thông tin tài khoản'}
          </h2>
          <button className={styles.drawerCloseBtn} onClick={handleCloseDrawer}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSaveUser} className={styles.drawerBody}>
          <div className={styles.drawerScrollArea}>
            <div className={styles.formSection}>
              <div className={styles.formGroup}>
                <label className={styles.label}>Họ và tên</label>
                <input
                  type="text"
                  placeholder="Ví dụ: Nguyễn Văn An"
                  className={styles.input}
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  disabled={drawerMode === 'view' || actionLoading}
                  required
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Số điện thoại</label>
                <input
                  type="text"
                  placeholder="Ví dụ: 0901 234 567"
                  className={styles.input}
                  value={formPhone}
                  onChange={(e) => setFormPhone(e.target.value)}
                  disabled={drawerMode === 'view' || actionLoading}
                />
              </div>

              <div className={styles.formGroup}>
                <label className={styles.label}>Email</label>
                <input
                  type="email"
                  placeholder="name@omnisales.vn"
                  className={styles.input}
                  value={formEmail}
                  onChange={(e) => setFormEmail(e.target.value)}
                  disabled={drawerMode !== 'create' || actionLoading}
                  required
                />
              </div>

              {drawerMode === 'create' && (
                <div className={styles.formGroup}>
                  <label className={styles.label}>Mật khẩu tài khoản</label>
                  <div className={styles.pwdInputWrapper}>
                    <input
                      type="text"
                      className={styles.input}
                      value={formPassword}
                      onChange={(e) => setFormPassword(e.target.value)}
                      disabled={actionLoading}
                      required
                    />
                    <button 
                      type="button" 
                      className={styles.btnGenPwd}
                      onClick={() => setFormPassword(generateRandomPassword())}
                      title="Tạo mật khẩu ngẫu nhiên"
                    >
                      Tạo mới
                    </button>
                  </div>
                  <span className={styles.fieldHelper}>
                    Mật khẩu có độ dài tối thiểu 6 ký tự, khuyên dùng mật khẩu mạnh bao gồm chữ hoa, số và ký tự đặc biệt.
                  </span>
                </div>
              )}

              {drawerMode !== 'create' && (
                <div className={styles.formGroup}>
                  <label className={styles.label}>Ngày tham gia</label>
                  <input
                    type="text"
                    className={styles.input}
                    value={formatDate(selectedUser?.createdAt)}
                    disabled
                  />
                </div>
              )}
            </div>

            <div className={styles.divider} />

            <div className={styles.formSection}>
              <h3 className={styles.sectionTitle}>VAI TRÒ & TRẠNG THÁI</h3>
              
              <div className={styles.formRow}>
                <div className={styles.formGroup} style={{ flex: 1 }}>
                  <label className={styles.label}>Vai trò</label>
                  <select
                    className={styles.select}
                    value={formRole}
                    onChange={(e) => setFormRole(e.target.value)}
                    disabled={drawerMode === 'view' || actionLoading}
                  >
                    <option value="SALES">Kinh doanh (Sales)</option>
                    <option value="OPERATIONS">Vận hành (Operations)</option>
                    <option value="OWNER">Shop Owner</option>
                    <option value="SYSTEM_ADMIN">System Admin</option>
                  </select>
                </div>

                {drawerMode !== 'create' && (
                  <div className={styles.formGroup} style={{ flex: 1 }}>
                    <label className={styles.label}>Trạng thái</label>
                    <select
                      className={styles.select}
                      value={formStatus}
                      onChange={(e) => setFormStatus(e.target.value)}
                      disabled={drawerMode === 'view' || actionLoading}
                    >
                      <option value="ACTIVE">Hoạt động</option>
                      <option value="INACTIVE">Chờ kích hoạt</option>
                      <option value="LOCKED">Bị khóa</option>
                    </select>
                  </div>
                )}
              </div>
            </div>

            <div className={styles.divider} />

            {/* ── Corresponding Permissions ── */}
            <div className={styles.formSection}>
              <h3 className={styles.sectionTitle}>QUYỀN HẠN TƯƠNG ỨNG</h3>
              <div className={styles.permissionBox}>
                {currentPermissions.length === 0 ? (
                  <div className={styles.noPermissionText}>Không có thông tin quyền hạn được định nghĩa.</div>
                ) : (
                  <ul className={styles.permissionList}>
                    {currentPermissions.map((perm, idx) => {
                      const isNegative = perm.startsWith('Không');
                      return (
                        <li key={idx} className={styles.permissionItem}>
                          {isNegative ? (
                            <ShieldAlert size={16} className={styles.iconNegativePerm} />
                          ) : (
                            <Check size={16} className={styles.iconPositivePerm} />
                          )}
                          <span className={isNegative ? styles.textNegativePerm : ''}>
                            {perm}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </div>
          </div>

          {/* ── Drawer Footer ── */}
          <div className={styles.drawerFooter}>
            {drawerMode === 'edit' && (
              <div className={styles.leftFooterBtns}>
                <button
                  type="button"
                  className={styles.btnResetPwd}
                  onClick={handleResetPassword}
                  disabled={actionLoading}
                  title="Gửi đặt lại mật khẩu ngẫu nhiên cho nhân viên"
                >
                  <Key size={16} />
                  Gửi đặt lại mật khẩu
                </button>
                <button
                  type="button"
                  className={styles.btnBlock}
                  onClick={handleDisableUser}
                  disabled={actionLoading}
                  title="Vô hiệu hóa ngay lập tức tài khoản này"
                >
                  <UserX size={16} />
                  Vô hiệu hóa
                </button>
              </div>
            )}

            <div className={styles.rightFooterBtns}>
              <button 
                type="button" 
                className={styles.btnCancel} 
                onClick={handleCloseDrawer}
                disabled={actionLoading}
              >
                {drawerMode === 'view' ? 'Đóng' : 'Hủy'}
              </button>
              
              {drawerMode !== 'view' && (
                <button 
                  type="submit" 
                  className={styles.btnSave}
                  disabled={actionLoading}
                >
                  {actionLoading ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              )}
            </div>
          </div>
        </form>
      </div>

      {/* ── Invite User Modal ── */}
      {isInviteOpen && (
        <div className={styles.modalOverlay} onClick={handleCloseInvite}>
          <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h2>Mời nhân viên mới</h2>
              <button className={styles.closeBtn} onClick={handleCloseInvite}>
                <X size={20} />
              </button>
            </div>
            
            <form onSubmit={handleSendInvite} className={styles.modalForm}>
              <div className={styles.formGroup}>
                <label htmlFor="invite-email" className={styles.label}>Email người được mời</label>
                <input
                  type="email"
                  id="invite-email"
                  placeholder="name@omnisales.vn"
                  className={`${styles.input} ${inviteError ? styles.inputError : ''}`}
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  disabled={inviteLoading}
                  required
                />
              </div>

              <div className={styles.formGroup}>
                <label htmlFor="invite-role" className={styles.label}>Vai trò gán sẵn</label>
                <select
                  id="invite-role"
                  className={styles.select}
                  value={inviteRole}
                  onChange={(e) => setInviteRole(e.target.value)}
                  disabled={inviteLoading}
                >
                  <option value="SALES">Sales Staff (Nhân viên bán hàng)</option>
                  <option value="OPERATIONS">Operations Staff (Nhân viên vận hành)</option>
                  <option value="SYSTEM_ADMIN">System Admin (Quản trị hệ thống)</option>
                  <option value="OWNER">Shop Owner (Chủ shop)</option>
                </select>
              </div>

              {inviteError && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#ef4444', fontSize: '13px', marginTop: '4px' }}>
                  <AlertTriangle size={16} />
                  <p className={styles.errorText}>{inviteError}</p>
                </div>
              )}

              <div className={styles.modalFooter}>
                <button type="button" className={styles.btnCancel} onClick={handleCloseInvite} disabled={inviteLoading}>
                  Hủy
                </button>
                <button type="submit" className={styles.btnSubmit} disabled={inviteLoading}>
                  {inviteLoading && <span className={styles.loadingSpinner} />}
                  {inviteLoading ? 'Đang gửi mời...' : 'Gửi lời mời'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Password Reset Success Modal ── */}
      {showTempPwd && (
        <div className={styles.modalOverlay} onClick={() => setShowTempPwd(false)}>
          <div className={styles.modalContent} onClick={(e) => e.stopPropagation()} style={{ maxWidth: '420px' }}>
            <div className={styles.modalHeader} style={{ borderBottom: 'none', paddingBottom: '0' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#16a34a' }}>
                <CheckCircle2 size={24} />
                <h2 style={{ fontSize: '16px', color: '#15803d' }}>Đặt lại mật khẩu thành công!</h2>
              </div>
              <button className={styles.closeBtn} onClick={() => setShowTempPwd(false)}>
                <X size={20} />
              </button>
            </div>
            
            <div style={{ padding: '20px 24px' }}>
              <p style={{ margin: '0 0 16px', fontSize: '14px', color: '#475569', lineHeight: '1.5' }}>
                Mật khẩu tạm thời cho tài khoản <strong>{tempPwdData.email}</strong> đã được đặt lại thành công. 
                Vui lòng cung cấp mật khẩu này cho nhân sự để đăng nhập:
              </p>
              
              <div style={{ 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'space-between', 
                backgroundColor: '#f1f5f9', 
                border: '1px solid #cbd5e1', 
                borderRadius: '8px', 
                padding: '12px 16px',
                marginBottom: '20px'
              }}>
                <code style={{ fontSize: '16px', fontWeight: '600', color: '#0f172a', fontFamily: 'monospace' }}>
                  {tempPwdData.password}
                </code>
                <button 
                  type="button" 
                  className={styles.btnCopy}
                  onClick={() => {
                    navigator.clipboard.writeText(tempPwdData.password);
                    toast.success('Đã sao chép mật khẩu!');
                  }}
                  title="Sao chép mật khẩu"
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#64748b',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    padding: '4px',
                    borderRadius: '4px',
                    transition: 'all 0.2s'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#e2e8f0'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  <Copy size={16} />
                </button>
              </div>

              <button 
                type="button" 
                className={styles.btnSubmit}
                onClick={() => setShowTempPwd(false)}
                style={{ width: '100%', justifyContent: 'center' }}
              >
                Xác nhận
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserListPage;
