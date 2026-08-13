import { NavLink } from 'react-router-dom';
import { ROUTES } from '../../../app/router/routes';
import styles from './ReportTabs.module.css';

const ReportTabs = () => (
  <nav className={styles.tabs} aria-label="Các loại báo cáo">
    <NavLink to={ROUTES.REPORTS} end className={({ isActive }) => isActive ? styles.active : ''}>
      Đơn hàng theo kênh
    </NavLink>
    <NavLink to={ROUTES.REPORT_PRODUCTS} className={({ isActive }) => isActive ? styles.active : ''}>
      Sản phẩm
    </NavLink>
    <NavLink to={ROUTES.REPORT_RETURNS} className={({ isActive }) => isActive ? styles.active : ''}>
      Trả hàng
    </NavLink>
  </nav>
);

export default ReportTabs;
