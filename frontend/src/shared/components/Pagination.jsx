import { useId } from 'react';
import { ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import styles from './Pagination.module.css';

const ELLIPSIS = 'ellipsis';

const clampPage = (page, totalPages) => Math.min(Math.max(page, 0), Math.max(totalPages - 1, 0));

const buildPageItems = (currentPage, totalPages, siblingCount) => {
  if (totalPages < 1) return [];

  const displayPage = currentPage + 1;
  const totalNumbers = siblingCount * 2 + 5;

  if (totalPages <= totalNumbers) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const leftSibling = Math.max(displayPage - siblingCount, 1);
  const rightSibling = Math.min(displayPage + siblingCount, totalPages);
  const showLeftEllipsis = leftSibling > 2;
  const showRightEllipsis = rightSibling < totalPages - 1;

  if (!showLeftEllipsis && showRightEllipsis) {
    const leftRange = Array.from({ length: 3 + siblingCount * 2 }, (_, index) => index + 1);
    return [...leftRange, ELLIPSIS, totalPages];
  }

  if (showLeftEllipsis && !showRightEllipsis) {
    const start = totalPages - (2 + siblingCount * 2);
    const rightRange = Array.from({ length: totalPages - start + 1 }, (_, index) => start + index);
    return [1, ELLIPSIS, ...rightRange];
  }

  const middleRange = Array.from(
    { length: rightSibling - leftSibling + 1 },
    (_, index) => leftSibling + index
  );

  return [1, ELLIPSIS, ...middleRange, ELLIPSIS, totalPages];
};

const Pagination = ({
  currentPage = 0,
  totalPages = 0,
  totalElements = 0,
  pageSize = 20,
  currentCount,
  onPageChange,
  itemLabel = 'mục',
  className = '',
  siblingCount = 1,
  showPageSizeSelector = false,
  pageSizeOptions = [10, 20, 50],
  onPageSizeChange,
  pageSizeSelectorDisabled = false,
  pageSizeLabel = 'Số mục mỗi trang',
}) => {
  const generatedPageSizeId = useId();
  const pageSizeSelectId = `pagination-page-size-${generatedPageSizeId.replace(/:/g, '')}`;
  const safePageSize = Math.max(Number(pageSize) || 1, 1);
  const normalizedPageSizeOptions = [...new Set(
    (Array.isArray(pageSizeOptions) ? pageSizeOptions : [])
      .map(Number)
      .filter((option) => Number.isFinite(option) && option > 0)
  )];
  if (!normalizedPageSizeOptions.includes(safePageSize)) {
    normalizedPageSizeOptions.push(safePageSize);
    normalizedPageSizeOptions.sort((a, b) => a - b);
  }
  const safeTotalElements = Math.max(Number(totalElements) || Number(currentCount) || 0, 0);
  const inferredTotalPages = Math.ceil(safeTotalElements / safePageSize);
  const safeTotalPages = Math.max(Number(totalPages) || inferredTotalPages || 0, 0);
  const safeCurrentPage = clampPage(Number(currentPage) || 0, Math.max(safeTotalPages, 1));
  const visibleCount = currentCount ?? Math.min(safePageSize, safeTotalElements);

  if (safeTotalPages <= 1 && safeTotalElements === 0) return null;

  const firstItem = safeTotalElements === 0 ? 0 : safeCurrentPage * safePageSize + 1;
  const lastItem = safeTotalElements === 0
    ? 0
    : Math.min(safeCurrentPage * safePageSize + visibleCount, safeTotalElements);
  const pageItems = buildPageItems(safeCurrentPage, safeTotalPages, siblingCount);
  const canGoPrevious = safeCurrentPage > 0;
  const canGoNext = safeCurrentPage < safeTotalPages - 1;

  const handleChange = (nextPage) => {
    if (!onPageChange) return;
    const normalizedPage = clampPage(nextPage, safeTotalPages);
    if (normalizedPage !== safeCurrentPage) {
      onPageChange(normalizedPage);
    }
  };

  const handlePageSizeChange = (event) => {
    if (!onPageSizeChange) return;
    const nextPageSize = Number(event.target.value);
    if (Number.isFinite(nextPageSize) && nextPageSize > 0 && nextPageSize !== safePageSize) {
      onPageSizeChange(nextPageSize);
    }
  };

  return (
    <nav className={`${styles.pagination} ${className}`} aria-label="Phân trang">
      <div className={styles.summary}>
        <p className={styles.info} aria-live="polite">
          Hiển thị <strong>{firstItem}-{lastItem}</strong> / {safeTotalElements} {itemLabel}
        </p>

        {showPageSizeSelector && (
          <div className={styles.pageSizeControl}>
            <label className={styles.pageSizeLabel} htmlFor={pageSizeSelectId}>
              {pageSizeLabel}
            </label>
            <div className={styles.selectWrapper}>
              <select
                id={pageSizeSelectId}
                className={styles.pageSizeSelect}
                value={safePageSize}
                onChange={handlePageSizeChange}
                disabled={pageSizeSelectorDisabled || !onPageSizeChange}
                aria-label={pageSizeLabel}
              >
                {normalizedPageSizeOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
              <ChevronDown className={styles.selectIcon} size={14} aria-hidden="true" />
            </div>
          </div>
        )}
      </div>

      {safeTotalPages >= 1 && (
        <div className={styles.controls}>
          <button
            type="button"
            className={styles.button}
            onClick={() => handleChange(safeCurrentPage - 1)}
            disabled={!canGoPrevious}
            aria-label="Trang trước"
          >
            <ChevronLeft size={16} />
            <span className={styles.navLabel}>Trước</span>
          </button>

          <div className={styles.pages} aria-label={`Trang ${safeCurrentPage + 1} trên ${safeTotalPages}`}>
            {pageItems.map((item, index) =>
              item === ELLIPSIS ? (
                <span key={`${item}-${index}`} className={styles.ellipsis} aria-hidden="true">
                  ...
                </span>
              ) : (
                <button
                  key={item}
                  type="button"
                  className={`${styles.button} ${styles.pageButton} ${
                    item === safeCurrentPage + 1 ? styles.active : ''
                  }`}
                  onClick={() => handleChange(item - 1)}
                  aria-current={item === safeCurrentPage + 1 ? 'page' : undefined}
                >
                  {item}
                </button>
              )
            )}
          </div>

          <button
            type="button"
            className={styles.button}
            onClick={() => handleChange(safeCurrentPage + 1)}
            disabled={!canGoNext}
            aria-label="Trang sau"
          >
            <span className={styles.navLabel}>Sau</span>
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </nav>
  );
};

export default Pagination;
