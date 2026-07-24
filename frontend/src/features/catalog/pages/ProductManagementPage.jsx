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

const ProductManagementPage = () => {
  const [searchInput, setSearchInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [platformFilters, setPlatformFilters] = useState([]);
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [tableRefreshKey, setTableRefreshKey] = useState(0);
  const navigate = useNavigate();

  const debouncedKeyword = useDebounce(searchInput, 500);

  const actions = (
    <>
      <button
        type="button"
        aria-label="Lịch sử đồng bộ"
        title="Lịch sử đồng bộ"
        className={`${styles.actionBtn} ${styles.secondaryBtn}`}
        onClick={() => navigate(ROUTES.SYNC_HISTORY)}
      >
        <RefreshCcw className={styles.secondaryIcon} />
        Lịch sử đồng bộ
      </button>
      <button
        type="button"
        aria-label="Nhật ký hệ thống"
        title="Nhật ký hệ thống"
        className={`${styles.actionBtn} ${styles.secondaryBtn}`}
        onClick={() => navigate(ROUTES.PRODUCT_LOGS)}
      >
        <History className={styles.secondaryIcon} />
        Nhật ký hệ thống
      </button>
      <button
        type="button"
        aria-label="Nhập Excel"
        title="Nhập Excel"
        className={`${styles.actionBtn} ${styles.importBtn}`}
        onClick={() => setIsImportModalOpen(true)}
      >
        <FileUp className={styles.importIcon} />
        Nhập Excel
      </button>
      <button
        type="button"
        aria-label="Xuất Excel"
        title="Xuất Excel"
        className={`${styles.actionBtn} ${styles.exportBtn}`}
        onClick={() => setIsExportModalOpen(true)}
      >
        <FileDown className={styles.exportIcon} />
        Xuất Excel
      </button>
      <button
        type="button"
        aria-label="Thêm sản phẩm mới"
        title="Thêm sản phẩm mới"
        className={`${styles.actionBtn} ${styles.primaryBtn}`}
        onClick={() => navigate(ROUTES.PRODUCT_CREATE)}
      >
        <Plus className={styles.primaryIcon} />
        Thêm sản phẩm mới
      </button>
    </>
  );

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <PageHeader
          title="Sản phẩm"
          subtitle="Quản lý kho hàng và các sản phẩm trên hệ thống"
          icon={() => <Package size={20} />}
          actions={actions}
        />
      </div>
      <ProductFilterBar
        searchInput={searchInput}
        onSearchChange={setSearchInput}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        platformFilters={platformFilters}
        onPlatformChange={setPlatformFilters}
      />
      <ProductTable
        key={`${tableRefreshKey}-${debouncedKeyword}-${statusFilter}-${platformFilters.join(',')}`}
        keyword={debouncedKeyword}
        statusFilter={statusFilter}
        platformFilters={platformFilters}
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
