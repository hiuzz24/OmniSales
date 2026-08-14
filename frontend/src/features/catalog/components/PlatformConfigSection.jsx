import { useEffect, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';
import {
  ChevronDown,
  ChevronRight,
  ChevronLeft,
  RefreshCw,
  Loader2,
  Search,
  Upload,
  CircleCheck,
  FolderTree,
  Ruler,
  SlidersHorizontal,
  Sparkles,
  Store,
  Tag,
  Type,
} from 'lucide-react';
import { toast } from 'react-toastify';
import platformLookupApi from '../../../api/platformLookupApi';
import { uploadImageToCloudinary } from '../../../api/cloudinaryApi';
import {
  resolveTikTokProductTitle,
  TIKTOK_TITLE_MAX_LENGTH,
  TIKTOK_TITLE_MIN_LENGTH,
} from '../utils/tikTokProductTitle';
import styles from './PlatformConfigSection.module.css';
import PlatformAttributeField from './PlatformAttributeField';
import usePlatformAttributes from '../hooks/usePlatformAttributes';
import usePlatformBrandBrowser from '../hooks/usePlatformBrandBrowser';
import usePlatformCategoryBrowser from '../hooks/usePlatformCategoryBrowser';

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
const TIKTOK_FREE_TEXT_ATTRIBUTE_IDS = new Set(['101489', '101490']);
/** Bóc payload từ các dạng response API platform. */
const extractData = (response) => response?.data?.data || response?.data || response || [];
/** Kiểm tra URL HTTP(S) dùng cho ảnh/bảng size. */
const isHttpUrl = (value) => {
  if (!value?.trim()) return false;
  try {
    const url = new URL(value.trim());
    return ['http:', 'https:'].includes(url.protocol);
  } catch {
    return false;
  }
};

/** Tạo cấu hình platform mặc định cho một channel chưa được thiết lập. */
const emptyConfig = (channel) => ({
  channelId: channel.channelId || channel.id,
  categoryId: '',
  categoryName: '',
  categoryVersion: channel.platform === 'TIKTOK' ? 'v2' : null,
  brandId: '',
  brandName: '',
  listingTitle: '',
  sizeChartImageUrl: '',
  attributes: {},
  variantAttributeValueMappings: {},
});

/** Điều phối cấu hình category, brand, attribute và package theo từng platform. */
const PlatformConfigSection = ({ channels = [], onSave, productId = null }) => {
  const { control, setValue } = useFormContext();
  const configs = useWatch({ control, name: 'channelConfigs', defaultValue: {} });
  const [productName, productDescription, productImages, productSku, productVariants, hasVariants] = useWatch({
    control,
    name: ['name', 'description', 'images', 'sku', 'variants', 'hasVariants'],
  });
  const platformChannels = channels.filter((channel) => ['LAZADA', 'TIKTOK'].includes(channel.platform));
  const shopifyChannels = channels.filter((channel) => channel.platform === 'SHOPIFY');
  const [openChannelId, setOpenChannelId] = useState(null);
  const {
    categoryState,
    setCategoryState,
    suggestionState,
    setSuggestionState,
    manualBrowser,
    setManualBrowser,
    searchValues,
    setSearchValues,
    categorySearchTimers,
  } = usePlatformCategoryBrowser();
  const {
    brandState,
    setBrandState,
    brandPages,
    setBrandPages,
    brandPageTokens,
    setBrandPageTokens,
    brandPageHistory,
    setBrandPageHistory,
    brandSearchValues,
    setBrandSearchValues,
    brandSearchTimers,
  } = usePlatformBrandBrowser();
  const {
    attributeState,
    setAttributeState,
    sizeChartUploading,
    setSizeChartUploading,
  } = usePlatformAttributes();
  const [loading, setLoading] = useState({});

  useEffect(() => () => {
    Object.values(categorySearchTimers.current).forEach((timer) => clearTimeout(timer));
    Object.values(brandSearchTimers.current).forEach((timer) => clearTimeout(timer));
  }, []);

  // Lấy cấu hình hiện tại hoặc tạo cấu hình rỗng cho kênh.
  const configFor = (channel) => configs[channel.channelId || channel.id] || emptyConfig(channel);
  // Cập nhật loading độc lập cho một kênh.
  const setLoadingFor = (channelId, value) => setLoading((previous) => ({ ...previous, [channelId]: value }));
  const primaryImageUrl = productImages?.find((image) => image.isPrimary)?.url || productImages?.[0]?.url;
  // Tạo tiêu đề TikTok từ dữ liệu sản phẩm và giới hạn ký tự của sàn.
  const tikTokTitleFor = (channel) => {
    const config = configFor(channel);
    return resolveTikTokProductTitle({
      listingTitle: config.listingTitle,
      productName,
      categoryName: config.categoryName,
      brandName: config.brandName,
      description: productDescription,
    });
  };
  // Tạo dấu vết input để nhận biết cấu hình cần tải lại lookup.
  const inputHash = (channel) => {
    const title = channel.platform === 'TIKTOK'
      ? tikTokTitleFor(channel).title
      : productName || '';
    return `${productId || 'new'}|${title}|${productDescription || ''}|${primaryImageUrl || ''}`;
  };
  // Kiểm tra attribute do hệ thống tự quản lý và không cho nhập tay.
  const isSystemManaged = (attribute) => SYSTEM_ATTRIBUTES.has(String(attribute.name || '').toLowerCase());
  // Nhận biết thông số Lazada được phép bỏ trống.
  const isOptionalLazadaSpecification = (attribute) => OPTIONAL_LAZADA_SPECIFICATIONS.has(
    String(attribute.name || '').trim().toLowerCase().replace(/[\s-]+/g, '_'));
  // Nhận biết attribute TikTok thuộc cấu hình listing V1.
  const isTikTokListingAttribute = (attribute) => TIKTOK_LISTING_ATTRIBUTE_IDS.has(String(attribute.id));

  // MANUAL_CATEGORY_BROWSER_FALLBACK: only called after the user chooses manual browsing.
  // Tải category platform theo node cha hoặc từ khóa tìm kiếm.
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

  // Debounce tìm category để tránh gọi lookup API sau mỗi phím.
  const searchCategories = (channel, value, immediate = false) => {
    const channelId = channel.channelId || channel.id;
    setSearchValues((previous) => ({ ...previous, [channelId]: value }));
    clearTimeout(categorySearchTimers.current[channelId]);

    // Thực hiện lần tìm category mới nhất sau thời gian debounce.
    const runSearch = () => loadCategories(channel, null, value.trim() || null);
    if (immediate) {
      runSearch();
      return;
    }
    categorySearchTimers.current[channelId] = setTimeout(runSearch, 350);
  };

  // Tải brand platform theo category, từ khóa và trạng thái phân trang.
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

  // Debounce tìm brand riêng cho từng kênh.
  const searchBrands = (channel, value, immediate = false) => {
    const channelId = channel.channelId || channel.id;
    setBrandSearchValues((previous) => ({ ...previous, [channelId]: value }));
    clearTimeout(brandSearchTimers.current[channelId]);

    // Thực hiện lần tìm brand mới nhất sau thời gian debounce.
    const runSearch = () => loadBrands(channel, value.trim() || null, null, 0, null);
    if (immediate) {
      runSearch();
      return;
    }
    brandSearchTimers.current[channelId] = setTimeout(runSearch, 350);
  };

  // Tải trang brand tiếp theo theo cơ chế page hoặc page token của platform.
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

  // Quay lại trang brand trước đó từ lịch sử phân trang cục bộ.
  const loadPreviousBrandPage = async (channel) => {
    const channelId = channel.channelId || channel.id;
    const current = brandPages[channelId];
    const history = brandPageHistory[channelId] || [];
    if (!current?.page || history.length === 0) return;
    const previousToken = history.at(-1);
    setBrandPageHistory((previous) => ({ ...previous, [channelId]: history.slice(0, -1) }));
    await loadBrands(channel, brandSearchValues[channelId] || null, null, current.page - 1, previousToken);
  };

  // Yêu cầu platform gợi ý category từ thông tin sản phẩm.
  const suggestCategories = async (channel) => {
    const channelId = channel.channelId || channel.id;
    if (!productId && (!productName?.trim() || !primaryImageUrl)) {
      toast.error('Hãy nhập tên sản phẩm và thêm ảnh chính trước khi gợi ý danh mục');
      return;
    }
    const resolvedTitle = channel.platform === 'TIKTOK' ? tikTokTitleFor(channel) : null;
    if (resolvedTitle && !resolvedTitle.valid) {
      toast.error(`Tên sản phẩm TikTok phải có từ ${TIKTOK_TITLE_MIN_LENGTH} đến ${TIKTOK_TITLE_MAX_LENGTH} ký tự`);
      return;
    }
    const config = configFor(channel);
    const hash = inputHash(channel);
    setLoadingFor(channelId, true);
    try {
      const response = await platformLookupApi.getCategorySuggestions(channel.platform, {
        channelId,
        productId: productId || undefined,
        title: productName,
        description: productDescription || '',
        primaryImageUrl: productId ? undefined : primaryImageUrl,
        listingTitle: channel.platform === 'TIKTOK' ? config.listingTitle || null : undefined,
        categoryName: channel.platform === 'TIKTOK' ? config.categoryName || null : undefined,
        brandName: channel.platform === 'TIKTOK' ? config.brandName || null : undefined,
        categoryVersion: config.categoryVersion || undefined,
      });
      const suggestions = extractData(response) || [];
      setSuggestionState((previous) => ({ ...previous, [channelId]: { hash, items: suggestions } }));
    } catch (error) {
      toast.error(error.response?.data?.message || `Không thể gợi ý danh mục ${channel.platform}`);
    } finally {
      setLoadingFor(channelId, false);
    }
  };

  // Tải schema attribute bắt buộc sau khi chọn category platform.
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

  // Gộp một phần thay đổi vào cấu hình của kênh.
  const updateConfig = (channel, patch) => {
    const channelId = channel.channelId || channel.id;
    setValue(`channelConfigs.${channelId}`, { ...configFor(channel), ...patch }, { shouldDirty: true });
  };

  // Tải ảnh bảng kích thước rồi gán URL vào field platform tương ứng.
  const uploadSizeChartImage = async (channel, event, attributeKey = null) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const channelId = channel.channelId || channel.id;
    try {
      setSizeChartUploading((previous) => ({ ...previous, [channelId]: true }));
      const imageUrl = await uploadImageToCloudinary(file);
      if (attributeKey) {
        const config = configFor(channel);
        updateConfig(channel, {
          attributes: { ...config.attributes, [attributeKey]: imageUrl },
        });
      } else {
        updateConfig(channel, { sizeChartImageUrl: imageUrl });
      }
      toast.success('Đã tải ảnh bảng size lên');
    } catch (error) {
      toast.error(error.message || 'Không thể tải ảnh bảng size lên');
    } finally {
      setSizeChartUploading((previous) => ({ ...previous, [channelId]: false }));
      event.target.value = '';
    }
  };

  // Mở hoặc đóng phần cấu hình và tải lookup ở lần mở đầu tiên.
  const toggle = async (channel) => {
    const channelId = channel.channelId || channel.id;
    const willOpen = openChannelId !== channelId;
    setOpenChannelId(willOpen ? channelId : null);
    if (!willOpen) return;
    const config = configFor(channel);
    if (config.categoryId && !attributeState[channelId]) await loadAttributes(channel, config);
  };

  // Chọn category, reset dữ liệu phụ thuộc rồi tải brand và attribute mới.
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
      categoryConfirmed: source === 'PLATFORM_SUGGESTION', brandId: '', brandName: '', attributes: {},
      variantAttributeValueMappings: {} };
    updateConfig(channel, next);
    await loadAttributes(channel, next);
    await loadBrands(channel, null, category.id);
  };

  // Render control phù hợp với kiểu dữ liệu của một platform attribute.
  const renderAttribute = (channel, attribute) => <PlatformAttributeField
    key={channel.platform === 'TIKTOK' ? attribute.id || attribute.name : attribute.name || attribute.id}
    channel={channel}
    attribute={attribute}
    config={configFor(channel)}
    updateConfig={updateConfig}
    uploadSizeChartImage={uploadSizeChartImage}
    sizeChartUploading={sizeChartUploading}
    hasVariants={hasVariants}
    productVariants={productVariants}
    productSku={productSku}
  />;

  if (platformChannels.length === 0 && shopifyChannels.length === 0) return null;
  return <section className={styles.section}>
    <div className={styles.header}><h3>Cấu hình theo sàn</h3><p>Chỉ cần cấu hình một lần cho mỗi sàn để chuẩn bị đồng bộ dữ liệu.</p></div>
    {shopifyChannels.map((channel) => {
      const channelId = channel.channelId || channel.id;
      return <div className={styles.shopifyReadyCard} key={channelId}>
        <span className={styles.shopifyIcon}><Store size={17} /></span>
        <div className={styles.shopifyDetails}>
          <span className={styles.platformBadge}>Shopify</span>
          <strong>{channel.channelName || channel.displayName || 'Shopify'}</strong>
          <span>Đã active. Sản phẩm sẽ dùng dữ liệu và tồn kho khả dụng từ OSMS khi đồng bộ.</span>
        </div>
        <span className={styles.statusReady}><CircleCheck size={13} /> Sẵn sàng</span>
      </div>;
    })}
    {platformChannels.map((channel) => {
      const channelId = channel.channelId || channel.id;
      const config = configFor(channel);
      const category = categoryState[channelId] || { items: [], history: [] };
      const suggestions = suggestionState[channelId] || { items: [], hash: null };
      const suggestionsAreStale = suggestions.hash && suggestions.hash !== inputHash(channel);
      const isManualBrowserOpen = Boolean(manualBrowser[channelId]);
      const configurableAttributes = (attributeState[channelId] || []).filter((attribute) =>
        !isSystemManaged(attribute));
      const requiredAttributes = configurableAttributes.filter((attribute) =>
        attribute.required && !(channel.platform === 'TIKTOK' && attribute.saleProperty));
      const optionalAttributes = configurableAttributes.filter((attribute) =>
        !attribute.required && (channel.platform === 'TIKTOK'
          ? !attribute.saleProperty && isTikTokListingAttribute(attribute)
          : !attribute.saleProperty && isOptionalLazadaSpecification(attribute)));
      const loadedBrands = brandState[channelId] || [];
      const brandPage = brandPages[channelId] || { page: 0, size: 50, totalElements: 0, totalPages: 0 };
      const selectedBrand = config.brandId && !loadedBrands.some((brand) => String(brand.id) === String(config.brandId))
        ? [{ id: config.brandId, name: config.brandName || `Brand #${config.brandId}` }]
        : [];
      const availableBrands = [...selectedBrand, ...loadedBrands];
      const platformLabel = channel.platform === 'TIKTOK' ? 'TikTok' : 'Lazada';
      const normalizedProductName = productName?.trim() || '';
      const resolvedTikTokTitle = channel.platform === 'TIKTOK' ? tikTokTitleFor(channel) : null;
      const isTikTokProductNameInvalid = resolvedTikTokTitle && !resolvedTikTokTitle.valid;
      const isSuggestionInputMissing = !productId && (!normalizedProductName || !primaryImageUrl);
      const isSizeChartUrlInvalid = Boolean(config.sizeChartImageUrl) && !isHttpUrl(config.sizeChartImageUrl);
      return <div className={`${styles.panel} ${openChannelId === channelId ? styles.panelOpen : ''}`} key={channelId}>
        <button className={styles.panelHeader} type="button" onClick={() => toggle(channel)}>
          <span className={styles.panelToggleIcon}>{openChannelId === channelId ? <ChevronDown size={17} /> : <ChevronRight size={17} />}</span>
          <span className={styles.channelHeading}>
            <span className={styles.channelTitleRow}>
              <span className={styles.panelTitle}>{channel.channelName || channel.displayName || channel.platform}</span>
              <span className={styles.platformBadge}>{platformLabel}</span>
            </span>
            <span className={styles.channelMeta}>{config.categoryId ? config.categoryName : 'Chưa chọn danh mục sản phẩm'}</span>
          </span>
          <span className={config.categoryId ? styles.statusReady : styles.statusPending}>
            {config.categoryId ? <CircleCheck size={13} /> : null}
            {config.categoryId ? 'Đã chọn danh mục' : 'Cấu hình sau'}
          </span>
        </button>
        {openChannelId === channelId && <div className={styles.panelBody}>

          {channel.platform === 'TIKTOK' && <div className={`${styles.configBlock} ${styles.titleBlock}`}>
            <div className={styles.configBlockHeader}>
              <span className={styles.sectionIcon}><Type size={16} /></span>
              <div>
                <div className={styles.configBlockTitle}>Tên hiển thị trên TikTok <span className={styles.requiredMark}>*</span></div>
                <p className={styles.configBlockDescription}>Tiêu đề tự động kết hợp tên sản phẩm với danh mục và thương hiệu TikTok.</p>
              </div>
              <span className={resolvedTikTokTitle.overridden ? styles.titleModeOverride : styles.titleModeAuto}>
                {resolvedTikTokTitle.overridden ? 'Đã chỉnh sửa' : 'Tự động'}
              </span>
            </div>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Tiêu đề gửi lên TikTok</span>
              <textarea
                className={`${styles.input} ${styles.titleInput}`}
                value={resolvedTikTokTitle.title}
                onChange={(event) => updateConfig(channel, { listingTitle: event.target.value })}
                rows={2}
              />
            </label>
            <div className={styles.titleFooter}>
              <span className={isTikTokProductNameInvalid ? styles.titleCountInvalid : styles.titleCount}>
                {resolvedTikTokTitle.title.length} / {TIKTOK_TITLE_MAX_LENGTH}
              </span>
              {resolvedTikTokTitle.overridden && <button
                type="button"
                className={styles.resetTitleButton}
                onClick={() => updateConfig(channel, { listingTitle: '' })}
              >
                <RefreshCw size={14} /> Khôi phục tiêu đề tự động
              </button>}
            </div>
            {isTikTokProductNameInvalid
              ? <p className={styles.titleError}>Tên sản phẩm TikTok phải có từ {TIKTOK_TITLE_MIN_LENGTH} đến {TIKTOK_TITLE_MAX_LENGTH} ký tự.</p>
              : null}
          </div>}
          
          <div className={`${styles.configBlock} ${styles.categoryBlock}`}>
            <div className={styles.configBlockHeader}>
              <span className={styles.sectionIcon}><FolderTree size={16} /></span>
              <div>
                <div className={styles.configBlockTitle}>Danh mục sản phẩm <span className={styles.requiredMark}>*</span></div>
                <p className={styles.configBlockDescription}>Chọn danh mục lá phù hợp để tải đúng thuộc tính và thương hiệu của {platformLabel}.</p>
              </div>
            </div>
            {config.categoryId && <div className={styles.selectedCategorySummary}>
              <span className={styles.selectedCategoryLabel}>Danh mục đang chọn</span>
              <strong>{config.categoryName}</strong>
            </div>}
            <div className={styles.actions}>
              <button type="button" className={styles.saveButton} onClick={() => suggestCategories(channel)}
                disabled={isSuggestionInputMissing || isTikTokProductNameInvalid || loading[channelId]}>
                Gợi ý danh mục
              </button>
              <button type="button" className={styles.backButton} onClick={() => {
                setManualBrowser((previous) => ({ ...previous, [channelId]: true }));
                if (!categoryState[channelId]) loadCategories(channel);
              }}>
                Chọn danh mục khác
              </button>
            </div>
            {isSuggestionInputMissing ? <p className={styles.helperText}>Nhập tên sản phẩm và ảnh chính trước khi gợi ý danh mục.</p> : null}
            {!isSuggestionInputMissing && isTikTokProductNameInvalid
              ? <p className={styles.helperText}>Tiêu đề TikTok phải có từ 25 đến 255 ký tự để gợi ý danh mục.</p>
              : null}
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
                  onChange={(event) => searchCategories(channel, event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      searchCategories(channel, event.currentTarget.value, true);
                    }
                  }} />
                <button type="button" className={styles.iconButton} title="Tìm danh mục"
                  onClick={() => searchCategories(channel, searchValues[channelId] || '', true)}>
                  <Search size={16} />
                </button>
                <button type="button" className={styles.iconButton} title="Làm mới" onClick={async () => {
                  await platformLookupApi.clearCache(channel.platform, channelId);
                  await loadCategories(channel);
                  if (config.categoryId) {
                    await loadAttributes(channel, config);
                    await loadBrands(channel, brandSearchValues[channelId] || null, null, 0);
                  }
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

          {config.categoryId && requiredAttributes.length > 0 && <div className={`${styles.configBlock} ${styles.requiredBlock}`}>
            <div className={styles.configBlockHeader}>
              <span className={styles.sectionIcon}><Sparkles size={16} /></span>
              <div>
                <div className={styles.configBlockTitle}>Thuộc tính bắt buộc</div>
                <p className={styles.configBlockDescription}>Hoàn thành các trường do {platformLabel} yêu cầu cho danh mục đã chọn.</p>
              </div>
              <span className={styles.fieldCount}>{requiredAttributes.length} trường</span>
            </div>
            <div className={styles.attributeGrid}>{requiredAttributes.map((attribute) => renderAttribute(channel, attribute))}</div>
          </div>}

          {config.categoryId && optionalAttributes.length > 0 && <div className={`${styles.configBlock} ${styles.optionalBlock}`}>
            <div className={styles.configBlockHeader}>
              <span className={styles.sectionIcon}><SlidersHorizontal size={16} /></span>
              <div>
                <div className={styles.configBlockTitle}>{channel.platform === 'TIKTOK' ? 'Thuộc tính bổ sung TikTok' : 'Thông số hiển thị trên Lazada'}</div>
                <p className={styles.configBlockDescription}>Thông tin bổ sung giúp nội dung sản phẩm đầy đủ hơn trên gian hàng.</p>
              </div>
              <span className={styles.fieldCount}>{optionalAttributes.length} trường</span>
            </div>
            <div className={styles.attributeGrid}>{optionalAttributes.map((attribute) => renderAttribute(channel, attribute))}</div>
          </div>}

          {['LAZADA', 'TIKTOK'].includes(channel.platform) && config.categoryId && <div className={`${styles.configBlock} ${styles.brandBlock}`}>
            <div className={styles.configBlockHeader}>
              <span className={styles.sectionIcon}><Tag size={16} /></span>
              <div>
                <div className={styles.configBlockTitle}>Thương hiệu {platformLabel} <span className={styles.requiredMark}>*</span></div>
                <p className={styles.configBlockDescription}>Tìm và chọn đúng thương hiệu đã được platform cung cấp.</p>
              </div>
            </div>
            <div className={styles.brandRow}>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Tìm kiếm thương hiệu</span>
                <div className={styles.actions}>
                  <input className={styles.input} placeholder="Nhập tên thương hiệu..." value={brandSearchValues[channelId] || ''}
                    onChange={(event) => searchBrands(channel, event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault();
                        searchBrands(channel, event.currentTarget.value, true);
                      }
                    }} />
                  <button type="button" className={styles.iconButton} title="Tìm thương hiệu"
                    onClick={() => searchBrands(channel, brandSearchValues[channelId] || '', true)}>
                    <Search size={16} />
                  </button>
                </div>
              </div>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Thương hiệu đã chọn</span>
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

          {channel.platform === 'TIKTOK' && config.categoryId && <div className={`${styles.configBlock} ${styles.sizeChartBlock}`}>
            <div className={styles.configBlockHeader}>
              <span className={styles.sectionIcon}><Ruler size={16} /></span>
              <div>
                <div className={styles.configBlockTitle}>Ảnh bảng size TikTok <span className={styles.requiredMark}>*</span></div>
                <p className={styles.configBlockDescription}>Dán URL ảnh hoặc tải ảnh bảng kích thước trực tiếp từ máy.</p>
              </div>
            </div>
            <div className={styles.sizeChartGrid}>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>URL ảnh bảng size</span>
              <input
                className={styles.input}
                type="url"
                placeholder="https://..."
                value={config.sizeChartImageUrl || ''}
                onChange={(event) => updateConfig(channel, { sizeChartImageUrl: event.target.value })}
              />
              {isSizeChartUrlInvalid
                ? <span className={styles.helperText}>URL ảnh phải bắt đầu bằng http:// hoặc https://.</span>
                : null}
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Tải ảnh từ máy</span>
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
            </div>
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
