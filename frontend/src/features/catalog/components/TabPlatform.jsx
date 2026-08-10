import { useEffect, useMemo, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { RefreshCw } from 'lucide-react';
import { toast } from 'react-toastify';
import productApi from '../../../api/productApi';
import PlatformConfigSection from './PlatformConfigSection';
import styles from './TabPlatform.module.css';

const statusLabel = {
  SYNCED: 'Đồng bộ thành công',
  PENDING: 'Chờ đồng bộ',
  FAILED: 'Lỗi đồng bộ',
  OUT_OF_SYNC: 'Cần đồng bộ',
};

const configFromSync = (sync) => ({
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

const TabPlatform = ({ product, onRefresh }) => {
  const mappings = useMemo(() => product?.channelSyncs || [], [product?.channelSyncs]);
  const [syncing, setSyncing] = useState({});
  const configMethods = useForm({
    defaultValues: {
      name: '',
      description: '',
      images: [],
      sku: '',
      variants: [],
      hasVariants: false,
      channelConfigs: {},
    },
  });
  const mappingConfigs = useMemo(() => mappings.reduce((result, mapping) => ({
    ...result,
    [mapping.channelId]: configFromSync(mapping),
  }), {}), [mappings]);

  useEffect(() => {
    const variants = product?.variants || [];
    const hasVariants = variants.length > 1
      || (variants.length === 1 && Object.keys(variants[0].optionValues || {}).length > 0);
    configMethods.reset({
      name: product?.name || '',
      description: product?.description || '',
      images: product?.images || [],
      sku: product?.sku || '',
      variants,
      hasVariants,
      channelConfigs: mappingConfigs,
    });
  }, [configMethods, mappingConfigs, product?.name, product?.description, product?.images, product?.sku, product?.variants]);

  const platformChannels = useMemo(() => mappings.map((mapping) => ({
    ...mapping,
    id: mapping.channelId,
    displayName: mapping.channelName,
  })), [mappings]);

  const saveConfig = async (channel, config) => {
    try {
      await productApi.updateChannelConfig(product.id, channel.channelId, config);
      toast.success('Đã lưu cấu hình platform');
      await onRefresh?.();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể lưu cấu hình platform');
    }
  };

  const syncChannel = async (mapping) => {
    if (!mapping.readyToSync) {
      toast.error(mapping.configurationError || 'Hoàn tất cấu hình trước khi đồng bộ');
      return;
    }
    try {
      setSyncing((previous) => ({ ...previous, [mapping.channelId]: true }));
      await productApi.syncChannel(product.id, mapping.channelId);
      toast.success(`Đã đưa yêu cầu đồng bộ ${mapping.channelName || mapping.platform} vào hàng đợi.`);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể đưa yêu cầu đồng bộ vào hàng đợi');
    } finally {
      setSyncing((previous) => ({ ...previous, [mapping.channelId]: false }));
    }
  };

  if (mappings.length === 0) {
    return <div className={styles.card}><p className={styles.emptyCell}>Sản phẩm chưa liên kết kênh bán hàng.</p></div>;
  }

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.titleRow}>
          <span className={styles.sectionIcon}><RefreshCw aria-hidden="true" /></span>
          <h3 className={styles.cardTitle}>Trạng thái đồng bộ</h3>
        </div>
        <p className={styles.cardSubtitle}>Kiểm tra trạng thái kết nối và đồng bộ của sản phẩm trên các sàn.</p>
      </div>
      <div className={styles.mappingList}>
        {mappings.map((mapping) => (
          <div className={styles.mappingRow} key={mapping.channelId}>
            <div className={styles.channelInfo}>
              <strong className={styles.channelName}>{mapping.channelName || mapping.platform}</strong>
              <div className={styles.channelType}>
                {mapping.platform}
              </div>
            </div>
            <div className={styles.statusInfo}>
              <span className={mapping.readyToSync ? styles.badgeSuccess : styles.badgeWarning}>
                {mapping.readyToSync ? 'Sẵn sàng đồng bộ' : mapping.configurationError || 'Thiếu cấu hình'}
              </span>
              <div className={styles.syncStatus}>{statusLabel[mapping.syncStatus] || mapping.syncStatus}</div>
            </div>
            <button
              type="button"
              className={styles.syncButton}
              onClick={() => syncChannel(mapping)}
              disabled={!mapping.readyToSync || syncing[mapping.channelId]}
            >
              <RefreshCw size={16} className={syncing[mapping.channelId] ? styles.spin : ''} />
              Đồng bộ ngay
            </button>
          </div>
        ))}
      </div>
      <FormProvider {...configMethods}>
        <PlatformConfigSection channels={platformChannels} onSave={saveConfig} productId={product.id} />
      </FormProvider>
    </div>
  );
};

export default TabPlatform;
