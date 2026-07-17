import { useEffect, useState } from 'react';
import { FormProvider, useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../app/router/routes';
import productApi from '../../../api/productApi';
import categoryApi from '../../../api/categoryApi';
import channelApi from '../../../api/channelApi';
import warehouseApi from '../../../api/warehouseApi';
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

const ProductCreatePage = () => {
  const navigate = useNavigate();
  const methods = useForm({ resolver: zodResolver(productEditorSchema), mode: 'onBlur', reValidateMode: 'onChange', defaultValues: defaultProductFormValues });
  const { control, setValue, setError, formState: { errors } } = methods;
  const [channels, setChannels] = useState([]);
  const [categories, setCategories] = useState([]);
  const [hasVariants, selectedChannels, price, costPrice] = useWatch({ control, name: ['hasVariants', 'channelIds', 'price', 'costPrice'] });

  useEffect(() => {
    Promise.all([categoryApi.getAll(), channelApi.getAll()]).then(([categoryResponse, channelResponse]) => {
      const categoryData = unwrap(categoryResponse);
      const channelData = unwrap(channelResponse);
      if (Array.isArray(categoryData)) setCategories(categoryData);
      if (Array.isArray(channelData)) setChannels(channelData);
    }).catch(() => toast.error('Không thể tải dữ liệu tạo sản phẩm'));
  }, []);

  const onSubmit = async (values) => {
    try {
      const response = await productApi.create(buildProductRequest(values, { mode: 'create' }));
      const created = unwrap(response);
      toast.success('Tạo sản phẩm thành công!');
      navigate(ROUTES.PRODUCT_DETAIL.replace(':id', created.id));
    } catch (error) {
      if (error.response?.status === 409) setError('sku', { type: 'server', message: 'SKU đã tồn tại trong hệ thống' });
      toast.error(error.response?.data?.message || 'Đã xảy ra lỗi khi tạo sản phẩm');
    }
  };
  const onInvalid = () => toast.error('Vui lòng kiểm tra lại thông tin');
  const selectedChannelDetails = channels.filter((channel, index) => (selectedChannels || []).includes(channel.id || channel._id || (channel.platform + index)));

  return <FormProvider {...methods}><div className={styles.page}>
    <div className={styles.header}><button className={styles.backBtn} onClick={() => navigate(ROUTES.PRODUCTS)}><ArrowLeft className={styles.backIcon} />Quay lại</button><div className={styles.headerText}><h1 className={styles.headerTitle}>Thêm sản phẩm mới</h1><p className={styles.headerSubtitle}>Tạo sản phẩm và đồng bộ lên các kênh bán hàng</p></div></div>
    <div className={styles.layout}><div className={styles.mainColumn}>
      <ProductImageUploader />
      <ProductForm categories={categories} />
      <div className={styles.variantToggleCard}><div className={styles.variantToggleInfo}><div className={styles.variantToggleTitle}>Biến thể sản phẩm</div><div className={styles.variantToggleSubtitle}>{hasVariants ? 'Sản phẩm có nhiều biến thể (size, màu...)' : 'Sản phẩm không có biến thể. Nhấn để thêm biến thể.'}</div></div><button type="button" className={`${styles.variantToggleBtn} ${hasVariants ? styles.variantToggleBtnActive : styles.variantToggleBtnInactive}`} onClick={() => setValue('hasVariants', !hasVariants, { shouldValidate: true })}>{hasVariants ? 'Đã bật biến thể' : 'Tạo biến thể'}</button></div>
      {!hasVariants ? <ProductPriceStock price={price} costPrice={costPrice} onChange={(field, value) => setValue(field, value, { shouldDirty: true })} errors={errors} channels={channels} selectedChannels={selectedChannels} disablePrice disableCostPrice /> : <ProductVariantForm channels={channels} selectedChannels={selectedChannels} disablePrice disableCostPrice />}
      <ProductShippingInfo />
      <PlatformConfigSection channels={selectedChannelDetails} />
    </div><ProductChannelSidebar channels={channels} onSubmit={onSubmit} onInvalid={onInvalid} onCancel={() => navigate(ROUTES.PRODUCTS)} /></div>
  </div></FormProvider>;
};

export default ProductCreatePage;
