import { ChevronLeft, ChevronRight, FileDown, Plus, Search } from 'lucide-react';
import Pagination from '../../../../shared/components/Pagination';
import { formatNumber } from './inventoryDocumentListUtils';

export const Badge = ({ label, icon: Icon, color = '#475569', bg = '#f8fafc', border = '#e2e8f0' }) => (
  <span style={{
    display: 'inline-flex',
    alignItems: 'center',
    gap: 5,
    padding: '6px 14px',
    borderRadius: 999,
    border: `1px solid ${border}`,
    background: bg,
    color,
    fontSize: 13,
    fontWeight: 700,
    whiteSpace: 'nowrap',
  }}>
    {Icon && <Icon size={13} />}
    {label}
  </span>
);

export const ActionMenuShell = ({ children, open, buttonIcon: ButtonIcon, onToggle, menuRef }) => (
  <div ref={menuRef} style={{ position: 'relative', display: 'inline-flex', justifyContent: 'flex-end' }}>
    <button
      type="button"
      title="Thao tác"
      onClick={onToggle}
      style={menuButtonStyle}
    >
      <ButtonIcon size={18} />
    </button>
    {open && (
      <div style={actionMenuStyle}>
        {children}
      </div>
    )}
  </div>
);

export const ActionMenuItem = ({ children, color = '#020617', disabled, onClick }) => (
  <button
    type="button"
    disabled={disabled}
    onClick={onClick}
    style={{
      ...actionMenuItemStyle,
      color,
      opacity: disabled ? 0.6 : 1,
      cursor: disabled ? 'not-allowed' : 'pointer',
    }}
  >
    {children}
  </button>
);

export const SearchInput = ({ value, onChange, placeholder }) => (
  <div style={{ position: 'relative', flex: '1', minWidth: 240 }}>
    <Search size={17} style={{ position: 'absolute', left: 13, top: '50%', transform: 'translateY(-50%)', color: '#8aa0bd', pointerEvents: 'none' }} />
    <input
      value={value}
      onChange={onChange}
      placeholder={placeholder}
      style={{ ...filterControlStyle, paddingLeft: 42 }}
    />
  </div>
);

