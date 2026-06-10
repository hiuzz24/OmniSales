import { Link } from 'react-router-dom';
import { ShoppingBag } from 'lucide-react';
import { ROUTES } from '../../../app/router/routes';
import styles from './Footer.module.css';

const FOOTER_LINKS = [
  { label: 'Tính năng', href: '/home#features' },
  { label: 'Khách hàng', href: '/home#testimonials' },
  { label: 'Về chúng tôi', href: '/about' },
];

const SUPPORT_LINKS = [
  { label: 'Trung tâm trợ giúp', href: '#' },
  { label: 'Tài liệu API', href: '#' },
  { label: 'Chính sách bảo mật', href: '#' },
  { label: 'Điều khoản sử dụng', href: '#' },
];

const Footer = () => {
  return (
    <footer className={styles.footer}>
      <div className={styles.footerInner}>
        <div className={styles.footerTop}>
          <div>
            <Link to="/home" className={styles.brand}>
              <div className={styles.brandIcon}>
                <ShoppingBag className="w-5 h-5" />
              </div>
              <span className={styles.brandName}>OmniSales</span>
            </Link>
            <p className={styles.tagline}>
              Nền tảng quản lý bán hàng đa kênh thông minh — kết nối Shopee, TikTok Shop và
              Lazada trên một nền tảng duy nhất.
            </p>
          </div>

          <div className={styles.footerColumn}>
            <p className={styles.columnTitle}>Liên kết</p>
            {FOOTER_LINKS.map((link) => (
              <Link
                key={link.label}
                to={link.href}
                className={styles.columnLink}
              >
                {link.label}
              </Link>
            ))}
            <Link to={ROUTES.LOGIN} className={styles.columnLink}>
              Đăng nhập
            </Link>
          </div>

          <div className={styles.footerColumn}>
            <p className={styles.columnTitle}>Hỗ trợ</p>
            {SUPPORT_LINKS.map((link) => (
              <a
                key={link.label}
                href={link.href}
                className={styles.columnLink}
              >
                {link.label}
              </a>
            ))}
          </div>
        </div>

        <hr className={styles.divider} />

        <div className={styles.footerBottom}>
          <p className={styles.copyright}>
            &copy; {new Date().getFullYear()} OmniSales. All rights reserved.
          </p>
          <div className={styles.bottomLinks}>
            <a href="#" className={styles.bottomLink}>Chính sách bảo mật</a>
            <a href="#" className={styles.bottomLink}>Điều khoản sử dụng</a>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;
