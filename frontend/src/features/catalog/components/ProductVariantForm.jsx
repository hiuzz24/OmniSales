import { useState, useRef } from 'react';
import { Plus, X, ImageIcon, Upload, Loader2, RefreshCcw } from 'lucide-react';
import { uploadImageToCloudinary } from '../../../api/cloudinaryApi';
import styles from './ProductVariantForm.module.css';

const emptyVariant = () => ({
  sku: '',
  barcode: '',
  name: '',
  price: '',
  costPrice: '',
  optionValues: { Size: '', 'Màu': '' },
  images: [],
});

const ProductVariantForm = ({ variants = [], onAdd, onRemove, onChange, errors = {}, globalError, hasOrders = false, channels = [], selectedChannels = [] }) => {
  const [editingImageIndex, setEditingImageIndex] = useState(null);
  const [urlValue, setUrlValue] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef(null);

  const handleFieldChange = (index, field, value) => {
    const updated = [...variants];
    updated[index] = { ...updated[index], [field]: value };
    onChange(updated);
  };

  const handleOptionChange = (index, optionKey, value) => {
    const updated = [...variants];
    updated[index] = {
      ...updated[index],
      optionValues: { ...updated[index].optionValues, [optionKey]: value },
    };
    onChange(updated);
  };

  const handleSaveImage = () => {
    if (!urlValue.trim() || editingImageIndex === null) return;
    const updated = [...variants];
    updated[editingImageIndex] = {
      ...updated[editingImageIndex],
      images: [{ url: urlValue.trim() }]
    };
    onChange(updated);
    setEditingImageIndex(null);
    setUrlValue('');
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file || editingImageIndex === null) return;

    try {
      setIsUploading(true);
      const secureUrl = await uploadImageToCloudinary(file);
      
      const updated = [...variants];
      updated[editingImageIndex] = {
        ...updated[editingImageIndex],
        images: [{ url: secureUrl }]
      };
      onChange(updated);
      setEditingImageIndex(null);
      setUrlValue('');
    } catch (error) {
      alert(error.message || 'Lỗi khi tải ảnh lên');
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const renderSuggestedPrices = (price) => {
    const numPrice = Number(price);
    if (!numPrice || isNaN(numPrice) || numPrice <= 0) return '-';

    const selectedList = channels.filter(c => selectedChannels.includes(c.id));
    if (selectedList.length === 0) return '-';

    return (
      <div style={{ fontSize: '12px', color: '#059669', display: 'flex', flexDirection: 'column', gap: '2px' }}>
        {selectedList.map(channel => {
          const rate = (channel.commissionRate || 0) / 100;
          if (rate >= 1) return <div key={channel.id}>{channel.platform}: N/A</div>;
          const suggested = Math.round(numPrice / (1 - rate));
          return <div key={channel.id}>{channel.platform}: {suggested.toLocaleString('vi-VN')}đ</div>;
        })}
      </div>
    );
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div>
          <div className={styles.cardTitle}>Biến thể sản phẩm</div>
          <div className={styles.cardSubtitle}>Thêm các phiên bản khác nhau (màu, size...)</div>
        </div>
        <button type="button" className={styles.addBtn} onClick={() => onAdd(emptyVariant())}>
          <Plus className={styles.addBtnIcon} />
          Thêm biến thể
        </button>
      </div>

      {variants.length === 0 ? (
        <div className={styles.emptyState}>
          Chưa có biến thể nào. Nhấn "Thêm biến thể" để tạo.
          {globalError && <div style={{ color: '#ef4444', marginTop: '8px', fontSize: '14px', fontWeight: '500' }}>{globalError}</div>}
        </div>
      ) : (
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.imgCell}></th>
                <th>Size</th>
                <th>Màu</th>
                <th>SKU</th>
                <th>Barcode</th>
                <th>Giá</th>
                <th style={{ width: '130px' }}>Giá đề xuất</th>
                <th>Giá vốn</th>
                <th className={styles.removeCell}></th>
              </tr>
            </thead>
            <tbody>
              {variants.map((variant, index) => {
                const variantErrors = errors[index] || {};
                return (
                  <tr key={index} style={{ opacity: variant.isActive === false ? 0.6 : 1 }}>
                    <td className={styles.imgCell}>
                      <div
                        className={styles.imgPlaceholder}
                        onClick={() => {
                          if (variant.isActive !== false) {
                            setEditingImageIndex(index);
                            setUrlValue(variant.images?.[0]?.url || '');
                          }
                        }}
                        title={variant.isActive === false ? '' : 'Thêm ảnh biến thể'}
                      >
                        {variant.images?.[0]?.url ? (
                          <img src={variant.images[0].url} alt={`Variant ${index}`} className={styles.imgThumbnail} />
                        ) : (
                          <ImageIcon className={styles.imgIcon} />
                        )}
                      </div>
                    </td>
                    <td>
                      <input
                        type="text"
                        className={styles.variantInput}
                        placeholder="S"
                        value={variant.optionValues?.Size || ''}
                        onChange={(e) => handleOptionChange(index, 'Size', e.target.value)}
                        disabled={variant.isActive === false}
                      />
                    </td>
                    <td>
                      <input
                        type="text"
                        className={styles.variantInput}
                        placeholder="Trắng"
                        value={variant.optionValues?.['Màu'] || ''}
                        onChange={(e) => handleOptionChange(index, 'Màu', e.target.value)}
                        disabled={variant.isActive === false}
                      />
                    </td>
                    <td>
                      <input
                        type="text"
                        className={`${styles.variantInput} ${variantErrors.sku ? styles.inputError : ''}`}
                        placeholder="ATN-001-S-W"
                        value={variant.sku}
                        onChange={(e) => handleFieldChange(index, 'sku', e.target.value)}
                        disabled={hasOrders || variant.isActive === false}
                      />
                      {variant.isActive === false && (
                        <div className={styles.inactiveBadge}>Đã vô hiệu hóa</div>
                      )}
                      {variantErrors.sku && <div className={styles.errorText}>{variantErrors.sku}</div>}
                    </td>
                    <td>
                      <input
                        type="text"
                        className={`${styles.variantInput} ${variantErrors.barcode ? styles.inputError : ''}`}
                        placeholder="Mã vạch"
                        value={variant.barcode || ''}
                        onChange={(e) => handleFieldChange(index, 'barcode', e.target.value)}
                        disabled={variant.isActive === false}
                      />
                      {variantErrors.barcode && <div className={styles.errorText}>{variantErrors.barcode}</div>}
                    </td>
                    <td>
                      <input
                        type="number"
                        className={`${styles.variantInput} ${variantErrors.price ? styles.inputError : ''}`}
                        placeholder="0"
                        value={variant.price}
                        onChange={(e) => handleFieldChange(index, 'price', e.target.value)}
                        min="0"
                        disabled={variant.isActive === false}
                      />
                      {variantErrors.price && <div className={styles.errorText}>{variantErrors.price}</div>}
                    </td>
                    <td>
                      {renderSuggestedPrices(variant.price)}
                    </td>
                    <td>
                      <input
                        type="number"
                        className={styles.variantInput}
                        placeholder="0"
                        value={variant.costPrice}
                        onChange={(e) => handleFieldChange(index, 'costPrice', e.target.value)}
                        min="0"
                        disabled={variant.isActive === false}
                      />
                    </td>
                    <td className={styles.removeCell}>
                      {variant.isActive === false ? (
                        <button
                          type="button"
                          className={styles.reactivateBtn}
                          onClick={() => handleFieldChange(index, 'isActive', true)}
                          title="Kích hoạt lại"
                        >
                          <RefreshCcw size={16} />
                        </button>
                      ) : (
                        <button
                          type="button"
                          className={styles.removeRowBtn}
                          onClick={() => onRemove(index)}
                          title="Xóa biến thể"
                        >
                          <X size={16} />
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {editingImageIndex !== null && (
        <div className={styles.urlInputOverlay} onClick={() => !isUploading && setEditingImageIndex(null)}>
          <div className={styles.urlInputModal} onClick={(e) => e.stopPropagation()}>
            <h3>Thêm ảnh cho biến thể</h3>
            
            <div className={styles.uploadSection}>
              <input
                type="file"
                accept="image/*"
                hidden
                ref={fileInputRef}
                onChange={handleFileUpload}
              />
              <button
                type="button"
                className={styles.uploadBtnModal}
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploading}
              >
                {isUploading ? (
                  <Loader2 className={`${styles.btnIcon} ${styles.spin}`} />
                ) : (
                  <Upload className={styles.btnIcon} />
                )}
                Tải ảnh lên từ thiết bị
              </button>
            </div>

            <div className={styles.divider}>
              <span>Hoặc nhập URL</span>
            </div>

            <input
              type="url"
              className={styles.urlInputField}
              placeholder="https://example.com/image.jpg"
              value={urlValue}
              onChange={(e) => setUrlValue(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSaveImage()}
              disabled={isUploading}
            />
            <div className={styles.urlInputActions}>
              <button
                type="button"
                className={styles.urlCancelBtn}
                onClick={() => {
                  setEditingImageIndex(null);
                  setUrlValue('');
                }}
                disabled={isUploading}
              >
                Hủy
              </button>
              <button
                type="button"
                className={styles.urlAddBtn}
                onClick={handleSaveImage}
                disabled={!urlValue.trim() || isUploading}
              >
                Lưu ảnh
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProductVariantForm;
