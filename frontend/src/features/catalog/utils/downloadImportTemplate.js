import * as XLSX from 'xlsx-js-style';
import { IMPORT_TEMPLATE_COLUMNS, IMPORT_TEMPLATE_EXAMPLES } from './importProducts';

const thinBorder = { style: 'thin', color: { rgb: 'FF7F7F7F' } };
const allBorders = {
  top: thinBorder,
  bottom: thinBorder,
  left: thinBorder,
  right: thinBorder,
};

const HEADER_STYLE = {
  font: { bold: true, color: { rgb: 'FFFFFFFF' }, sz: 11 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FF1F4E78' } },
  alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
  border: allBorders,
};

const EXAMPLE_STYLE = {
  font: { italic: true, color: { rgb: 'FF475569' }, sz: 10 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FFF1F5F9' } },
  border: allBorders,
  alignment: { vertical: 'center' },
};

const REQUIRED_BADGE_STYLE = {
  font: { bold: true, color: { rgb: 'FFB91C1C' }, sz: 10 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FFFEE2E2' } },
  alignment: { horizontal: 'center', vertical: 'center' },
  border: allBorders,
};

const OPTIONAL_BADGE_STYLE = {
  font: { color: { rgb: 'FF64748B' }, sz: 10 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FFF1F5F9' } },
  alignment: { horizontal: 'center', vertical: 'center' },
  border: allBorders,
};

const SECTION_TITLE_STYLE = {
  font: { bold: true, color: { rgb: 'FFFFFFFF' }, sz: 12 },
  fill: { patternType: 'solid', fgColor: { rgb: 'FF0F766E' } },
  alignment: { horizontal: 'left', vertical: 'center' },
  border: allBorders,
};

const NOTE_STYLE = {
  font: { italic: true, color: { rgb: 'FF334155' }, sz: 10 },
  alignment: { vertical: 'center', wrapText: true },
  border: allBorders,
};

/** Gắn style vào một ô Excel mà không làm mất dữ liệu hiện có. */
const setCellStyle = (worksheet, ref, style) => {
  const cell = worksheet[ref] || { t: 's', v: '' };
  cell.s = { ...(cell.s || {}), ...style };
  worksheet[ref] = cell;
};

/** Chuyển chỉ số cột dạng số sang ký hiệu cột Excel. */
const colLetter = (index) => {
  let s = '';
  let n = index;
  while (n >= 0) {
    s = String.fromCharCode(65 + (n % 26)) + s;
    n = Math.floor(n / 26) - 1;
  }
  return s;
};

/** Áp dụng cùng style cho toàn bộ ô trong một hàng. */
const applyRowStyle = (worksheet, rowIndex, style, numCols) => {
  for (let c = 0; c < numCols; c += 1) {
    setCellStyle(worksheet, `${colLetter(c)}${rowIndex}`, style);
  }
};

/** Tạo sheet ví dụ để người dùng tham khảo cấu trúc Product/Variant. */
const buildTemplateSheet = () => {
  const header = IMPORT_TEMPLATE_COLUMNS.map((c) => c.label);
  const aoa = [header];

  IMPORT_TEMPLATE_EXAMPLES.forEach((group, groupIdx) => {
    aoa.push([{ v: group.label, mergeAcross: IMPORT_TEMPLATE_COLUMNS.length - 1 }]);
    group.rows.forEach((rowData) => {
      const row = IMPORT_TEMPLATE_COLUMNS.map((col) => {
        const v = rowData[col.key];
        return v == null ? '' : v;
      });
      aoa.push(row);
    });
    if (groupIdx < IMPORT_TEMPLATE_EXAMPLES.length - 1) {
      aoa.push(new Array(IMPORT_TEMPLATE_COLUMNS.length).fill(''));
    }
  });

  aoa.push(new Array(IMPORT_TEMPLATE_COLUMNS.length).fill(''));
  aoa.push(new Array(IMPORT_TEMPLATE_COLUMNS.length).fill(''));
  aoa.push(new Array(IMPORT_TEMPLATE_COLUMNS.length).fill(''));
  aoa.push(new Array(IMPORT_TEMPLATE_COLUMNS.length).fill(''));

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);

  const numCols = IMPORT_TEMPLATE_COLUMNS.length;
  worksheet['!cols'] = IMPORT_TEMPLATE_COLUMNS.map(() => ({ wch: 22 }));

  const rowsRange = [];
  rowsRange.push({ hpt: 32 });

  IMPORT_TEMPLATE_EXAMPLES.forEach((group, groupIdx) => {
    rowsRange.push({ hpt: 24 });
    group.rows.forEach(() => rowsRange.push({ hpt: 22 }));
    if (groupIdx < IMPORT_TEMPLATE_EXAMPLES.length - 1) {
      rowsRange.push({ hpt: 14 });
    }
  });
  for (let i = 0; i < 4; i += 1) rowsRange.push({ hpt: 14 });
  worksheet['!rows'] = rowsRange;

  applyRowStyle(worksheet, 1, HEADER_STYLE, numCols);

  let rowIdx = 2;
  IMPORT_TEMPLATE_EXAMPLES.forEach((group, groupIdx) => {
    applyRowStyle(worksheet, rowIdx, SECTION_TITLE_STYLE, numCols);
    worksheet['!merges'] = worksheet['!merges'] || [];
    worksheet['!merges'].push({
      s: { r: rowIdx - 1, c: 0 },
      e: { r: rowIdx - 1, c: numCols - 1 },
    });
    rowIdx += 1;
    group.rows.forEach(() => {
      applyRowStyle(worksheet, rowIdx, EXAMPLE_STYLE, numCols);
      rowIdx += 1;
    });
    if (groupIdx < IMPORT_TEMPLATE_EXAMPLES.length - 1) rowIdx += 1;
  });

  return worksheet;
};

/** Tạo sheet trống dùng để nhập dữ liệu thực tế. */
const buildInputSheet = () => {
  const numCols = IMPORT_TEMPLATE_COLUMNS.length;
  const header = IMPORT_TEMPLATE_COLUMNS.map((c) => c.label);

  const aoa = [header];

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);

  worksheet['!cols'] = IMPORT_TEMPLATE_COLUMNS.map(() => ({ wch: 22 }));
  worksheet['!rows'] = [{ hpt: 32 }];

  applyRowStyle(worksheet, 1, HEADER_STYLE, numCols);

  worksheet['!autofilter'] = { ref: `A1:${colLetter(numCols - 1)}1` };
  worksheet['!freeze'] = { xSplit: 0, ySplit: 1 };

  return worksheet;
};

/** Tạo sheet hướng dẫn cách chuẩn bị dữ liệu import. */
const buildInstructionsSheet = () => {
  const numCols = IMPORT_TEMPLATE_COLUMNS.length;
  const aoa = [];

  aoa.push(['HƯỚNG DẪN IMPORT SẢN PHẨM TỪ EXCEL']);
  aoa.push([]);

  aoa.push(['Cấu trúc file có 4 sheet:']);
  aoa.push(['  1. "Hướng dẫn" - Sheet này.']);
  aoa.push(['  2. "Mô tả cột" - Bảng giải thích ý nghĩa từng cột, đánh dấu BẮT BUỘC / Tùy chọn.']);
  aoa.push(['  3. "Template" - Sheet tham khảo với 3 ví dụ mẫu sản phẩm đầy đủ các kiểu biến thể.']);
  aoa.push(['  4. "Nhập liệu" - Sheet bạn sẽ điền sản phẩm. Chỉ có 1 dòng header, các dòng dưới để trống.']);
  aoa.push([]);

  aoa.push(['1. Cách nhập dữ liệu']);
  aoa.push(['- Mở sheet "Nhập liệu" (sheet cuối cùng). Chỉ có 1 dòng header.']);
  aoa.push(['- Mỗi dòng bạn điền bên dưới tương ứng với 1 BIẾN THỂ (variant).']);
  aoa.push(['- Nếu một sản phẩm có nhiều biến thể (ví dụ: 3 màu × 2 size = 6 biến thể), hãy tạo 6 dòng']);
  aoa.push(['  với cùng MÃ SẢN PHẨM (SKU cha) nhưng khác SKU biến thể và Tên biến thể.']);
  aoa.push(['- Hệ thống sẽ tự động gộp các dòng có cùng SKU cha thành 1 sản phẩm với nhiều biến thể.']);
  aoa.push([]);

  aoa.push(['2. Các cột BẮT BUỘC (không được để trống)']);
  aoa.push(['- Mã sản phẩm (SKU) - productSku: Mã duy nhất, dùng để gộp các biến thể']);
  aoa.push(['- Tên sản phẩm - productName: Tên hiển thị chính']);
  aoa.push(['- Danh mục - categoryName: Tên danh mục đã có trong hệ thống (không phân biệt hoa/thường)']);
  aoa.push(['- SKU biến thể - variantSku: Mã duy nhất cho từng biến thể, không trùng nhau']);
  aoa.push([]);
  aoa.push(['3. Cột TÙY CHỌN (có thể để trống)']);
  aoa.push(['- Tên biến thể - variantName: Tên hiển thị biến thể (VD: Đỏ / M)']);
  aoa.push(['- Mô tả, Thương hiệu, Đơn vị, Barcode, Trọng lượng: Tùy chọn']);
  aoa.push(['- Trạng thái - status: DRAFT (mặc định), ACTIVE, INACTIVE']);
  aoa.push([]);

  aoa.push(['4. Cập nhật sản phẩm đã có']);
  aoa.push(['- Nếu Mã sản phẩm (SKU) đã tồn tại trong hệ thống, các dòng có cùng SKU sẽ CẬP NHẬT']);
  aoa.push(['  sản phẩm đó (thay vì tạo mới).']);
  aoa.push(['- Nếu SKU biến thể đã tồn tại thuộc sản phẩm khác, hệ thống sẽ báo lỗi trùng SKU biến thể.']);
  aoa.push([]);

  aoa.push(['5. Trạng thái (status) - Các giá trị hợp lệ']);
  aoa.push(['- DRAFT: Nháp (mặc định nếu bỏ trống)']);
  aoa.push(['- ACTIVE: Đang bán']);
  aoa.push(['- INACTIVE: Ngừng bán']);
  aoa.push([]);

  aoa.push(['6. Ví dụ mẫu (xem sheet "Template")']);
  IMPORT_TEMPLATE_EXAMPLES.forEach((group) => {
    aoa.push([`- ${group.label}: ${group.rows.length} dòng (${group.rows.length} biến thể)`]);
  });
  aoa.push([]);

  aoa.push(['7. Giới hạn']);
  aoa.push(['- Kích thước file tối đa: 20MB']);
  aoa.push(['- Định dạng hỗ trợ: .xlsx, .xls']);
  aoa.push(['- Nếu có bất kỳ lỗi nào, toàn bộ file sẽ được rollback (không lưu gì cả).']);

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);

  worksheet['!cols'] = [{ wch: 110 }];
  const titleRef = 'A1';
  worksheet[titleRef] = worksheet[titleRef] || { t: 's', v: '' };
  worksheet[titleRef].s = {
    font: { bold: true, color: { rgb: 'FFFFFFFF' }, sz: 14 },
    fill: { patternType: 'solid', fgColor: { rgb: 'FF1F4E78' } },
    alignment: { horizontal: 'center', vertical: 'center' },
    border: allBorders,
  };
  worksheet['!merges'] = [{ s: { r: 0, c: 0 }, e: { r: 0, c: numCols - 1 } }];

  const rowsHeight = [{ hpt: 32 }];
  for (let i = 1; i < aoa.length; i += 1) rowsHeight.push({ hpt: 22 });
  worksheet['!rows'] = rowsHeight;

  for (let r = 2; r <= aoa.length; r += 1) {
    const ref = `A${r}`;
    if (worksheet[ref]) {
      worksheet[ref].s = NOTE_STYLE;
    }
  }

  return worksheet;
};

/** Tạo sheet mô tả ý nghĩa và mức bắt buộc của từng cột. */
const buildColumnsSheet = () => {
  const aoa = [
    ['#', 'Tên cột (header)', 'Key', 'Bắt buộc?', 'Ví dụ', 'Mô tả'],
    [1, 'Mã sản phẩm (SKU)', 'productSku', 'BẮT BUỘC', 'SP-001', 'Mã sản phẩm - dùng gộp các biến thể. Trùng SKU = cập nhật sản phẩm có sẵn.'],
    [2, 'Tên sản phẩm', 'productName', 'BẮT BUỘC', 'Áo thun nam cổ tròn', 'Tên hiển thị chính của sản phẩm.'],
    [3, 'Danh mục', 'categoryName', 'BẮT BUỘC', 'Thời trang nam', 'Tên danh mục đã có trong hệ thống.'],
    [4, 'Mô tả', 'description', 'Tùy chọn', 'Áo thun cotton 100%', 'Mô tả chi tiết sản phẩm.'],
    [5, 'Thương hiệu', 'brand', 'Tùy chọn', 'OEM', 'Thương hiệu sản phẩm.'],
    [6, 'Đơn vị', 'unit', 'Tùy chọn', 'cái', 'Đơn vị tính (cái, hộp, kg...).'],
    [7, 'Trạng thái', 'status', 'Tùy chọn', 'DRAFT', 'DRAFT / ACTIVE / INACTIVE. Mặc định DRAFT.'],
    [8, 'SKU biến thể', 'variantSku', 'BẮT BUỘC', 'SP-001-RED-M', 'SKU riêng cho từng biến thể, không được trùng.'],
    [9, 'Tên biến thể', 'variantName', 'Tùy chọn', 'Đỏ / M', 'Tên hiển thị của biến thể.'],
    [10, 'Barcode', 'barcode', 'Tùy chọn', '8934673001234', 'Mã vạch của biến thể, không trùng.'],
    [11, 'Trọng lượng (g)', 'weightGrams', 'Tùy chọn', '250', 'Trọng lượng tính bằng gram.'],
  ];

  const worksheet = XLSX.utils.aoa_to_sheet(aoa);
  worksheet['!cols'] = [
    { wch: 5 },
    { wch: 22 },
    { wch: 18 },
    { wch: 12 },
    { wch: 22 },
    { wch: 50 },
  ];

  applyRowStyle(worksheet, 1, HEADER_STYLE, 6);

  for (let r = 2; r <= aoa.length; r += 1) {
    const required = aoa[r - 1][3];
    const style = required === 'BẮT BUỘC' ? REQUIRED_BADGE_STYLE : OPTIONAL_BADGE_STYLE;
    setCellStyle(worksheet, `D${r}`, style);
    setCellStyle(worksheet, `A${r}`, { ...NOTE_STYLE, alignment: { horizontal: 'center', vertical: 'center' } });
    setCellStyle(worksheet, `B${r}`, { ...NOTE_STYLE, font: { bold: true, color: { rgb: 'FF1F4E78' }, sz: 10 } });
    setCellStyle(worksheet, `C${r}`, { ...NOTE_STYLE, font: { italic: true, color: { rgb: 'FF64748B' }, sz: 10 } });
    setCellStyle(worksheet, `E${r}`, { ...NOTE_STYLE, font: { italic: true, color: { rgb: 'FF334155' }, sz: 10 } });
    setCellStyle(worksheet, `F${r}`, NOTE_STYLE);
  }

  return worksheet;
};

/** Sinh và tải xuống workbook mẫu import Product. */
export const downloadImportTemplate = () => {
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, buildInstructionsSheet(), 'Hướng dẫn');
  XLSX.utils.book_append_sheet(workbook, buildColumnsSheet(), 'Mô tả cột');
  XLSX.utils.book_append_sheet(workbook, buildTemplateSheet(), 'Template');
  XLSX.utils.book_append_sheet(workbook, buildInputSheet(), 'Nhập liệu');

  const now = new Date();
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(
    now.getDate(),
  ).padStart(2, '0')}`;
  XLSX.writeFile(workbook, `template-import-san-pham_${stamp}.xlsx`);
};
