import { formatVND } from '../../../../shared/utils/currencyUtils';

/**
 * Displays the total import value for an inventory receipt.
 *
 * @param {{ totalAmount: number }} props
 */
const ReceiptSummary = ({ totalAmount }) => {
  return (
    <div className="flex items-center justify-between rounded-lg border border-blue-200 bg-blue-50 px-5 py-4">
      <span className="text-sm font-medium text-blue-700">Tổng giá trị nhập kho</span>
      <span className="text-lg font-semibold text-blue-900">
        {formatVND(totalAmount ?? 0)}
      </span>
    </div>
  );
};

export default ReceiptSummary;
