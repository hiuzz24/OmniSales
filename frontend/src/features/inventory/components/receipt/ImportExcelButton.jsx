import { useRef } from 'react';
import { Upload } from 'lucide-react';
import { toast } from 'react-toastify';
import * as XLSX from 'xlsx';

/**
 * Button that triggers a hidden file input, parses an Excel file using the
 * xlsx library, validates each data row, and calls `onImport` with the
 * array of valid items.
 *
 * Expected Excel template (row 1 = header, data from row 2):
 *   Column A: SKU
 *   Column B: Số lượng (quantity)
 *   Column C: Đơn giá (unit price)
 *
 * @param {{
 *   onImport: (items: Array<{ sku: string, quantity: number, unitPrice: number }>) => void
 * }} props
 */
const ImportExcelButton = ({ onImport }) => {
  const inputRef = useRef(null);

  const handleButtonClick = () => {
    inputRef.current?.click();
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];

    // Reset input so the same file can be re-selected later
    if (inputRef.current) {
      inputRef.current.value = '';
    }

    if (!file) return;

    const reader = new FileReader();

    reader.onload = (event) => {
      try {
        const data = new Uint8Array(event.target.result);
        const workbook = XLSX.read(data, { type: 'array' });

        // Use the first sheet
        const sheetName = workbook.SheetNames[0];
        const sheet = workbook.Sheets[sheetName];

        // Convert to array-of-arrays; header row is included
        const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: undefined });

        // Skip header row (index 0); process from row index 1 onward
        const dataRows = rows.slice(1);

        const validItems = [];
        const errors = [];

        dataRows.forEach((row, idx) => {
          const rowNumber = idx + 2; // 1-based, accounting for the header row

          const rawSku = row[0];
          const rawQty = row[1];
          const rawPrice = row[2];

          // Skip completely empty rows
          if (
            (rawSku === undefined || rawSku === null || rawSku === '') &&
            (rawQty === undefined || rawQty === null || rawQty === '') &&
            (rawPrice === undefined || rawPrice === null || rawPrice === '')
          ) {
            return;
          }

          let hasRowError = false;

          // Validate SKU
          const sku = rawSku !== undefined && rawSku !== null ? String(rawSku).trim() : '';
          if (!sku) {
            errors.push(`Dòng ${rowNumber}: SKU không hợp lệ.`);
            hasRowError = true;
          }

          // Validate quantity
          const quantity = Number(rawQty);
          if (rawQty === undefined || rawQty === null || rawQty === '' || isNaN(quantity) || quantity <= 0) {
            errors.push(`Dòng ${rowNumber}: Số lượng phải lớn hơn 0.`);
            hasRowError = true;
          }

          // Validate unit price
          const unitPrice = Number(rawPrice);
          if (rawPrice === undefined || rawPrice === null || rawPrice === '' || isNaN(unitPrice) || unitPrice < 0) {
            errors.push(`Dòng ${rowNumber}: Đơn giá không hợp lệ.`);
            hasRowError = true;
          }

          if (!hasRowError) {
            validItems.push({ sku, quantity, unitPrice });
          }
        });

        const validCount = validItems.length;
        const errorCount = errors.length;

        if (validCount === 0) {
          toast.error('Không có dòng hợp lệ nào trong file.');
          return;
        }

        if (errorCount > 0) {
          toast.warn(`Có ${errorCount} dòng lỗi. Chỉ nhập ${validCount} dòng hợp lệ.`);
        } else {
          toast.success(`Đã nhập ${validCount} sản phẩm từ Excel.`);
        }

        onImport(validItems);
      } catch {
        toast.error('Không thể đọc dữ liệu từ file Excel.');
      }
    };

    reader.onerror = () => {
      toast.error('Không thể đọc dữ liệu từ file Excel.');
    };

    reader.readAsArrayBuffer(file);
  };

  return (
    <>
      <input
        ref={inputRef}
        type="file"
        accept=".xlsx,.xls"
        className="hidden"
        onChange={handleFileChange}
        aria-hidden="true"
        tabIndex={-1}
      />
      <button
        type="button"
        onClick={handleButtonClick}
        className="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 hover:border-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <Upload size={16} />
        Import Excel
      </button>
    </>
  );
};

export default ImportExcelButton;
