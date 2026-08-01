import { useEffect, useMemo, useState } from 'react';
import { Check, Gift, Loader2, Search, X } from 'lucide-react';
import inventoryApi from '../../../../api/inventoryApi';
import warehouseService from '../../services/warehouseService';
import styles from './OrderGiftProductPickerModal.module.css';

const unwrap = (response) => response?.data?.data ?? response?.data ?? response;

const normalizeProduct = (item) => ({
  productVariantId: item.variantId ?? item.id,
  variantIds: item.variantIds ?? [],
  sku: item.sku ?? '-',
  productName: item.productName ?? 'Sản phẩm',
  variantName: item.variantName ?? item.name ?? '',
  availableQuantity: Number(item.availableQuantity ?? 0),
  platforms: item.platforms ?? (item.platform ? [item.platform] : []),
  quantity: 1,
});

export default function OrderGiftProductPickerModal({ order, value = [], onConfirm, onClose }) {
  const [products, setProducts] = useState([]);
  const [selected, setSelected] = useState(() => Object.fromEntries(
    value.map((item) => [item.productVariantId, { ...item }]),
  ));
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const warehouseResponse = await warehouseService.getMaster();
        const warehouse = unwrap(warehouseResponse);
        if (!warehouse?.id) throw new Error('Không tìm thấy Kho mặc định đa sàn');
        const variantsResponse = await inventoryApi.getAvailableVariantsByWarehouse(warehouse.id);
        const variants = unwrap(variantsResponse) ?? [];
        if (active) setProducts(variants.map(normalizeProduct));
      } catch (loadError) {
        if (active) setError(loadError?.response?.data?.message || loadError?.message || 'Không thể tải sản phẩm trong kho');
      } finally {
        if (active) setLoading(false);
      }
    };
    load();
    return () => { active = false; };
  }, []);

  const filteredProducts = useMemo(() => {
    const search = keyword.trim().toLowerCase();
    if (!search) return products;
    return products.filter((item) => [item.productName, item.variantName, item.sku]
      .some((text) => String(text ?? '').toLowerCase().includes(search)));
  }, [keyword, products]);

  const selectedItems = useMemo(() => Object.values(selected), [selected]);
  const hasInvalidQuantity = selectedItems.some((item) => Number(item.quantity) <= 0);
  const hasOverAvailable = selectedItems.some(
    (item) => Number(item.quantity) > Number(item.availableQuantity ?? 0),
  );

  const toggle = (product) => {
    setSelected((current) => {
      const next = { ...current };
      if (next[product.productVariantId]) delete next[product.productVariantId];
      else next[product.productVariantId] = product;
      return next;
    });
  };

  const updateQuantity = (variantId, rawValue) => {
    const quantity = Math.max(1, Number.parseInt(rawValue, 10) || 1);
    setSelected((current) => ({
      ...current,
      [variantId]: { ...current[variantId], quantity },
    }));
  };

  const confirm = () => {
    if (hasInvalidQuantity) return;
    onConfirm(selectedItems.map((item) => ({
      ...item,
      quantity: Number(item.quantity),
    })));
  };

  return (
    <div className={styles.backdrop} onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="gift-picker-title">
        <header className={styles.header}>
          <div className={styles.heading}>
            <span className={styles.icon}><Gift size={18} /></span>
            <div>
              <h2 id="gift-picker-title">Thêm quà tặng</h2>
              <p>Đơn {order?.externalOrderId || '-'}</p>
            </div>
          </div>
          <button type="button" className={styles.iconButton} onClick={onClose} aria-label="Đóng" title="Đóng">
            <X size={18} />
          </button>
        </header>

        <div className={styles.searchBox}>
          <Search size={16} />
          <input
            autoFocus
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="Tìm theo tên sản phẩm hoặc SKU..."
          />
        </div>

        <div className={styles.content}>
          {loading && <div className={styles.state}><Loader2 className={styles.spin} size={18} /> Đang tải sản phẩm...</div>}
          {!loading && error && <div className={styles.error}>{error}</div>}
          {!loading && !error && filteredProducts.length === 0 && (
            <div className={styles.state}>Không có sản phẩm phù hợp trong kho.</div>
          )}
          {!loading && !error && filteredProducts.map((product) => {
            const selectedItem = selected[product.productVariantId];
            return (
              <div
                key={product.productVariantId}
                className={`${styles.productRow} ${selectedItem ? styles.selectedRow : ''}`}
                onClick={() => toggle(product)}
              >
                <span className={styles.checkbox} aria-hidden="true">
                  {selectedItem && <Check size={13} />}
                </span>
                <div className={styles.productInfo}>
                  <strong>{product.productName}</strong>
                  <span>{product.variantName || 'Biến thể mặc định'}</span>
                  <div className={styles.meta}>
                    <code>{product.sku}</code>
                    {product.platforms.length > 0 && <span>{product.platforms.join(', ')}</span>}
                  </div>
                </div>
                <div className={styles.stock}>
                  <span>Có thể xuất</span>
                  <strong>{product.availableQuantity.toLocaleString('vi-VN')}</strong>
                </div>
                {selectedItem && (
                  <label className={styles.quantity} onClick={(event) => event.stopPropagation()}>
                    <span>Số lượng</span>
                    <input
                      type="number"
                      min="1"
                      value={selectedItem.quantity}
                      onChange={(event) => updateQuantity(product.productVariantId, event.target.value)}
                    />
                  </label>
                )}
              </div>
            );
          })}
        </div>

        <footer className={styles.footer}>
          <div>
            <strong>{selectedItems.length} sản phẩm quà tặng</strong>
            {hasOverAvailable && <span className={styles.warning}>Số lượng chọn đang vượt tồn có thể xuất.</span>}
          </div>
          <div className={styles.footerActions}>
            <button type="button" className={styles.cancelButton} onClick={onClose}>Hủy</button>
            <button type="button" className={styles.confirmButton} disabled={loading || hasInvalidQuantity} onClick={confirm}>
              Xác nhận
            </button>
          </div>
        </footer>
      </section>
    </div>
  );
}
