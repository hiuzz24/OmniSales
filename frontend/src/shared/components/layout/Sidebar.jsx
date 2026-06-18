import { useState } from 'react';
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
  Menu,
  ChevronDown,
  ChevronUp,
  LayoutGrid,
  ClipboardList,
  ClipboardMinus,
  ArrowLeftRight,
  ClipboardCheck,
} from 'lucide-react';
import { ROUTES } from '../../../app/router/routes';
import styles from './Sidebar.module.css';

const MENU_ITEMS = [
  { path: ROUTES.DASHBOARD, icon: LayoutDashboard, label: 'Dashboard' },
  { path: '/products', icon: Package, label: 'Sản phẩm' },
  {
    key: 'inventory',
    icon: Warehouse,
    label: 'Kho hàng',
    basePath: '/inventory',
    children: [
      { path: ROUTES.INVENTORY,          icon: LayoutGrid,       label: 'Tổng quan kho' },
      { path: ROUTES.INVENTORY_RECEIPT,  icon: ClipboardList,    label: 'Phiếu nhập kho' },
      { path: ROUTES.INVENTORY_ISSUE,    icon: ClipboardMinus,   label: 'Phiếu xuất kho' },
      { path: ROUTES.STOCK_TRANSFER,     icon: ArrowLeftRight,   label: 'Phiếu chuyển kho' },
      { path: ROUTES.STOCKTAKE,          icon: ClipboardCheck,   label: 'Phiếu kiểm kho' },
    ],
  },
  { path: '/pos', icon: Store, label: 'Bán hàng (POS)' },
  { path: '/orders', icon: ShoppingCart, label: 'Đơn hàng' },
  { path: '/channels', icon: Share2, label: 'Kênh bán hàng' },
  { path: '/analytics', icon: BarChart2, label: 'Phân tích' },
  { path: '/staff', icon: Users, label: 'Nhân sự' },
  { path: '/settings', icon: Settings, label: 'Cài đặt' },
];

const Sidebar = () => {
  const location = useLocation();
  const isInventorySection = location.pathname.startsWith('/inventory');
  const [openMenus, setOpenMenus] = useState(() =>
    isInventorySection ? { inventory: true } : {}
  );

  const toggleMenu = (key) => {
    setOpenMenus((prev) => ({ ...prev, [key]: !prev[key] }));
  };

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
            // ---- Group item with children ----
            if (item.children) {
              const isGroupActive = location.pathname.startsWith(item.basePath);
              const isOpen = openMenus[item.key] ?? isGroupActive;
              const Icon = item.icon;
              const Toggle = isOpen ? ChevronUp : ChevronDown;

              return (
                <div key={item.key} className={styles.menuGroup}>
                  {/* Parent row */}
                  <button
                    className={`${styles.navItem} ${styles.navGroupBtn} ${isGroupActive ? styles.navItemActive : ''}`}
                    onClick={() => toggleMenu(item.key)}
                    aria-expanded={isOpen}
                  >
                    {isGroupActive && <div className={styles.activeIndicator} />}
                    <Icon className={styles.navIcon} />
                    <span className={styles.navLabel}>{item.label}</span>
                    <Toggle className={styles.chevron} size={16} />
                  </button>

                  {/* Children */}
                  <div className={`${styles.subMenu} ${isOpen ? styles.subMenuOpen : ''}`}>
                    {item.children.map((child) => {
                      const isChildActive = location.pathname === child.path;
                      const ChildIcon = child.icon;
                      return (
                        <Link
                          key={child.path}
                          to={child.path}
                          className={`${styles.subNavItem} ${isChildActive ? styles.subNavItemActive : ''}`}
                        >
                          <ChildIcon className={styles.subNavIcon} size={18} />
                          <span className={styles.navLabel}>{child.label}</span>
                        </Link>
                      );
                    })}
                  </div>
                </div>
              );
            }

            // ---- Regular item ----
            const isActive = location.pathname.startsWith(item.path);
            const Icon = item.icon;

            return (
              <Link
                key={item.path}
                to={item.path}
                className={`${styles.navItem} ${isActive ? styles.navItemActive : ''}`}
              >
                {isActive && <div className={styles.activeIndicator} />}
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
