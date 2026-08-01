import { useState, useRef } from 'react';
import { useFieldArray, useFormContext, useWatch } from 'react-hook-form';
import { Plus, X, ImageIcon, Upload, Loader2, RefreshCcw } from 'lucide-react';
import { uploadImageToCloudinary } from '../../../api/cloudinaryApi';
import styles from './ProductVariantForm.module.css';

const isHttpUrl = (value) => {
  if (!value?.trim()) return false;
  try {
    const url = new URL(value.trim());
    return ['http:', 'https:'].includes(url.protocol);
  } catch {
    return false;
  }
};

const emptyVariant = () => ({
  sku: '',
  barcode: '',
  name: '',
  price: '0',
  costPrice: '0',
  optionValues: { Size: '', 'Màu': '' },
  images: [],
});

const ProductVariantForm = ({
  hasOrders = false,
  channels = [],
  selectedChannels = [],
  disablePrice = false,
  disableCostPrice = false,
}) => {
  const { control, setValue, formState: { errors } } = useFormContext();
  const { fields, append, remove } = useFieldArray({ control, name: 'variants', keyName: 'formId' });
  const variants = useWatch({ control, name: 'variants', defaultValue: [] });
  const globalError = errors.variants?.message;
  const [editingImageIndex, setEditingImageIndex] = useState(null);
  const [urlValue, setUrlValue] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [failedImageUrls, setFailedImageUrls] = useState({});
  const fileInputRef = useRef(null);

  const handleFieldChange = (index, field, value) => {
    setValue(`variants.${index}.${field}`, value, { shouldDirty: true, shouldValidate: true });
  };

  const handleOptionChange = (index, optionKey, value) => {
    setValue(`variants.${index}.optionValues.${optionKey}`, value, { shouldDirty: true, shouldValidate: true });
  };

  const handleSaveImage = () => {
    if (!urlValue.trim() || editingImageIndex === null) return;
    if (!isHttpUrl(urlValue)) {
      alert('URL ảnh biến thể phải bắt đầu bằng http:// hoặc https://');
      return;
    }
    setValue(`variants.${editingImageIndex}.images`, [{ url: urlValue.trim() }], {
      shouldDirty: true,
      shouldValidate: true,
    });
    setEditingImageIndex(null);
    setUrlValue('');
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file || editingImageIndex === null) return;

    try {
      setIsUploading(true);
      const secureUrl = await uploadImageToCloudinary(file);
      if (!isHttpUrl(secureUrl)) {
        throw new Error('Cloudinary không trả về URL ảnh hợp lệ');
      }
      setValue(`variants.${editingImageIndex}.images`, [{ url: secureUrl }], {
        shouldDirty: true,
        shouldValidate: true,
      });
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

    const selectedList = channels.filter((c, i) => selectedChannels.includes(c.id || c._id || (c.platform + i)));
    if (selectedList.length === 0) return '-';

    return (
      <div style={{ fontSize: '12px', color: '#059669', display: 'flex', flexDirection: 'column', gap: '2px' }}>
        {selectedList.map((channel, i) => {
          const channelId = channel.id || channel._id || (channel.platform + i);
          const rate = (channel.commissionRate || 0) / 100;
          if (rate >= 1) return <div key={channelId}>{channel.platform}: N/A</div>;
          const suggested = Math.round(numPrice / (1 - rate));
          return <div key={channelId}>{channel.platform}: {suggested.toLocaleString('vi-VN')}đ</div>;
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
        <button type="button" className={styles.addBtn} onClick={() => append(emptyVariant())}>
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
                <th className={styles.imgCell}>Ảnh *</th>
                <th>Size *</th>
                <th>Màu *</th>
                <th>SKU *</th>
                <th>Barcode</th>
                <th>Giá *</th>
                <th style={{ width: '130px' }}>Giá đề xuất</th>
                <th>Giá vốn *</th>
                <th className={styles.removeCell}></th>
              </tr>
            </thead>
            <tbody>
              {variants.map((variant, index) => {
                const variantErrors = errors.variants?.[index] || {};
                const imageUrl = variant.images?.[0]?.url;
                const canDisplayImage = isHttpUrl(imageUrl) && !failedImageUrls[imageUrl];
                return (
                  <tr key={fields[index]?.formId || index} style={{ opacity: variant.isActive === false ? 0.6 : 1 }}>
                    <td className={styles.imgCell}>
                      <div
                        className={styles.imgPlaceholder}
                        onClick={() => {
                          if (variant.isActive !== false) {
                            setEditingImageIndex(index);
                            setUrlValue(variant.images?.[0]?.url || '');
                          }
                        }}
                        title={variant.isActive === false
                          ? ''
                          : imageUrl && !canDisplayImage
                            ? 'Ảnh biến thể không tải được. Nhấn để thay ảnh.'
                            : 'Thêm ảnh biến thể'}
                      >
                        {canDisplayImage ? (
                          <img
                            src={imageUrl}
                            alt={`Variant ${index + 1}`}
                            className={styles.imgThumbnail}
                            onError={() => setFailedImageUrls((previous) => ({ ...previous, [imageUrl]: true }))}
                          />
                        ) : (
                          <ImageIcon className={styles.imgIcon} />
                        )}
                      </div>
                      {variantErrors.images && <div className={styles.errorText}>{variantErrors.images.message}</div>}
                    </td>
                    <td>
                      <input
                        type="text"
                        className={`${styles.variantInput} ${variantErrors.optionValues?.Size ? styles.inputError : ''}`}
                        placeholder="S"
                        value={variant.optionValues?.Size || ''}
                        onChange={(e) => handleOptionChange(index, 'Size', e.target.value)}
                        disabled={variant.isActive === false}
                      />
                      {variantErrors.optionValues?.Size && <div className={styles.errorText}>{variantErrors.optionValues.Size.message}</div>}
                    </td>
                    <td>
                      <input
                        type="text"
                        className={`${styles.variantInput} ${variantErrors.optionValues?.['Màu'] ? styles.inputError : ''}`}
                        placeholder="Trắng"
                        value={variant.optionValues?.['Màu'] || ''}
                        onChange={(e) => handleOptionChange(index, 'Màu', e.target.value)}
                        disabled={variant.isActive === false}
                      />
                      {variantErrors.optionValues?.['Màu'] && <div className={styles.errorText}>{variantErrors.optionValues['Màu'].message}</div>}
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
                      {variantErrors.sku && <div className={styles.errorText}>{variantErrors.sku.message}</div>}
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
                      {variantErrors.barcode && <div className={styles.errorText}>{variantErrors.barcode.message}</div>}
                    </td>
                    <td>
                      <input
                        type="number"
                        className={`${styles.variantInput} ${variantErrors.price ? styles.inputError : ''}`}
                        placeholder="0"
                        value={disablePrice ? '0' : variant.price}
                        onChange={(e) => handleFieldChange(index, 'price', disablePrice ? '0' : e.target.value)}
                        min="0"
                        disabled={disablePrice || variant.isActive === false}
                      />
                      {variantErrors.price && <div className={styles.errorText}>{variantErrors.price.message}</div>}
                    </td>
                    <td>
                      {renderSuggestedPrices(disablePrice ? 0 : variant.price)}
                    </td>
                    <td>
                      <input
                        type="number"
                        className={`${styles.variantInput} ${variantErrors.costPrice ? styles.inputError : ''}`}
                        placeholder="0"
                        value={disableCostPrice ? (variant.costPrice ?? '0') : variant.costPrice}
                        onChange={(e) => handleFieldChange(index, 'costPrice', disableCostPrice ? (variant.costPrice ?? '0') : e.target.value)}
                        min="0"
                        disabled={disableCostPrice || variant.isActive === false}
                      />
                      {variantErrors.costPrice && <div className={styles.errorText}>{variantErrors.costPrice.message}</div>}
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
                          onClick={() => remove(index)}
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
