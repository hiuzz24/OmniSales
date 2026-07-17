import { useEffect, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';
import { ChevronDown, ChevronRight, ChevronLeft, RefreshCw, Loader2, Upload } from 'lucide-react';
import { toast } from 'react-toastify';
import platformLookupApi from '../../../api/platformLookupApi';
import { uploadImageToCloudinary } from '../../../api/cloudinaryApi';
import styles from './PlatformConfigSection.module.css';

const SUPPORTED_BINDINGS = ['Size', 'M\u00e0u'];
const SYSTEM_ATTRIBUTES = new Set([
  'sellersku', 'seller_sku', 'price', 'supply_price', 'quantity',
  'package_weight', 'package_width', 'package_height', 'package_length',
  'brand', 'brand_id',
]);
const OPTIONAL_LAZADA_SPECIFICATIONS = new Set([
  'clothing_material', 'pattern', 'neckline', 'clothing_style', 'details',
  'sleeves_type', 'sleeve_type',
]);
const TIKTOK_LISTING_ATTRIBUTE_IDS = new Set(['100149', '101489', '101490']);
const extractData = (response) => response?.data?.data || response?.data || response || [];

const emptyConfig = (channel) => ({
  channelId: channel.channelId || channel.id,
  categoryId: '',
  categoryName: '',
  categoryVersion: channel.platform === 'TIKTOK' ? 'v1' : null,
  brandId: '',
  brandName: '',
  sizeChartImageUrl: '',
  attributes: {},
  variantAttributeBindings: {},
});

const PlatformConfigSection = ({ channels = [], onSave, productId = null }) => {
  const { control, setValue } = useFormContext();
  const configs = useWatch({ control, name: 'channelConfigs', defaultValue: {} });
  const [productName, productDescription, productImages] = useWatch({ control, name: ['name', 'description', 'images'] });
  const platformChannels = channels.filter((channel) => ['LAZADA', 'TIKTOK'].includes(channel.platform));
  const [openChannelId, setOpenChannelId] = useState(null);
  const [categoryState, setCategoryState] = useState({});
  const [suggestionState, setSuggestionState] = useState({});
  const [manualBrowser, setManualBrowser] = useState({});
  const [attributeState, setAttributeState] = useState({});
  const [brandState, setBrandState] = useState({});
  const [brandPages, setBrandPages] = useState({});
  const [brandPageTokens, setBrandPageTokens] = useState({});
  const [brandPageHistory, setBrandPageHistory] = useState({});
  const [searchValues, setSearchValues] = useState({});
  const [brandSearchValues, setBrandSearchValues] = useState({});
  const [loading, setLoading] = useState({});
  const [sizeChartUploading, setSizeChartUploading] = useState({});

  const configFor = (channel) => configs[channel.channelId || channel.id] || emptyConfig(channel);
  const setLoadingFor = (channelId, value) => setLoading((previous) => ({ ...previous, [channelId]: value }));
  const primaryImageUrl = productImages?.find((image) => image.isPrimary)?.url || productImages?.[0]?.url;
  const inputHash = () => productId
    ? `saved-product:${productId}`
    : `${productName || ''}|${productDescription || ''}|${primaryImageUrl || ''}`;
  const isSystemManaged = (attribute) => SYSTEM_ATTRIBUTES.has(String(attribute.name || '').toLowerCase());
  const isOptionalLazadaSpecification = (attribute) => OPTIONAL_LAZADA_SPECIFICATIONS.has(
    String(attribute.name || '').trim().toLowerCase().replace(/[\s-]+/g, '_'));
  const isTikTokListingAttribute = (attribute) => TIKTOK_LISTING_ATTRIBUTE_IDS.has(String(attribute.id));

  // MANUAL_CATEGORY_BROWSER_FALLBACK: only called after the user chooses manual browsing.
  const loadCategories = async (channel, parentId = null, keyword = null) => {
    const channelId = channel.channelId || channel.id;
    setLoadingFor(channelId, true);
    try {
      const response = await platformLookupApi.getCategories(channel.platform, {
        channelId,
        parentId: parentId || undefined,
        keyword: keyword || undefined,
        categoryVersion: configFor(channel).categoryVersion || undefined,
      });
      setCategoryState((previous) => ({
        ...previous,
        [channelId]: { parentId, items: extractData(response), history: previous[channelId]?.history || [] },
      }));
    } catch (error) {
      toast.error(error.response?.data?.message || `Cannot load ${channel.platform} categories`);
    } finally {
      setLoadingFor(channelId, false);
    }
  };

  const loadBrands = async (channel, keyword = null, categoryIdOverride = null, page = 0, pageToken = null) => {
    if (!['LAZADA', 'TIKTOK'].includes(channel.platform)) return;
    const channelId = channel.channelId || channel.id;
    const categoryId = categoryIdOverride || configFor(channel).categoryId;
    if (channel.platform === 'TIKTOK' && !categoryId) return;
    try {
      const response = await platformLookupApi.getBrands(channel.platform, {
        channelId,
        categoryId: categoryId || undefined,
        categoryVersion: configFor(channel).categoryVersion || undefined,
        keyword: keyword || undefined,
        page,
        size: 50,
        pageToken: pageToken || undefined,
      });
      const result = extractData(response);
      setBrandState((previous) => ({ ...previous, [channelId]: result.items || [] }));
      setBrandPages((previous) => ({ ...previous, [channelId]: result }));
      if (channel.platform === 'TIKTOK') {
        setBrandPageTokens((previous) => ({ ...previous, [channelId]: pageToken }));
        if (page === 0 && !pageToken) {
          setBrandPageHistory((previous) => ({ ...previous, [channelId]: [] }));
        }
      }
    } catch (error) {
      const platformLabel = channel.platform === 'TIKTOK' ? 'TikTok' : 'Lazada';
      toast.error(error.response?.data?.message || `Không thể tải thương hiệu ${platformLabel}`);
    }
  };

  const loadNextBrandPage = async (channel) => {
    const channelId = channel.channelId || channel.id;
    const current = brandPages[channelId];
    if (!current?.nextPageToken) return;
    const currentToken = brandPageTokens[channelId] || null;
    setBrandPageHistory((previous) => ({
      ...previous,
      [channelId]: [...(previous[channelId] || []), currentToken],
    }));
    await loadBrands(channel, brandSearchValues[channelId] || null, null, (current.page || 0) + 1, current.nextPageToken);
  };

  const loadPreviousBrandPage = async (channel) => {
    const channelId = channel.channelId || channel.id;
    const current = brandPages[channelId];
    const history = brandPageHistory[channelId] || [];
    if (!current?.page || history.length === 0) return;
    const previousToken = history.at(-1);
    setBrandPageHistory((previous) => ({ ...previous, [channelId]: history.slice(0, -1) }));
    await loadBrands(channel, brandSearchValues[channelId] || null, null, current.page - 1, previousToken);
  };

  const suggestCategories = async (channel) => {
    const channelId = channel.channelId || channel.id;
    if (!productId && (!productName?.trim() || !primaryImageUrl)) {
      toast.error('Hãy nhập tên sản phẩm và thêm ảnh chính trước khi gợi ý danh mục');
      return;
    }
    const hash = inputHash();
    setLoadingFor(channelId, true);
    try {
      const response = await platformLookupApi.getCategorySuggestions(channel.platform, productId
        ? { channelId, productId, categoryVersion: configFor(channel).categoryVersion || undefined }
        : { channelId, title: productName, description: productDescription || '', primaryImageUrl,
          categoryVersion: configFor(channel).categoryVersion || undefined });
      const suggestions = extractData(response) || [];
      setSuggestionState((previous) => ({ ...previous, [channelId]: { hash, items: suggestions } }));
    } catch (error) {
      toast.error(error.response?.data?.message || `Không thể gợi ý danh mục ${channel.platform}`);
    } finally {
      setLoadingFor(channelId, false);
    }
  };

  const loadAttributes = async (channel, config) => {
    const channelId = channel.channelId || channel.id;
    if (!config.categoryId) return;
    setLoadingFor(channelId, true);
    try {
      const response = await platformLookupApi.getAttributes(channel.platform, config.categoryId, {
        channelId,
        categoryVersion: config.categoryVersion || undefined,
      });
      setAttributeState((previous) => ({ ...previous, [channelId]: extractData(response) }));
    } catch (error) {
      toast.error(error.response?.data?.message || `Cannot load ${channel.platform} attributes`);
    } finally {
      setLoadingFor(channelId, false);
    }
  };

  useEffect(() => {
    if (!openChannelId) return undefined;
    const channel = platformChannels.find((item) => (item.channelId || item.id) === openChannelId);
    if (!channel || !['LAZADA', 'TIKTOK'].includes(channel.platform)) return undefined;
    if (!configFor(channel).categoryId) return undefined;
    const timer = setTimeout(() => loadBrands(channel, brandSearchValues[openChannelId] || null, null, 0), 300);
    return () => clearTimeout(timer);
  }, [openChannelId, brandSearchValues]);

  useEffect(() => {
    setSuggestionState({});
  }, [productName, productDescription, productImages?.[0]?.url]);

  const updateConfig = (channel, patch) => {
    const channelId = channel.channelId || channel.id;
    setValue(`channelConfigs.${channelId}`, { ...configFor(channel), ...patch }, { shouldDirty: true });
  };

  const uploadSizeChartImage = async (channel, event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const channelId = channel.channelId || channel.id;
    try {
      setSizeChartUploading((previous) => ({ ...previous, [channelId]: true }));
      const imageUrl = await uploadImageToCloudinary(file);
      updateConfig(channel, { sizeChartImageUrl: imageUrl });
      toast.success('Đã tải ảnh bảng size lên');
    } catch (error) {
      toast.error(error.message || 'Không thể tải ảnh bảng size lên');
    } finally {
      setSizeChartUploading((previous) => ({ ...previous, [channelId]: false }));
      event.target.value = '';
    }
  };

  const toggle = async (channel) => {
    const channelId = channel.channelId || channel.id;
    const willOpen = openChannelId !== channelId;
    setOpenChannelId(willOpen ? channelId : null);
    if (!willOpen) return;
    const config = configFor(channel);
    if (config.categoryId && !attributeState[channelId]) await loadAttributes(channel, config);
  };

  const selectCategory = async (channel, category, source = 'MANUAL_BROWSER') => {
    const current = configFor(channel);
    if (!category.leaf) {
      const channelId = channel.channelId || channel.id;
      setCategoryState((previous) => ({
        ...previous,
        [channelId]: {
          ...(previous[channelId] || {}),
          history: [...(previous[channelId]?.history || []), previous[channelId]?.parentId || null],
        },
      }));
      await loadCategories(channel, category.id);
      return;
    }
    const next = { ...current, categoryId: category.id, categoryName: category.name, categorySource: source,
      categoryConfirmed: source === 'PLATFORM_SUGGESTION', brandId: '', brandName: '', attributes: {}, variantAttributeBindings: {} };
    updateConfig(channel, next);
    await loadAttributes(channel, next);
    await loadBrands(channel, null, category.id);
  };

  const renderAttribute = (channel, attribute) => {
    const config = configFor(channel);
    const attributeKey = channel.platform === 'TIKTOK' ? attribute.id : attribute.name;
    const storedValue = config.attributes?.[attributeKey];
    const value = typeof storedValue === 'object' && storedValue !== null
      ? storedValue.valueId || storedValue.valueName || ''
      : storedValue ?? '';
    if (attribute.saleProperty) {
      const binding = config.variantAttributeBindings?.[attributeKey] || '';
      return <label className={styles.field} key={attributeKey}>
        <span>{attribute.label || attribute.name}{attribute.required ? ' *' : ''}</span>
        <select className={styles.input} value={binding} onChange={(event) => updateConfig(channel, {
          variantAttributeBindings: { ...config.variantAttributeBindings, [attributeKey]: event.target.value },
        })}>
          <option value="">Chưa liên kết</option>
          {SUPPORTED_BINDINGS.map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      </label>;
    }
    const options = attribute.options || [];
    const selectedOption = options.find((option) => String(option.id) === String(value)
      || String(option.name) === String(value));
    const selectedValue = selectedOption ? String(selectedOption.id || selectedOption.name) : '';
    return <label className={styles.field} key={attributeKey}>
      <span>{attribute.label || attribute.name}{attribute.required ? ' *' : ''}</span>
      {options.length > 0 ? <select className={styles.input} value={selectedValue} onChange={(event) => {
        const option = options.find((item) => String(item.id || item.name) === event.target.value);
        updateConfig(channel, {
          attributes: {
            ...config.attributes,
            [attributeKey]: channel.platform === 'TIKTOK'
              ? {
                  attributeId: attribute.id,
                  attributeName: attribute.name,
                  valueId: option?.id || event.target.value,
                  valueName: option?.name || event.target.value,
                }
              : option?.name || event.target.value,
          },
        });
      }}>
        <option value="">Chọn giá trị</option>
        {options.map((option) => <option key={option.id || option.name} value={option.id || option.name}>{option.name}</option>)}
      </select> : <input className={styles.input} value={value} onChange={(event) => updateConfig(channel, {
        attributes: {
          ...config.attributes,
          [attributeKey]: channel.platform === 'TIKTOK'
            ? { attributeId: attribute.id, attributeName: attribute.name, valueName: event.target.value }
            : event.target.value,
        },
      })} />}
    </label>;
  };

  if (platformChannels.length === 0) return null;
  return <section className={styles.section}>
    <div className={styles.header}><h3>Cấu hình theo sàn</h3><p>Chỉ cần cấu hình một lần cho mỗi sàn để chuẩn bị đồng bộ dữ liệu.</p></div>
    {platformChannels.map((channel) => {
      const channelId = channel.channelId || channel.id;
      const config = configFor(channel);
      const category = categoryState[channelId] || { items: [], history: [] };
      const suggestions = suggestionState[channelId] || { items: [], hash: null };
      const suggestionsAreStale = suggestions.hash && suggestions.hash !== inputHash();
      const isManualBrowserOpen = Boolean(manualBrowser[channelId]);
      const productAttributes = (attributeState[channelId] || []).filter((attribute) =>
        !attribute.saleProperty && !isSystemManaged(attribute));
      const requiredAttributes = productAttributes.filter((attribute) => attribute.required);
      const optionalAttributes = productAttributes.filter((attribute) =>
        !attribute.required && (channel.platform === 'TIKTOK'
          ? isTikTokListingAttribute(attribute)
          : isOptionalLazadaSpecification(attribute)));
      const loadedBrands = brandState[channelId] || [];
      const brandPage = brandPages[channelId] || { page: 0, size: 50, totalElements: 0, totalPages: 0 };
      const selectedBrand = config.brandId && !loadedBrands.some((brand) => String(brand.id) === String(config.brandId))
        ? [{ id: config.brandId, name: config.brandName || `Brand #${config.brandId}` }]
        : [];
      const availableBrands = [...selectedBrand, ...loadedBrands];
      const platformLabel = channel.platform === 'TIKTOK' ? 'TikTok' : 'Lazada';
      return <div className={styles.panel} key={channelId}>
        <button className={styles.panelHeader} type="button" onClick={() => toggle(channel)}>
          <span>{openChannelId === channelId ? <ChevronDown size={18} color="#6b7280" /> : <ChevronRight size={18} color="#6b7280" />}</span>
          <span className={styles.panelTitle}>{channel.channelName || channel.displayName || channel.platform}</span>
          <span className={config.categoryId ? styles.statusReady : styles.statusPending}>{config.categoryId ? config.categoryName : 'Cấu hình sau'}</span>
        </button>
        {openChannelId === channelId && <div className={styles.panelBody}>
          
          <div className={styles.categorySection}>
            <div className={styles.categorySectionTitle}>
              <span className={styles.sectionIcon}>📁</span> Danh mục sản phẩm *
            </div>
            <div className={styles.actions}>
              <button type="button" className={styles.saveButton} onClick={() => suggestCategories(channel)}
                disabled={(!productId && (!productName?.trim() || !primaryImageUrl)) || loading[channelId]}>
                Gợi ý danh mục
              </button>
              <button type="button" className={styles.backButton} onClick={() => {
                setManualBrowser((previous) => ({ ...previous, [channelId]: true }));
                if (!categoryState[channelId]) loadCategories(channel);
              }}>
                Chọn danh mục khác
              </button>
            </div>
            {!productId && (!productName?.trim() || !primaryImageUrl) ? <p className={styles.helperText}>Nhập tên sản phẩm và ảnh chính trước khi gợi ý danh mục.</p> : null}
            {loading[channelId] && <span className={styles.helperText}>Đang tải dữ liệu...</span>}
            {suggestionsAreStale && <p className={styles.helperText}>Thông tin sản phẩm đã thay đổi. Hãy gợi ý lại hoặc chọn danh mục thủ công.</p>}
            {!suggestionsAreStale && suggestions.items.length > 0 && <div className={styles.categoryList}>
              {suggestions.items.map((item) => <button type="button" key={item.categoryId}
                className={item.categoryId === config.categoryId ? styles.categorySelected : styles.categoryButton}
                disabled={!item.selectable}
                title={item.disabledReason || item.categoryName}
                onClick={() => selectCategory(channel, { id: item.categoryId, name: item.categoryName, leaf: true }, 'PLATFORM_SUGGESTION')}>
                <span>{item.categoryName}{!item.selectable && item.disabledReason ? ` - ${item.disabledReason}` : ''}</span>
              </button>)}
            </div>}
            {isManualBrowserOpen && <div className={styles.manualBrowser}>
              <div className={styles.actions}>
                <input className={styles.search} placeholder="Tìm kiếm danh mục..." value={searchValues[channelId] || ''}
                  onChange={(event) => setSearchValues((previous) => ({ ...previous, [channelId]: event.target.value }))} />
                <button type="button" className={styles.iconButton} title="Làm mới" onClick={async () => {
                  await platformLookupApi.clearCache(channel.platform, channelId);
                  await loadCategories(channel);
                  if (config.categoryId) await loadBrands(channel, brandSearchValues[channelId] || null, null, 0);
                }}><RefreshCw size={16} /></button>
              </div>
              {category.history.length > 0 && <button className={styles.backButton} style={{ marginBottom: '16px' }} type="button" onClick={() => loadCategories(channel, category.history.at(-1))}>
                <ChevronLeft size={16} /> Quay lại danh mục trước
              </button>}
              <div className={styles.categoryList}>
                {category.items.map((item) => <button type="button" key={item.id} className={item.id === config.categoryId ? styles.categorySelected : styles.categoryButton} onClick={() => selectCategory(channel, item)}>
                  <span>{item.name}</span>
                  {!item.leaf && <ChevronRight size={16} color="#9ca3af" />}
                </button>)}
                {!loading[channelId] && category.items.length === 0 && <span className={styles.helperText}>Không tìm thấy danh mục.</span>}
              </div>
            </div>}
          </div>

          {config.categoryId && requiredAttributes.length > 0 && <div className={styles.attributes}>
            <h4><span className={styles.sectionIcon}>✨</span> Thuộc tính bắt buộc</h4>
            {requiredAttributes.map((attribute) => renderAttribute(channel, attribute))}
          </div>}

          {config.categoryId && optionalAttributes.length > 0 && <div className={styles.attributes}>
            <h4><span className={styles.sectionIcon}>⚙️</span> {channel.platform === 'TIKTOK' ? 'Thuộc tính bổ sung TikTok' : 'Thông số hiển thị trên Lazada'}</h4>
            {optionalAttributes.map((attribute) => renderAttribute(channel, attribute))}
          </div>}

          {['LAZADA', 'TIKTOK'].includes(channel.platform) && config.categoryId && <div className={styles.brandSection}>
            <div className={styles.categorySectionTitle}>
              <span className={styles.sectionIcon}>🏷️</span> Thương hiệu {platformLabel} *
            </div>
            <div className={styles.brandRow}>
              <div className={styles.field}>
                <span>Tìm kiếm</span>
                <input className={styles.input} placeholder="Nhập tên thương hiệu..." value={brandSearchValues[channelId] || ''}
                  onChange={(event) => setBrandSearchValues((previous) => ({ ...previous, [channelId]: event.target.value }))} />
              </div>
              <div className={styles.field}>
                <span>Chọn thương hiệu từ danh sách</span>
                <select className={styles.input} value={config.brandId || ''} onChange={(event) => {
                  const brand = availableBrands.find((item) => String(item.id) === String(event.target.value));
                  updateConfig(channel, { brandId: event.target.value, brandName: brand?.name || '' });
                }}>
                  <option value="">-- Chọn thương hiệu {platformLabel} --</option>
                  {availableBrands.map((brand) => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
                </select>
                <div className={styles.pagination}>
                  <button type="button" className={styles.backButton} disabled={brandPage.page <= 0}
                    onClick={() => channel.platform === 'TIKTOK'
                      ? loadPreviousBrandPage(channel)
                      : loadBrands(channel, brandSearchValues[channelId] || null, null, brandPage.page - 1)}>Trước</button>
                  <span>Trang {(brandPage.page || 0) + 1}</span>
                  <button type="button" className={styles.backButton}
                    disabled={channel.platform === 'TIKTOK'
                      ? !brandPage.nextPageToken
                      : !brandPage.hasNext}
                    onClick={() => channel.platform === 'TIKTOK'
                      ? loadNextBrandPage(channel)
                      : loadBrands(channel, brandSearchValues[channelId] || null, null, brandPage.page + 1)}>Sau</button>
                </div>
              </div>
            </div>
          </div>}

          {channel.platform === 'TIKTOK' && config.categoryId && <div className={styles.attributes}>
            <h4><span className={styles.sectionIcon}>📏</span> Ảnh bảng size TikTok *</h4>
            <label className={styles.field}>
              <span>URL ảnh bảng size</span>
              <input
                className={styles.input}
                type="url"
                placeholder="https://..."
                value={config.sizeChartImageUrl || ''}
                onChange={(event) => updateConfig(channel, { sizeChartImageUrl: event.target.value })}
              />
            </label>
            <label className={styles.field}>
              <span>Hoặc chọn ảnh từ máy</span>
              <span className={styles.fileUploadRow}>
                <input
                  className={styles.fileInput}
                  type="file"
                  accept="image/*"
                  onChange={(event) => uploadSizeChartImage(channel, event)}
                  disabled={sizeChartUploading[channelId]}
                />
                {sizeChartUploading[channelId] && <Loader2 className={styles.spin} size={16} />}
                {!sizeChartUploading[channelId] && <Upload size={16} />}
              </span>
            </label>
          </div>}

          {onSave && <div className={styles.saveRow}>
            <button type="button" className={styles.saveButton} onClick={() => onSave(channel, config)}>Lưu cấu hình</button>
          </div>}
        </div>}
      </div>;
    })}
  </section>;
};

export default PlatformConfigSection;
