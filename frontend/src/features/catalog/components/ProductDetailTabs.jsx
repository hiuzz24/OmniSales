import styles from './ProductDetailTabs.module.css';

const ProductDetailTabs = ({ activeTab, onChange, variantsCount }) => {
  const tabs = [
    { id: 'overview', label: 'Tổng quan' },
    { id: 'platform', label: 'Platform Mapping' },
    { id: 'images', label: 'Hình ảnh' },
    { id: 'variants', label: `Biến thể (${variantsCount})` },
  ];

  return (
    <div className={styles.tabsContainer}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          className={`${styles.tab} ${activeTab === tab.id ? styles.activeTab : ''}`}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
};

export default ProductDetailTabs;
