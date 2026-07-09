import { useState, useEffect, useCallback } from 'react';
import { 
  ClipboardList, AlertTriangle, Lock, Search, Eye, Pencil, Trash, X, 
  Calendar, RefreshCw, ChevronLeft, ChevronRight, Check, ShieldAlert,
  Info, Database
} from 'lucide-react';
import { toast } from 'react-toastify';
import PageHeader from '../../../shared/components/PageHeader';
import auditApi from '../../../api/auditApi';
import styles from './SystemLogPage.module.css';

const SystemLogPage = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);

  // Stats
  const [stats, setStats] = useState({
    total: 0,
    errors: 0,
    warnings: 0,
    logins: 0
  });

  // Filters State
  const [query, setQuery] = useState('');
  const [type, setType] = useState('ALL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Active filters for api query
  const [activeFilters, setActiveFilters] = useState({
    query: '',
    type: 'ALL',
    startDate: '',
    endDate: ''
  });

  // Modal / Dialog States
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedLog, setSelectedLog] = useState(null);

  // Edit Form State
  const [editMessage, setEditMessage] = useState('');
  const [editType, setEditType] = useState('INFO');
  const [editUser, setEditUser] = useState('');
  const [editIp, setEditIp] = useState('');
  const [editDetails, setEditDetails] = useState('');
  const [submitLoading, setSubmitLoading] = useState(false);

  // Fetch stats (all logs count for summary dashboard cards)
  const fetchStats = useCallback(async () => {
    try {
      // Fetch up to 1000 items to compute counts
      const res = await auditApi.getLogs({ size: 1000 });
      const allLogs = res.content || [];
      setStats({
        total: allLogs.length,
        errors: allLogs.filter(l => l.type === 'ERROR').length,
        warnings: allLogs.filter(l => l.type === 'WARNING').length,
        logins: allLogs.filter(l => l.type === 'LOGIN').length
      });
    } catch (error) {
      console.error('Failed to fetch stats:', error);
    }
  }, []);

  // Fetch paginated logs
  const fetchLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page: currentPage,
        size: pageSize,
        query: activeFilters.query,
        type: activeFilters.type,
        startDate: activeFilters.startDate,
        endDate: activeFilters.endDate
      };
      const res = await auditApi.getLogs(params);
      setLogs(res.content || []);
      setTotalElements(res.totalElements || 0);
      setTotalPages(res.totalPages || 1);
    } catch (error) {
      console.error('Failed to fetch logs:', error);
      toast.error('Không thể tải danh sách nhật ký hệ thống');
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize, activeFilters]);

  // Initial fetch and fetch on page/filter changes
  useEffect(() => {
    fetchLogs();
  }, [fetchLogs]);

  // Fetch stats initially and whenever logs change
  useEffect(() => {
    fetchStats();
  }, [logs, fetchStats]);

  // Apply filters
  const handleSearch = (e) => {
    e.preventDefault();
    setCurrentPage(0);
    setActiveFilters({ query, type, startDate, endDate });
  };

  // Reset filters
  const handleReset = () => {
    setQuery('');
    setType('ALL');
    setStartDate('');
    setEndDate('');
    setCurrentPage(0);
    setActiveFilters({ query: '', type: 'ALL', startDate: '', endDate: '' });
    toast.info('Đã đặt lại các bộ lọc');
  };

  // Open Log Details Modal
  const handleOpenDetail = (log) => {
    setSelectedLog(log);
    setDetailModalOpen(true);
  };

  // Open Log Edit Modal
  const handleOpenEdit = (log) => {
    setSelectedLog(log);
    setEditMessage(log.message || '');
    setEditType(log.type || 'INFO');
    setEditUser(log.user || '');
    setEditIp(log.ip || '');
    setEditDetails(log.details || '');
    setEditModalOpen(true);
  };

  // Handle Edit Submit
  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!editMessage.trim()) {
      toast.error('Nội dung thông điệp không được để trống');
      return;
    }

    setSubmitLoading(true);
    try {
      const updatedData = {
        message: editMessage,
        type: editType,
        user: editUser,
        ip: editIp,
        details: editDetails
      };
      await auditApi.updateLog(selectedLog.id, updatedData);
      toast.success('Cập nhật thông tin nhật ký thành công');
      setEditModalOpen(false);
      fetchLogs();
    } catch (error) {
      console.error('Failed to update log:', error);
      toast.error('Cập nhật nhật ký thất bại');
    } finally {
      setSubmitLoading(false);
    }
  };

  // Open Delete Confirmation Dialog
  const handleOpenDelete = (log) => {
    setSelectedLog(log);
    setDeleteDialogOpen(true);
  };

  // Handle Delete Confirm
  const handleDeleteConfirm = async () => {
    try {
      await auditApi.deleteLog(selectedLog.id);
      toast.success('Đã xóa dòng nhật ký thành công');
      setDeleteDialogOpen(false);
      // If current page becomes empty after deletion, go back a page
      if (logs.length === 1 && currentPage > 0) {
        setCurrentPage(currentPage - 1);
      } else {
        fetchLogs();
      }
    } catch (error) {
      console.error('Failed to delete log:', error);
      toast.error('Xóa nhật ký thất bại');
    }
  };

  // Helper: Format log type badge class
  const getTypeBadgeClass = (logType) => {
    switch (logType) {
      case 'ERROR':
        return `${styles.badge} ${styles.badgeError}`;
      case 'WARNING':
        return `${styles.badge} ${styles.badgeWarning}`;
      case 'LOGIN':
        return `${styles.badge} ${styles.badgeLogin}`;
      case 'INFO':
        return `${styles.badge} ${styles.badgeInfo}`;
      default:
        return styles.badge;
    }
  };

  // Helper: Format log type translation
  const translateType = (logType) => {
    switch (logType) {
      case 'ERROR': return 'Lỗi';
      case 'WARNING': return 'Cảnh báo';
      case 'LOGIN': return 'Đăng nhập';
      case 'INFO': return 'Thông tin';
      default: return logType;
    }
  };

  // Helper: Format Timestamp
  const formatDateTime = (timestamp) => {
    if (!timestamp) return '-';
    const date = new Date(timestamp);
    return date.toLocaleString('vi-VN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  return (
    <div className={styles.container}>
      <PageHeader 
        title="Logs Hệ Thống" 
        subtitle="Giám sát và quản trị lịch sử hoạt động, đăng nhập và các sự cố lỗi hệ thống."
        icon={() => <ShieldAlert size={20} />}
        actions={
          <button onClick={fetchLogs} className={styles.btnRefresh} title="Tải lại danh sách">
            <RefreshCw size={16} className={loading ? styles.spinner : ''} />
            <span>Tải lại</span>
          </button>
        }
      />

      {/* Stats Cards Dashboard */}
      <div className={styles.statsGrid}>
        <div className={`${styles.statCard} ${styles.statTotal}`}>
          <div className={styles.statIcon}><ClipboardList size={22} /></div>
          <div className={styles.statInfo}>
            <h3>Tổng số logs</h3>
            <p>{stats.total}</p>
          </div>
        </div>
        <div className={`${styles.statCard} ${styles.statError}`}>
          <div className={styles.statIcon}><AlertTriangle size={22} /></div>
          <div className={styles.statInfo}>
            <h3>Nhật ký Lỗi</h3>
            <p>{stats.errors}</p>
          </div>
        </div>
        <div className={`${styles.statCard} ${styles.statWarning}`}>
          <div className={styles.statIcon}><AlertTriangle size={22} /></div>
          <div className={styles.statInfo}>
            <h3>Cảnh báo</h3>
            <p>{stats.warnings}</p>
          </div>
        </div>
        <div className={`${styles.statCard} ${styles.statLogin}`}>
          <div className={styles.statIcon}><Lock size={22} /></div>
          <div className={styles.statInfo}>
            <h3>Đăng nhập</h3>
            <p>{stats.logins}</p>
          </div>
        </div>
      </div>

      {/* Filter panel */}
      <form onSubmit={handleSearch} className={styles.filterBar}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input 
            type="text" 
            placeholder="Tìm kiếm thông điệp, người thực hiện, IP..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className={styles.searchInput}
          />
        </div>
        <div className={styles.filtersWrapper}>
          <div className={styles.filterSelectGroup}>
            <label className={styles.filterLabel}>Loại log</label>
            <select 
              value={type} 
              onChange={(e) => setType(e.target.value)}
              className={styles.filterSelect}
            >
              <option value="ALL">Tất cả loại</option>
              <option value="LOGIN">Đăng nhập</option>
              <option value="ERROR">Lỗi hệ thống</option>
              <option value="WARNING">Cảnh báo</option>
              <option value="INFO">Thông tin</option>
            </select>
          </div>
          
          <div className={styles.filterSelectGroup}>
            <label className={styles.filterLabel}>Từ ngày</label>
            <input 
              type="date" 
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className={styles.filterDateInput}
            />
          </div>

          <div className={styles.filterSelectGroup}>
            <label className={styles.filterLabel}>Đến ngày</label>
            <input 
              type="date" 
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className={styles.filterDateInput}
            />
          </div>

          <div className={styles.filterActions}>
            <button type="submit" className={styles.btnSearch}>
              Tìm kiếm
            </button>
            <button type="button" onClick={handleReset} className={styles.btnReset}>
              Đặt lại
            </button>
          </div>
        </div>
      </form>

      {/* Logs Table Card */}
      <div className={styles.tableCard}>
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.th} style={{ width: '180px' }}>Thời gian</th>
                <th className={styles.th} style={{ width: '130px' }}>Loại log</th>
                <th className={styles.th}>Thông điệp hệ thống</th>
                <th className={styles.th} style={{ width: '180px' }}>Người thực hiện</th>
                <th className={styles.th} style={{ width: '130px' }}>Địa chỉ IP</th>
                <th className={styles.th} style={{ width: '120px', textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="6" className={styles.loadingRow}>
                    <div className={styles.spinnerWrapper}>
                      <RefreshCw size={24} className={styles.spinner} />
                      <span>Đang tải danh sách nhật ký...</span>
                    </div>
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan="6" className={styles.emptyRow}>
                    <Info size={36} className={styles.emptyIcon} />
                    <p>Không tìm thấy nhật ký hệ thống phù hợp với bộ lọc</p>
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className={styles.tr}>
                    <td className={styles.td}>{formatDateTime(log.timestamp)}</td>
                    <td className={styles.td}>
                      <span className={getTypeBadgeClass(log.type)}>
                        {translateType(log.type)}
                      </span>
                    </td>
                    <td className={`${styles.td} ${styles.tdMessage}`} title={log.message}>
                      {log.message}
                    </td>
                    <td className={styles.td}>
                      <span className={styles.userBadge}>{log.user || 'SYSTEM'}</span>
                    </td>
                    <td className={styles.td}>
                      <code className={styles.ipCode}>{log.ip || '0.0.0.0'}</code>
                    </td>
                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      <div className={styles.actionGroup}>
                        <button 
                          onClick={() => handleOpenDetail(log)} 
                          className={`${styles.actionBtn} ${styles.actionBtnView}`}
                          title="Xem chi tiết"
                        >
                          <Eye size={16} />
                        </button>
                        <button 
                          onClick={() => handleOpenEdit(log)} 
                          className={`${styles.actionBtn} ${styles.actionBtnEdit}`}
                          title="Chỉnh sửa log"
                        >
                          <Pencil size={16} />
                        </button>
                        <button 
                          onClick={() => handleOpenDelete(log)} 
                          className={`${styles.actionBtn} ${styles.actionBtnDelete}`}
                          title="Xóa log"
                        >
                          <Trash size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination bar */}
        {!loading && logs.length > 0 && (
          <div className={styles.paginationBar}>
            <div className={styles.paginationInfo}>
              Hiển thị {logs.length} trên tổng số <strong>{totalElements}</strong> dòng logs
            </div>
            <div className={styles.paginationControls}>
              <button 
                onClick={() => setCurrentPage(prev => Math.max(prev - 1, 0))}
                disabled={currentPage === 0}
                className={styles.paginationBtn}
              >
                <ChevronLeft size={16} />
              </button>
              <span className={styles.paginationText}>
                Trang <strong>{currentPage + 1}</strong> / {totalPages}
              </span>
              <button 
                onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages - 1))}
                disabled={currentPage === totalPages - 1}
                className={styles.paginationBtn}
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 1. VIEW DETAILS MODAL */}
      {detailModalOpen && selectedLog && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent} style={{ maxWidth: '750px' }}>
            <div className={styles.modalHeader}>
              <div className={styles.modalHeaderTitle}>
                <Database size={20} className={styles.modalTitleIcon} />
                <h2>Chi tiết nhật ký hệ thống #{selectedLog.id}</h2>
              </div>
              <button onClick={() => setDetailModalOpen(false)} className={styles.modalCloseBtn}>
                <X size={20} />
              </button>
            </div>
            
            <div className={styles.modalBody}>
              <div className={styles.detailGrid}>
                <div className={styles.detailItem}>
                  <span className={styles.detailLabel}>Thời gian</span>
                  <span className={styles.detailVal}>{formatDateTime(selectedLog.timestamp)}</span>
                </div>
                <div className={styles.detailItem}>
                  <span className={styles.detailLabel}>Loại log</span>
                  <span className={styles.detailVal}>
                    <span className={getTypeBadgeClass(selectedLog.type)}>
                      {translateType(selectedLog.type)}
                    </span>
                  </span>
                </div>
                <div className={styles.detailItem}>
                  <span className={styles.detailLabel}>Người thực hiện</span>
                  <span className={styles.detailVal}>{selectedLog.user || 'SYSTEM'}</span>
                </div>
                <div className={styles.detailItem}>
                  <span className={styles.detailLabel}>Địa chỉ IP</span>
                  <span className={styles.detailVal}><code className={styles.ipCode}>{selectedLog.ip || '0.0.0.0'}</code></span>
                </div>
              </div>

              <div className={styles.detailSection} style={{ marginTop: '16px' }}>
                <span className={styles.detailLabel}>Thông điệp</span>
                <p className={styles.detailMessageText}>{selectedLog.message}</p>
              </div>

              {selectedLog.details && (
                <div className={styles.detailSection} style={{ marginTop: '16px' }}>
                  <span className={styles.detailLabel}>Chi tiết kỹ thuật / Stack Trace</span>
                  <pre className={styles.technicalDetails}>
                    <code>{selectedLog.details}</code>
                  </pre>
                </div>
              )}
            </div>

            <div className={styles.modalFooter}>
              <button onClick={() => setDetailModalOpen(false)} className={styles.btnSecondary}>
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 2. EDIT LOG MODAL */}
      {editModalOpen && selectedLog && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent} style={{ maxWidth: '650px' }}>
            <div className={styles.modalHeader}>
              <div className={styles.modalHeaderTitle}>
                <Pencil size={20} className={styles.modalTitleIcon} />
                <h2>Chỉnh sửa nhật ký #{selectedLog.id}</h2>
              </div>
              <button onClick={() => setEditModalOpen(false)} className={styles.modalCloseBtn}>
                <X size={20} />
              </button>
            </div>
            
            <form onSubmit={handleEditSubmit}>
              <div className={styles.modalBody}>
                <div className={styles.formRow}>
                  <div className={styles.formGroup} style={{ flex: 1 }}>
                    <label className={styles.formLabel}>Loại log</label>
                    <select 
                      value={editType} 
                      onChange={(e) => setEditType(e.target.value)}
                      className={styles.formSelect}
                    >
                      <option value="INFO">Thông tin</option>
                      <option value="LOGIN">Đăng nhập</option>
                      <option value="WARNING">Cảnh báo</option>
                      <option value="ERROR">Lỗi</option>
                    </select>
                  </div>
                  <div className={styles.formGroup} style={{ flex: 1 }}>
                    <label className={styles.formLabel}>Địa chỉ IP</label>
                    <input 
                      type="text" 
                      value={editIp}
                      onChange={(e) => setEditIp(e.target.value)}
                      className={styles.formInput}
                    />
                  </div>
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>Người thực hiện</label>
                  <input 
                    type="text" 
                    value={editUser}
                    onChange={(e) => setEditUser(e.target.value)}
                    className={styles.formInput}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>Thông điệp chính</label>
                  <textarea 
                    value={editMessage}
                    onChange={(e) => setEditMessage(e.target.value)}
                    rows={3}
                    className={styles.formTextarea}
                    required
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>Chi tiết kỹ thuật / Ghi chú admin</label>
                  <textarea 
                    value={editDetails}
                    onChange={(e) => setEditDetails(e.target.value)}
                    rows={5}
                    className={styles.formTextarea}
                    style={{ fontFamily: 'Courier New, monospace', fontSize: '13px' }}
                  />
                </div>
              </div>

              <div className={styles.modalFooter}>
                <button type="button" onClick={() => setEditModalOpen(false)} className={styles.btnSecondary}>
                  Hủy
                </button>
                <button type="submit" className={styles.btnPrimary} disabled={submitLoading}>
                  {submitLoading ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 3. DELETE DIALOG */}
      {deleteDialogOpen && selectedLog && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent} style={{ maxWidth: '450px' }}>
            <div className={styles.modalHeader} style={{ borderBottom: 'none' }}>
              <div className={styles.modalHeaderTitle}>
                <AlertTriangle size={24} style={{ color: '#ef4444', marginRight: '10px' }} />
                <h2>Xác nhận xóa log</h2>
              </div>
              <button onClick={() => setDeleteDialogOpen(false)} className={styles.modalCloseBtn}>
                <X size={20} />
              </button>
            </div>
            
            <div className={styles.modalBody}>
              <p style={{ margin: 0, color: '#475569', fontSize: '14px', lineHeight: '1.5' }}>
                Bạn có chắc chắn muốn xóa dòng nhật ký hệ thống số <strong>#{selectedLog.id}</strong> không?
                Hành động này sẽ xóa vĩnh viễn dòng nhật ký này ra khỏi cơ sở dữ liệu và không thể hoàn tác.
              </p>
              <div style={{ marginTop: '16px', padding: '12px', background: '#f8fafc', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <span style={{ fontSize: '12px', fontWeight: 'bold', color: '#64748b', display: 'block', marginBottom: '4px' }}>Thông điệp log</span>
                <span style={{ fontSize: '13px', color: '#0f172a', fontWeight: '500' }}>{selectedLog.message}</span>
              </div>
            </div>

            <div className={styles.modalFooter} style={{ borderTop: 'none' }}>
              <button onClick={() => setDeleteDialogOpen(false)} className={styles.btnSecondary}>
                Hủy bỏ
              </button>
              <button onClick={handleDeleteConfirm} className={styles.btnDanger}>
                Xác nhận xóa
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SystemLogPage;
