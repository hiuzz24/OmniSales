import * as XLSX from 'xlsx-js-style';

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

const formatVariantName = (variant) => {
  if (!variant) return '';
  if (variant.name) return variant.name;
  if (!variant.optionValues) return '';
  return Object.values(variant.optionValues)
    .filter((v) => v != null && v !== '')
    .join(' / ');
};

export const PRODUCT_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true, getValue: () => '' },
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
    getValue: (p, _idx, ctx) => (ctx?.rowType === 'variant' ? `↳ ${formatVariantName(ctx.variant)}` : p.name || ''),
  },
  {
    key: 'sku',
    label: 'SKU',
    width: 18,
    defaultChecked: true,
    getValue: (p, _idx, ctx) =>
      ctx?.rowType === 'variant' ? ctx.variant?.sku || '' : p.sku || '',
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
    getValue: (p, _idx, ctx) =>
      ctx?.rowType === 'variant'
        ? ctx.variant?.price ?? ''
        : getPriceRange(p.variants),
  },
  {
    key: 'costPrice',
    label: 'Giá vốn (VNĐ)',
    width: 14,
    defaultChecked: false,
    getValue: (p, _idx, ctx) =>
      ctx?.rowType === 'variant'
        ? ctx.variant?.costPrice ?? ''
        : p.variants?.[0]?.costPrice ?? '',
  },
  {
    key: 'stock',
    label: 'Tồn kho',
    width: 10,
    defaultChecked: true,
    getValue: (p, _idx, ctx) =>
      ctx?.rowType === 'variant'
        ? ctx.variant?.availableQuantity ?? ctx.variant?.quantityOnHand ?? 0
        : getTotalStock(p.variants),
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

const thinBorder = { style: 'thin', color: { rgb: 'FF7F7F7F' } };

const allBorders = {
  top: thinBorder,
  bottom: thinBorder,
  left: thinBorder,
  right: thinBorder,
};

const HEADER_STYLE = {
  font: { bold: true, color: { rgb: 'FFFFFFFF' }, sz: 12 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FF1F4E78' } },
  alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
  border: allBorders,
};

const PRODUCT_STYLE = {
  font: { bold: true, color: { rgb: 'FF1F4E78' }, sz: 11 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FFEAF2FB' } },
  border: allBorders,
  alignment: { vertical: 'center' },
};

const VARIANT_STYLE = {
  font: { bold: false, color: { rgb: 'FF404040' }, sz: 11 },
  border: allBorders,
  alignment: { vertical: 'center' },
};

const PRICE_BOLD_STYLE = {
  numFmt: '#,##0',
  font: { bold: true, color: { rgb: 'FF1F4E78' }, sz: 11 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FFEAF2FB' } },
  border: allBorders,
};

const PRICE_VARIANT_STYLE = {
  numFmt: '#,##0',
  font: { bold: false, color: { rgb: 'FF404040' }, sz: 11 },
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

const applyRowStyle = (worksheet, rowIndex, style, numCols) => {
  for (let c = 0; c < numCols; c += 1) {
    setCellStyle(worksheet, `${colLetter(c)}${rowIndex + 1}`, style);
  }
};

const PRICE_KEYS = new Set(['price', 'costPrice', 'stock']);

const buildAoa = (products, selectedColumns) => {
  const header = selectedColumns.map((c) => c.label);
  const aoa = [header];
  const rowMeta = [];
  const cellType = [];

  const isNumericValue = (v) => typeof v === 'number' && !Number.isNaN(v);

  const toCell = (v) => {
    if (isNumericValue(v)) return { t: 'n', v };
    return { t: 's', v: v == null ? '' : String(v) };
  };

  products.forEach((p, productIdx) => {
    const productCtx = { rowType: 'product' };
    const productRow = selectedColumns.map((col) => {
      if (col.key === 'stt') return productIdx + 1;
      return col.getValue(p, productIdx, productCtx);
    });
    const productCells = productRow.map((v) => toCell(v));
    aoa.push(productCells);
    cellType.push(productCells);
    rowMeta.push('product');

    const variants = p.variants && p.variants.length > 0 ? p.variants : [];
    variants.forEach((variant) => {
      const variantCtx = { rowType: 'variant', variant };
      const variantRow = selectedColumns.map((col) => {
        if (col.key === 'stt') return '';
        return col.getValue(p, productIdx, variantCtx);
      });
      const variantCells = variantRow.map((v) => toCell(v));
      aoa.push(variantCells);
      cellType.push(variantCells);
      rowMeta.push('variant');
    });
  });

  return { aoa, rowMeta, cellType };
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

  const { aoa, rowMeta, cellType } = buildAoa(products, selectedColumns);
  const worksheet = XLSX.utils.aoa_to_sheet(aoa);

  worksheet['!cols'] = selectedColumns.map((c) => ({ wch: c.width }));
  worksheet['!rows'] = [{ hpt: 24 }];
  rowMeta.forEach(() => worksheet['!rows'].push({ hpt: 20 }));

  const numCols = selectedColumns.length;
  applyRowStyle(worksheet, 0, HEADER_STYLE, numCols);

  cellType.forEach((cells, i) => {
    const rowIndex = i + 1;
    const baseStyle = rowMeta[i] === 'product' ? PRODUCT_STYLE : VARIANT_STYLE;
    cells.forEach((cell, c) => {
      const colKey = selectedColumns[c].key;
      if (PRICE_KEYS.has(colKey) && cell.t === 'n') {
        setCellStyle(
          worksheet,
          `${colLetter(c)}${rowIndex}`,
          rowMeta[i] === 'product' ? PRICE_BOLD_STYLE : PRICE_VARIANT_STYLE,
        );
      } else {
        setCellStyle(worksheet, `${colLetter(c)}${rowIndex}`, baseStyle);
      }
    });
  });

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
