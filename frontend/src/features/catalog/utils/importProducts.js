import * as XLSX from 'xlsx-js-style';

export const IMPORT_TEMPLATE_COLUMNS = [
  { key: 'productSku',   label: 'Mã sản phẩm (SKU)', required: true,  example: 'SP-001' },
  { key: 'productName',  label: 'Tên sản phẩm',      required: true,  example: 'Áo thun nam cổ tròn' },
  { key: 'categoryName', label: 'Danh mục',          required: true,  example: 'Thời trang nam' },
  { key: 'description',  label: 'Mô tả',             required: false, example: 'Áo thun cotton 100%' },
  { key: 'brand',         label: 'Thương hiệu',       required: false, example: 'OEM' },
  { key: 'unit',          label: 'Đơn vị',            required: false, example: 'cái' },
  { key: 'status',        label: 'Trạng thái',        required: false, example: 'DRAFT' },
  { key: 'variantSku',    label: 'SKU biến thể',      required: true,  example: 'SP-001-RED-M' },
  { key: 'variantName',  label: 'Tên biến thể',      required: false, example: 'Đỏ / M' },
  { key: 'barcode',       label: 'Barcode',            required: false, example: '8934673001234' },
  { key: 'weightGrams',   label: 'Trọng lượng (g)',   required: false, example: '250' },
];

export const IMPORT_TEMPLATE_EXAMPLES = [
  {
    label: 'Sản phẩm A (1 biến thể)',
    rows: [
      {
        productSku: 'SP-001',
        productName: 'Áo thun nam cổ tròn',
        categoryName: 'Thời trang nam',
        description: 'Áo thun cotton 100%',
        brand: 'OEM',
        unit: 'cái',
        status: 'DRAFT',
        variantSku: 'SP-001-DEFAULT',
        variantName: 'Mặc định',
        barcode: '8934673001234',
        weightGrams: '250',
      },
    ],
  },
  {
    label: 'Sản phẩm B (nhiều biến thể)',
    rows: [
      {
        productSku: 'SP-002',
        productName: 'Áo polo nam',
        categoryName: 'Thời trang nam',
        description: 'Áo polo có cổ, vải cá sấu',
        brand: 'OEM',
        unit: 'cái',
        status: 'ACTIVE',
        variantSku: 'SP-002-RED-M',
        variantName: 'Đỏ / M',
        barcode: '8934673002001',
        weightGrams: '300',
      },
      {
        productSku: 'SP-002',
        productName: 'Áo polo nam',
        categoryName: 'Thời trang nam',
        description: 'Áo polo có cổ, vải cá sấu',
        brand: 'OEM',
        unit: 'cái',
        status: 'ACTIVE',
        variantSku: 'SP-002-RED-L',
        variantName: 'Đỏ / L',
        barcode: '8934673002002',
        weightGrams: '320',
      },
    ],
  },
];

const HEADER_NORMALIZE = {
  'mã sản phẩm (sku)': 'productSku',
  'mã sản phẩm': 'productSku',
  'sku': 'productSku',
  'productsku': 'productSku',
  'product sku': 'productSku',
  'sku cha': 'productSku',
  'tên sản phẩm': 'productName',
  'tên sp': 'productName',
  'productname': 'productName',
  'product name': 'productName',
  'danh mục': 'categoryName',
  'categoryname': 'categoryName',
  'category name': 'categoryName',
  'mô tả': 'description',
  'description': 'description',
  'thương hiệu': 'brand',
  'brand': 'brand',
  'đơn vị': 'unit',
  'don vi': 'unit',
  'unit': 'unit',
  'trạng thái': 'status',
  'trang thai': 'status',
  'status': 'status',
  'sku biến thể': 'variantSku',
  'variantsku': 'variantSku',
  'variant sku': 'variantSku',
  'tên biến thể': 'variantName',
  'variantname': 'variantName',
  'variant name': 'variantName',
  'barcode': 'barcode',
  'trọng lượng (g)': 'weightGrams',
  'trọng lượng': 'weightGrams',
  'weightgrams': 'weightGrams',
  'weight grams': 'weightGrams',
};

const REQUIRED_KEYS = ['productSku', 'productName', 'variantSku'];