export const FilterSelect = ({ value, onChange, children, minWidth = 190 }) => (
  <select
    value={value}
    onChange={onChange}
    style={{ ...filterControlStyle, minWidth, flex: `0 1 ${minWidth}px` }}
  >
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
}) {
  return (
    <div style={pageStyle}>
      {/* â”€â”€ Page Header â”€â”€ */}
      <div style={headerContainerStyle}>
        <div style={titleWrapperStyle}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{ width: 44, height: 44, borderRadius: 14, background: iconBg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <HeaderIcon size={22} color={iconColor} />
            </div>
            <div>
              <h1 style={{ margin: 0, color: '#111827', fontSize: 26, lineHeight: 1.2, fontWeight: 800, letterSpacing: '-0.3px' }}>{title}</h1>
              <p style={{ margin: '5px 0 0', color: '#6b7280', fontSize: 14, fontWeight: 400 }}>{description}</p>
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          {onExport && (
            <button type="button" onClick={onExport} style={secondaryButtonStyle}
              onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 6px 20px rgba(16, 185, 129, 0.4)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 4px 14px 0 rgba(16, 185, 129, 0.35)'; }}>
              <FileDown size={15} />
              Xuất Excel
            </button>
          )}
          {onCreate && (
            <button type="button" onClick={onCreate} style={primaryButtonStyle}
              onMouseEnter={(e) => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 6px 20px rgba(99, 102, 241, 0.4)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 4px 14px 0 rgba(99, 102, 241, 0.39)'; }}>
              <Plus size={16} />
              {createLabel}
            </button>
          )}
        </div>
      </div>

      {/* â”€â”€ Stats Cards â”€â”€ */}
      {stats.length > 0 && (
        <div style={statsGridStyle}>
          {stats.map((stat) => {
            const Icon = stat.icon;
            return (
              <button
                key={stat.key ?? stat.label}
                type="button"
                disabled={!stat.onClick}
                onClick={stat.onClick}
                style={{
                  ...statCardStyle,
                  borderColor: stat.active ? (stat.border ?? stat.color) : '#e5e7eb',
                  background: stat.active ? stat.bg : '#ffffff',
                  boxShadow: stat.active
                    ? `0 0 0 3px ${stat.border ?? stat.bg}`
                    : '0 1px 4px rgba(0,0,0,0.06)',
                  cursor: stat.onClick ? 'pointer' : 'default',
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ fontSize: 13, color: '#6b7280', fontWeight: 500 }}>{stat.label}</span>
                  <strong style={{ fontSize: 28, color: '#111827', lineHeight: 1.1, fontWeight: 800, letterSpacing: '-0.5px', marginTop: 4 }}>
                    {formatNumber(stat.value ?? 0)}
                  </strong>
                  <span style={{ fontSize: 12, color: '#9ca3af', marginTop: 6 }}>{statUnit}</span>
                </div>
                <div style={{
                  width: 40, height: 40, borderRadius: 12,
                  background: stat.bg,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                  boxShadow: `0 4px 12px ${stat.bg}80`,
                }}>
                  <Icon size={19} color={stat.color} />
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* â”€â”€ Filter Bar â”€â”€ */}
      <div style={filterBarStyle}>
        {filters}
      </div>

      {/* â”€â”€ Table Card â”€â”€ */}
      <div style={tableCardStyle}>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', minWidth: minTableWidth, borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th
                    key={column.key ?? column.label}
                    style={{
                      padding: '13px 12px',
                      textAlign: column.align ?? 'left',
                      color: '#374151',
                      fontSize: 12,
                      fontWeight: 700,
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                      whiteSpace: 'nowrap',
                      borderBottom: '1px solid #e5e7eb',
                      background: '#f9fafb',
                    }}
                  >
                    {column.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={colSpan ?? columns.length} style={emptyCellStyle}>Đang tải dữ liệu...</td></tr>
              ) : rows?.length ? (
                rows
              ) : (
                <tr><td colSpan={colSpan ?? columns.length} style={emptyCellStyle}>{emptyText}</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div style={footerStyle}>
          <span>{footerLeft}</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
            {footerRight}
          </div>
        </div>

        {!loading && pagination && (
          <Pagination
            currentPage={pagination.page}
            totalPages={pagination.totalPages}
            totalElements={pagination.totalElements}
            pageSize={pagination.pageSize ?? 10}
            currentCount={rows?.length ?? 0}
            itemLabel={pagination.itemLabel ?? 'phiếu'}
            onPageChange={(nextPage) => {
              if (pagination.onPageChange) {
                pagination.onPageChange(nextPage);
                return;
              }
              if (nextPage > pagination.page) pagination.onNext?.();
              if (nextPage < pagination.page) pagination.onPrevious?.();
            }}
          />
        )}
        {pagination?.totalPages < 0 && (
          <div style={paginationStyle}>
            <span>{pagination.label}</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <button
                type="button"
                disabled={pagination.page <= 0}
                onClick={pagination.onPrevious}
                style={{ ...paginationButtonStyle, opacity: pagination.page <= 0 ? 0.4 : 1, cursor: pagination.page <= 0 ? 'not-allowed' : 'pointer' }}
              >
                <ChevronLeft size={15} />
                Trước
              </button>
              <span style={{ minWidth: 100, textAlign: 'center', color: '#111827', fontWeight: 700, fontSize: 13 }}>
                Trang {pagination.page + 1} / {pagination.totalPages}
              </span>
              <button
                type="button"
                disabled={pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0}
                onClick={pagination.onNext}
                style={{
                  ...paginationButtonStyle,
                  opacity: pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0 ? 0.4 : 1,
                  cursor: pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0 ? 'not-allowed' : 'pointer',
                }}
              >
                Sau
                <ChevronRight size={15} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* â”€â”€â”€ Layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const pageStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: 20,
};

const headerContainerStyle = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  gap: 16,
  flexWrap: 'wrap',
};

const titleWrapperStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 0,
};

/* â”€â”€â”€ Action Buttons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const primaryButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 7,
  border: 'none',
  borderRadius: 10,
  background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
  color: '#fff',
  fontSize: 13.5,
  fontWeight: 700,
  padding: '9px 18px',
  cursor: 'pointer',
  boxShadow: '0 4px 14px 0 rgba(99, 102, 241, 0.39)',
  transition: 'all 0.2s ease',
  letterSpacing: '0.01em',
};

const secondaryButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 7,
  border: 'none',
  borderRadius: 10,
  background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
  color: '#fff',
  fontSize: 13.5,
  fontWeight: 700,
  padding: '9px 16px',
  cursor: 'pointer',
  boxShadow: '0 4px 14px 0 rgba(16, 185, 129, 0.35)',
  transition: 'all 0.2s ease',
};

/* â”€â”€â”€ Stats Grid â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const statsGridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
  gap: 14,
};

const statCardStyle = {
  minHeight: 120,
  border: '1px solid #e5e7eb',
  borderRadius: 14,
  padding: '18px 20px',
  textAlign: 'left',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'flex-start',
  transition: 'all 0.2s ease',
  outline: 'none',
};

/* â”€â”€â”€ Filter Bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const filterBarStyle = {
  border: '1px solid #e5e7eb',
  borderRadius: 14,
  background: '#ffffff',
  padding: '14px 16px',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
};

const filterControlStyle = {
  width: '100%',
  height: 38,
  border: '1px solid #dbe4ef',
  borderRadius: 8,
  background: '#fff',
  color: '#111827',
  fontSize: 13.5,
  outline: 'none',
  padding: '0 12px',
  boxSizing: 'border-box',
  fontFamily: 'inherit',
  transition: 'border-color 0.15s',
};

/* â”€â”€â”€ Table Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const tableCardStyle = {
  border: '1px solid #e5e7eb',
  borderRadius: 14,
  background: '#fff',
  overflow: 'hidden',
  boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
};

const emptyCellStyle = {
  padding: '60px 10px',
  textAlign: 'center',
  color: '#9ca3af',
  borderBottom: '1px solid #f3f4f6',
  fontSize: 14,
};

/* â”€â”€â”€ Footer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const footerStyle = {
  minHeight: 46,
  padding: '0 16px',
  borderTop: '1px solid #f3f4f6',
  background: '#f9fafb',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  color: '#6b7280',
  fontSize: 13,
  flexWrap: 'wrap',
};

/* â”€â”€â”€ Pagination â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const paginationStyle = {
  padding: '13px 16px',
  borderTop: '1px solid #f3f4f6',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  color: '#6b7280',
  fontSize: 13,
  flexWrap: 'wrap',
  background: '#f9fafb',
};

const paginationButtonStyle = {
  height: 34,
  display: 'inline-flex',
  alignItems: 'center',
  gap: 5,
  border: '1px solid #d1d5db',
  borderRadius: 8,
  background: '#fff',
  color: '#374151',
  fontSize: 13,
  fontWeight: 600,
  padding: '0 12px',
  cursor: 'pointer',
  transition: 'all 0.15s',
};

/* â”€â”€â”€ Action Menu â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */
const menuButtonStyle = {
  width: 30,
  height: 30,
  border: 'none',
  borderRadius: 8,
  background: 'transparent',
  color: '#374151',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
};

const actionMenuStyle = {
  position: 'absolute',
  right: 0,
  top: 36,
  zIndex: 20,
  minWidth: 170,
  padding: 6,
  border: '1px solid #e5e7eb',
  borderRadius: 10,
  background: '#fff',
  boxShadow: '0 12px 28px rgba(15, 23, 42, 0.13)',
};

const actionMenuItemStyle = {
  width: '100%',
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '9px 11px',
  border: 'none',
  borderRadius: 7,
  background: 'transparent',
  fontSize: 13.5,
  fontWeight: 600,
  textAlign: 'left',
  fontFamily: 'inherit',
  transition: 'background 0.12s',
};
