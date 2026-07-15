import { useState, useEffect } from 'react';
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
import { validateProductForm } from '../models/Product';
import styles from './ProductCreatePage.module.css';

const ProductCreatePage = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    name: '',
    sku: '',
    barcode: '',
    size: '',
    color: '',
    description: '',
    categoryId: '',
    brand: '',
    unit: '',
  });
  const [images, setImages] = useState([]);
  const [price, setPrice] = useState('0');
  const [costPrice, setCostPrice] = useState('0');
  const [hasVariants, setHasVariants] = useState(false);
  const [variants, setVariants] = useState([]);
  const [weightGrams, setWeightGrams] = useState('');
  const [dimensions, setDimensions] = useState('');
  const [lowStockThreshold, setLowStockThreshold] = useState('5');
  const [showProduct, setShowProduct] = useState(true);

  const [channels, setChannels] = useState([]);
  const [selectedChannels, setSelectedChannels] = useState([]);

  const [categories, setCategories] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState('');

  const [errors, setErrors] = useState({});
  const [variantErrors, setVariantErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const loadData = async () => {
      try {
        const [catData, chanData, warehouseData] = await Promise.all([
          categoryApi.getAll(),
          channelApi.getAll(),
          warehouseApi.getAll(),
        ]);
        const catList = catData.data?.data || catData.data || catData;
        if (Array.isArray(catList)) {
          setCategories(catList);
        }
        const chanList = chanData.data?.data || chanData.data || chanData;
        if (Array.isArray(chanList)) {
          setChannels(chanList);
          setSelectedChannels(chanList.map((c, i) => c.id || c._id || (c.platform + i)));
        }
        const warehouseList = warehouseData.data?.data || warehouseData.data || warehouseData;
        if (Array.isArray(warehouseList)) {
          setWarehouses(warehouseList);
          const channelWarehouseId = Array.isArray(chanList)
            ? chanList.map((channel) => channel.metadata?.defaultWarehouseId).find(Boolean)
            : null;
          const preferredWarehouseId = warehouseList.some((warehouse) => warehouse.id === channelWarehouseId)
            ? channelWarehouseId
            : warehouseList[0]?.id;
          setSelectedWarehouseId((prev) => prev || preferredWarehouseId || '');
        }
      } catch (error) {
        console.error('Failed to load initial data:', error);
      }
    };
    loadData();
  }, []);

  const validate = () => {
    const dataToValidate = {
      ...formData,
      hasVariants,
      price: '0',
      costPrice: '0',
      weightGrams: weightGrams,
      lowStockThreshold: lowStockThreshold,
      dimensions: dimensions,
      variants: hasVariants ? variants.map((variant) => ({ ...variant, price: '0', costPrice: '0' })) : [],
    };

    const result = validateProductForm(dataToValidate);

    if (result.success) {
      setErrors({});
      setVariantErrors({});
      return true;
    } else {
      setErrors(result.fieldErrors);
      setVariantErrors(result.variantErrors);
      return false;
    }
  };

  const buildRequestBody = () => {
    const status = showProduct ? 'ACTIVE' : 'DRAFT';

    let requestVariants;
    if (hasVariants) {
      requestVariants = variants.map((v) => ({
        sku: v.sku,
        barcode: v.barcode || null,
        name: [v.optionValues?.Size, v.optionValues?.['Màu']].filter(Boolean).join(' / ') || v.sku,
        price: 0,
        costPrice: 0,
        optionValues: v.optionValues,
        images: v.images?.length > 0 ? v.images.map((img, i) => ({ url: img.url, isPrimary: false, sortOrder: i })) : [],
      }));
    } else {
      requestVariants = [{
        sku: formData.sku,
        barcode: formData.barcode || null,
        name: formData.name || 'Mặc định',
        price: 0,
        costPrice: 0,
        optionValues: Object.fromEntries(
          Object.entries({ Size: formData.size, 'Màu': formData.color }).filter(([, v]) => v)
        ),
        images: images.map((img, i) => ({
          url: img.url,
          sortOrder: i,
          isPrimary: false,
        })),
      }];
    }

    return {
      name: formData.name,
      sku: formData.sku,
      description: formData.description || null,
      categoryId: formData.categoryId || null,
      brand: formData.brand || null,
      unit: formData.unit || null,
      status,
      weightGrams: weightGrams ? Number(weightGrams) : null,
      lowStockThreshold: lowStockThreshold === '' ? 5 : Number(lowStockThreshold),
      attributes: dimensions ? { dimensions } : {},
      warehouseId: selectedWarehouseId || null,
      channelIds: selectedChannels,
      variants: requestVariants,
      images: images.map((img, i) => ({
        url: img.url,
        sortOrder: i,
        isPrimary: i === 0,
      })),
    };
  };

  const handleSubmit = async () => {
    if (!validate()) {
      toast.error('Vui lòng kiểm tra lại thông tin');
      return;
    }

    setIsSubmitting(true);
    try {
      const body = buildRequestBody();
      await productApi.create(body);
      toast.success('Tạo sản phẩm thành công!');
      navigate(ROUTES.PRODUCTS);
    } catch (error) {
      if (error.response?.status === 409) {
        setErrors((prev) => ({
          ...prev,
          sku: 'SKU đã tồn tại trong hệ thống',
        }));
        toast.error('SKU đã tồn tại');
      } else {
        const msg = error.response?.data?.message || 'Đã xảy ra lỗi khi tạo sản phẩm';
        toast.error(msg);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = () => {
    navigate(ROUTES.PRODUCTS);
  };

  const handleChannelToggle = (channelId) => {
    setSelectedChannels((prev) =>
      prev.includes(channelId)
        ? prev.filter((id) => id !== channelId)
        : [...prev, channelId]
    );
  };

  const handlePriceChange = (field) => {
    if (field === 'price') setPrice('0');
    if (field === 'costPrice') setCostPrice('0');
  };

  const handleShippingChange = (field, value) => {
    if (field === 'weightGrams') setWeightGrams(value);
    if (field === 'dimensions') setDimensions(value);
    if (field === 'lowStockThreshold') setLowStockThreshold(value);
  };

  const handleAddVariant = (variant) => {
    setVariants((prev) => [...prev, variant]);
  };

  const handleRemoveVariant = (index) => {
    setVariants((prev) => prev.filter((_, i) => i !== index));
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={handleCancel}>
          <ArrowLeft className={styles.backIcon} />
          Quay lại
        </button>
        <div className={styles.headerText}>
          <h1 className={styles.headerTitle}>Thêm sản phẩm mới</h1>
          <p className={styles.headerSubtitle}>
            Tạo sản phẩm và đồng bộ lên các kênh bán hàng
          </p>
        </div>
      </div>

      <div className={styles.layout}>
        <div className={styles.mainColumn}>
          <ProductImageUploader images={images} onChange={setImages} />

          <ProductForm
            formData={formData}
            onChange={setFormData}
            categories={categories}
            errors={errors}
            hasVariants={hasVariants}
          />

          <div className={styles.variantToggleCard}>
            <div className={styles.variantToggleInfo}>
              <div className={styles.variantToggleTitle}>Biến thể sản phẩm</div>
              <div className={styles.variantToggleSubtitle}>
                {hasVariants
                  ? 'Sản phẩm có nhiều biến thể (size, màu...)'
                  : 'Sản phẩm không có biến thể. Nhấn để thêm biến thể.'}
              </div>
            </div>
            <button
              type="button"
              className={`${styles.variantToggleBtn} ${hasVariants ? styles.variantToggleBtnActive : styles.variantToggleBtnInactive}`}
              onClick={() => setHasVariants(!hasVariants)}
            >
              {hasVariants ? 'Đã bật biến thể' : 'Tạo biến thể'}
            </button>
          </div>

          {!hasVariants && (
            <ProductPriceStock
              price={price}
              costPrice={costPrice}
              onChange={handlePriceChange}
              errors={errors}
              channels={channels}
              selectedChannels={selectedChannels}
              disablePrice
              disableCostPrice
            />
          )}

          {hasVariants && (
            <ProductVariantForm
              variants={variants}
              onAdd={handleAddVariant}
              onRemove={handleRemoveVariant}
              onChange={setVariants}
              errors={variantErrors}
              globalError={errors.variants}
              channels={channels}
              selectedChannels={selectedChannels}
              disablePrice
              disableCostPrice
            />
          )}

          <ProductShippingInfo
            weightGrams={weightGrams}
            dimensions={dimensions}
            lowStockThreshold={lowStockThreshold}
            onChange={handleShippingChange}
            errors={errors}
          />
        </div>

        <ProductChannelSidebar
          channels={channels}
          selectedChannels={selectedChannels}
          onChannelToggle={handleChannelToggle}
          warehouses={warehouses}
          selectedWarehouseId={selectedWarehouseId}
          onWarehouseChange={setSelectedWarehouseId}
          showProduct={showProduct}
          onStatusToggle={() => setShowProduct(!showProduct)}
          onSubmit={handleSubmit}
          onCancel={handleCancel}
          isSubmitting={isSubmitting}
        />
      </div>
    </div>
  );
};

export default ProductCreatePage;
