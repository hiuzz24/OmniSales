/**
 * Helper utilities for product Excel import E2E tests
 */

const XLSX = require('xlsx');
const path = require('path');
const fs = require('fs');

/**
 * Column header mapping that matches the Vietnamese labels the backend
 * expects on the "Nhập liệu" sheet.
 *
 * NOTE: Backend requires a `price` column even though the frontend
 * template does not include it. The frontend `Trọng lượng (g)` column is
 * exposed here so the helper can produce both valid-import and
 * backend-validation-error scenarios.
 */
const IMPORT_HEADERS = [
  'Mã sản phẩm (SKU)',
  'Tên sản phẩm',
  'Danh mục',
  'Mô tả',
  'Thương hiệu',
  'Đơn vị',
  'Trạng thái',
  'SKU biến thể',
  'Tên biến thể',
  'Barcode',
  'Trọng lượng (g)',
];

const BACKEND_HEADERS = [
  'Mã sản phẩm (SKU)',
  'Tên sản phẩm',
  'Danh mục',
  'Mô tả',
  'Thương hiệu',
  'Đơn vị',
  'Trạng thái',
  'SKU biến thể',
  'Tên biến thể',
  'Giá bán',
  'Barcode',
  'Trọng lượng (g)',
];

/**
 * Build a minimal Excel workbook containing one "Nhập liệu" sheet.
 * @param {Array<Object>} rows - Rows keyed by field name. Supported fields:
 *   productSku, productName, categoryName, description, brand, unit, status,
 *   variantSku, variantName, price, barcode, weightGrams.
 * @param {Object} [options]
 * @param {boolean} [options.useBackendHeaders=false] - Use header set that
 *   includes the `price` column required by the backend.
 * @returns {Buffer} xlsx file buffer
 */
function buildImportWorkbook(rows, options = {}) {
  const headers = options.useBackendHeaders ? BACKEND_HEADERS : IMPORT_HEADERS;
  const aoa = [headers];
  for (const r of rows) {
    aoa.push([
      r.productSku || '',
      r.productName || '',
      r.categoryName || '',
      r.description || '',
      r.brand || '',
      r.unit || '',
      r.status || '',
      r.variantSku || '',
      r.variantName || '',
      options.useBackendHeaders ? (r.price || '') : '',
      r.barcode || '',
      r.weightGrams || '',
    ]);
  }
  const worksheet = XLSX.utils.aoa_to_sheet(aoa);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Nhập liệu');
  return XLSX.write(workbook, { type: 'buffer', bookType: 'xlsx' });
}

/**
 * Write the import workbook to a temporary file and return the file path.
 * @param {Array<Object>} rows
 * @param {Object} [options] forwarded to {@link buildImportWorkbook}
 * @param {string} [options.prefix='product-import']
 * @returns {string} absolute path to the temp .xlsx file
 */
function writeImportFile(rows, options = {}) {
  const { prefix = 'product-import' } = options;
  const buffer = buildImportWorkbook(rows, options);
  const filename = `${prefix}-${Date.now()}-${Math.floor(Math.random() * 99999)}.xlsx`;
  const filepath = path.join(require('os').tmpdir(), filename);
  fs.writeFileSync(filepath, buffer);
  return filepath;
}

/**
 * Build a workbook that is intentionally invalid to exercise the
 * frontend's client-side validation. The current implementation only
 * looks for "Nhập liệu" / "Template" / "Import" sheet names, so we use
 * an unrecognized sheet name with no required headers.
 */
function buildInvalidWorkbook() {
  const worksheet = XLSX.utils.aoa_to_sheet([
    ['foo', 'bar', 'baz'],
    ['1', '2', '3'],
  ]);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'RandomSheet');
  return XLSX.write(workbook, { type: 'buffer', bookType: 'xlsx' });
}

/**
 * Build a non-Excel file (a small text file renamed as .xlsx) so the
 * backend's "only .xlsx/.xls accepted" guard has something to reject.
 */
function buildNonExcelBuffer() {
  return Buffer.from('this is not actually an excel file', 'utf-8');
}

/**
 * Cleanup helper - delete a temp file (best effort).
 */
function cleanupImportFile(filepath) {
  try {
    if (filepath && fs.existsSync(filepath)) {
      fs.unlinkSync(filepath);
    }
  } catch (_) {
    // ignore
  }
}

module.exports = {
  IMPORT_HEADERS,
  BACKEND_HEADERS,
  buildImportWorkbook,
  writeImportFile,
  buildInvalidWorkbook,
  buildNonExcelBuffer,
  cleanupImportFile,
};