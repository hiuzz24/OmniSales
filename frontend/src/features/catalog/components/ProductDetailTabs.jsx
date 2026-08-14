import { Boxes, Image, LayoutDashboard, PanelsTopLeft } from 'lucide-react';
import styles from './ProductDetailTabs.module.css';

/** Điều hướng giữa tổng quan, biến thể, hình ảnh và platform của sản phẩm. */
const ProductDetailTabs = ({ activeTab, onChange, variantsCount }) => {
  const tabs = [
    { id: 'overview', label: 'Tổng quan', icon: LayoutDashboard },
    { id: 'platform', label: 'Platform Mapping', icon: PanelsTopLeft },
    { id: 'images', label: 'Hình ảnh', icon: Image },
    { id: 'variants', label: `Biến thể (${variantsCount})`, icon: Boxes },
  ];

  return (
    <div className={styles.tabsContainer}>
      {tabs.map((tab) => {
        const Icon = tab.icon;
        return (
          <button
            key={tab.id}
            type="button"
            className={`${styles.tab} ${activeTab === tab.id ? styles.activeTab : ''}`}
            onClick={() => onChange(tab.id)}
          >
            <Icon className={styles.tabIcon} aria-hidden="true" />
            {tab.label}
          </button>
        );
      })}
    </div>
  );
};

export default ProductDetailTabs;
