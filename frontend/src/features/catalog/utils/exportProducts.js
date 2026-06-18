import * as XLSX from 'xlsx';

const STATUS_LABELS = {
  ACTIVE: 'Hoạt động',
  INACTIVE: 'Ngừng bán',
  DRAFT: 'Nháp',
};

const getPriceRange = (variants) => {
  if (!variants || variants.length === 0) return 0;
  const prices = variants.map((v) => v.price).filter((p) => p != null);
  if (prices.length === 0) return 0;
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max ? min : `${min} - ${max}`;
};

const getTotalStock = (variants) => {
  if (!variants) return 0;
  return variants.reduce((sum, v) => sum + (v.availableQuantity || v.quantityOnHand || 0), 0);
};

const getPrimaryImage = (product) => {
  return (
    product.images?.find((img) => img.isPrimary)?.url ||
    product.variants?.[0]?.images?.[0]?.url ||
    product.images?.[0]?.url ||
    ''
  );
};

export const PRODUCT_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true, getValue: (_p, idx) => idx + 1 },
  {
    key: 'id',
    label: 'Mã sản phẩm',
    width: 38,
    defaultChecked: false,
    getValue: (p) => p.id || '',
  },
  {
    key: 'name',
    label: 'Tên sản phẩm',
    width: 40,
    defaultChecked: true,
    getValue: (p) => p.name || '',
  },
  {
    key: 'sku',
    label: 'SKU',
    width: 18,
    defaultChecked: true,
    getValue: (p) => p.sku || p.variants?.[0]?.sku || '',
  },
  {
    key: 'barcode',
    label: 'Barcode',
    width: 16,
    defaultChecked: false,
    getValue: (p) => p.barcode || '',
  },
  {
    key: 'brand',
    label: 'Thương hiệu',
    width: 16,
    defaultChecked: false,
    getValue: (p) => p.brand || '',
  },
  {
    key: 'category',
    label: 'Danh mục',
    width: 22,
    defaultChecked: true,
    getValue: (p) => p.categoryName || 'Chưa phân loại',
  },
  {
    key: 'description',
    label: 'Mô tả',
    width: 40,
    defaultChecked: false,
    getValue: (p) => p.description || '',
  },
  {
    key: 'channels',
    label: 'Kênh bán',
    width: 18,
    defaultChecked: false,
    getValue: (p) => (p.channels || []).join(', '),
  },
  {
    key: 'price',
    label: 'Giá bán (VNĐ)',
    width: 18,
    defaultChecked: true,
    getValue: (p) => getPriceRange(p.variants),
  },
  {
    key: 'costPrice',
    label: 'Giá vốn (VNĐ)',
    width: 14,
    defaultChecked: false,
    getValue: (p) => p.variants?.[0]?.costPrice ?? '',
  },
  {
    key: 'stock',
    label: 'Tồn kho',
    width: 10,
    defaultChecked: true,
    getValue: (p) => getTotalStock(p.variants),
  },
  {
    key: 'variantCount',
    label: 'Số biến thể',
    width: 12,
    defaultChecked: false,
    getValue: (p) => p.variants?.length || 0,
  },
  {
    key: 'weight',
    label: 'Trọng lượng (g)',
    width: 14,
    defaultChecked: false,
    getValue: (p) => p.weightGrams ?? '',
  },
  {
    key: 'dimensions',
    label: 'Kích thước',
    width: 16,
    defaultChecked: false,
    getValue: (p) => p.dimensions || '',
  },
  {
    key: 'status',
    label: 'Trạng thái',
    width: 14,
    defaultChecked: true,
    getValue: (p) => STATUS_LABELS[p.status?.toUpperCase()] || p.status || '',
  },
  {
    key: 'image',
    label: 'Hình ảnh chính',
    width: 40,
    defaultChecked: false,
    getValue: (p) => getPrimaryImage(p),
  },
  {
    key: 'createdAt',
    label: 'Ngày tạo',
    width: 20,
    defaultChecked: false,
    getValue: (p) => (p.createdAt ? new Date(p.createdAt).toLocaleString('vi-VN') : ''),
  },
  {
    key: 'updatedAt',
    label: 'Ngày cập nhật',
    width: 20,
    defaultChecked: false,
    getValue: (p) => (p.updatedAt ? new Date(p.updatedAt).toLocaleString('vi-VN') : ''),
  },
];

const buildRows = (products, selectedColumns) => {
  return products.map((p, idx) => {
    const row = {};
    selectedColumns.forEach((col) => {
      row[col.label] = col.getValue(p, idx);
    });
    return row;
  });
};

export const exportProductsToExcel = (
  products,
  selectedColumnKeys = null,
  filename = 'danh-sach-san-pham',
) => {
  if (!products || products.length === 0) {
    return { success: false, message: 'Không có sản phẩm nào để xuất' };
  }

  const selectedColumns =
    selectedColumnKeys && selectedColumnKeys.length > 0
      ? PRODUCT_EXPORT_COLUMNS.filter((c) => selectedColumnKeys.includes(c.key))
      : PRODUCT_EXPORT_COLUMNS.filter((c) => c.defaultChecked);

  if (selectedColumns.length === 0) {
    return { success: false, message: 'Vui lòng chọn ít nhất 1 cột để xuất' };
  }

  const rows = buildRows(products, selectedColumns);
  const worksheet = XLSX.utils.json_to_sheet(rows);

  worksheet['!cols'] = selectedColumns.map((c) => ({ wch: c.width }));

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Sản phẩm');

  const now = new Date();
  const timestamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(
    now.getDate(),
  ).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}`;

  const finalFilename = `${filename}_${timestamp}.xlsx`;
  XLSX.writeFile(workbook, finalFilename);

  return {
    success: true,
    message: `Đã xuất ${products.length} sản phẩm với ${selectedColumns.length} cột`,
    filename: finalFilename,
  };
};
