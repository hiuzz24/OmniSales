// Test script for Excel import
const XLSX = require('xlsx-js-style');

const data = [
  ['Mã sản phẩm (SKU cha)', 'Tên sản phẩm', 'Danh mục', 'Thương hiệu', 'Mô tả', 'Đơn vị', 'Trạng thái', 'SKU biến thể', 'Tên biến thể', 'Giá bán', 'Giá vốn', 'Barcode', 'Trọng lượng (g)', 'Tùy chọn (JSON)'],
  ['SP-IMPORT-001', 'Áo thun test import', 'Thời trang', 'TestBrand', 'Sản phẩm test import', 'cái', 'ACTIVE', 'SP-IMPORT-001-RED-M', 'Đỏ / M', 150000, 80000, '8900000000001', 200, '{"color":"red","size":"M"}'],
  ['SP-IMPORT-001', 'Áo thun test import', 'Thời trang', 'TestBrand', 'Sản phẩm test import', 'cái', 'ACTIVE', 'SP-IMPORT-001-BLUE-L', 'Xanh dương / L', 150000, 80000, '8900000000002', 220, '{"color":"blue","size":"L"}'],
  ['SP-IMPORT-002', 'Quần jean test import', 'Thời trang', 'TestBrand', '', 'cái', 'DRAFT', 'SP-IMPORT-002-32', 'Size 32', 350000, 180000, '', 400, '{"size":"32"}'],
];

const ws = XLSX.utils.aoa_to_sheet(data);
const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, ws, 'Products');
XLSX.writeFile(wb, 'D:/FULearning/đồ án/OmniSales/test-import.xlsx');
console.log('Test Excel file created');
