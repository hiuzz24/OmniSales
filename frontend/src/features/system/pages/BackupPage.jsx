import { useState, useEffect, useCallback } from 'react';
import { 
  Database, RefreshCw, Plus, Download, RotateCcw, Trash, X, AlertTriangle, 
  ShieldAlert, CheckCircle2, Lock, FileText, Info
} from 'lucide-react';
import { toast } from 'react-toastify';
import PageHeader from '../../../shared/components/PageHeader';
import backupApi from '../../../api/backupApi';
import styles from './BackupPage.module.css';

const BackupPage = () => {
  const [backups, setBackups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);

  // Modals & Action States
  const [restoreModalOpen, setRestoreModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [selectedBackup, setSelectedBackup] = useState(null);
  
  // Restore Confirm Form States
  const [confirmPassword, setConfirmPassword] = useState('');
  const [confirmText, setConfirmText] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  // Fetch backups from backend
  const fetchBackups = useCallback(async () => {
    setLoading(true);
    try {
      const res = await backupApi.getBackups({ page: currentPage, size: pageSize });
      setBackups(res.content || []);
      setTotalElements(res.totalElements || 0);
      setTotalPages(res.totalPages || 1);
    } catch (error) {
      console.error('Failed to fetch backups:', error);
      toast.error('Không thể tải danh sách bản sao lưu dữ liệu');
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize]);

  useEffect(() => {
    fetchBackups();
  }, [fetchBackups]);

  // Create manual backup action
  const handleCreateBackup = useCallback(async () => {
    setLoading(true);
    try {
      toast.info('Đang thực hiện sao lưu cơ sở dữ liệu trên server...');
      const res = await backupApi.createBackup();
      if (res.status === 'SUCCESS') {
        toast.success(`Tạo bản sao lưu "${res.filename}" thành công!`);
      } else {
        toast.error('Sao lưu thất bại. Vui lòng kiểm tra nhật ký hệ thống.');
      }
      fetchBackups();
    } catch (error) {
      console.error('Backup creation error:', error);
      toast.error('Tạo bản sao lưu thất bại');
    } finally {
      setLoading(false);
    }
  }, [fetchBackups]);

  // Trigger file download
  const handleDownload = async (id, filename) => {
    try {
      toast.info('Đang chuẩn bị tải về tệp sao lưu...');
      const response = await backupApi.downloadBackup(id);
      
      const url = window.URL.createObjectURL(new Blob([response.data || response]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      
      link.parentNode.removeChild(link);
      window.URL.revokeObjectURL(url);
      
      toast.success('Tải về tệp sao lưu thành công!');
    } catch (error) {
      console.error('Download trigger error:', error);
      toast.error('Không thể tải về tệp sao lưu');
    }
  };

  // Open Restore Confirm Modal
  const handleOpenRestore = (backup) => {
    setSelectedBackup(backup);
    setConfirmPassword('');
    setConfirmText('');
    setRestoreModalOpen(true);
  };

  // Submit Restore request
  const handleRestoreSubmit = async (e) => {
    e.preventDefault();
    if (!confirmPassword.trim()) {
      toast.error('Vui lòng nhập mật khẩu tài khoản');
      return;
    }
    if (confirmText !== 'XÁC NHẬN') {
      toast.error('Vui lòng nhập đúng chữ "XÁC NHẬN"');
      return;
    }

    setActionLoading(true);
    try {
      toast.info('Đang tiến hành ngắt kết nối và khôi phục cơ sở dữ liệu. Vui lòng đợi...');
      await backupApi.restoreBackup(selectedBackup.id, confirmPassword);
      toast.success('Khôi phục cơ sở dữ liệu thành công! Hệ thống đã được khôi phục về trạng thái trước đó.');
      setRestoreModalOpen(false);
      fetchBackups();
    } catch (error) {
      console.error('Database restore error:', error);
      const errorMsg = error?.response?.data?.message || 'Khôi phục dữ liệu thất bại. Mật khẩu không chính xác hoặc có lỗi hệ thống.';
      toast.error(errorMsg);
    } finally {
      setActionLoading(false);
    }
  };

  // Open Delete Modal
  const handleOpenDelete = (backup) => {
    setSelectedBackup(backup);
    setDeleteModalOpen(true);
  };

  // Confirm delete action
  const handleDeleteConfirm = async () => {
    setActionLoading(true);
    try {
      await backupApi.deleteBackup(selectedBackup.id);
      toast.success(`Đã xóa tệp sao lưu "${selectedBackup.filename}" thành công.`);
      setDeleteModalOpen(false);
      
      // Handle pagination adjust
      if (backups.length === 1 && currentPage > 0) {
        setCurrentPage(currentPage - 1);
      } else {
        fetchBackups();
      }
    } catch (error) {
      console.error('Backup delete error:', error);
      toast.error('Xóa tệp sao lưu thất bại');
    } finally {
      setActionLoading(false);
    }
  };

  // Helper: format bytes size
  const formatBytes = (bytes) => {
    if (!bytes || bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Helper: format timestamp date
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
        title="Sao Lưu & Khôi Phục Dữ Liệu" 
        subtitle="Quản lý các điểm khôi phục hệ thống, thiết lập backup cơ sở dữ liệu thủ công hoặc tự động."
        icon={() => <Database size={20} />}
        actions={
          <div className={styles.headerActions}>
            <button onClick={fetchBackups} className={styles.btnRefresh} title="Tải lại danh sách">
              <RefreshCw size={16} className={loading ? styles.spinner : ''} />
              <span>Tải lại</span>
            </button>
            <button onClick={handleCreateBackup} className={styles.btnCreate} disabled={loading}>
              <Plus size={16} />
              <span>Tạo bản sao lưu ngay</span>
            </button>
          </div>
        }
      />

      {/* Database connection details banner */}
      <div className={styles.infoBanner}>
        <Info size={20} className={styles.infoBannerIcon} />
        <div className={styles.infoBannerContent}>
          <h3>Cấu hình hệ thống sao lưu</h3>
          <p>
            Dữ liệu sao lưu được nén nhị phân an toàn dưới định dạng PostgreSQL Custom (`.backup`). 
            Hệ thống tự động thực hiện backup định kỳ vào lúc **2:00 AM hàng ngày**.
          </p>
        </div>
      </div>

      {/* Backups Table */}
      <div className={styles.tableCard}>
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.th} style={{ width: '280px' }}>Tên bản sao lưu</th>
                <th className={styles.th} style={{ width: '130px' }}>Dung lượng</th>
                <th className={styles.th} style={{ width: '140px' }}>Phương thức</th>
                <th className={styles.th} style={{ width: '180px' }}>Thời gian tạo</th>
                <th className={styles.th} style={{ width: '140px' }}>Người thực hiện</th>
                <th className={styles.th} style={{ width: '140px', textAlign: 'center' }}>Trạng thái</th>
                <th className={styles.th} style={{ width: '150px', textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {loading && backups.length === 0 ? (
                <tr>
                  <td colSpan="7" className={styles.loadingRow}>
                    <div className={styles.spinnerWrapper}>
                      <RefreshCw size={24} className={styles.spinner} />
                      <span>Đang tải danh sách bản sao lưu...</span>
                    </div>
                  </td>
                </tr>
              ) : backups.length === 0 ? (
                <tr>
                  <td colSpan="7" className={styles.emptyRow}>
                    <Database size={36} className={styles.emptyIcon} />
                    <p>Chưa có tệp tin sao lưu dữ liệu nào được khởi tạo</p>
                  </td>
                </tr>
              ) : (
                backups.map((backup) => (
                  <tr key={backup.id} className={styles.tr}>
                    <td className={styles.td}>
                      <div className={styles.fileNameCell}>
                        <FileText size={18} className={styles.fileIcon} />
                        <span className={styles.fileName} title={backup.filename}>{backup.filename}</span>
                      </div>
                    </td>
                    <td className={styles.td}>{formatBytes(backup.fileSize)}</td>
                    <td className={styles.td}>
                      <span className={backup.type === 'SCHEDULED' ? styles.typeAuto : styles.typeManual}>
                        {backup.type === 'SCHEDULED' ? 'Tự động' : 'Thủ công'}
                      </span>
                    </td>
                    <td className={styles.td}>{formatDateTime(backup.createdAt)}</td>
                    <td className={styles.td}>
                      <span className={styles.userLabel}>{backup.createdBy || 'SYSTEM'}</span>
                    </td>
                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      <span className={`${styles.badge} ${backup.status === 'SUCCESS' ? styles.badgeSuccess : styles.badgeFailed}`}>
                        {backup.status === 'SUCCESS' ? 'Thành công' : 'Thất bại'}
                      </span>
                    </td>
                    <td className={styles.td} style={{ textAlign: 'center' }}>
                      <div className={styles.actionGroup}>
                        <button 
                          onClick={() => handleDownload(backup.id, backup.filename)} 
                          className={`${styles.actionBtn} ${styles.actionBtnDownload}`}
                          disabled={backup.status === 'FAILED'}
                          title="Tải về file backup"
                        >
                          <Download size={16} />
                        </button>
                        <button 
                          onClick={() => handleOpenRestore(backup)} 
                          className={`${styles.actionBtn} ${styles.actionBtnRestore}`}
                          disabled={backup.status === 'FAILED'}
                          title="Khôi phục hệ thống từ file này"
                        >
                          <RotateCcw size={16} />
                        </button>
                        <button 
                          onClick={() => handleOpenDelete(backup)} 
                          className={`${styles.actionBtn} ${styles.actionBtnDelete}`}
                          title="Xóa tệp sao lưu"
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
        {!loading && backups.length > 0 && (
          <div className={styles.paginationBar}>
            <div className={styles.paginationInfo}>
              Hiển thị {backups.length} trên tổng số <strong>{totalElements}</strong> bản sao lưu
            </div>
            <div className={styles.paginationControls}>
              <button 
                onClick={() => setCurrentPage(prev => Math.max(prev - 1, 0))}
                disabled={currentPage === 0}
                className={styles.paginationBtn}
              >
                <ChevronLeftIcon size={16} />
              </button>
              <span className={styles.paginationText}>
                Trang <strong>{currentPage + 1}</strong> / {totalPages}
              </span>
              <button 
                onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages - 1))}
                disabled={currentPage === totalPages - 1}
                className={styles.paginationBtn}
              >
                <ChevronRightIcon size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 1. RESTORE SYSTEM DIALOG (SECURE OVERWRITE MODAL) */}
      {restoreModalOpen && selectedBackup && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent} style={{ maxWidth: '520px' }}>
            <div className={styles.modalHeader}>
              <div className={styles.modalHeaderTitle}>
                <AlertTriangle size={24} style={{ color: '#ef4444', marginRight: '10px' }} />
                <h2>Xác nhận khôi phục & ghi đè cơ sở dữ liệu</h2>
              </div>
              <button onClick={() => !actionLoading && setRestoreModalOpen(false)} className={styles.modalCloseBtn} disabled={actionLoading}>
                <X size={20} />
              </button>
            </div>
            
            <form onSubmit={handleRestoreSubmit}>
              <div className={styles.modalBody}>
                {/* Critical warning message */}
                <div className={styles.dangerAlertBox}>
                  <ShieldAlert size={22} className={styles.dangerAlertIcon} />
                  <div>
                    <h4>CẢNH BÁO NGUY HIỂM!</h4>
                    <p>
                      Hành động khôi phục dữ liệu từ tệp sao lưu <strong>{selectedBackup.filename}</strong> sẽ xóa toàn bộ dữ liệu 
                      hiện tại của hệ thống để ghi đè dữ liệu cũ. Hành động này **không thể hoàn tác**.
                    </p>
                  </div>
                </div>

                <div className={styles.formGroup} style={{ marginTop: '16px' }}>
                  <label className={styles.formLabel}>
                    Xác nhận mật khẩu tài khoản Admin
                  </label>
                  <div className={styles.inputWrapper}>
                    <Lock size={16} className={styles.inputIcon} />
                    <input 
                      type="password" 
                      placeholder="Nhập mật khẩu quản trị viên để tiếp tục"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      className={styles.formInput}
                      required
                      disabled={actionLoading}
                    />
                  </div>
                </div>

                <div className={styles.formGroup} style={{ marginTop: '16px' }}>
                  <label className={styles.formLabel}>
                    Để đảm bảo an toàn, vui lòng gõ chính xác chữ <strong>XÁC NHẬN</strong>
                  </label>
                  <input 
                    type="text" 
                    placeholder="Gõ XÁC NHẬN vào đây"
                    value={confirmText}
                    onChange={(e) => setConfirmText(e.target.value)}
                    className={styles.formInput}
                    required
                    disabled={actionLoading}
                  />
                </div>

                {actionLoading && (
                  <div className={styles.restoringState}>
                    <RefreshCw size={24} className={styles.spinner} />
                    <span>Đang ngắt kết nối hồ chứa & thực hiện phục hồi dữ liệu... Vui lòng không tắt trình duyệt!</span>
                  </div>
                )}
              </div>

              <div className={styles.modalFooter}>
                <button 
                  type="button" 
                  onClick={() => setRestoreModalOpen(false)} 
                  className={styles.btnSecondary}
                  disabled={actionLoading}
                >
                  Hủy bỏ
                </button>
                <button 
                  type="submit" 
                  className={styles.btnDanger}
                  disabled={actionLoading || !confirmPassword.trim() || confirmText !== 'XÁC NHẬN'}
                >
                  {actionLoading ? 'Đang khôi phục...' : 'Bắt đầu khôi phục và ghi đè'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 2. DELETE BACKUP DIALOG */}
      {deleteModalOpen && selectedBackup && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent} style={{ maxWidth: '460px' }}>
            <div className={styles.modalHeader} style={{ borderBottom: 'none' }}>
              <div className={styles.modalHeaderTitle}>
                <AlertTriangle size={24} style={{ color: '#f59e0b', marginRight: '10px' }} />
                <h2>Xóa tệp sao lưu</h2>
              </div>
              <button onClick={() => !actionLoading && setDeleteModalOpen(false)} className={styles.modalCloseBtn} disabled={actionLoading}>
                <X size={20} />
              </button>
            </div>
            
            <div className={styles.modalBody}>
              <p style={{ margin: 0, color: '#475569', fontSize: '14px', lineHeight: '1.5' }}>
                Bạn có chắc chắn muốn xóa tệp sao lưu <strong>{selectedBackup.filename}</strong>? 
                Bản ghi trong cơ sở dữ liệu và tệp vật lý lưu trên server sẽ bị xóa vĩnh viễn.
              </p>
            </div>

            <div className={styles.modalFooter} style={{ borderTop: 'none' }}>
              <button 
                onClick={() => setDeleteModalOpen(false)} 
                className={styles.btnSecondary}
                disabled={actionLoading}
              >
                Hủy bỏ
              </button>
              <button 
                onClick={handleDeleteConfirm} 
                className={styles.btnDanger}
                disabled={actionLoading}
              >
                {actionLoading ? 'Đang xóa...' : 'Xác nhận xóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

// Quick custom components for pagination icons since Lucide icons list is custom
const ChevronLeftIcon = ({ size }) => <ChevronLeft size={size} />;
const ChevronRightIcon = ({ size }) => <ChevronRight size={size} />;

// Workaround for import issues in React
import { ChevronLeft, ChevronRight } from 'lucide-react';

export default BackupPage;
