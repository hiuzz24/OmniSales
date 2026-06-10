import LoginForm from '../components/LoginForm';
import styles from './LoginPage.module.css';

const LoginPage = () => {
  return (
    <div className={styles.page}>
      <header className={styles.topBar}>
        <div className={styles.brandName}>OmniSales</div>
        <nav>
          <button id="help-btn" className={styles.helpButton} type="button">
            Help
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
              <h2 className={styles.brandTitle}>Unified Operations</h2>
              <p className={styles.brandSubtitle}>
                Manage inventory, orders, and customers across all sales channels from a single interface.
              </p>
            </div>
          </aside>
        </div>
      </main>

      <footer className={styles.footer}>
        <div>OmniSales &copy; 2024 OmniSales. All rights reserved.</div>
        <div className={styles.footerLinks}>
          <a href="#" className={styles.footerLink}>Terms</a>
          <a href="#" className={styles.footerLink}>Privacy</a>
          <a href="#" className={styles.footerLink}>Contact</a>
        </div>
      </footer>
    </div>
  );
};

export default LoginPage;
