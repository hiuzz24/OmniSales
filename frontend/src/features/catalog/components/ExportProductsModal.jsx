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
import productApi from '../../../api/productApi';
import { exportProductsToExcel, PRODUCT_EXPORT_COLUMNS } from '../utils/exportProducts';
import styles from './ExportProductsModal.module.css';

const PAGE_SIZE = 20;

const getStatusBadge = (status) => {
  switch (status?.toUpperCase()) {
    case 'ACTIVE':
      return <Badge variant="success">Hoạt động</Badge>;
    case 'INACTIVE':
      return <Badge variant="danger">Ngừng bán</Badge>;
    case 'DRAFT':
      return <Badge variant="default">Nháp</Badge>;
    default:
      return <Badge variant="default">{status || '-'}</Badge>;
  }
};

const getDefaultColumnKeys = () =>
  PRODUCT_EXPORT_COLUMNS.filter((c) => c.defaultChecked).map((c) => c.key);

const ExportProductsModal = ({ isOpen, onClose }) => {
  const [products, setProducts] = useState([]);
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
        const response = await productApi.getAll(page, PAGE_SIZE, debouncedKeyword, '', '');
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
        setProducts(list);
      } catch (error) {
        console.error('Failed to fetch products for export:', error);
        setProducts([]);
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
    products.length > 0 && products.every((p) => p.id && selectedIds.has(p.id));

  const toggleAllOnPage = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        products.forEach((p) => p.id && next.delete(p.id));
      } else {
        products.forEach((p) => p.id && next.add(p.id));
      }
      return next;
    });
  };

  const handleSelectAllMatching = async () => {
    if (totalElements <= products.length && !keyword) {
      setSelectedIds(new Set(products.map((p) => p.id).filter(Boolean)));
      return;
    }
    try {
      setLoading(true);
      const totalPages = Math.ceil(totalElements / PAGE_SIZE) || 1;
      const ids = new Set();
      for (let p = 0; p < totalPages; p += 1) {
        const response = await productApi.getAll(p, PAGE_SIZE, keyword, '', '');
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
    setSelectedColumnKeys(PRODUCT_EXPORT_COLUMNS.map((c) => c.key));
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
        productApi
          .getById(id)
          .then((res) => res.data?.data || res.data || res)
          .catch(() => null),
      );
      const results = await Promise.all(detailPromises);
      const valid = results.filter(Boolean);
      if (valid.length === 0) {
        alert('Không thể tải chi tiết sản phẩm để xuất Excel.');
        return;
      }
      const result = exportProductsToExcel(valid, selectedColumnKeys, 'danh-sach-san-pham');
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
            <h2 className={styles.title}>Xuất Excel sản phẩm</h2>
            <p className={styles.subtitle}>
              Chọn những sản phẩm bạn muốn xuất ra file Excel
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
              placeholder="Tìm kiếm sản phẩm theo tên, SKU..."
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
                    {PRODUCT_EXPORT_COLUMNS.map((col) => {
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
                  <th>Sản phẩm</th>
                  <th>SKU</th>
                  <th>Danh mục</th>
                  <th>Giá bán</th>
                  <th>Tồn kho</th>
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
                ) : products.length === 0 ? (
                  <tr>
                    <td colSpan="7" className={styles.emptyState}>
                      Không có sản phẩm nào
                    </td>
                  </tr>
                ) : (
                  products.map((p) => {
                    const isSelected = p.id && selectedIds.has(p.id);
                    return (
                      <tr
                        key={p.id}
                        className={`${styles.tr} ${isSelected ? styles.trSelected : ''}`}
                        onClick={() => p.id && toggleOne(p.id)}
                      >
                        <td className={styles.tdCheck} onClick={(e) => e.stopPropagation()}>
                          <button
                            type="button"
                            onClick={() => toggleOne(p.id)}
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
                        <td>
                          <div className={styles.productName}>{p.name || 'N/A'}</div>
                          {p.brand && <div className={styles.productBrand}>Thương hiệu: {p.brand}</div>}
                        </td>
                        <td>{p.sku || p.variants?.[0]?.sku || '-'}</td>
                        <td>{p.categoryName || 'Chưa phân loại'}</td>
                        <td>
                          {(() => {
                            if (!p.variants || p.variants.length === 0) return 0;
                            const prices = p.variants.map((v) => v.price).filter((x) => x != null);
                            if (prices.length === 0) return 0;
                            const min = Math.min(...prices);
                            const max = Math.max(...prices);
                            return min === max
                              ? min.toLocaleString('vi-VN')
                              : `${min.toLocaleString('vi-VN')} - ${max.toLocaleString('vi-VN')}`;
                          })()}
                        </td>
                        <td>
                          {p.variants?.reduce(
                            (sum, v) => sum + (v.availableQuantity || v.quantityOnHand || 0),
                            0,
                          ) || 0}
                        </td>
                        <td>{getStatusBadge(p.status)}</td>
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

export default ExportProductsModal;
