import { Bell, User } from 'lucide-react';
import styles from './Topbar.module.css';
import useAuth from '../../../features/auth/hooks/useAuth';

const Topbar = () => {
  const { user } = useAuth();

  const roleMap = {
    'OWNER': 'Chủ cửa hàng',
    'OPERATIONS': 'Quản lý',
    'STAFFS': 'Nhân viên',
    'SYSTEM_ADMIN': 'Quản trị viên'
  };

  return (
    <header className={styles.topbar}>
      <div className={styles.container}>
        <div className={styles.titleContainer}>
          <h2 className={styles.title}>
            Hệ thống quản lý bán hàng đa kênh
          </h2>
        </div>
        
        <div className={styles.actions}>
          <button className={styles.notificationBtn}>
            <span className={styles.notificationBadge} />
            <Bell className={styles.icon} />
          </button>
          
          <div className={styles.profileSection}>
            <div className={styles.userInfo}>
              <span className={styles.userName}>{user?.fullName}</span>
              <span className={styles.userRole}>{roleMap[user?.role] || user?.role}</span>
            </div>
            <button className={styles.profileBtn}>
              <div className={styles.avatarWrapper}>
                <div className={styles.avatarInner}>
                  {user?.avatarUrl ? (
                    <img src={user.avatarUrl} alt="Avatar" style={{width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover'}} />
                  ) : (
                    <User className={styles.avatarIcon} />
                  )}
                </div>
              </div>
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};

export default Topbar;
