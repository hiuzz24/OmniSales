import { useNavigate } from 'react-router-dom';
import useAuth from '../../auth/hooks/useAuth';
import styles from './DashboardPage.module.css';

const ROLE_LABEL = {
  operation: 'Operation',
  sale: 'Sale',
  owner: 'Owner',
};

const DashboardPage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.badge}>{ROLE_LABEL[user?.role] ?? user?.role}</div>

        <div className={styles.icon}>
          <span className="material-symbols-outlined">dashboard</span>
        </div>

        <h1 className={styles.title}>Dashboard</h1>
        <p className={styles.sub}>
          Logged in as <strong>{user?.email ?? user?.username ?? 'User'}</strong>
        </p>

        <div className={styles.infoGrid}>
          <div className={styles.infoItem}>
            <span className={styles.infoLabel}>Role</span>
            <span className={styles.infoValue}>{user?.role}</span>
          </div>
          <div className={styles.infoItem}>
            <span className={styles.infoLabel}>Status</span>
            <span className={`${styles.infoValue} ${styles.active}`}>Active</span>
          </div>
        </div>

        <button className={styles.logoutBtn} onClick={handleLogout}>
          <span className="material-symbols-outlined">logout</span>
          Sign out
        </button>
      </div>
    </div>
  );
};

export default DashboardPage;
