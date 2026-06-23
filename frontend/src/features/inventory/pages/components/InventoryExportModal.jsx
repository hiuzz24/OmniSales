import { useEffect, useMemo, useState } from 'react';
import { Download, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { exportInventoryWorkbook } from './inventoryExcelExport';

export default function InventoryExportModal({
  open,
  onClose,
  rows = [],
  columns = [],
  detailColumns = [],
  buildDetailRows,
  getDateValue,
  loadRows,
  title,
  fileName,
  sheetName,
}) {
  const [includeDetails, setIncludeDetails] = useState(false);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [exporting, setExporting] = useState(false);
  const currentColumns = includeDetails && detailColumns.length ? detailColumns : columns;
  const defaultKeys = useMemo(
    () => currentColumns.filter((column) => column.defaultChecked !== false).map((column) => column.key),
    [currentColumns],
  );
  const [selectedKeys, setSelectedKeys] = useState(defaultKeys);

  useEffect(() => {
    if (open) setSelectedKeys(defaultKeys);
  }, [defaultKeys, open]);

  if (!open) return null;

  const selectedCount = selectedKeys.length;
  const canExportDetails = detailColumns.length > 0 && typeof buildDetailRows === 'function';

  const toggleKey = (key) => {
    setSelectedKeys((current) => (
      current.includes(key)
        ? current.filter((item) => item !== key)
        : [...current, key]
    ));
  };

  const parseDate = (value, endOfDay = false) => {
    if (!value) return null;
    const parsed = new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
    return Number.isNaN(parsed.getTime()) ? 'invalid' : parsed;
  };

  const getRowsInDateRange = (baseRows) => {
    const from = parseDate(fromDate);
    const to = parseDate(toDate, true);

    if (from === 'invalid' || to === 'invalid') {
      return { error: 'Ngày lọc không hợp lệ.' };
    }
    if (from && to && from > to) {
      return { error: 'Ngày bắt đầu không được lớn hơn ngày kết thúc.' };
    }
    if (!getDateValue || (!from && !to)) {
      return { rows: baseRows };
    }

    return {
      rows: baseRows.filter((row) => {
        const raw = getDateValue(row);
        if (!raw) return false;
        const rowDate = new Date(raw);
        if (Number.isNaN(rowDate.getTime())) return false;
        if (from && rowDate < from) return false;
        if (to && rowDate > to) return false;
        return true;
      }),
    };
  };

  const handleExport = async () => {
    setExporting(true);
    let result;
    try {
      const baseRows = typeof loadRows === 'function' ? await loadRows() : rows;
      const filteredRows = getRowsInDateRange(baseRows);
      if (filteredRows.error) {
        toast.error(filteredRows.error);
        setExporting(false);
        return;
      }
      if (!filteredRows.rows.length) {
        toast.error('Không có dữ liệu trong khoảng thời gian đã chọn.');
        setExporting(false);
        return;
      }
      const exportRows = includeDetails && canExportDetails
        ? await buildDetailRows(filteredRows.rows)
        : filteredRows.rows;

      result = exportInventoryWorkbook({
        rows: exportRows,
        columns: currentColumns,
        selectedColumnKeys: selectedKeys,
        title: includeDetails && canExportDetails ? `${title} - CHI TIẾT SKU` : title,
        fileName,
        sheetName,
      });
    } catch (error) {
      toast.error(error?.message || 'Không thể lấy dữ liệu chi tiết để xuất Excel.');
      setExporting(false);
      return;
    }
    setExporting(false);
    if (result.success) {
      toast.success(result.message);
      onClose?.();
    } else {
      toast.error(result.message);
    }
  };

  return (
    <div style={overlayStyle} role="presentation" onMouseDown={onClose}>
      <div style={modalStyle} role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <div style={headerStyle}>
          <div>
            <h2 style={titleStyle}>Xuất Excel</h2>
            <p style={subtitleStyle}>{title} · {rows.length} dòng dữ liệu đang hiển thị</p>
          </div>
          <button type="button" style={iconButtonStyle} onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </div>

        <div style={toolbarStyle}>
          <span style={counterStyle}>{selectedCount} / {currentColumns.length} cột được chọn</span>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button type="button" style={ghostButtonStyle} onClick={() => setSelectedKeys(currentColumns.map((column) => column.key))}>
              Chọn tất cả
            </button>
            <button type="button" style={ghostButtonStyle} onClick={() => setSelectedKeys(defaultKeys)}>
              Mặc định
            </button>
            <button type="button" style={ghostButtonStyle} onClick={() => setSelectedKeys([])}>
              Bỏ chọn
            </button>
          </div>
        </div>

        <div style={filterGridStyle}>
          <label style={fieldStyle}>
            <span style={fieldLabelStyle}>Từ ngày</span>
            <input type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} style={dateInputStyle} />
          </label>
          <label style={fieldStyle}>
            <span style={fieldLabelStyle}>Đến ngày</span>
            <input type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} style={dateInputStyle} />
          </label>
          {canExportDetails && (
            <label style={detailToggleStyle}>
              <input
                type="checkbox"
                checked={includeDetails}
                onChange={(event) => setIncludeDetails(event.target.checked)}
              />
              <span>Xuất chi tiết SKU, số lượng, đơn giá, thành tiền</span>
            </label>
          )}
        </div>

        <div style={columnGridStyle}>
          {currentColumns.map((column) => (
            <label key={column.key} style={checkboxLabelStyle}>
              <input
                type="checkbox"
                checked={selectedKeys.includes(column.key)}
                onChange={() => toggleKey(column.key)}
              />
              <span>{column.label}</span>
            </label>
          ))}
        </div>

        <div style={footerStyle}>
          <button type="button" style={cancelButtonStyle} onClick={onClose} disabled={exporting}>Hủy</button>
          <button type="button" style={{ ...primaryButtonStyle, opacity: exporting ? 0.7 : 1 }} onClick={handleExport} disabled={exporting}>
            <Download size={16} />
            {exporting ? 'Đang xuất...' : 'Xuất file'}
          </button>
        </div>
      </div>
    </div>
  );
}