export const parseImportFile = async (file) => {
  if (!file) {
    return { ok: false, message: 'Không có file được chọn' };
  }
  const name = (file.name || '').toLowerCase();
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    return { ok: false, message: 'Chỉ chấp nhận file .xlsx hoặc .xls' };
  }

  const arrayBuffer = await file.arrayBuffer();
  const workbook = XLSX.read(arrayBuffer, { type: 'array' });
  if (!workbook.SheetNames || workbook.SheetNames.length === 0) {
    return { ok: false, message: 'File Excel không có sheet nào' };
  }

  const PREFERRED_SHEET_NAMES = ['Nhập liệu', 'Nhap lieu', 'nhaplieu', 'Template', 'template', 'import', 'Import'];
  let targetSheetName = null;
  for (const preferred of PREFERRED_SHEET_NAMES) {
    const found = workbook.SheetNames.find(
      (n) => n && n.toLowerCase().trim() === preferred.toLowerCase().trim(),
    );
    if (found) {
      targetSheetName = found;
      break;
    }
  }

  const requiredFoundInSomeSheet = REQUIRED_KEYS.some((rk) => {
    return workbook.SheetNames.some((sn) => {
      const sh = workbook.Sheets[sn];
      if (!sh) return false;
      const aoa = XLSX.utils.sheet_to_json(sh, { header: 1, defval: '', raw: false });
      if (aoa.length < 1) return false;
      const hdr = aoa[0].map((h) => String(h || '').trim());
      const keys = hdr.map((h) => HEADER_NORMALIZE[h.toLowerCase()] || '');
      return keys.includes(rk);
    });
  });

  const candidateSheets = (targetSheetName || requiredFoundInSomeSheet)
    ? [targetSheetName || workbook.SheetNames.find((sn) => {
        const sh = workbook.Sheets[sn];
        if (!sh) return false;
        const aoa = XLSX.utils.sheet_to_json(sh, { header: 1, defval: '', raw: false });
        if (aoa.length < 1) return false;
        const hdr = aoa[0].map((h) => String(h || '').trim());
        const keys = hdr.map((h) => HEADER_NORMALIZE[h.toLowerCase()] || '');
        return keys.some((k) => k);
      })]
    : workbook.SheetNames;

  let headerRow = null;
  let columnKeys = null;
  let rows = [];
  let totalRows = 0;
  let matchedSheet = null;
  let lastError = '';
  const errors = [];

  for (const sheetName of candidateSheets) {
    const sheet = workbook.Sheets[sheetName];
    if (!sheet) continue;
    const aoa = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '', raw: false });
    if (aoa.length < 2) {
      errors.push(`Sheet "${sheetName}" không có dữ liệu`);
      continue;
    }
    const hdr = aoa[0].map((h) => String(h || '').trim());
    const keys = hdr.map((h) => HEADER_NORMALIZE[h.toLowerCase()] || '');
    const missing = REQUIRED_KEYS.filter((k) => !keys.includes(k));
    if (missing.length > 0) {
      const sampleHeader = hdr[0] || '(trống)';
      errors.push(
        `Sheet "${sheetName}" thiếu cột bắt buộc: ${missing.join(', ')}. ` +
          `Header đọc được: [${hdr.join(' | ')}]. Header đầu tiên: "${sampleHeader}".`,
      );
      continue;
    }
    const parsedRows = [];
    for (let i = 1; i < aoa.length; i += 1) {
      const row = aoa[i];
      if (!row || row.every((c) => c == null || String(c).trim() === '')) continue;
      const obj = {};
      keys.forEach((key, idx) => {
        if (key) {
          const value = row[idx];
          obj[key] = value == null ? '' : String(value).trim();
        }
      });
      obj.__rowIndex = i + 1;
      parsedRows.push(obj);
    }
    if (parsedRows.length === 0) {
      lastError = `Sheet "${sheetName}" không có dòng dữ liệu nào`;
      continue;
    }
    headerRow = hdr;
    columnKeys = keys;
    rows = parsedRows;
    totalRows = parsedRows.length;
    matchedSheet = sheetName;
    break;
  }

  if (!matchedSheet) {
    const expected = IMPORT_TEMPLATE_COLUMNS.filter((c) => c.required).map((c) => c.label);
    return {
      ok: false,
      message:
        `Không tìm thấy sheet hợp lệ nào.\n\n` +
        `Các sheet đã thử: ${workbook.SheetNames.join(', ')}.\n` +
        `Lỗi chi tiết:\n- ${errors.join('\n- ')}\n\n` +
        `Các cột bắt buộc phải có (ít nhất 1 sheet):\n- ${expected.join('\n- ')}\n\n` +
        `Gợi ý: Tải lại file template mẫu và điền dữ liệu vào sheet "Nhập liệu" (sheet cuối cùng).`,
    };
  }

  return {
    ok: true,
    headers: headerRow,
    columnKeys,
    rows,
    totalRows,
    sheetName: matchedSheet,
  };
};
