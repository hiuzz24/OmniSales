import { ChevronLeft, ChevronRight, FileDown, Plus, Search } from 'lucide-react';
import Pagination from '../../../../shared/components/Pagination';
import { formatNumber } from './inventoryDocumentListUtils';
import styles from './InventoryDocumentListPage.module.css';

export const Badge = ({ label, icon: Icon, color = '#475569', bg = '#f8fafc', border = '#e2e8f0' }) => (
  <span className={styles.badge} style={{ '--badge-color': color, '--badge-bg': bg, '--badge-border': border }}>
    {Icon && <Icon size={13} aria-hidden="true" />}
    {label}
  </span>
);

export const ActionMenuShell = ({ children, open, buttonIcon: ButtonIcon, onToggle, menuRef }) => (
  <div ref={menuRef} className={styles.menuWrap}>
    <button type="button" aria-label="Mở menu thao tác" title="Thao tác" onClick={onToggle} className={styles.menuButton} aria-expanded={open}>
      <ButtonIcon size={18} aria-hidden="true" />
    </button>
    {open && <div className={styles.actionMenu} role="menu">{children}</div>}
  </div>
);

export const ActionMenuItem = ({ children, color = '#172033', disabled, onClick }) => (
  <button type="button" role="menuitem" disabled={disabled} onClick={onClick} className={styles.actionMenuItem} style={{ '--menu-color': color }}>
    {children}
  </button>
);

export const SearchInput = ({ value, onChange, placeholder }) => (
  <label className={styles.searchControl}>
    <span className={styles.srOnly}>Tìm kiếm</span>
    <Search size={17} aria-hidden="true" />
    <input value={value} onChange={onChange} placeholder={placeholder} />
  </label>
);

export const FilterSelect = ({ value, onChange, children, minWidth = 190 }) => (
  <select value={value} onChange={onChange} className={styles.filterSelect} style={{ '--select-width': `${minWidth}px` }} aria-label="Lọc dữ liệu">
    {children}
  </select>
);

export default function InventoryDocumentListPage({
  icon: HeaderIcon,
  iconBg,
  iconColor,
  title,
  description,
  createLabel,
  onCreate,
  onExport,
  extraActions,
  statUnit = 'phiếu',
  stats = [],
  filters,
  columns = [],
  rows,
  loading,
  emptyText,
  colSpan,
  minTableWidth = 1120,
  footerLeft,
  footerRight,
  pagination,
  children,
}) {
  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.titleGroup}>
          <span className={styles.headerIcon} style={{ '--icon-bg': iconBg, '--icon-color': iconColor }}>
            <HeaderIcon size={20} aria-hidden="true" />
          </span>
          <div>
            <h1>{title}</h1>
            <p>{description}</p>
          </div>
        </div>
        <div className={styles.headerActions}>
          {extraActions}
          {onExport && (
            <button type="button" onClick={onExport} className={`${styles.actionButton} ${styles.exportButton}`}>
              <FileDown size={16} aria-hidden="true" /> Xuất Excel
            </button>
          )}
          {onCreate && (
            <button type="button" onClick={onCreate} className={`${styles.actionButton} ${styles.primaryButton}`}>
              <Plus size={16} aria-hidden="true" /> {createLabel}
            </button>
          )}
        </div>
      </header>

      {stats.length > 0 && (
        <section className={styles.statsGrid} aria-label={`Thống kê ${title.toLowerCase()}`}>
          {stats.map((stat) => {
            const Icon = stat.icon;
            return (
              <button
                key={stat.key ?? stat.label}
                type="button"
                disabled={!stat.onClick}
                onClick={stat.onClick}
                className={`${styles.statCard} ${stat.active ? styles.statCardActive : ''}`}
                style={{ '--stat-color': stat.color, '--stat-bg': stat.bg, '--stat-border': stat.border ?? stat.color }}
                aria-pressed={stat.onClick ? Boolean(stat.active) : undefined}
              >
                <span><span className={styles.statLabel}>{stat.label}</span><strong>{formatNumber(stat.value ?? 0)}</strong><small>{statUnit}</small></span>
                <span className={styles.statIcon}><Icon size={18} aria-hidden="true" /></span>
              </button>
            );
          })}
        </section>
      )}

      <section className={styles.filterBar} aria-label="Bộ lọc">{filters}</section>

      {children ? (
        children
      ) : (
        <section className={styles.tableCard} aria-label={title}>
          <div className={styles.tableScroll}>
            <table className={styles.table} style={{ '--table-min-width': `${minTableWidth}px` }}>
              <thead><tr>{columns.map((column) => (
                <th key={column.key ?? column.label} style={{ textAlign: column.align ?? 'left' }}>{column.label}</th>
              ))}</tr></thead>
              <tbody>
                {loading ? (
                  Array.from({ length: 5 }, (_, index) => <tr key={index} className={styles.skeletonRow}><td colSpan={colSpan ?? columns.length}><span /></td></tr>)
                ) : rows?.length ? rows : (
                  <tr><td colSpan={colSpan ?? columns.length} className={styles.emptyCell}>{emptyText}</td></tr>
                )}
              </tbody>
            </table>
          </div>

          {(footerLeft || footerRight) && <div className={styles.tableFooter}><div>{footerLeft}</div><div>{footerRight}</div></div>}

          {!loading && pagination && pagination.totalPages >= 0 && (
            <div className={styles.paginationWrap}>
              <Pagination
                currentPage={pagination.page}
                totalPages={pagination.totalPages}
                totalElements={pagination.totalElements}
                pageSize={pagination.pageSize ?? 10}
                currentCount={rows?.length ?? 0}
                itemLabel={pagination.itemLabel ?? 'phiếu'}
                onPageChange={(nextPage) => {
                  if (pagination.onPageChange) return pagination.onPageChange(nextPage);
                  if (nextPage > pagination.page) pagination.onNext?.();
                  if (nextPage < pagination.page) pagination.onPrevious?.();
                  return undefined;
                }}
              />
            </div>
          )}
          {pagination?.totalPages < 0 && (
            <div className={styles.simplePagination}>
              <span>{pagination.label}</span>
              <div>
                <button type="button" disabled={pagination.page <= 0} onClick={pagination.onPrevious}><ChevronLeft size={15} /> Trước</button>
                <strong>Trang {pagination.page + 1} / {pagination.totalPages}</strong>
                <button type="button" disabled={pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0} onClick={pagination.onNext}>Sau <ChevronRight size={15} /></button>
              </div>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
