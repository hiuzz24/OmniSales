import { Link, useLocation } from 'react-router-dom';
import { 
  LayoutDashboard, 
  Package, 
  Warehouse, 
  Store, 
  ShoppingCart, 
  Share2, 
  BarChart2, 
  Users, 
  Settings,
  Menu
} from 'lucide-react';
import { ROUTES } from '../../../app/router/routes';
import styles from './Sidebar.module.css';

const MENU_ITEMS = [
  { path: ROUTES.DASHBOARD, icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/products', icon: Package, label: 'Sản phẩm' },
  { path: '/inventory', icon: Warehouse, label: 'Kho hàng' },
  { path: '/pos', icon: Store, label: 'Bán hàng (POS)' },
  { path: '/orders', icon: ShoppingCart, label: 'Đơn hàng' },
  { path: '/channels', icon: Share2, label: 'Kênh bán hàng' },
  { path: '/analytics', icon: BarChart2, label: 'Phân tích' },
  { path: '/staff', icon: Users, label: 'Nhân sự' },
  { path: '/settings', icon: Settings, label: 'Cài đặt' },
];

const Sidebar = () => {
  const location = useLocation();

  return (
    <aside className={styles.sidebar}>
      <div className={styles.header}>
        <div className={styles.headerInner}>
          <h1 className={styles.brandName}>OmniSales</h1>
          <Menu className={styles.menuIcon} size={24} />
        </div>
      </div>
      
      <div className={styles.navContainer}>
        <nav className={styles.nav}>
          {MENU_ITEMS.map((item) => {
            const isActive = location.pathname.startsWith(item.path);
            const Icon = item.icon;
            
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`${styles.navItem} ${isActive ? styles.navItemActive : ''}`}
              >
                {isActive && (
                  <div className={styles.activeIndicator} />
                )}
                <Icon className={styles.navIcon} />
                <span className={styles.navLabel}>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </div>
    </aside>
  );
};

export default Sidebar;
