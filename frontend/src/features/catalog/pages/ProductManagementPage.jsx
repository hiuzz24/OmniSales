import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../app/router/routes';
import useDebounce from '../../../shared/hooks/useDebounce';
import { Plus, History, FileSpreadsheet } from 'lucide-react';
import PageHeader from '../../../shared/components/PageHeader';
import ProductFilterBar from '../components/ProductFilterBar';
import ProductTable from '../components/ProductTable';
import ExportProductsModal from '../components/ExportProductsModal';
import styles from './ProductManagementPage.module.css';

const ProductManagementPage = () => {
  const [searchInput, setSearchInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [platformFilter, setPlatformFilter] = useState('');
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const navigate = useNavigate();

  const debouncedKeyword = useDebounce(searchInput, 500);

  const actions = (
    <>
      <button 
        className={`${styles.actionBtn} ${styles.secondaryBtn}`}
        onClick={() => navigate(ROUTES.PRODUCT_LOGS)}
      >
        <History className={styles.secondaryIcon} />
        Product Logs
      </button>
      <button
        className={`${styles.actionBtn} ${styles.exportBtn}`}
        onClick={() => setIsExportModalOpen(true)}
      >
        <FileSpreadsheet className={styles.exportIcon} />
        Export Excel
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
      <ProductTable keyword={debouncedKeyword} statusFilter={statusFilter} platformFilter={platformFilter} />
      <ExportProductsModal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
      />
    </div>
  );
};

export default ProductManagementPage;
