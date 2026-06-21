import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Search,
  X,
  Download,
  CheckSquare,
  Square,
  Columns3,
  ChevronDown,
  Check,
} from 'lucide-react';
import Badge from '../../../shared/components/Badge';
import customerApi from '../../../api/customerApi';
import { exportCustomersToExcel, CUSTOMER_EXPORT_COLUMNS } from '../utils/exportCustomers';
import styles from './ExportCustomersModal.module.css';

const PAGE_SIZE = 20;

const getStatusBadge = (isActive) => {
  if (isActive) return <Badge variant="success">Hoạt động</Badge>;
  return <Badge variant="danger">Ngừng hoạt động</Badge>;
};

const getGenderLabel = (gender) => {
  if (gender === 'MALE') return 'Nam';
  if (gender === 'FEMALE') return 'Nữ';
  if (gender === 'OTHER') return 'Khác';
  return gender || '-';
};

const getDefaultColumnKeys = () =>
  CUSTOMER_EXPORT_COLUMNS.filter((c) => c.defaultChecked).map((c) => c.key);

const ExportCustomersModal = ({ isOpen, onClose }) => {
  const [customers, setCustomers] = useState([]);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [selectedColumnKeys, setSelectedColumnKeys] = useState(getDefaultColumnKeys);
  const [isColumnMenuOpen, setIsColumnMenuOpen] = useState(false);
  const columnMenuRef = useRef(null);

  const debouncedKeyword = useMemo(() => keyword, [keyword]);

  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => {
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword, isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const fetchPage = async () => {
      try {
        setLoading(true);
        const response = await customerApi.getAll(page, PAGE_SIZE, debouncedKeyword, '', '');
        const responseData = response.data?.data || response.data || response;
        let list = [];
        if (responseData.content) {
          list = responseData.content;
          setTotalElements(responseData.totalElements || 0);
        } else if (Array.isArray(responseData)) {
          list = responseData;
          setTotalElements(responseData.length);
        } else if (responseData.data) {
          list = responseData.data;
          setTotalElements(responseData.total || responseData.data.length || 0);
        }
        setCustomers(list);
      } catch (error) {
        console.error('Failed to fetch customers for export:', error);
        setCustomers([]);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    };
    fetchPage();
  }, [page, debouncedKeyword, isOpen]);

  useEffect(() => {
    if (!isOpen) {
      setSelectedIds(new Set());
      setKeyword('');
      setPage(0);
      setSelectedColumnKeys(getDefaultColumnKeys());
      setIsColumnMenuOpen(false);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isColumnMenuOpen) return undefined;
    const handleClickOutside = (event) => {
      if (columnMenuRef.current && !columnMenuRef.current.contains(event.target)) {
        setIsColumnMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isColumnMenuOpen]);

  const toggleOne = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const allOnPageSelected =
    customers.length > 0 && customers.every((c) => c.id && selectedIds.has(c.id));

  const toggleAllOnPage = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        customers.forEach((c) => c.id && next.delete(c.id));
      } else {
        customers.forEach((c) => c.id && next.add(c.id));
      }
      return next;
    });
  };

  const handleSelectAllMatching = async () => {
    if (totalElements <= customers.length && !keyword) {
      setSelectedIds(new Set(customers.map((c) => c.id).filter(Boolean)));
      return;
    }
    try {
      setLoading(true);
      const totalPages = Math.ceil(totalElements / PAGE_SIZE) || 1;
      const ids = new Set();
      for (let p = 0; p < totalPages; p += 1) {
        const response = await customerApi.getAll(p, PAGE_SIZE, keyword, '', '');
        const responseData = response.data?.data || response.data || response;
        const list = responseData.content || (Array.isArray(responseData) ? responseData : []);
        list.forEach((item) => item.id && ids.add(item.id));
      }
      setSelectedIds(ids);
    } catch (error) {
      console.error('Failed to select all matching:', error);
    } finally {
      setLoading(false);
    }
  };

  const toggleColumn = (key) => {
    setSelectedColumnKeys((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key],
    );
  };

  const selectAllColumns = () => {
    setSelectedColumnKeys(CUSTOMER_EXPORT_COLUMNS.map((c) => c.key));
  };

  const selectDefaultColumns = () => {
    setSelectedColumnKeys(getDefaultColumnKeys());
  };

  const deselectAllColumns = () => {
    setSelectedColumnKeys([]);
  };

  const handleExport = async () => {
    if (selectedIds.size === 0) return;
    if (selectedColumnKeys.length === 0) {
      alert('Vui lòng chọn ít nhất 1 cột để xuất.');
      return;
    }
    try {
      setExporting(true);
      const detailPromises = Array.from(selectedIds).map((id) =>
        customerApi
          .getById(id)
          .then((res) => res.data?.data || res.data || res)
          .catch(() => null),
      );
      const results = await Promise.all(detailPromises);
      const valid = results.filter(Boolean);
      if (valid.length === 0) {
        alert('Không thể tải chi tiết khách hàng để xuất Excel.');
        return;
      }
      const result = exportCustomersToExcel(valid, selectedColumnKeys, 'danh-sach-khach-hang');
      if (result.success) {
        onClose();
      } else {
        alert(result.message);
      }
    } catch (error) {
      console.error('Export error:', error);
      alert('Có lỗi khi xuất Excel. Vui lòng thử lại.');
    } finally {
      setExporting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>Xuất Excel khách hàng</h2>
            <p className={styles.subtitle}>
              Chọn những khách hàng bạn muốn xuất ra file Excel
            </p>
          </div>
          <button className={styles.closeBtn} onClick={onClose} aria-label="Đóng">
            <X size={20} />
          </button>
        </div>

        <div className={styles.toolbar}>
          <div className={styles.searchWrapper}>
            <Search size={16} className={styles.searchIcon} />
            <input
              type="text"
              className={styles.searchInput}
              placeholder="Tìm kiếm khách hàng theo tên, mã, SĐT, email..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
          </div>
          <div className={styles.toolbarActions}>
            <div className={styles.columnPickerWrapper} ref={columnMenuRef}>
              <button
                type="button"
                className={styles.columnPickerBtn}
                onClick={() => setIsColumnMenuOpen((v) => !v)}
                aria-expanded={isColumnMenuOpen}
              >
                <Columns3 size={16} />
                Cột xuất ({selectedColumnKeys.length})
                <ChevronDown
                  size={14}
                  className={`${styles.columnPickerChevron} ${
                    isColumnMenuOpen ? styles.columnPickerChevronOpen : ''
                  }`}
                />
              </button>
              {isColumnMenuOpen && (
                <div className={styles.columnMenu}>
                  <div className={styles.columnMenuHeader}>
                    <span className={styles.columnMenuTitle}>Chọn cột cần xuất</span>
                    <div className={styles.columnMenuActions}>
                      <button
                        type="button"
                        className={styles.columnMenuLink}
                        onClick={selectAllColumns}
                      >
                        Tất cả
                      </button>
                      <span className={styles.columnMenuDivider}>|</span>
                      <button
                        type="button"
                        className={styles.columnMenuLink}
                        onClick={selectDefaultColumns}
                      >
                        Mặc định
                      </button>
                      <span className={styles.columnMenuDivider}>|</span>
                      <button
                        type="button"
                        className={`${styles.columnMenuLink} ${styles.columnMenuLinkDanger}`}
                        onClick={deselectAllColumns}
                      >
                        Bỏ chọn
                      </button>
                    </div>
                  </div>
                  <div className={styles.columnMenuList}>
                    {CUSTOMER_EXPORT_COLUMNS.map((col) => {
                      const checked = selectedColumnKeys.includes(col.key);
                      return (
                        <label
                          key={col.key}
                          className={`${styles.columnMenuItem} ${
                            checked ? styles.columnMenuItemChecked : ''
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleColumn(col.key)}
                            className={styles.columnMenuCheckbox}
                          />
                          <span className={styles.columnMenuItemLabel}>{col.label}</span>
                          {checked && <Check size={14} className={styles.columnMenuItemCheck} />}
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
            <button
              type="button"
              className={styles.selectAllBtn}
              onClick={handleSelectAllMatching}
              disabled={loading || totalElements === 0}
            >
              Chọn tất cả ({totalElements})
            </button>
            <span className={styles.selectedCount}>
              Đã chọn: <strong>{selectedIds.size}</strong>
            </span>
          </div>
        </div>

        <div className={styles.body}>
          <div className={styles.tableResponsive}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.thCheck}>
                    <button
                      type="button"
                      onClick={toggleAllOnPage}
                      className={styles.checkBtn}
                      aria-label="Chọn tất cả trên trang"
                    >
                      {allOnPageSelected ? (
                        <CheckSquare size={18} className={styles.checkActive} />
                      ) : (
                        <Square size={18} className={styles.checkInactive} />
                      )}
                    </button>
                  </th>
                  <th>Mã KH</th>
                  <th>Họ và tên</th>
                  <th>Giới tính</th>
                  <th>Số điện thoại</th>
                  <th>Email</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan="7" className={styles.emptyState}>
                      Đang tải...
                    </td>
                  </tr>
                ) : customers.length === 0 ? (
                  <tr>
                    <td colSpan="7" className={styles.emptyState}>
                      Không có khách hàng nào
                    </td>
                  </tr>
                ) : (
                  customers.map((c) => {
                    const isSelected = c.id && selectedIds.has(c.id);
                    return (
                      <tr
                        key={c.id}
                        className={`${styles.tr} ${isSelected ? styles.trSelected : ''}`}
                        onClick={() => c.id && toggleOne(c.id)}
                      >
                        <td className={styles.tdCheck} onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            onClick={() => toggleOne(c.id)}
                            className={styles.checkBtn}
                            aria-label={isSelected ? 'Bỏ chọn' : 'Chọn'}
                          >
                            {isSelected ? (
                              <CheckSquare size={18} className={styles.checkActive} />
                            ) : (
                              <Square size={18} className={styles.checkInactive} />
                            )}
                          </button>
                        </td>
                        <td className={styles.customerCode}>{c.code || '-'}</td>
                        <td className={styles.customerName}>{c.fullName || 'N/A'}</td>
                        <td>{getGenderLabel(c.gender)}</td>
                        <td>{c.phone || '-'}</td>
                        <td>{c.email || '-'}</td>
                        <td>{getStatusBadge(c.isActive)}</td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className={styles.footer}>
          <div className={styles.pagination}>
            <button
              type="button"
              className={styles.pageBtn}
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Trước
            </button>
            <span className={styles.pageInfo}>
              Trang {page + 1} / {Math.max(1, Math.ceil(totalElements / PAGE_SIZE))}
            </span>
            <button
              type="button"
              className={styles.pageBtn}
              disabled={(page + 1) * PAGE_SIZE >= totalElements}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau
            </button>
          </div>
          <div className={styles.footerActions}>
            <button type="button" className={styles.cancelBtn} onClick={onClose}>
              Hủy
            </button>
            <button
              type="button"
              className={styles.exportBtn}
              onClick={handleExport}
              disabled={selectedIds.size === 0 || exporting || selectedColumnKeys.length === 0}
            >
              <Download size={16} />
              {exporting ? 'Đang xuất...' : `Xuất Excel (${selectedIds.size})`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ExportCustomersModal;
