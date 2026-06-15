import { Trash2 } from 'lucide-react';
import { formatVND } from '../../../../shared/utils/currencyUtils';

/**
 * Table of products in an inventory receipt with inline editing for
 * quantity and unit price.
 *
 * @param {{
 *   items: Array<{
 *     variantId: string,
 *     sku: string,
 *     productName: string,
 *     variantName: string,
 *     quantity: number,
 *     unitPrice: number,
 *     lineAmount: number
 *   }>,
 *   onQuantityChange: (index: number, value: string) => void,
 *   onPriceChange: (index: number, value: string) => void,
 *   onRemove: (index: number) => void
 * }} props
 */
const ReceiptProductTable = ({ items = [], onQuantityChange, onPriceChange, onRemove }) => {
  if (items.length === 0) {
    return (
      <div className="flex items-center justify-center rounded-lg border border-dashed border-gray-300 bg-gray-50 py-16 text-sm text-gray-500">
        Chưa có sản phẩm. Hãy thêm sản phẩm vào danh sách.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200">
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left font-medium text-gray-600">
              Tên sản phẩm
            </th>
            <th className="px-4 py-3 text-left font-medium text-gray-600">
              SKU
            </th>
            <th className="w-32 px-4 py-3 text-left font-medium text-gray-600">
              Số lượng
            </th>
            <th className="w-40 px-4 py-3 text-left font-medium text-gray-600">
              Đơn giá
            </th>
            <th className="w-36 px-4 py-3 text-right font-medium text-gray-600">
              Thành tiền
            </th>
            <th className="w-14 px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 bg-white">
          {items.map((item, index) => {
            const qtyInvalid = item.quantity !== '' && Number(item.quantity) <= 0;
            const priceInvalid = item.unitPrice !== '' && Number(item.unitPrice) < 0;
            const lineAmount = (Number(item.quantity) || 0) * (Number(item.unitPrice) || 0);

            return (
              <tr key={item.variantId ?? index} className="hover:bg-gray-50">
                {/* Product name + variant */}
                <td className="px-4 py-3">
                  <div className="font-medium text-gray-800">{item.productName}</div>
                  {item.variantName && (
                    <div className="text-xs text-gray-500">{item.variantName}</div>
                  )}
                </td>

                {/* SKU */}
                <td className="px-4 py-3 text-gray-600">{item.sku}</td>

                {/* Quantity (editable) */}
                <td className="px-4 py-3">
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={item.quantity}
                    onChange={(e) => onQuantityChange(index, e.target.value)}
                    className={`w-full rounded-md border px-2 py-1.5 text-sm outline-none transition focus:ring-2 focus:ring-blue-500 ${
                      qtyInvalid
                        ? 'border-red-400 bg-red-50 focus:ring-red-400'
                        : 'border-gray-300 bg-white'
                    }`}
                  />
                  {qtyInvalid && (
                    <p className="mt-1 text-xs text-red-600">Số lượng phải lớn hơn 0.</p>
                  )}
                </td>

                {/* Unit Price (editable) */}
                <td className="px-4 py-3">
                  <input
                    type="number"
                    min="0"
                    step="1000"
                    value={item.unitPrice}
                    onChange={(e) => onPriceChange(index, e.target.value)}
                    className={`w-full rounded-md border px-2 py-1.5 text-sm outline-none transition focus:ring-2 focus:ring-blue-500 ${
                      priceInvalid
                        ? 'border-red-400 bg-red-50 focus:ring-red-400'
                        : 'border-gray-300 bg-white'
                    }`}
                  />
                  {priceInvalid && (
                    <p className="mt-1 text-xs text-red-600">Đơn giá không hợp lệ.</p>
                  )}
                </td>

                {/* Line amount (computed, display only) */}
                <td className="px-4 py-3 text-right font-medium text-gray-800">
                  {formatVND(lineAmount)}
                </td>

                {/* Remove button */}
                <td className="px-4 py-3 text-center">
                  <button
                    type="button"
                    onClick={() => onRemove(index)}
                    className="rounded p-1.5 text-gray-400 transition hover:bg-red-50 hover:text-red-600"
                    aria-label="Xóa sản phẩm"
                  >
                    <Trash2 size={16} />
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default ReceiptProductTable;
