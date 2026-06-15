import { useState, useEffect, useCallback } from 'react';
import { X, Search, Loader2 } from 'lucide-react';
import { useDebounce } from '../../../../shared/hooks/useDebounce';
import axiosClient from '../../../../api/axiosClient';

/**
 * AddProductModal
 *
 * Props:
 *   isOpen           {boolean}   — whether the modal is visible
 *   onClose          {function}  — called when the modal is dismissed
 *   onConfirm        {function}  — called with array of selected items when user confirms
 *   existingVariantIds {string[]} — variantIds already present in the receipt table
 */
export default function AddProductModal({ isOpen, onClose, onConfirm, existingVariantIds = [] }) {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState([]);
  const [selected, setSelected] = useState({}); // { [variantId]: resultItem }
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const debouncedKeyword = useDebounce(keyword, 300);

  // Reset state whenever the modal opens/closes
  useEffect(() => {
    if (!isOpen) {
      setKeyword('');
      setResults([]);
      setSelected({});
      setError(null);
      setLoading(false);
    }
  }, [isOpen]);

  // Search variants whenever the debounced keyword changes
  useEffect(() => {
    if (!debouncedKeyword.trim()) {
      setResults([]);
      setError(null);
      return;
    }

    let cancelled = false;

    const fetchVariants = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await axiosClient.get('/catalog/variants', {
          params: { search: debouncedKeyword.trim(), page: 0, size: 20 },
        });
        if (!cancelled) {
          // Support both paginated { content: [] } and plain array responses
          const items = response.data?.content ?? response.data ?? [];
          setResults(Array.isArray(items) ? items : []);
        }
      } catch {
        if (!cancelled) {
          setError('Không thể tải danh sách sản phẩm. Vui lòng thử lại.');
          setResults([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    fetchVariants();
    return () => { cancelled = true; };
  }, [debouncedKeyword]);

  const toggleSelect = useCallback((item) => {
    if (existingVariantIds.includes(item.variantId)) return; // already in table — not selectable
    setSelected((prev) => {
      const next = { ...prev };
      if (next[item.variantId]) {
        delete next[item.variantId];
      } else {
        next[item.variantId] = item;
      }
      return next;
    });
  }, [existingVariantIds]);

  const handleConfirm = () => {
    const selectedItems = Object.values(selected).map((item) => ({
      variantId: item.variantId,
      sku: item.sku,
      productName: item.productName,
      variantName: item.variantName,
      quantity: 1,
      unitPrice: 0,
    }));
    onConfirm(selectedItems);
  };

  const selectedCount = Object.keys(selected).length;

  if (!isOpen) return null;

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      {/* Modal container */}
      <div className="w-full max-w-lg rounded-lg bg-white shadow-xl flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">Thêm sản phẩm</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            aria-label="Đóng"
          >
            <X size={20} />
          </button>
        </div>

        {/* Search input */}
        <div className="px-6 py-3 border-b border-gray-100">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="Tìm theo tên hoặc SKU..."
              className="w-full pl-9 pr-4 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              autoFocus
            />
          </div>
        </div>

        {/* Results list */}
        <div className="flex-1 overflow-y-auto px-6 py-2 min-h-0">
          {/* Loading */}
          {loading && (
            <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500">
              <Loader2 size={16} className="animate-spin" />
              Đang tìm kiếm...
            </div>
          )}

          {/* Error */}
          {!loading && error && (
            <p className="py-6 text-center text-sm text-red-500">{error}</p>
          )}

          {/* Empty keyword */}
          {!loading && !error && !debouncedKeyword.trim() && (
            <p className="py-6 text-center text-sm text-gray-400">
              Nhập từ khóa để tìm kiếm sản phẩm.
            </p>
          )}

          {/* No results */}
          {!loading && !error && debouncedKeyword.trim() && results.length === 0 && (
            <p className="py-6 text-center text-sm text-gray-400">
              Không tìm thấy sản phẩm nào phù hợp.
            </p>
          )}

          {/* Results */}
          {!loading && !error && results.length > 0 && (
            <ul className="divide-y divide-gray-100">
              {results.map((item) => {
                const isExisting = existingVariantIds.includes(item.variantId);
                const isSelected = Boolean(selected[item.variantId]);

                return (
                  <li
                    key={item.variantId}
                    onClick={() => toggleSelect(item)}
                    className={[
                      'flex items-center gap-3 px-2 py-3 rounded-md cursor-pointer select-none transition-colors',
                      isExisting
                        ? 'bg-gray-100 cursor-not-allowed opacity-70'
                        : isSelected
                        ? 'bg-blue-50 hover:bg-blue-100'
                        : 'hover:bg-gray-50',
                    ].join(' ')}
                  >
                    {/* Checkbox */}
                    <input
                      type="checkbox"
                      checked={isSelected}
                      disabled={isExisting}
                      onChange={() => toggleSelect(item)}
                      onClick={(e) => e.stopPropagation()}
                      className="w-4 h-4 accent-blue-600 shrink-0 cursor-pointer disabled:cursor-not-allowed"
                    />

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-sm text-gray-900 truncate">
                          {item.productName}
                        </span>
                        {item.variantName && (
                          <span className="text-sm text-gray-500 truncate">
                            — {item.variantName}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="inline-block text-xs font-mono bg-gray-200 text-gray-600 px-1.5 py-0.5 rounded">
                          {item.sku}
                        </span>
                        {isExisting && (
                          <span className="inline-block text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-medium">
                            Đã có
                          </span>
                        )}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-200">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 transition-colors"
          >
            Hủy
          </button>
          <button
            onClick={handleConfirm}
            disabled={selectedCount === 0}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Thêm ({selectedCount})
          </button>
        </div>
      </div>
    </div>
  );
}
