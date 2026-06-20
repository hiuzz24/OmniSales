import * as XLSX from 'xlsx-js-style';

const thinBorder = { style: 'thin', color: { rgb: 'FF7F7F7F' } };

const allBorders = {
  top: thinBorder,
  bottom: thinBorder,
  left: thinBorder,
  right: thinBorder,
};

const HEADER_STYLE = {
  font: { bold: true, color: { rgb: 'FFFFFFFF' }, sz: 12 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FF3498DB' } },
  alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
  border: allBorders,
};

const colLetter = (index) => {
  let s = '';
  let n = index;
  while (n >= 0) {
    s = String.fromCharCode(65 + (n % 26)) + s;
    n = Math.floor(n / 26) - 1;
  }
  return s;
};

const setCellStyle = (worksheet, ref, style) => {
  const cell = worksheet[ref] || { t: 's', v: '' };
  cell.s = { ...(cell.s || {}), ...style };
  worksheet[ref] = cell;
};

const getAddressStr = (address) => {
  if (!address) return '';
  if (typeof address === 'string') return address;
  const parts = [address.street, address.ward, address.district, address.city]
    .filter(Boolean)
    .join(', ');
  return parts || '';
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (isNaN(d)) return '';
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
};

export const CUSTOMER_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true, getValue: () => '' },
  {
    key: 'code',
    label: 'Mã khách hàng',
    width: 18,
    defaultChecked: true,
    getValue: (c) => c.code || '',
  },
  {
    key: 'fullName',
    label: 'Họ và tên',
    width: 28,
    defaultChecked: true,
    getValue: (c) => c.fullName || '',
  },
  {
    key: 'gender',
    label: 'Giới tính',
    width: 10,
    defaultChecked: true,
    getValue: (c) => {
      if (c.gender === 'MALE') return 'Nam';
      if (c.gender === 'FEMALE') return 'Nữ';
      if (c.gender === 'OTHER') return 'Khác';
      return c.gender || '';
    },
  },
  {
    key: 'birth',
    label: 'Ngày sinh',
    width: 14,
    defaultChecked: false,
    getValue: (c) => (c.birth ? formatDate(c.birth) : ''),
  },
  {
    key: 'phone',
    label: 'Số điện thoại',
    width: 16,
    defaultChecked: true,
    getValue: (c) => c.phone || '',
  },
  {
    key: 'email',
    label: 'Email',
    width: 28,
    defaultChecked: true,
    getValue: (c) => c.email || '',
  },
  {
    key: 'address',
    label: 'Địa chỉ',
    width: 38,
    defaultChecked: false,
    getValue: (c) => getAddressStr(c.address),
  },
  {
    key: 'notes',
    label: 'Ghi chú',
    width: 30,
    defaultChecked: false,
    getValue: (c) => c.notes || '',
  },
  {
    key: 'isActive',
    label: 'Trạng thái',
    width: 14,
    defaultChecked: true,
    getValue: (c) => (c.isActive ? 'Hoạt động' : 'Ngừng hoạt động'),
  },
  {
    key: 'orderCount',
    label: 'Số đơn hàng',
    width: 14,
    defaultChecked: true,
    getValue: (c) => c.orderCount ?? 0,
  },
  {
    key: 'totalSpent',
    label: 'Tổng chi tiêu (VNĐ)',
    width: 20,
    defaultChecked: true,
    getValue: (c) => c.totalSpent ?? 0,
  },
  {
    key: 'createdAt',
    label: 'Ngày tạo',
    width: 18,
    defaultChecked: false,
    getValue: (c) =>
      c.createdAt ? new Date(c.createdAt).toLocaleString('vi-VN') : '',
  },
];

const PRICE_KEYS = new Set(['orderCount', 'totalSpent']);

export const exportCustomersToExcel = (
  customers,
  selectedColumnKeys = null,
  filename = 'danh-sach-khach-hang',
) => {
  if (!customers || customers.length === 0) {
    return { success: false, message: 'Không có khách hàng nào để xuất' };
  }

  const selectedColumns =
    selectedColumnKeys && selectedColumnKeys.length > 0
      ? CUSTOMER_EXPORT_COLUMNS.filter((c) => selectedColumnKeys.includes(c.key))
      : CUSTOMER_EXPORT_COLUMNS.filter((c) => c.defaultChecked);

  if (selectedColumns.length === 0) {
    return { success: false, message: 'Vui lòng chọn ít nhất 1 cột để xuất' };
  }

  const header = selectedColumns.map((c) => c.label);
  const aoa = [header];
  const rowMeta = [];
  const cellTypeData = [];

  const isNumericValue = (v) => typeof v === 'number' && !Number.isNaN(v);

  const toCell = (v) => {
    if (isNumericValue(v)) return { t: 'n', v };
    return { t: 's', v: v == null ? '' : String(v) };
  };

  customers.forEach((c, idx) => {
    const row = selectedColumns.map((col) => {
      if (col.key === 'stt') return idx + 1;
      return col.getValue(c, idx);
    });
    const cells = row.map((v) => toCell(v));
    aoa.push(cells);
    cellTypeData.push(cells);
    rowMeta.push('customer');
  });

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);

  worksheet['!cols'] = selectedColumns.map((c) => ({ wch: c.width }));
  worksheet['!rows'] = [{ hpt: 24 }];
  rowMeta.forEach(() => worksheet['!rows'].push({ hpt: 22 }));

  const numCols = selectedColumns.length;
  applyRowStyle(worksheet, 0, HEADER_STYLE, numCols);

  cellTypeData.forEach((cells, i) => {
    const rowIndex = i + 1;
    cells.forEach((cell, c) => {
      if (PRICE_KEYS.has(selectedColumns[c].key) && cell.t === 'n') {
        setCellStyle(worksheet, `${colLetter(c)}${rowIndex}`, {
          numFmt: '#,##0',
          font: { bold: false, color: { rgb: 'FF404040' }, sz: 11 },
          border: allBorders,
          alignment: { vertical: 'center' },
        });
      } else {
        setCellStyle(worksheet, `${colLetter(c)}${rowIndex}`, {
          font: { bold: false, color: { rgb: 'FF404040' }, sz: 11 },
          border: allBorders,
          alignment: { vertical: 'center' },
        });
      }
    });
  });

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Khách hàng');

  const now = new Date();
  const timestamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(
    now.getDate(),
  ).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(
    now.getMinutes(),
  ).padStart(2, '0')}`;

  const finalFilename = `${filename}_${timestamp}.xlsx`;
  XLSX.writeFile(workbook, finalFilename);

  return {
    success: true,
    message: `Đã xuất ${customers.length} khách hàng với ${selectedColumns.length} cột`,
    filename: finalFilename,
  };
};

function applyRowStyle(worksheet, rowIndex, style, numCols) {
  for (let c = 0; c < numCols; c += 1) {
    setCellStyle(worksheet, `${colLetter(c)}${rowIndex + 1}`, style);
  }
}
