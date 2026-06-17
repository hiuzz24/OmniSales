import { LinkIcon } from 'lucide-react';
import styles from './TabPlatform.module.css';

const TabPlatform = ({ product }) => {
  // Mock data for Platform Mapping
  const mockPlatforms = [
    { id: 1, name: 'Shopee', platformSku: `SP-${product.sku}`, status: 'Đã đồng bộ', price: '150.000 đ', stock: 98, lastSync: '10:30 03/06/2026' },
    { id: 2, name: 'TikTok Shop', platformSku: `TT-${product.sku}`, status: 'Đã đồng bộ', price: '145.000 đ', stock: 87, lastSync: '10:25 03/06/2026' },
    { id: 3, name: 'Lazada', platformSku: `LZ-${product.sku}`, status: 'Đang chờ', price: '155.000 đ', stock: 60, lastSync: '09:15 03/06/2026' },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div className={styles.titleRow}>
          <LinkIcon className={styles.titleIcon} />
          <h3 className={styles.cardTitle}>Platform Mapping <span className={styles.mockNote}>(Mock)</span></h3>
        </div>
        <p className={styles.cardSubtitle}>Thông tin kết nối và đồng bộ với các nền tảng bán hàng</p>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Nền tảng</th>
              <th>Platform SKU</th>
              <th>Trạng thái</th>
              <th>Giá bán</th>
              <th>Tồn kho</th>
              <th>Lần đồng bộ cuối</th>
            </tr>
          </thead>
          <tbody>
            {mockPlatforms.map((item) => (
              <tr key={item.id}>
                <td className={styles.fw500}>{item.name}</td>
                <td>{item.platformSku}</td>
                <td>
                  <span className={item.status === 'Đã đồng bộ' ? styles.badgeSuccess : styles.badgeWarning}>
                    {item.status}
                  </span>
                </td>
                <td>{item.price}</td>
                <td>{item.stock}</td>
                <td className={styles.textGray}>{item.lastSync}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default TabPlatform;
