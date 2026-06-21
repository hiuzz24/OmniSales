import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { Loader2 } from 'lucide-react';
import productApi from '../../../api/productApi';
import channelApi from '../../../api/channelApi';
import { ROUTES } from '../../../app/router/routes';
import styles from './ProductDetailPage.module.css';
import ProductDetailHeader from '../components/ProductDetailHeader';
import ProductStatsGrid from '../components/ProductStatsGrid';
import ProductDetailTabs from '../components/ProductDetailTabs';
import TabOverview from '../components/TabOverview';
import TabInventory from '../components/TabInventory';
import TabPlatform from '../components/TabPlatform';
import TabImages from '../components/TabImages';
import TabVariants from '../components/TabVariants';

const ProductDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [channels, setChannels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    fetchProduct();
  }, [id]);

  const fetchProduct = async () => {
    try {
      setLoading(true);
      const [prodRes, chanRes] = await Promise.all([
        productApi.getById(id),
        channelApi.getAll()
      ]);
      const responseData = prodRes.data?.data || prodRes.data || prodRes;
      setProduct(responseData);
      setChannels(chanRes.data?.data || chanRes.data || chanRes);
    } catch (error) {
      toast.error('Không thể tải thông tin sản phẩm');
      navigate(ROUTES.PRODUCTS);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    try {
      setLoading(true);
      await productApi.delete(id);
      toast.success('Xóa sản phẩm thành công');
      navigate(ROUTES.PRODUCTS);
    } catch (error) {
      toast.error('Có lỗi xảy ra khi xóa sản phẩm');
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className={styles.loadingContainer}>
        <Loader2 className={`${styles.spinner} ${styles.spin}`} />
        <p>Đang tải thông tin sản phẩm...</p>
      </div>
    );
  }

  if (!product) return null;

  return (
    <div className={styles.page}>
      <ProductDetailHeader
        product={product}
        onBack={() => navigate(ROUTES.PRODUCTS)}
        onDelete={handleDelete}
        onEdit={() => navigate(ROUTES.PRODUCT_EDIT.replace(':id', product.id))}
      />

      <div className={styles.mainContent}>
        <ProductStatsGrid product={product} />

        <div className={styles.tabsSection}>
          <ProductDetailTabs
            activeTab={activeTab}
            onChange={setActiveTab}
            variantsCount={product.variants?.length || 0}
          />

          <div className={styles.tabContent}>
            {activeTab === 'overview' && <TabOverview product={product} />}
            {activeTab === 'inventory' && <TabInventory product={product} />}
            {activeTab === 'platform' && <TabPlatform product={product} channels={channels} />}
            {activeTab === 'images' && <TabImages product={product} />}
            {activeTab === 'variants' && <TabVariants product={product} />}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductDetailPage;

