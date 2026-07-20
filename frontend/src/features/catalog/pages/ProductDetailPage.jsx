import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
  const [insights, setInsights] = useState(null);
  const [insightsLoading, setInsightsLoading] = useState(true);
  const [insightsError, setInsightsError] = useState('');
  const [isSyncing, setIsSyncing] = useState(false);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    fetchProduct();
    fetchInsights();
  }, [id]);

  useEffect(() => {
    if (activeTab === 'inventory') {
      fetchInsights();
    }
  }, [activeTab]);

  const fetchProduct = async () => {
    try {
      setLoading(true);
      const [productResult, channelResult] = await Promise.allSettled([
        productApi.getById(id),
        channelApi.getAll(),
      ]);

      if (productResult.status === 'rejected') {
        throw productResult.reason;
      }

      const prodRes = productResult.value;
      const responseData = prodRes.data?.data || prodRes.data || prodRes;
      setProduct(responseData);
      if (channelResult.status === 'fulfilled') {
        const chanRes = channelResult.value;
        setChannels(chanRes.data?.data || chanRes.data || chanRes);
      } else {
        setChannels([]);
      }
    } catch (error) {
      toast.error('Không thể tải thông tin sản phẩm');
      navigate(ROUTES.PRODUCTS);
    } finally {
      setLoading(false);
    }
  };

  const fetchInsights = async () => {
    try {
      setInsightsLoading(true);
      setInsightsError('');
      const response = await productApi.getInsights(id);
      setInsights(response.data?.data || response.data || response);
    } catch (error) {
      setInsightsError('Không thể tải dữ liệu thống kê sản phẩm.');
    } finally {
      setInsightsLoading(false);
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

  const handleSync = async () => {
    try {
      setIsSyncing(true);
      const res = await productApi.sync(id);
      const data = res.data?.data || res.data || res;

      if (data && data.failedCount > 0) {
        const failedChannels = (data.details || [])
          .filter((detail) => !detail.success)
          .map((detail) => detail.channelName || detail.platform)
          .filter(Boolean);
        const failedLabel = failedChannels.length > 0
          ? ` Kênh lỗi: ${failedChannels.join(', ')}.`
          : '';

        toast.warning(
          `Đã đồng bộ thành công ${data.successCount || 0}/${data.totalChannels || 0} kênh.${failedLabel}`,
        );
      } else {
        toast.success('Đồng bộ thành công lên tất cả các kênh!');
      }

      await Promise.all([fetchProduct(), fetchInsights()]);
    } catch (error) {
      toast.error('Đồng bộ thất bại. Vui lòng thử lại.');
    } finally {
      setIsSyncing(false);
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
        onSync={handleSync}
        isSyncing={isSyncing}
      />

      <div className={styles.mainContent}>
        <ProductStatsGrid
          insights={insights}
          loading={insightsLoading}
          error={insightsError}
          onRetry={fetchInsights}
        />

        <div className={styles.tabsSection}>
          <ProductDetailTabs
            activeTab={activeTab}
            onChange={setActiveTab}
            variantsCount={product.variants?.length || 0}
          />

          <div className={styles.tabContent}>
            {activeTab === 'overview' && <TabOverview product={product} />}
            {activeTab === 'inventory' && (
              <TabInventory
                productId={product.id}
                insights={insights}
                insightsLoading={insightsLoading}
                insightsError={insightsError}
                onRetryInsights={fetchInsights}
              />
            )}
            {activeTab === 'platform' && <TabPlatform product={product} onRefresh={fetchProduct} />}
            {activeTab === 'images' && <TabImages product={product} />}
            {activeTab === 'variants' && <TabVariants product={product} />}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductDetailPage;
