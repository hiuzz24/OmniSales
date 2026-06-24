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
  fill: { patternType: 'solid', fgColor: { rgb: 'FF7C3AED' } }, // Purple/Violet
  alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
  border: allBorders,
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (isNaN(d)) return '';
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

export const TRANSFER_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, getValue: (_, idx) => idx + 1 },
  { key: 'transferCode', label: 'Mã phiếu', width: 20, getValue: (t) => t.transferCode || '' },
  { key: 'fromWarehouseName', label: 'Kho xuất', width: 24, getValue: (t) => t.fromWarehouseName || '' },
  { key: 'toWarehouseName', label: 'Kho nhận', width: 24, getValue: (t) => t.toWarehouseName || '' },
  { key: 'skuCount', label: 'SL SKU', width: 10, getValue: (t) => t.skuCount ?? 0 },
  { key: 'totalQuantity', label: 'Tổng SL', width: 12, getValue: (t) => t.totalQuantity ?? 0 },
  { key: 'note', label: 'Ghi chú', width: 30, getValue: (t) => t.note || '' },
  {
    key: 'status', label: 'Trạng thái', width: 16,
    getValue: (t) => {
      const map = { DRAFT: 'Nháp', IN_TRANSIT: 'Đang vận chuyển', RECEIVED: 'Hoàn thành', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy' };
      return map[t.status] || t.status || '';
    }
  },
  { key: 'createdBy', label: 'Người tạo', width: 20, getValue: (t) => t.createdBy || '' },
  { key: 'createdAt', label: 'Ngày tạo', width: 18, getValue: (t) => formatDate(t.createdAt) },
];

export const exportTransfersToExcel = (transfers, filename = 'danh-sach-phieu-chuyen-kho') => {
  const columns = TRANSFER_EXPORT_COLUMNS;

  const aoa = [
    columns.map((c) => ({ v: c.label })),
    ...transfers.map((t, idx) =>
      columns.map((col) => {
        const value = col.getValue(t, idx);
        const isNumeric = col.key === 'skuCount' || col.key === 'totalQuantity' || col.key === 'stt';
        return { v: value, t: isNumeric ? 'n' : 's' };
      })
    ),
  ];

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);
  worksheet['!cols'] = columns.map((c) => ({ wch: c.width }));

  for (let colIdx = 0; colIdx < columns.length; colIdx += 1) {
    const cellRef = XLSX.utils.encode_cell({ r: 0, c: colIdx });
    worksheet[cellRef] = { ...worksheet[cellRef], s: HEADER_STYLE };
  }

  for (let rowIdx = 1; rowIdx < aoa.length; rowIdx += 1) {
    for (let colIdx = 0; colIdx < columns.length; colIdx += 1) {
      const cellRef = XLSX.utils.encode_cell({ r: rowIdx, c: colIdx });
      worksheet[cellRef] = {
        ...worksheet[cellRef],
        t: aoa[rowIdx][colIdx].t,
        v: aoa[rowIdx][colIdx].v,
        s: {
          font: { color: { rgb: 'FF404040' }, sz: 11 },
          alignment: { vertical: 'center', horizontal: columns[colIdx].key === 'stt' || columns[colIdx].key === 'skuCount' || columns[colIdx].key === 'totalQuantity' ? 'right' : 'left' },
          border: allBorders,
        },
      };
    }
  }

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Phiếu chuyển kho');

  const now = new Date();
  const ts = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
  const finalFilename = `${filename}_${ts}.xlsx`;

  XLSX.writeFile(workbook, finalFilename);
  return { success: true, filename: finalFilename, count: transfers.length };
};
