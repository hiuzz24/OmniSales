import { useNavigate } from 'react-router-dom';
import useAuth from '../../auth/hooks/useAuth';
import styles from './AdminPage.module.css';

const AdminPage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={`${styles.badge} ${styles.adminBadge}`}>System Admin</div>

        <div className={`${styles.icon} ${styles.adminIcon}`}>
          <span className="material-symbols-outlined">admin_panel_settings</span>
        </div>

        <h1 className={styles.title}>Admin Panel</h1>
        <p className={styles.sub}>
          Logged in as <strong>{user?.email ?? user?.username ?? 'Admin'}</strong>
        </p>

        <div className={styles.infoGrid}>
          <div className={styles.infoItem}>
            <span className={styles.infoLabel}>Role</span>
            <span className={styles.infoValue}>{user?.role}</span>
          </div>
          <div className={styles.infoItem}>
            <span className={styles.infoLabel}>Access</span>
            <span className={`${styles.infoValue} ${styles.active}`}>Full</span>
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

export default AdminPage;
