/**
 * ReceiptInfoPanel — right-side panel for the inventory receipt create form.
 *
 * All form state is managed by the parent via React Hook Form.
 * This component is purely presentational.
 *
 * @param {{
 *   register:     import('react-hook-form').UseFormRegister<any>,
 *   errors:       import('react-hook-form').FieldErrors,
 *   warehouses:   Array<{ id: string, name: string }>,
 *   suppliers:    Array<{ id: string, name: string }>,
 *   onSubmit:     () => void,
 *   onCancel:     () => void,
 *   isSubmitting: boolean,
 * }} props
 */
const ReceiptInfoPanel = ({
  register,
  errors,
  warehouses = [],
  suppliers = [],
  onSubmit,
  onCancel,
  isSubmitting = false,
}) => {
  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="flex flex-col gap-5 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-gray-800">Thông tin phiếu nhập</h2>

      {/* ── Kho nhập (required) ───────────────────────────── */}
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">
          Kho nhập <span className="text-red-500">*</span>
        </label>
        <select
          {...register('warehouseId')}
          className={`w-full rounded-md border px-3 py-2 text-sm outline-none transition focus:ring-2 focus:ring-blue-500 ${
            errors.warehouseId
              ? 'border-red-400 bg-red-50 focus:ring-red-400'
              : 'border-gray-300 bg-white'
          }`}
        >
          <option value="">-- Chọn kho nhập --</option>
          {warehouses.map((wh) => (
            <option key={wh.id} value={wh.id}>
              {wh.name}
            </option>
          ))}
        </select>
        {errors.warehouseId && (
          <p className="text-xs text-red-600">{errors.warehouseId.message}</p>
        )}
      </div>

      {/* ── Nhà cung cấp (optional) ───────────────────────── */}
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Nhà cung cấp</label>
        <select
          {...register('supplierId')}
          className="w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm outline-none transition focus:ring-2 focus:ring-blue-500"
        >
          <option value="">-- Không có --</option>
          {suppliers.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </div>

      {/* ── Số hóa đơn ───────────────────────────────────── */}
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Số hóa đơn</label>
        <input
          type="text"
          maxLength={100}
          placeholder="Nhập số hóa đơn (nếu có)"
          {...register('invoiceNumber')}
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none transition placeholder:text-gray-400 focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* ── Ngày nhập (required) ──────────────────────────── */}
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">
          Ngày nhập <span className="text-red-500">*</span>
        </label>
        <input
          type="date"
          max={today}
          {...register('receivedAt')}
          className={`w-full rounded-md border px-3 py-2 text-sm outline-none transition focus:ring-2 focus:ring-blue-500 ${
            errors.receivedAt
              ? 'border-red-400 bg-red-50 focus:ring-red-400'
              : 'border-gray-300 bg-white'
          }`}
        />
        {errors.receivedAt && (
          <p className="text-xs text-red-600">{errors.receivedAt.message}</p>
        )}
      </div>

      {/* ── Ghi chú ───────────────────────────────────────── */}
      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium text-gray-700">Ghi chú</label>
        <textarea
          rows={3}
          placeholder="Ghi chú thêm (nếu có)"
          {...register('notes')}
          className="w-full resize-none rounded-md border border-gray-300 px-3 py-2 text-sm outline-none transition placeholder:text-gray-400 focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* ── Lưu ý (hardcoded) ─────────────────────────────── */}
      <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-4 text-sm text-yellow-800">
        <p className="mb-2 font-medium">⚠️ Lưu ý:</p>
        <ul className="space-y-1 pl-1">
          <li>• Kiểm tra kỹ số lượng và đơn giá trước khi tạo phiếu.</li>
          <li>• Tồn kho sẽ tự động cập nhật sau khi phiếu được tạo.</li>
          <li>• Có thể import Excel để nhập nhanh nhiều sản phẩm cùng lúc.</li>
        </ul>
      </div>

      {/* ── Action buttons ────────────────────────────────── */}
      <div className="flex flex-col gap-2 pt-1 sm:flex-row">
        <button
          type="button"
          onClick={onSubmit}
          disabled={isSubmitting}
          className="flex-1 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting ? 'Đang tạo...' : 'Tạo phiếu nhập'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-1"
        >
          Hủy
        </button>
      </div>
    </div>
  );
};

export default ReceiptInfoPanel;
