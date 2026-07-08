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
  fill: { patternType: 'solid', fgColor: { rgb: 'FF2563EB' } },
  alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
  border: allBorders,
};

const formatDate = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (isNaN(d)) return '';
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

const formatCurrency = (amount) => {
  if (amount == null) return '';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 0,
  }).format(amount);
};

export const ORDER_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true, getValue: () => '' },
  {
    key: 'externalOrderId', label: 'Mã đơn', width: 22, defaultChecked: true,
    getValue: (o) => o.externalOrderId || '',
  },
  {
    key: 'createdAt', label: 'Ngày tạo', width: 18, defaultChecked: true,
    getValue: (o) => formatDate(o.createdAt),
  },
  {
    key: 'channelName', label: 'Kênh', width: 14, defaultChecked: true,
    getValue: (o) => o.channelName || '',
  },
  {
    key: 'buyerName', label: 'Khách hàng', width: 24, defaultChecked: true,
    getValue: (o) => o.buyerName || '',
  },
  {
    key: 'buyerPhone', label: 'SĐT', width: 14, defaultChecked: true,
    getValue: (o) => o.buyerPhone || '',
  },
  {
    key: 'items', label: 'Sản phẩm', width: 30, defaultChecked: true,
    getValue: (o) => o.items?.map(i => `${i.name} x${i.quantity}`).join('; ') || '',
  },
  {
    key: 'totalAmount', label: 'Tổng tiền', width: 16, defaultChecked: true,
    getValue: (o) => formatCurrency(o.totalAmount),
  },
  {
    key: 'status', label: 'Trạng thái', width: 14, defaultChecked: true,
    getValue: (o) => {
      const map = { PENDING: 'Chờ xử lý', CONFIRMED: 'Đã xác nhận', PROCESSING: 'Đang xử lý', SHIPPED: 'Sẵn sàng giao', IN_TRANSIT: 'Đang vận chuyển', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy' };
      return map[o.status] || o.status || '';
    },
  },
  {
    key: 'paymentStatus', label: 'Thanh toán', width: 18, defaultChecked: true,
    getValue: (o) => {
      const map = { UNPAID: 'Chưa thanh toán', PAID: 'Đã thanh toán' };
      return map[o.paymentStatus] || o.paymentStatus || '';
    },
  },
  {
    key: 'note', label: 'Ghi chú', width: 24, defaultChecked: false,
    getValue: (o) => o.note || '',
  },
];

export const exportOrdersToExcel = (orders, selectedColumnKeys = null, filename = 'danh-sach-don-hang') => {
  const columns = selectedColumnKeys
    ? ORDER_EXPORT_COLUMNS.filter((c) => selectedColumnKeys.includes(c.key))
    : ORDER_EXPORT_COLUMNS.filter((c) => c.defaultChecked);

  const aoa = [
    columns.map((c) => ({ v: c.label })),
    ...orders.map((order, idx) =>
      columns.map((col) => {
        const value = col.getValue(order);
        const isNumeric = col.key === 'totalAmount';
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
        t: 's',
        v: aoa[rowIdx][colIdx].v,
        s: {
          font: { color: { rgb: 'FF404040' }, sz: 11 },
          alignment: { vertical: 'center' },
          border: allBorders,
        },
      };
    }
  }

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Đơn hàng');

  const now = new Date();
  const ts = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;
  const finalFilename = `${filename}_${ts}.xlsx`;

  XLSX.writeFile(workbook, finalFilename);
  return { success: true, filename: finalFilename, count: orders.length };
};
