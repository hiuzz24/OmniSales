import { Outlet } from 'react-router-dom';
import Sidebar from '../../shared/components/layout/Sidebar';
import Topbar from '../../shared/components/layout/Topbar';
import styles from './DashboardLayout.module.css';

const DashboardLayout = () => {
  return (
    <div className={styles.dashboardLayout}>
      <Sidebar />
      <Topbar />
      <div className={styles.contentWrapper}>
        <main className={styles.mainContent}>
          <div className={styles.backgroundDecoration} />
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