const overlayStyle = {
  position: 'fixed',
  inset: 0,
  zIndex: 1000,
  background: 'rgba(15, 23, 42, 0.35)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 20,
};

const modalStyle = {
  width: 'min(760px, 100%)',
  maxHeight: '90vh',
  overflow: 'auto',
  borderRadius: 12,
  background: '#fff',
  boxShadow: '0 24px 60px rgba(15, 23, 42, 0.22)',
};

const headerStyle = {
  padding: '20px 22px',
  borderBottom: '1px solid #e5e7eb',
  display: 'flex',
  alignItems: 'flex-start',
  justifyContent: 'space-between',
  gap: 16,
};

const titleStyle = {
  margin: 0,
  color: '#020617',
  fontSize: 20,
  fontWeight: 800,
};

const subtitleStyle = {
  margin: '6px 0 0',
  color: '#64748b',
  fontSize: 13,
};

const iconButtonStyle = {
  width: 34,
  height: 34,
  border: '1px solid #e5e7eb',
  borderRadius: 8,
  background: '#fff',
  color: '#020617',
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
};

const toolbarStyle = {
  padding: '14px 22px',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  flexWrap: 'wrap',
};

const counterStyle = {
  color: '#334155',
  fontSize: 13,
  fontWeight: 700,
};

const columnGridStyle = {
  padding: '0 22px 18px',
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
  gap: 10,
};

const filterGridStyle = {
  padding: '0 22px 16px',
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
  gap: 10,
  alignItems: 'end',
};

const fieldStyle = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
};

const fieldLabelStyle = {
  color: '#475569',
  fontSize: 12,
  fontWeight: 700,
};

const dateInputStyle = {
  height: 38,
  border: '1px solid #dbe4ef',
  borderRadius: 8,
  padding: '0 12px',
  color: '#020617',
  fontSize: 14,
  outline: 'none',
};

const detailToggleStyle = {
  minHeight: 38,
  border: '1px solid #dbe4ef',
  borderRadius: 8,
  padding: '0 12px',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  color: '#020617',
  fontSize: 13,
  fontWeight: 700,
};

const checkboxLabelStyle = {
  minHeight: 42,
  border: '1px solid #e5e7eb',
  borderRadius: 8,
  padding: '0 12px',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  color: '#020617',
  fontSize: 14,
  fontWeight: 600,
  cursor: 'pointer',
};

const footerStyle = {
  padding: '16px 22px',
  borderTop: '1px solid #e5e7eb',
  display: 'flex',
  justifyContent: 'flex-end',
  gap: 10,
};

const ghostButtonStyle = {
  height: 34,
  border: '1px solid #dbe4ef',
  borderRadius: 8,
  background: '#fff',
  color: '#020617',
  fontSize: 13,
  fontWeight: 700,
  padding: '0 12px',
  cursor: 'pointer',
};

const cancelButtonStyle = {
  ...ghostButtonStyle,
  height: 38,
};

const primaryButtonStyle = {
  height: 38,
  border: 'none',
  borderRadius: 8,
  background: '#020617',
  color: '#fff',
  fontSize: 14,
  fontWeight: 800,
  padding: '0 16px',
  display: 'inline-flex',
  alignItems: 'center',
  gap: 8,
  cursor: 'pointer',
};
