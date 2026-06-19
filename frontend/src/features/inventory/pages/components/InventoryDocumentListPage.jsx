import { ChevronLeft, ChevronRight, Download, Plus, Search } from 'lucide-react';
import { formatNumber } from './inventoryDocumentListUtils';

export const Badge = ({ label, icon: Icon, color = '#475569', bg = '#f8fafc', border = '#e2e8f0' }) => (
  <span style={{
    display: 'inline-flex',
    alignItems: 'center',
    gap: 5,
    padding: '4px 10px',
    borderRadius: 999,
    border: `1px solid ${border}`,
    background: bg,
    color,
    fontSize: 12,
    fontWeight: 600,
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
  <div style={{ position: 'relative', minWidth: 260, flex: '1 1 320px' }}>
    <Search size={18} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: '#8aa0bd' }} />
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
      <div style={headerStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{ width: 40, height: 40, borderRadius: 12, background: iconBg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <HeaderIcon size={20} color={iconColor} />
          </div>
          <div>
            <h1 style={{ margin: 0, color: '#020617', fontSize: 24, lineHeight: 1.2, fontWeight: 700 }}>{title}</h1>
            <p style={{ margin: '4px 0 0', color: '#536b8f', fontSize: 14 }}>{description}</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <button type="button" onClick={onExport} style={secondaryButtonStyle}>
            <Download size={16} />
            Xuất Excel
          </button>
          <button type="button" onClick={onCreate} style={primaryButtonStyle}>
            <Plus size={17} />
            {createLabel}
          </button>
        </div>
      </div>

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
                borderColor: stat.active ? stat.color : '#e5e7eb',
                background: stat.active ? stat.bg : '#fff',
                boxShadow: stat.active ? `0 0 0 3px ${stat.border ?? stat.bg}` : 'none',
                cursor: stat.onClick ? 'pointer' : 'default',
              }}
            >
              <span>
                <span style={{ display: 'block', fontSize: 14, color: '#475569', marginBottom: 22 }}>{stat.label}</span>
                <strong style={{ display: 'block', fontSize: 24, color: '#020617', lineHeight: 1 }}>{formatNumber(stat.value ?? 0)}</strong>
                <span style={{ display: 'block', fontSize: 12, color: '#8aa0bd', marginTop: 8 }}>phiếu</span>
              </span>
              <span style={{
                width: 34,
                height: 34,
                borderRadius: 10,
                background: stat.bg,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
                <Icon size={17} color={stat.color} />
              </span>
            </button>
          );
        })}
      </div>

      <div style={filterBarStyle}>
        {filters}
      </div>

      <div style={tableCardStyle}>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', minWidth: minTableWidth, borderCollapse: 'collapse', fontSize: 14 }}>
            <thead>
              <tr>
                {columns.map((column) => (
                  <th
                    key={column.key ?? column.label}
                    style={{
                      padding: '14px 12px',
                      textAlign: column.align ?? 'left',
                      color: '#020617',
                      fontWeight: 700,
                      whiteSpace: 'nowrap',
                      borderBottom: '1px solid #dfe7f2',
                      background: '#f8fafc',
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
          <div style={paginationStyle}>
            <span>{pagination.label}</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <button
                type="button"
                disabled={pagination.page <= 0}
                onClick={pagination.onPrevious}
                style={{ ...paginationButtonStyle, opacity: pagination.page <= 0 ? 0.5 : 1, cursor: pagination.page <= 0 ? 'not-allowed' : 'pointer' }}
              >
                <ChevronLeft size={16} />
                Trước
              </button>
              <span style={{ minWidth: 92, textAlign: 'center', color: '#020617', fontWeight: 700 }}>
                Trang {pagination.page + 1} / {pagination.totalPages}
              </span>
              <button
                type="button"
                disabled={pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0}
                onClick={pagination.onNext}
                style={{
                  ...paginationButtonStyle,
                  opacity: pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0 ? 0.5 : 1,
                  cursor: pagination.page >= pagination.totalPages - 1 || pagination.totalElements === 0 ? 'not-allowed' : 'pointer',
                }}
              >
                Sau
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

const pageStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: 24,
};

const headerStyle = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  gap: 16,
  flexWrap: 'wrap',
};

const primaryButtonStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 8,
  border: 'none',
  borderRadius: 8,
  background: '#020617',
  color: '#fff',
  fontSize: 14,
  fontWeight: 700,
  padding: '10px 16px',
  cursor: 'pointer',
};

const secondaryButtonStyle = {
  ...primaryButtonStyle,
  border: '1px solid #e5e7eb',
  background: '#fff',
  color: '#020617',
  fontWeight: 600,
};

const statsGridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(4, minmax(180px, 1fr))',
  gap: 16,
};

const statCardStyle = {
  minHeight: 128,
  border: '1px solid #e5e7eb',
  borderRadius: 10,
  padding: '20px 22px',
  textAlign: 'left',
  display: 'flex',
  justifyContent: 'space-between',
};

const filterBarStyle = {
  border: '1px solid #e5e7eb',
  borderRadius: 10,
  background: '#fff',
  padding: 16,
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  flexWrap: 'wrap',
};

const filterControlStyle = {
  width: '100%',
  height: 38,
  border: '1px solid #dbe4ef',
  borderRadius: 8,
  background: '#fff',
  color: '#020617',
  fontSize: 14,
  outline: 'none',
  padding: '0 14px',
  boxSizing: 'border-box',
};

const tableCardStyle = {
  border: '1px solid #dfe7f2',
  borderRadius: 10,
  background: '#fff',
  overflow: 'hidden',
};

const emptyCellStyle = {
  padding: '64px 10px',
  textAlign: 'center',
  color: '#8aa0bd',
  borderBottom: '1px solid #e5e7eb',
};

const footerStyle = {
  minHeight: 46,
  padding: '0 16px',
  borderTop: '1px solid #eef2f7',
  background: '#f8fafc',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  color: '#536b8f',
  fontSize: 13,
  flexWrap: 'wrap',
};

const paginationStyle = {
  padding: '14px 16px',
  borderTop: '1px solid #eef2f7',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  color: '#536b8f',
  fontSize: 13,
  flexWrap: 'wrap',
};

const paginationButtonStyle = {
  height: 34,
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  border: '1px solid #dbe4ef',
  borderRadius: 8,
  background: '#fff',
  color: '#020617',
  fontSize: 13,
  fontWeight: 700,
  padding: '0 12px',
};

const menuButtonStyle = {
  width: 30,
  height: 30,
  border: 'none',
  borderRadius: 8,
  background: 'transparent',
  color: '#020617',
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
  minWidth: 168,
  padding: 6,
  border: '1px solid #e5e7eb',
  borderRadius: 8,
  background: '#fff',
  boxShadow: '0 12px 24px rgba(15, 23, 42, 0.14)',
};

const actionMenuItemStyle = {
  width: '100%',
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '8px 10px',
  border: 'none',
  borderRadius: 6,
  background: 'transparent',
  fontSize: 13,
  fontWeight: 600,
  textAlign: 'left',
};
