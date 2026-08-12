import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import { Loader2 } from 'lucide-react';
import productApi from '../../../api/productApi';
import { ROUTES } from '../../../app/router/routes';
import styles from './ProductDetailPage.module.css';
import ProductDetailHeader from '../components/ProductDetailHeader';
import ProductDetailTabs from '../components/ProductDetailTabs';
import TabOverview from '../components/TabOverview';
import TabPlatform from '../components/TabPlatform';
import TabImages from '../components/TabImages';
import TabVariants from '../components/TabVariants';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';

const ProductDetailPage = () => {
  const { user } = useAuth();
  const canManageProducts = user?.role === ROLES.OWNER || user?.role === ROLES.SALES;
  const canSyncProducts = user?.role === ROLES.OWNER;
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isSyncing, setIsSyncing] = useState(false);
  const [activeTab, setActiveTab] = useState('overview');

  const channelMappings = product?.channelSyncs || [];
  const blockedMappings = channelMappings.filter((mapping) => !mapping.readyToSync);
  const syncAllDisabled = channelMappings.length === 0 || blockedMappings.length > 0;
  const syncAllDisabledReason = channelMappings.length === 0
    ? 'Sản phẩm chưa liên kết kênh bán hàng.'
    : blockedMappings.length > 0
      ? `Chưa thể đồng bộ tất cả: ${blockedMappings.map((mapping) => (
        `${mapping.channelName || mapping.platform}: ${mapping.configurationError || 'thiếu cấu hình bắt buộc'}`
      )).join('; ')}`
      : '';

  useEffect(() => {
    fetchProduct();
  }, [id]);

  const fetchProduct = async () => {
    try {
      setLoading(true);
      const prodRes = await productApi.getById(id);
      const responseData = prodRes.data?.data || prodRes.data || prodRes;
      setProduct(responseData);
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

  const handleSync = async () => {
    if (syncAllDisabled) {
      toast.warning(syncAllDisabledReason);
      return;
    }
    try {
      setIsSyncing(true);
      await productApi.sync(id);
      toast.success('Đã đưa yêu cầu đồng bộ vào hàng đợi.');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể đưa yêu cầu đồng bộ vào hàng đợi.');
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
        syncAllDisabled={syncAllDisabled}
        syncAllDisabledReason={syncAllDisabledReason}
        canManage={canManageProducts}
        canSync={canSyncProducts}
      />

      <div className={styles.mainContent}>
        <div className={styles.tabsSection}>
          <ProductDetailTabs
            activeTab={activeTab}
            onChange={setActiveTab}
            variantsCount={product.variants?.length || 0}
          />

          <div className={styles.tabContent}>
            {activeTab === 'overview' && <TabOverview product={product} />}
            {activeTab === 'platform' && (
              <TabPlatform
                product={product}
                onRefresh={fetchProduct}
                canConfigure={canManageProducts}
                canSync={canSyncProducts}
              />
            )}
            {activeTab === 'images' && <TabImages product={product} />}
            {activeTab === 'variants' && <TabVariants product={product} />}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProductDetailPage;
