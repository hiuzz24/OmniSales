import LoginForm from '../components/LoginForm';
import styles from './LoginPage.module.css';

const LoginPage = () => {
  return (
    <div className={styles.page}>
      <header className={styles.topBar}>
        <div className={styles.brandName}>OSMS</div>
        <nav>
          <button id="help-btn" className={styles.helpButton} type="button">
            Trợ giúp
          </button>
        </nav>
      </header>

      <main className={styles.mainContent}>
        <div className={styles.contentWrapper}>
          <section className={styles.formPanel} aria-label="Đăng nhập">
            <div className={styles.formContainer}>
              <LoginForm />
            </div>
          </section>

          <aside className={styles.brandPanel} aria-hidden="true">
            <div className={`${styles.floatingCard} ${styles.floatingCard1}`}>
              <div className={styles.floatingCardLabel}>Doanh thu tháng này</div>
              <div className={styles.floatingCardValue}>₫2.4 tỷ</div>
              <div className={styles.floatingCardTrend}>↑ +18.3% so với tháng trước</div>
            </div>

            <div className={`${styles.floatingCard} ${styles.floatingCard2}`}>
              <div className={styles.floatingCardLabel}>Đơn hàng mới</div>
              <div className={styles.floatingCardValue}>1,284 đơn</div>
              <div className={styles.floatingCardTrend}>↑ +7.1% hôm nay</div>
            </div>

            <div className={styles.brandContent}>
              <div className={styles.brandLogoBox}>
                <span className={`material-symbols-outlined ${styles.brandLogoIcon}`}>
                  storefront
                </span>
              </div>

              <h2 className={styles.brandTagline}>
                Quản lý thương mại<br />
                <span className={styles.brandTaglineAccent}>thông minh hơn</span>
              </h2>

              <p className={styles.brandDescription}>
                Nền tảng quản lý đa kênh toàn diện giúp doanh nghiệp bán lẻ
                tăng trưởng bền vững và hiệu quả hơn mỗi ngày.
              </p>

              <ul className={styles.featureList}>
                <li className={styles.featureItem}>
                  <div className={styles.featureIconBox}>
                    <span className="material-symbols-outlined">bar_chart</span>
                  </div>
                  Báo cáo thời gian thực &amp; phân tích chuyên sâu
                </li>
                <li className={styles.featureItem}>
                  <div className={styles.featureIconBox}>
                    <span className="material-symbols-outlined">sync_alt</span>
                  </div>
                  Đồng bộ đa kênh: Shopee, Lazada, TikTok Shop
                </li>
                <li className={styles.featureItem}>
                  <div className={styles.featureIconBox}>
                    <span className="material-symbols-outlined">inventory_2</span>
                  </div>
                  Quản lý kho hàng &amp; đơn hàng tự động hoá
                </li>
                <li className={styles.featureItem}>
                  <div className={styles.featureIconBox}>
                    <span className="material-symbols-outlined">psychology</span>
                  </div>
                  AI hỗ trợ định giá &amp; dự báo tồn kho
                </li>
              </ul>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
};

export default LoginPage;
