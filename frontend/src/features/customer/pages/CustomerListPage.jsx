import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../app/router/routes';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import useDebounce from '../../../shared/hooks/useDebounce';
import {
  Plus, Search, Eye, Pencil, Trash2,
  FileDown, UserCheck, UserX, TrendingUp, Phone, Mail, Users, RefreshCw
} from 'lucide-react';
import PageHeader from '../../../shared/components/PageHeader';
import Pagination from '../../../shared/components/Pagination';
import customerService from '../services/customerService';
import orderService from '../../order/services/orderService';
import ExportCustomersModal from '../components/ExportCustomersModal';
import styles from './CustomerListPage.module.css';

const PAGE_SIZE = 20;

const GENDER_MAP = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

const CustomerListPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const role = user?.role;

  const [searchInput, setSearchInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [genderFilter, setGenderFilter] = useState('ALL');
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [deleteId, setDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [stats, setStats] = useState({ totalCustomers: 0, activeCustomers: 0 });
  const [aggregated, setAggregated] = useState({ totalOrders: 0, totalSpent: 0 });
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [uncustomerdCount, setUncustomerdCount] = useState(0);
  const [syncing, setSyncing] = useState(false);
  const [confirmSync, setConfirmSync] = useState(false);

  const debouncedSearch = useDebounce(searchInput, 500);

  const canCreate = role === ROLES.OWNER || role === ROLES.SALES;
  const canEdit = role === ROLES.OWNER || role === ROLES.SALES;
  const canDelete = role === ROLES.OWNER;

  const fetchCustomers = useCallback(async () => {
    setLoading(true);
    try {
      const data = await customerService.getPageWithOrderCustomers(page, PAGE_SIZE, debouncedSearch, statusFilter, genderFilter);
      setCustomers(data.content || []);
      setTotalPages(data.totalPages || 0);
      setTotalElements(data.totalElements || 0);
      setAggregated({
        totalOrders: data.aggregated?.totalOrders || 0,
        totalSpent: data.aggregated?.totalSpent || 0,
      });
    } catch (error) {
      console.error('Failed to fetch customers:', error);
    } finally {
      setLoading(false);
    }
  }, [page, debouncedSearch, statusFilter, genderFilter]);

  useEffect(() => { fetchCustomers(); }, [fetchCustomers]);

  const fetchStats = useCallback(async () => {
    try {
      const data = await customerService.getStats();
      setStats(data);
    } catch (error) {
      console.error('Failed to fetch stats:', error);
    }
  }, []);

  useEffect(() => { fetchStats(); }, [fetchStats]);

  const fetchUncustomerdCount = useCallback(async () => {
    try {
      const data = await orderService.getUncustomerdCount();
      setUncustomerdCount(data?.count ?? 0);
    } catch (error) {
      console.error('Failed to fetch uncustomerd count:', error);
    }
  }, []);

  useEffect(() => { fetchUncustomerdCount(); }, [fetchUncustomerdCount]);

  useEffect(() => { setPage(0); }, [debouncedSearch, statusFilter, genderFilter]);

  const handleSyncFromOrders = async () => {
    setConfirmSync(false);
    setSyncing(true);
    try {
      const result = await customerService.syncFromOrders();
      const updatedCount = result?.updatedCount ?? 0;
      await Promise.all([fetchCustomers(), fetchStats(), fetchUncustomerdCount()]);
      // eslint-disable-next-line no-alert
      alert(updatedCount > 0
        ? `Đã đồng bộ ${updatedCount} đơn hàng về danh sách khách hàng.`
        : 'Tất cả đơn hàng đã có khách hàng. Không có gì để đồng bộ.');
    } catch (error) {
      console.error('Failed to sync customers from orders:', error);
      // eslint-disable-next-line no-alert
      alert('Đồng bộ thất bại. Vui lòng thử lại.');
    } finally {
      setSyncing(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteId) return;
    setDeleting(true);
    try {
      await customerService.delete(deleteId);
      setDeleteId(null);
      fetchCustomers();
      fetchStats();
    } catch (error) {
      console.error('Failed to delete customer:', error);
    } finally {
      setDeleting(false);
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
    return new Date(dateString).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  };

  const formatCurrency = (amount) => {
    if (amount === null || amount === undefined) return '-';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(amount);
  };

  const getInitials = (name) => {
    if (!name) return '?';
    const parts = name.trim().split(' ');
    return parts[parts.length - 1].charAt(0).toUpperCase();
  };

  const getGenderLabel = (gender) => GENDER_MAP[gender] || gender || '-';

  const statsItems = [
    { label: 'Tổng khách hàng', value: stats.totalCustomers, icon: Users, color: 'slate' },
    { label: 'Đang hoạt động', value: stats.activeCustomers, icon: UserCheck, color: 'teal' },
    { label: 'Tổng đơn hàng (trong trang + KH từ đơn)', value: aggregated.totalOrders.toLocaleString('vi-VN'), icon: TrendingUp, color: 'blue' },
    { label: 'Tổng chi tiêu (trong trang + KH từ đơn)', value: formatCurrency(aggregated.totalSpent), icon: TrendingUp, color: 'violet', isVND: true },
  ];

  return (
    <div className={`${styles.page} product-workspace`}>
      {/* Header */}
      <div className={styles.pageHeader}>
        <div className={styles.headerLeft}>
          <div className={styles.headerIcon}>
            <Users size={20} />
          </div>
          <div>
            <h1 className={styles.pageTitle}>Khách hàng</h1>
            <p className={styles.pageSubtitle}>Quản lý thông tin và lịch sử mua hàng của khách hàng</p>
          </div>
        </div>
        <div className={styles.headerActions}>
          <button
            className={`${styles.btnOutline} ${styles.btnSync}`}
            onClick={() => setConfirmSync(true)}
            disabled={syncing}
            title="Kéo khách hàng từ đơn hàng về danh sách khách hàng"
          >
            <RefreshCw size={15} className={syncing ? styles.spinning : ''} />
            Đồng bộ KH từ đơn
            {uncustomerdCount > 0 && (
              <span className={styles.syncBadge}>{uncustomerdCount}</span>
            )}
          </button>
          <button className={styles.btnOutline} onClick={() => setIsExportModalOpen(true)}>
            <FileDown size={15} />
            Xuất Excel
          </button>
          {canCreate && (
            <button className={styles.btnPrimary} onClick={() => navigate(ROUTES.CUSTOMER_CREATE)}>
              <Plus size={15} />
              Thêm khách hàng
            </button>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className={styles.statsGrid}>
        {statsItems.map((s) => (
          <div key={s.label} className={styles.statCard}>
            <div className={styles.statHeader}>
              <span className={styles.statLabel}>{s.label}</span>
              <div className={`${styles.statIconWrap} ${styles[`statIcon_${s.color}`]}`}>
                <s.icon size={15} />
              </div>
            </div>
            <p className={`${styles.statValue} ${s.isVND ? styles.statValueSmall : ''}`}>{s.value}</p>
          </div>
        ))}
      </div>

      {/* Filters Card */}
      <div className={styles.filterCard}>
        <div className={styles.filterRow}>
          <div className={styles.searchWrapper}>
            <Search size={16} className={styles.searchIcon} />
            <input
              type="text"
              placeholder="Tìm theo tên, mã KH, email, SĐT..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className={styles.searchInput}
            />
          </div>
          <select className={styles.select} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Hoạt động</option>
            <option value="INACTIVE">Không hoạt động</option>
          </select>
          <select className={styles.select} value={genderFilter} onChange={(e) => setGenderFilter(e.target.value)}>
            <option value="ALL">Tất cả giới tính</option>
            <option value="MALE">Nam</option>
            <option value="FEMALE">Nữ</option>
            <option value="OTHER">Khác</option>
          </select>
        </div>
      </div>

      {/* Table Card */}
      <div className={styles.tableCard}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th className={styles.thPl}>Mã KH</th>
              <th>Tên khách hàng</th>
              <th>Liên hệ</th>
              <th>Giới tính</th>
              <th className={styles.textRight}>Đơn hàng</th>
              <th className={styles.textRight}>Chi tiêu</th>
              <th>Trạng thái</th>
              <th className={styles.thAction}></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} className={styles.emptyCell}>Đang tải...</td>
              </tr>
            ) : customers.length === 0 ? (
              <tr>
                <td colSpan={8} className={styles.emptyCell}>
                  {debouncedSearch ? 'Không tìm thấy khách hàng nào' : 'Chưa có khách hàng nào'}
                </td>
              </tr>
            ) : (
              customers.map((c) => (
                <tr key={c.id} className={styles.tableRow}>
                  <td className={styles.thPl}>
                    <span className={styles.customerCode}>{c.code || '-'}</span>
                  </td>
                  <td>
                    <div className={styles.customerInfo}>
                      <div className={styles.avatar}>
                        {getInitials(c.fullName)}
                      </div>
                      <div>
                        <p className={styles.customerName}>
                          {c.fullName || '-'}
                          {c.fromOrders && (
                            <span className={styles.fromOrdersBadge} title="Khách hàng này chỉ tồn tại trong đơn hàng, không có trong danh sách khách hàng hiện tại">
                              KH từ đơn hàng
                            </span>
                          )}
                        </p>
                        <p className={styles.customerMeta}>Địa chỉ: {formatAddress(c.address)}</p>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className={styles.contactInfo}>
                      <span className={styles.contactRow}>
                        <Phone size={11} />
                        {c.phone || '-'}
                      </span>
                      <span className={`${styles.contactRow} ${styles.contactRowMuted}`}>
                        <Mail size={11} />
                        {c.email || '-'}
                      </span>
                    </div>
                  </td>
                  <td>
                    <span className={`${styles.genderBadge} ${c.gender === 'MALE' ? styles.genderMale : c.gender === 'FEMALE' ? styles.genderFemale : styles.genderOther}`}>
                      {getGenderLabel(c.gender)}
                    </span>
                  </td>
                  <td className={`${styles.textRight} ${styles.cellNum}`}>{c.orderCount || 0}</td>
                  <td className={`${styles.textRight} ${styles.cellNum}`}>{formatCurrency(c.totalSpent)}</td>
                  <td>
                    {c.isActive ? (
                      <span className={`${styles.statusBadge} ${styles.statusActive}`}>
                        <UserCheck size={11} />
                        Hoạt động
                      </span>
                    ) : (
                      <span className={`${styles.statusBadge} ${styles.statusInactive}`}>
                        <UserX size={11} />
                        Không HĐ
                      </span>
                    )}
                  </td>
                  <td className={styles.tdAction}>
                    <div className={styles.actionGroup}>
                      <button
                        className={styles.actionBtn}
                        onClick={() => navigate(ROUTES.CUSTOMER_DETAIL.replace(':id', c.id))}
                        title="Xem chi tiết"
                      >
                        <Eye size={14} />
                      </button>
                      {canEdit && (
                        <button
                          className={styles.actionBtn}
                          onClick={() => navigate(ROUTES.CUSTOMER_EDIT.replace(':id', c.id))}
                          title="Chỉnh sửa"
                        >
                          <Pencil size={14} />
                        </button>
                      )}
                      {canDelete && (
                        <button
                          className={`${styles.actionBtn} ${styles.actionBtnDanger}`}
                          onClick={() => setDeleteId(c.id)}
                          title="Xóa"
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={PAGE_SIZE}
          currentCount={customers.length}
          itemLabel="khách hàng"
          onPageChange={setPage}
        />
        <div className={styles.tableFooter} hidden>
          <span className={styles.footerInfo}>
            Hiển thị {customers.length} / {totalElements} khách hàng
          </span>
          {totalPages > 1 && (
            <div className={styles.pagination}>
              <button className={styles.pageBtn} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                ‹
              </button>
              <span className={styles.pageInfo}>Trang {page + 1} / {totalPages}</span>
              <button className={styles.pageBtn} disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
                ›
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Delete Modal */}
      {deleteId && (
        <div className={styles.modalOverlay} onClick={() => setDeleteId(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Xác nhận xóa</h3>
            <p className={styles.modalBody}>Bạn có chắc chắn muốn xóa khách hàng này? Hành động này không thể hoàn tác.</p>
            <div className={styles.modalActions}>
              <button className={styles.cancelBtn} onClick={() => setDeleteId(null)} disabled={deleting}>Hủy</button>
              <button className={styles.confirmDeleteBtn} onClick={handleDelete} disabled={deleting}>
                {deleting ? 'Đang xóa...' : 'Xóa'}
              </button>
            </div>
          </div>
        </div>
      )}

      <ExportCustomersModal isOpen={isExportModalOpen} onClose={() => setIsExportModalOpen(false)} />

      {/* Confirm sync modal */}
      {confirmSync && (
        <div className={styles.modalOverlay} onClick={() => setConfirmSync(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Đồng bộ khách hàng từ đơn hàng</h3>
            <p className={styles.modalBody}>
              {uncustomerdCount > 0 ? (
                <>
                  Có <b>{uncustomerdCount}</b> đơn hàng chưa gắn khách hàng trong hệ thống. Hệ thống sẽ tự động:
                  <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
                    <li>Tìm khách hàng trùng tên + số điện thoại trong DB để tái sử dụng.</li>
                    <li>Tạo khách hàng mới cho các đơn còn lại (tên mặc định: <b>Khách vãng lai</b>).</li>
                    <li>Gắn <b>customer_id</b> cho từng đơn hàng để liệt kê trên trang này.</li>
                  </ul>
                </>
              ) : (
                'Hiện tại tất cả đơn hàng đã có khách hàng. Bạn có thể chạy lại để kiểm tra.'
              )}
            </p>
            <div className={styles.modalActions}>
              <button className={styles.cancelBtn} onClick={() => setConfirmSync(false)} disabled={syncing}>Hủy</button>
              <button
                className={styles.confirmDeleteBtn}
                onClick={handleSyncFromOrders}
                disabled={syncing}
              >
                {syncing ? 'Đang đồng bộ...' : 'Đồng bộ ngay'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CustomerListPage;
