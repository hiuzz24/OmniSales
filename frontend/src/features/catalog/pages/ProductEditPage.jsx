import { useEffect, useRef, useState } from 'react';
import { FormProvider, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Link2, PackageCheck, Store, TriangleAlert } from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../app/router/routes';
import productApi from '../../../api/productApi';
import categoryApi from '../../../api/categoryApi';
import channelApi from '../../../api/channelApi';
import ProductImageUploader from '../components/ProductImageUploader';
import ProductForm from '../components/ProductForm';
import ProductPriceStock from '../components/ProductPriceStock';
import ProductVariantForm from '../components/ProductVariantForm';
import ProductShippingInfo from '../components/ProductShippingInfo';
import ProductChannelSidebar from '../components/ProductChannelSidebar';
import PlatformConfigSection from '../components/PlatformConfigSection';
import { buildProductRequest, defaultProductFormValues, normalizeVariantForEditor, productEditorSchema, seedVariantFromProduct } from '../models/Product';
import styles from './ProductCreatePage.module.css';

const unwrap = (response) => response?.data?.data || response?.data || response;
const channelId = (channel, index) => channel.id || channel._id || `${channel.platform}${index}`;

const toChannelConfig = (sync) => ({
  channelId: sync.channelId,
  categoryId: sync.platformConfig?.categoryId || '',
  categoryName: sync.platformConfig?.categoryName || '',
  categorySource: sync.platformConfig?.categorySource || '',
  categoryConfirmed: Boolean(sync.platformConfig?.categoryConfirmed),
  categoryVersion: sync.platformConfig?.categoryVersion || (sync.platform === 'TIKTOK' ? 'v2' : null),
  brandId: sync.platformConfig?.brandId || '',
  brandName: sync.platformConfig?.brandName || '',
  listingTitle: sync.platformConfig?.listingTitle || '',
  sizeChartImageUrl: sync.platformConfig?.sizeChartImageUrl || '',
  attributes: sync.platformConfig?.attributes || {},
  variantAttributeValueMappings: sync.platformConfig?.variantAttributeValueMappings || {},
});

const ProductEditPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const marketplaceSectionRef = useRef(null);
  const methods = useForm({
    resolver: zodResolver(productEditorSchema),
    mode: 'onBlur',
    reValidateMode: 'onChange',
    defaultValues: defaultProductFormValues,
  });
  const { control, getValues, reset, setError, setValue, formState: { errors } } = methods;
  const [channels, setChannels] = useState([]);
  const [categories, setCategories] = useState([]);
  const [existingAttributes, setExistingAttributes] = useState({});
  const [loading, setLoading] = useState(true);
  const [hasVariants, selectedChannels, price, costPrice, hasOrders] = useWatch({
    control,
    name: ['hasVariants', 'channelIds', 'price', 'costPrice', 'hasOrders'],
  });

  useEffect(() => {
    Promise.all([categoryApi.getAll(), channelApi.getAll(), productApi.getById(id)])
      .then(([categoryResponse, channelResponse, productResponse]) => {
        const product = unwrap(productResponse);
        const channelData = unwrap(channelResponse);
        const categoryData = unwrap(categoryResponse);
        if (Array.isArray(categoryData)) setCategories(categoryData);
        if (Array.isArray(channelData)) setChannels(channelData);

        const normalizedVariants = (product.variants || []).map(normalizeVariantForEditor);
        const firstVariant = normalizedVariants[0] || {};
        const legacyDefaultVariant = normalizedVariants.length === 1
          && normalizedVariants[0]?.sku === product.sku
          && !(normalizedVariants[0]?.images || []).length;
        const selected = (channelData || [])
          .filter((channel, index) =>
            product.channelIds?.includes(channelId(channel, index))
            || (!product.channelIds?.length && product.channels?.includes(channel.platform)))
          .map(channelId);
        const configs = (product.channelSyncs || []).reduce((result, sync) => {
          if (sync.channelId) result[sync.channelId] = toChannelConfig(sync);
          return result;
        }, {});

        reset({
          ...defaultProductFormValues,
          version: product.version,
          hasOrders: product.hasOrders,
          name: product.name || '',
          sku: product.sku || '',
          barcode: firstVariant.barcode || '',
          size: firstVariant.optionValues?.Size || '',
          color: firstVariant.optionValues?.['Màu'] || '',
          description: product.description || '',
          categoryId: product.categoryId || '',
          brand: product.brand || '',
          unit: product.unit || '',
          status: product.status === 'ACTIVE' ? 'ACTIVE' : 'DRAFT',
          hasVariants: product.hasVariants ?? !legacyDefaultVariant,
          price: firstVariant.price ?? '0',
          costPrice: firstVariant.costPrice ?? '0',
          packageWeightKg: product.weightGrams ? String(product.weightGrams / 1000) : '',
          packageWidthCm: product.attributes?.packageWidthCm || '',
          packageHeightCm: product.attributes?.packageHeightCm || '',
          packageLengthCm: product.attributes?.packageLengthCm || '',
          lowStockThreshold: product.lowStockThreshold ?? '5',
          images: product.images || [],
          variants: normalizedVariants,
          channelIds: selected,
          channelConfigs: configs,
        });
        setExistingAttributes(product.attributes || {});
      })
      .catch(() => toast.error('Lỗi khi tải dữ liệu sản phẩm'))
      .finally(() => setLoading(false));
  }, [id, reset]);

  useEffect(() => {
    if (!loading && location.state?.focusMarketplace) {
      window.setTimeout(() => {
        marketplaceSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 150);
    }
  }, [loading, location.state]);

  const saveProduct = async (values, shouldSync = false) => {
    try {
      await productApi.update(id, buildProductRequest(values, { mode: 'edit', existingAttributes }));
      if (shouldSync) {
        await productApi.sync(id);
        toast.success('Đã cập nhật và đồng bộ sản phẩm lên các sàn đang liên kết.');
      } else {
        toast.success('Cập nhật sản phẩm thành công!');
      }
      navigate(ROUTES.PRODUCT_DETAIL.replace(':id', id));
    } catch (error) {
      if (error.response?.status === 409) {
        setError('sku', { type: 'server', message: 'SKU đã tồn tại trong hệ thống' });
      }
      toast.error(error.response?.data?.message || 'Đã xảy ra lỗi khi cập nhật sản phẩm');
    }
  };

  const selectedDetails = channels.filter((channel, index) => (selectedChannels || []).includes(channelId(channel, index)));

  const toggleVariants = () => {
    if (!hasVariants) {
      setValue('variants', seedVariantFromProduct(getValues()), {
        shouldDirty: true,
        shouldValidate: true,
      });
    }
    setValue('hasVariants', !hasVariants, { shouldDirty: true, shouldValidate: true });
  };

  if (loading) return <div style={{ padding: '20px' }}>Đang tải...</div>;

  return (
    <FormProvider {...methods}>
      <div className={styles.page}>
        <div className={styles.header}>
          <button className={styles.backBtn} onClick={() => navigate(ROUTES.PRODUCT_DETAIL.replace(':id', id))} type="button">
            <ArrowLeft className={styles.backIcon} />
            Quay lại
          </button>
          <div className={styles.headerText}>
            <h1 className={styles.headerTitle}>Chỉnh sửa sản phẩm</h1>
            <p className={styles.headerSubtitle}>Cập nhật thông tin, liên kết sàn và đồng bộ tồn kho dùng chung</p>
          </div>
        </div>

        <div className={styles.layout}>
          <div className={styles.mainColumn}>
            <section className={styles.productOverview}>
              <ProductImageUploader />
              <ProductForm categories={categories} />
            </section>

            <div className={styles.variantToggleCard}>
              <div className={styles.variantToggleInfo}>
                <div className={styles.variantToggleTitle}>Biến thể sản phẩm</div>
                <div className={styles.variantToggleSubtitle}>
                  {hasVariants
                    ? 'Sản phẩm có nhiều biến thể theo size, màu hoặc thuộc tính khác.'
                    : 'Sản phẩm đang dùng một biến thể mặc định.'}
                </div>
              </div>
              <button
                type="button"
                className={`${styles.variantToggleBtn} ${hasVariants ? styles.variantToggleBtnActive : styles.variantToggleBtnInactive}`}
                onClick={toggleVariants}
              >
                {hasVariants ? 'Đã bật biến thể' : 'Tạo biến thể'}
              </button>
            </div>

            {!hasVariants ? (
              <ProductPriceStock
                price={price}
                costPrice={costPrice}
                onChange={(field, value) => setValue(field, value, { shouldDirty: true })}
                errors={errors}
                channels={channels}
                selectedChannels={selectedChannels}
                disableCostPrice
              />
            ) : (
              <ProductVariantForm
                hasOrders={hasOrders}
                channels={channels}
                selectedChannels={selectedChannels}
                disableCostPrice
              />
            )}

            <ProductShippingInfo />

            <section ref={marketplaceSectionRef} className={styles.marketplaceGuide} id="marketplace-linking">
              <div className={styles.marketplaceGuideHeader}>
                <div className={styles.marketplaceGuideIcon}><Link2 size={20} /></div>
                <div>
                  <h2>Liên kết sàn bán</h2>
                  <p>Bật sàn ở cột bên phải, bổ sung trường bắt buộc theo từng sàn, sau đó lưu hoặc đồng bộ lên các sàn đang active.</p>
                </div>
              </div>
              <div className={styles.marketplaceGuideGrid}>
                <div className={styles.marketplaceGuideItem}>
                  <PackageCheck size={18} />
                  <div><strong>Một tồn kho dùng chung</strong><span>Số lượng tồn kho lấy từ kho mặc định, không nhập tồn riêng cho từng sàn.</span></div>
                </div>
                <div className={styles.marketplaceGuideItem}>
                  <Store size={18} />
                  <div><strong>Shopify</strong><span>Cần tên, ảnh, SKU/variant, giá và location inventory để tạo/cập nhật sản phẩm.</span></div>
                </div>
                <div className={styles.marketplaceGuideItem}>
                  <TriangleAlert size={18} />
                  <div><strong>Lazada / TikTok</strong><span>Cần danh mục, thương hiệu và thuộc tính bắt buộc theo danh mục trước khi tạo sản phẩm.</span></div>
                </div>
                <div className={styles.marketplaceGuideItem}>
                  <CheckCircle2 size={18} />
                  <div><strong>Đồng bộ có kiểm soát</strong><span>Nút “Cập nhật & đồng bộ sàn” sẽ lưu cấu hình rồi đẩy lên các sàn đã liên kết.</span></div>
                </div>
              </div>
            </section>

            <PlatformConfigSection channels={selectedDetails} productId={id} />
          </div>

          <ProductChannelSidebar
            channels={channels}
            onSubmit={(values) => saveProduct(values, false)}
            onInvalid={() => toast.error('Vui lòng kiểm tra lại thông tin')}
            onCancel={() => navigate(ROUTES.PRODUCT_DETAIL.replace(':id', id))}
            isEditMode
          />
        </div>
      </div>
    </FormProvider>
  );
};

export default ProductEditPage;
