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
  categoryVersion: sync.platformConfig?.categoryVersion || (sync.platform === 'TIKTOK' ? 'v1' : null),
  brandId: sync.platformConfig?.brandId || '',
  brandName: sync.platformConfig?.brandName || '',
  sizeChartImageUrl: sync.platformConfig?.sizeChartImageUrl || '',
  attributes: sync.platformConfig?.attributes || {},
  variantAttributeBindings: sync.platformConfig?.variantAttributeBindings || {},
});

const TabPlatform = ({ product, onRefresh }) => {
  const mappings = useMemo(() => product?.channelSyncs || [], [product?.channelSyncs]);
  const [syncing, setSyncing] = useState({});
  const configMethods = useForm({ defaultValues: { channelConfigs: {} } });
  const mappingConfigs = useMemo(() => mappings.reduce((result, mapping) => ({
    ...result,
    [mapping.channelId]: configFromSync(mapping),
  }), {}), [mappings]);

  useEffect(() => {
    configMethods.reset({
      channelConfigs: mappingConfigs,
    });
  }, [configMethods, mappingConfigs]);

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
      toast.success(`Đã đồng bộ ${mapping.channelName || mapping.platform}`);
      await onRefresh?.();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Đồng bộ channel thất bại');
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
        <div className={styles.titleRow}><h3 className={styles.cardTitle}>Cấu hình platform</h3></div>
        <p className={styles.cardSubtitle}>Mỗi channel có category và trạng thái đồng bộ độc lập.</p>
      </div>
      <div className={styles.mappingList}>
        {mappings.map((mapping) => (
          <div className={styles.mappingRow} key={mapping.channelId}>
            <div>
              <strong>{mapping.channelName || mapping.platform}</strong>
              <div className={styles.textGray}>{mapping.platform}</div>
            </div>
            <div>
              <span className={mapping.readyToSync ? styles.badgeSuccess : styles.badgeWarning}>
                {mapping.readyToSync ? 'Ready to sync' : mapping.configurationError || 'Thiếu cấu hình'}
              </span>
              <div className={styles.textGray}>{statusLabel[mapping.syncStatus] || mapping.syncStatus}</div>
            </div>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => syncChannel(mapping)}
              disabled={!mapping.readyToSync || syncing[mapping.channelId]}
            >
              <RefreshCw size={15} className={syncing[mapping.channelId] ? styles.spin : ''} />
              Đồng bộ
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
