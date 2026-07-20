import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../app/router/routes';
import useDebounce from '../../../shared/hooks/useDebounce';
import { Plus, History, FileUp, FileDown, RefreshCcw, Package } from 'lucide-react';
import PageHeader from '../../../shared/components/PageHeader';
import ProductFilterBar from '../components/ProductFilterBar';
import ProductTable from '../components/ProductTable';
import ExportProductsModal from '../components/ExportProductsModal';
import ImportProductsModal from '../components/ImportProductsModal';
import styles from './ProductManagementPage.module.css';

const PLATFORM_LABELS = {
  LAZADA: 'Lazada',
  SHOPIFY: 'Shopify',
  TIKTOK: 'TikTok Shop',
};

const formatCount = (value) => Number(value ?? 0).toLocaleString('vi-VN');

const buildProductSyncMessage = ({ channel, direction, result }) => {
  const channelLabel = `${PLATFORM_LABELS[channel.platform] ?? channel.platform} - ${channel.displayName ?? 'Chưa đặt tên'}`;
  if (direction === 'from-marketplace') {
    return `Đã đồng bộ ${channelLabel}: lấy được ${formatCount(result?.productCount)} sản phẩm và ${formatCount(result?.variantCount)} sản phẩm con từ sàn.`;
  }

  return `Đã đồng bộ ${channelLabel}: đẩy ${formatCount(result?.productCount)} sản phẩm và ${formatCount(result?.pushedVariantCount)} SKU tồn kho lên sàn.`;
};

const ProductManagementPage = () => {
  const [searchInput, setSearchInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [platformFilter, setPlatformFilter] = useState('');
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [tableRefreshKey, setTableRefreshKey] = useState(0);
  const navigate = useNavigate();

  const debouncedKeyword = useDebounce(searchInput, 500);

  const actions = (
    <>
      <button
        className={`${styles.actionBtn} ${styles.secondaryBtn}`}
        onClick={() => navigate(ROUTES.SYNC_HISTORY)}
      >
        <RefreshCcw className={styles.secondaryIcon} />
        Lịch sử đồng bộ
      </button>
      <button
        className={`${styles.actionBtn} ${styles.secondaryBtn}`}
        onClick={() => navigate(ROUTES.PRODUCT_LOGS)}
      >
        <History className={styles.secondaryIcon} />
        Nhật ký hệ thống
      </button>
      <button
        className={`${styles.actionBtn} ${styles.importBtn}`}
        onClick={() => setIsImportModalOpen(true)}
      >
        <FileUp className={styles.importIcon} />
        Nhập Excel
      </button>
      <button
        className={`${styles.actionBtn} ${styles.exportBtn}`}
        onClick={() => setIsExportModalOpen(true)}
      >
        <FileDown className={styles.exportIcon} />
        Xuất Excel
      </button>
      <button className={`${styles.actionBtn} ${styles.primaryBtn}`} onClick={() => navigate(ROUTES.PRODUCT_CREATE)}>
        <Plus className={styles.primaryIcon} />
        Thêm sản phẩm mới
      </button>
    </>
  );

  return (
    <div className={styles.page}>
      <PageHeader
        title="Sản phẩm"
        subtitle="Quản lý kho hàng và các sản phẩm trên hệ thống"
        icon={() => <Package size={20}/>}
        actions={actions}
      />
      <ProductFilterBar
        searchInput={searchInput}
        onSearchChange={setSearchInput}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        platformFilter={platformFilter}
        onPlatformChange={setPlatformFilter}
      />
      <ProductTable
        key={`${tableRefreshKey}-${debouncedKeyword}-${statusFilter}-${platformFilter}`}
        keyword={debouncedKeyword}
        statusFilter={statusFilter}
        platformFilter={platformFilter}
      />
      <ExportProductsModal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
      />
      <ImportProductsModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        onSuccess={() => setTableRefreshKey((k) => k + 1)}
      />
    </div>
  );
};

export default ProductManagementPage;
