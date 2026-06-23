import * as XLSX from 'xlsx-js-style';

const thinBorder = { style: 'thin', color: { rgb: 'FFB7C3D0' } };
const allBorders = {
  top: thinBorder,
  bottom: thinBorder,
  left: thinBorder,
  right: thinBorder,
};

const TITLE_STYLE = {
  font: { bold: true, sz: 14, color: { rgb: 'FF111827' } },
  alignment: { horizontal: 'left', vertical: 'center' },
};

const SUBTITLE_STYLE = {
  font: { bold: true, sz: 16, color: { rgb: 'FF111827' } },
  alignment: { horizontal: 'left', vertical: 'center' },
};

const META_STYLE = {
  font: { sz: 10, italic: true, color: { rgb: 'FF64748B' } },
  alignment: { horizontal: 'left', vertical: 'center' },
};

const HEADER_STYLE = {
  font: { bold: true, sz: 11, color: { rgb: 'FFFFFFFF' } },
  fill: { patternType: 'solid', fgColor: { rgb: 'FF1F4E78' } },
  alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
  border: allBorders,
};

const BODY_STYLE = {
  font: { sz: 11, color: { rgb: 'FF111827' } },
  alignment: { vertical: 'center', wrapText: true },
  border: allBorders,
};

const BODY_ALT_STYLE = {
  ...BODY_STYLE,
  fill: { patternType: 'solid', fgColor: { rgb: 'FFF8FAFC' } },
};

const NUMBER_STYLE = {
  ...BODY_STYLE,
  numFmt: '#,##0',
  alignment: { horizontal: 'right', vertical: 'center' },
};

const NUMBER_ALT_STYLE = {
  ...NUMBER_STYLE,
  fill: { patternType: 'solid', fgColor: { rgb: 'FFF8FAFC' } },
};

const CURRENCY_STYLE = {
  ...NUMBER_STYLE,
  numFmt: '#,##0 "₫"',
};

const CURRENCY_ALT_STYLE = {
  ...CURRENCY_STYLE,
  fill: { patternType: 'solid', fgColor: { rgb: 'FFF8FAFC' } },
};

const STATUS_LABELS = {
  DRAFT: 'Lưu tạm',
  CONFIRMED: 'Hoàn thành',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  IN_PROGRESS: 'Đang kiểm',
  ORDER: 'Xuất bán hàng',
  DISPOSAL: 'Xuất hủy',
  TRANSFER: 'Trả nhà cung cấp',
  ADJUSTMENT: 'Xuất dùng',
  'in-stock': 'Đủ hàng',
  'low-stock': 'Sắp hết',
  'out-of-stock': 'Hết hàng',
  negative: 'Tồn âm',
};

export const formatExportDateTime = (value) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export const getStatusLabel = (status) => STATUS_LABELS[status] || status || '';

const colLetter = (index) => {
  let letters = '';
  let n = index;
  while (n >= 0) {
    letters = String.fromCharCode(65 + (n % 26)) + letters;
    n = Math.floor(n / 26) - 1;
  }
  return letters;
};

const setCellStyle = (worksheet, rowIndex, colIndex, style) => {
  const ref = `${colLetter(colIndex)}${rowIndex + 1}`;
  worksheet[ref] = {
    ...(worksheet[ref] || { t: 's', v: '' }),
    s: style,
  };
};

const toCell = (value, column) => {
  if (value == null || value === '') return '';
  if (column.type === 'number' || column.type === 'currency') {
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : '';
  }
  return value;
};

const getCellStyle = (column, rowIndex) => {
  const odd = rowIndex % 2 === 1;
  if (column.type === 'currency') return odd ? CURRENCY_ALT_STYLE : CURRENCY_STYLE;
  if (column.type === 'number') return odd ? NUMBER_ALT_STYLE : NUMBER_STYLE;
  return odd ? BODY_ALT_STYLE : BODY_STYLE;
};

const stamp = () => {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
};

