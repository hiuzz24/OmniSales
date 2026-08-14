import { Loader2, Upload } from 'lucide-react';
import styles from './PlatformConfigSection.module.css';

const TIKTOK_FREE_TEXT_ATTRIBUTE_IDS = new Set(['101489', '101490']);

/** Kiểm tra chuỗi có phải URL HTTP(S) hợp lệ hay không. */
const isHttpUrl = (value) => {
  if (!value?.trim()) return false;
  try {
    return ['http:', 'https:'].includes(new URL(value.trim()).protocol);
  } catch {
    return false;
  }
};

/** Render đúng loại input cho một attribute của TikTok hoặc Lazada. */
const PlatformAttributeField = ({
  channel,
  attribute,
  config,
  updateConfig,
  uploadSizeChartImage,
  sizeChartUploading,
  hasVariants,
  productVariants,
  productSku,
}) => {
  const attributeKey = channel.platform === 'TIKTOK'
    ? attribute.id || attribute.name
    : attribute.name || attribute.id;
  const storedValue = config.attributes?.[attributeKey];
  const value = typeof storedValue === 'object' && storedValue !== null
    ? storedValue.valueId || storedValue.valueName || ''
    : storedValue ?? '';
  const normalizedName = String(attribute.name || '').trim().toLowerCase().replace(/[\s-]+/g, '_');
  const lazadaSizeChart = channel.platform === 'LAZADA'
    && ['size_chart', 'size_chart_image'].includes(normalizedName);

  if (lazadaSizeChart) {
    const channelId = channel.channelId || channel.id;
    return <div className={`${styles.field} ${styles.fullWidth}`}>
      <span className={styles.fieldLabel}>{attribute.label || attribute.name}{attribute.required ? <span className={styles.requiredMark}> *</span> : null}</span>
      <input className={styles.input} type="url" placeholder="https://..." value={value}
        onChange={(event) => updateConfig(channel, { attributes: { ...config.attributes, [attributeKey]: event.target.value } })} />
      {Boolean(value) && !isHttpUrl(String(value))
        ? <span className={styles.helperText}>URL ảnh phải bắt đầu bằng http:// hoặc https://.</span>
        : null}
      <label className={styles.fileUploadRow}>
        <input className={styles.fileInput} type="file" accept="image/*"
          onChange={(event) => uploadSizeChartImage(channel, event, attributeKey)}
          disabled={sizeChartUploading[channelId]} />
        {sizeChartUploading[channelId] ? <Loader2 className={styles.spin} size={16} /> : <Upload size={16} />}
        <span>{sizeChartUploading[channelId] ? 'Đang tải...' : 'Chọn ảnh bảng size từ máy'}</span>
      </label>
    </div>;
  }

  if (attribute.saleProperty) {
    if (channel.platform !== 'LAZADA') return null;
    const skus = hasVariants
      ? (productVariants || []).filter((variant) => variant.isActive !== false && variant.sku?.trim())
          .map((variant) => variant.sku.trim())
      : (productSku?.trim() ? [productSku.trim()] : []);
    const uniqueSkus = [...new Set(skus)];
    const mappings = config.variantAttributeValueMappings?.[attributeKey] || {};
    const options = attribute.options || [];
    return <div className={`${styles.field} ${styles.fullWidth}`}>
      <span className={styles.fieldLabel}>{attribute.label || attribute.name}{attribute.required ? <span className={styles.requiredMark}> *</span> : null}</span>
      {uniqueSkus.length === 0
        ? <span className={styles.helperText}>Hãy nhập SKU sản phẩm trước khi map thuộc tính Lazada.</span>
        : <div className={styles.skuMappingContainer}>{uniqueSkus.map((sku) =>
          <label className={styles.skuMappingRow} key={`${attributeKey}-${sku}`}>
            <span className={styles.skuLabel}>SKU: {sku}</span>
            <select className={styles.input} value={mappings[sku] || ''} onChange={(event) => updateConfig(channel, {
              variantAttributeValueMappings: {
                ...config.variantAttributeValueMappings,
                [attributeKey]: { ...mappings, [sku]: event.target.value },
              },
            })}>
              <option value="">Chọn {attribute.label || attribute.name} Lazada</option>
              {options.map((option) => <option key={option.id || option.platformValue || option.name}
                value={option.platformValue || option.id || option.name}>{option.name || option.platformValue}</option>)}
            </select>
          </label>)}</div>}
    </div>;
  }

  const options = channel.platform === 'TIKTOK'
    && TIKTOK_FREE_TEXT_ATTRIBUTE_IDS.has(String(attribute.id)) ? [] : attribute.options || [];
  const selectedOption = options.find((option) => String(option.id) === String(value)
    || String(option.name) === String(value) || String(option.platformValue) === String(value));
  const selectedValue = selectedOption
    ? String(channel.platform === 'LAZADA'
        ? selectedOption.platformValue || selectedOption.id || selectedOption.name
        : selectedOption.id || selectedOption.name)
    : '';
  return <label className={styles.field}>
    <span className={styles.fieldLabel}>{attribute.label || attribute.name}{attribute.required ? <span className={styles.requiredMark}> *</span> : null}</span>
    {options.length > 0
      ? <select className={styles.input} value={selectedValue} onChange={(event) => {
          const option = options.find((item) => String(channel.platform === 'LAZADA'
            ? item.platformValue || item.id || item.name : item.id || item.name) === event.target.value);
          updateConfig(channel, { attributes: {
            ...config.attributes,
            [attributeKey]: channel.platform === 'TIKTOK'
              ? { attributeId: attribute.id, attributeName: attribute.name,
                  valueId: option?.id || event.target.value, valueName: option?.name || event.target.value }
              : option?.platformValue || option?.name || event.target.value,
          } });
        }}>
          <option value="">Chọn giá trị</option>
          {options.map((option) => <option key={option.id || option.platformValue || option.name}
            value={channel.platform === 'LAZADA'
              ? option.platformValue || option.id || option.name : option.id || option.name}>
            {option.name || option.platformValue}
          </option>)}
        </select>
      : <input className={styles.input} value={value} onChange={(event) => updateConfig(channel, { attributes: {
          ...config.attributes,
          [attributeKey]: channel.platform === 'TIKTOK'
            ? { attributeId: attribute.id, attributeName: attribute.name, valueName: event.target.value }
            : event.target.value,
        } })} />}
  </label>;
};

export default PlatformAttributeField;
