import { Link } from 'react-router-dom';
import LoginForm from '../components/LoginForm';
import styles from './LoginPage.module.css';

const LoginPage = () => {
  return (
    <div className={styles.page}>
      <header className={styles.topBar}>
        <Link to="/home" className={styles.brandNameLink}>OSMS</Link>
        <nav>
          <button id="help-btn" className={styles.helpButton} type="button">
            Trợ giúp
          </button>
        </nav>
      </header>

      <main className={styles.mainContent}>
        <div className={styles.contentWrapper}>
          <section className={styles.formPanel} aria-label="Login">
            <div className={styles.formContainer}>
              <LoginForm />
            </div>
          </section>

          <aside className={styles.brandPanel} aria-hidden="true">
            <img 
              src="/login_hero_illustration.png" 
              alt="Unified Operations 3D Illustration" 
              className={styles.brandImage} 
            />
            <div className={styles.brandTextContainer}>
              <h2 className={styles.brandTitle}>Quản lý hợp nhất</h2>
              <p className={styles.brandSubtitle}>
                Quản lý hàng tồn kho, đơn hàng và khách hàng trên tất cả các kênh bán hàng từ một giao diện duy nhất.
              </p>
            </div>
          </aside>
        </div>
      </main>

      <footer className={styles.footer}>
        <div>OmniSales &copy; 2026 OmniSales. Đã đăng ký bản quyền.</div>
        <div className={styles.footerLinks}>
          <a href="#" className={styles.footerLink}>Điều khoản</a>
          <a href="#" className={styles.footerLink}>Bảo mật</a>
          <a href="#" className={styles.footerLink}>Liên hệ</a>
        </div>
      </footer>
    </div>
  );
};

export default LoginPage;
