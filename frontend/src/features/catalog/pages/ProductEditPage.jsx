import { useEffect, useState } from 'react';
import { FormProvider, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
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
import { buildProductRequest, defaultProductFormValues, productEditorSchema } from '../models/Product';
import styles from './ProductCreatePage.module.css';

const unwrap = (response) => response?.data?.data || response?.data || response;
const channelId = (channel, index) => channel.id || channel._id || (channel.platform + index);

const ProductEditPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const methods = useForm({ resolver: zodResolver(productEditorSchema), mode: 'onBlur', reValidateMode: 'onChange', defaultValues: defaultProductFormValues });
  const { control, reset, setError, setValue, formState: { errors } } = methods;
  const [channels, setChannels] = useState([]);
  const [categories, setCategories] = useState([]);
  const [existingAttributes, setExistingAttributes] = useState({});
  const [loading, setLoading] = useState(true);
  const [hasVariants, selectedChannels, price, costPrice, hasOrders] = useWatch({ control, name: ['hasVariants', 'channelIds', 'price', 'costPrice', 'hasOrders'] });

  useEffect(() => {
    Promise.all([categoryApi.getAll(), channelApi.getAll(), productApi.getById(id)]).then(([categoryResponse, channelResponse, productResponse]) => {
      const product = unwrap(productResponse);
      const channelData = unwrap(channelResponse);
      const categoryData = unwrap(categoryResponse);
      if (Array.isArray(categoryData)) setCategories(categoryData);
      if (Array.isArray(channelData)) setChannels(channelData);
      const isDefault = product.variants?.length === 1 && !Object.keys(product.variants[0].optionValues || {}).length;
      const selected = (channelData || []).filter((channel, index) => product.channelIds?.includes(channelId(channel, index)) || (!product.channelIds?.length && product.channels?.includes(channel.platform))).map(channelId);
      const configs = (product.channelSyncs || []).reduce((result, sync) => {
        if (sync.channelId) result[sync.channelId] = { channelId: sync.channelId, categoryId: sync.platformConfig?.categoryId || '', categoryName: sync.platformConfig?.categoryName || '', categorySource: sync.platformConfig?.categorySource || '', categoryConfirmed: Boolean(sync.platformConfig?.categoryConfirmed), categoryVersion: sync.platformConfig?.categoryVersion || (sync.platform === 'TIKTOK' ? 'v1' : null), brandId: sync.platformConfig?.brandId || '', brandName: sync.platformConfig?.brandName || '', sizeChartImageUrl: sync.platformConfig?.sizeChartImageUrl || '', attributes: sync.platformConfig?.attributes || {}, variantAttributeBindings: sync.platformConfig?.variantAttributeBindings || {} };
        return result;
      }, {});
      const first = product.variants?.[0] || {};
      reset({ ...defaultProductFormValues, version: product.version, hasOrders: product.hasOrders, name: product.name || '', sku: product.sku || '', barcode: first.barcode || '', size: first.optionValues?.Size || '', color: first.optionValues?.['Màu'] || '', description: product.description || '', categoryId: product.categoryId || '', brand: product.brand || '', unit: product.unit || '', status: product.status === 'ACTIVE' ? 'ACTIVE' : 'DRAFT', hasVariants: !isDefault, price: first.price ?? '0', costPrice: first.costPrice ?? '0', packageWeightKg: product.weightGrams ? String(product.weightGrams / 1000) : '', packageWidthCm: product.attributes?.packageWidthCm || '', packageHeightCm: product.attributes?.packageHeightCm || '', packageLengthCm: product.attributes?.packageLengthCm || '', lowStockThreshold: product.lowStockThreshold ?? '5', images: product.images || [], variants: product.variants || [], channelIds: selected, channelConfigs: configs });
      setExistingAttributes(product.attributes || {});
    }).catch(() => toast.error('Lỗi khi tải dữ liệu sản phẩm')).finally(() => setLoading(false));
  }, [id, reset]);

  const onSubmit = async (values) => {
    try { await productApi.update(id, buildProductRequest(values, { mode: 'edit', existingAttributes })); toast.success('Cập nhật sản phẩm thành công!'); navigate(ROUTES.PRODUCT_DETAIL.replace(':id', id)); }
    catch (error) { if (error.response?.status === 409) setError('sku', { type: 'server', message: 'SKU đã tồn tại trong hệ thống' }); toast.error(error.response?.data?.message || 'Đã xảy ra lỗi khi cập nhật sản phẩm'); }
  };
  const selectedDetails = channels.filter((channel, index) => (selectedChannels || []).includes(channelId(channel, index)));
  if (loading) return <div style={{ padding: '20px' }}>Đang tải...</div>;
  return <FormProvider {...methods}><div className={styles.page}>
    <div className={styles.header}><button className={styles.backBtn} onClick={() => navigate(ROUTES.PRODUCT_DETAIL.replace(':id', id))}><ArrowLeft className={styles.backIcon} />Quay lại</button><div className={styles.headerText}><h1 className={styles.headerTitle}>Chỉnh sửa sản phẩm</h1><p className={styles.headerSubtitle}>Cập nhật thông tin và đồng bộ thay đổi</p></div></div>
    <div className={styles.layout}><div className={styles.mainColumn}>
      <ProductImageUploader /><ProductForm categories={categories} />
      <div className={styles.variantToggleCard}><div className={styles.variantToggleInfo}><div className={styles.variantToggleTitle}>Biến thể sản phẩm</div><div className={styles.variantToggleSubtitle}>{hasVariants ? 'Sản phẩm có nhiều biến thể (size, màu...)' : 'Sản phẩm đang dùng biến thể mặc định.'}</div></div><button type="button" className={`${styles.variantToggleBtn} ${hasVariants ? styles.variantToggleBtnActive : styles.variantToggleBtnInactive}`} onClick={() => setValue('hasVariants', !hasVariants, { shouldValidate: true })}>{hasVariants ? 'Đã bật biến thể' : 'Tạo biến thể'}</button></div>
      {!hasVariants ? <ProductPriceStock price={price} costPrice={costPrice} onChange={(field, value) => setValue(field, value, { shouldDirty: true })} errors={errors} channels={channels} selectedChannels={selectedChannels} disableCostPrice /> : <ProductVariantForm hasOrders={hasOrders} channels={channels} selectedChannels={selectedChannels} disableCostPrice />}
      <ProductShippingInfo /><PlatformConfigSection channels={selectedDetails} />
    </div><ProductChannelSidebar channels={channels} onSubmit={onSubmit} onInvalid={() => toast.error('Vui lòng kiểm tra lại thông tin')} onCancel={() => navigate(ROUTES.PRODUCT_DETAIL.replace(':id', id))} isEditMode /></div>
  </div></FormProvider>;
};
export default ProductEditPage;