export const exportInventoryWorkbook = ({
  rows,
  columns,
  selectedColumnKeys,
  title,
  fileName,
  sheetName = 'Inventory',
  brandName = 'OmniSales',
  extraSheets = [],
}) => {
  const selectedColumns = selectedColumnKeys?.length
    ? columns.filter((column) => selectedColumnKeys.includes(column.key))
    : columns.filter((column) => column.defaultChecked !== false);

  if (!selectedColumns.length) {
    return { success: false, message: 'Vui lòng chọn ít nhất 1 cột để xuất Excel.' };
  }

  if (!rows?.length) {
    return { success: false, message: 'Không có dữ liệu để xuất Excel.' };
  }

  const tableStartRow = 4;
  const aoa = [
    [brandName],
    [title],
    [`Xuất lúc: ${formatExportDateTime(new Date())}`],
    [],
    selectedColumns.map((column) => column.label),
    ...rows.map((row, index) => selectedColumns.map((column) => {
      if (column.key === 'stt') return index + 1;
      return toCell(column.getValue?.(row, index) ?? row[column.key], column);
    })),
  ];

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);
  const lastColumn = Math.max(selectedColumns.length - 1, 0);
  worksheet['!merges'] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: lastColumn } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: lastColumn } },
    { s: { r: 2, c: 0 }, e: { r: 2, c: lastColumn } },
  ];
  worksheet['!cols'] = selectedColumns.map((column) => ({ wch: column.width ?? 18 }));
  worksheet['!rows'] = [
    { hpt: 22 },
    { hpt: 26 },
    { hpt: 18 },
    { hpt: 12 },
    { hpt: 28 },
    ...rows.map(() => ({ hpt: 24 })),
  ];
  worksheet['!autofilter'] = {
    ref: `A${tableStartRow + 1}:${colLetter(lastColumn)}${tableStartRow + rows.length + 1}`,
  };

  setCellStyle(worksheet, 0, 0, TITLE_STYLE);
  setCellStyle(worksheet, 1, 0, SUBTITLE_STYLE);
  setCellStyle(worksheet, 2, 0, META_STYLE);

  selectedColumns.forEach((_, colIndex) => {
    setCellStyle(worksheet, tableStartRow, colIndex, HEADER_STYLE);
  });

  rows.forEach((_, rowIndex) => {
    selectedColumns.forEach((column, colIndex) => {
      setCellStyle(worksheet, tableStartRow + rowIndex + 1, colIndex, getCellStyle(column, rowIndex));
    });
  });

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, sheetName.slice(0, 31));

  extraSheets.forEach((sheet) => {
    const sheetRows = sheet.rows ?? [];
    const sheetColumns = sheet.selectedColumnKeys?.length
      ? sheet.columns.filter((column) => sheet.selectedColumnKeys.includes(column.key))
      : sheet.columns.filter((column) => column.defaultChecked !== false);

    if (!sheetColumns.length) return;

    const sheetAoa = [
      [brandName],
      [sheet.title],
      [`Xuáº¥t lÃºc: ${formatExportDateTime(new Date())}`],
      [],
      sheetColumns.map((column) => column.label),
      ...sheetRows.map((row, index) => sheetColumns.map((column) => {
        if (column.key === 'stt') return index + 1;
        return toCell(column.getValue?.(row, index) ?? row[column.key], column);
      })),
    ];
    const extraWorksheet = XLSX.utils.aoa_to_sheet(sheetAoa);
    const extraLastColumn = Math.max(sheetColumns.length - 1, 0);
    extraWorksheet['!merges'] = [
      { s: { r: 0, c: 0 }, e: { r: 0, c: extraLastColumn } },
      { s: { r: 1, c: 0 }, e: { r: 1, c: extraLastColumn } },
      { s: { r: 2, c: 0 }, e: { r: 2, c: extraLastColumn } },
    ];
    extraWorksheet['!cols'] = sheetColumns.map((column) => ({ wch: column.width ?? 18 }));
    extraWorksheet['!rows'] = [
      { hpt: 22 },
      { hpt: 26 },
      { hpt: 18 },
      { hpt: 12 },
      { hpt: 28 },
      ...sheetRows.map(() => ({ hpt: 24 })),
    ];
    extraWorksheet['!autofilter'] = {
      ref: `A${tableStartRow + 1}:${colLetter(extraLastColumn)}${tableStartRow + sheetRows.length + 1}`,
    };

    setCellStyle(extraWorksheet, 0, 0, TITLE_STYLE);
    setCellStyle(extraWorksheet, 1, 0, SUBTITLE_STYLE);
    setCellStyle(extraWorksheet, 2, 0, META_STYLE);
    sheetColumns.forEach((_, colIndex) => {
      setCellStyle(extraWorksheet, tableStartRow, colIndex, HEADER_STYLE);
    });
    sheetRows.forEach((_, rowIndex) => {
      sheetColumns.forEach((column, colIndex) => {
        setCellStyle(extraWorksheet, tableStartRow + rowIndex + 1, colIndex, getCellStyle(column, rowIndex));
      });
    });

    XLSX.utils.book_append_sheet(workbook, extraWorksheet, (sheet.sheetName ?? 'Sheet').slice(0, 31));
  });

  const finalFilename = `${fileName}_${stamp()}.xlsx`;
  XLSX.writeFile(workbook, finalFilename);
  return {
    success: true,
    message: `Đã xuất ${rows.length} dòng với ${selectedColumns.length} cột.`,
    filename: finalFilename,
  };
};
