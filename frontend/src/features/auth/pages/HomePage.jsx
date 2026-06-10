import { Link } from 'react-router-dom';
import {
  ShoppingBag,
  ArrowRight,
  TrendingUp,
  ShoppingCart,
  Package,
  Users,
  Layers,
  Zap,
  BarChart3,
  Shield,
  Globe,
  CheckCircle,
  Star,
  ChevronRight,
  Menu,
  X,
} from 'lucide-react';
import Footer from '../components/Footer';
import styles from './HomePage.module.css';
import useAuth from '../hooks/useAuth';
import { ROUTES } from '../../../app/router/routes';
import { useState } from 'react';

const HERO_IMG =
  'https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=1600&h=800&fit=crop&auto=format&q=90';

const PLATFORMS = [
  { name: 'Shopee', color: '#EE4D2D', logo: 'SP' },
  { name: 'TikTok Shop', color: '#010101', logo: 'TT' },
  { name: 'Lazada', color: '#0F146D', logo: 'LZ' },
];

const FEATURES = [
  {
    icon: Package,
    title: 'Quản lý sản phẩm đa kênh',
    desc: 'Đồng bộ catalog, giá và tồn kho thời gian thực trên tất cả sàn thương mại điện tử.',
    iconBg: '#eff6ff',
    iconColor: '#2563eb',
  },
  {
    icon: ShoppingCart,
    title: 'Xử lý đơn hàng tập trung',
    desc: 'Nhận, xác nhận và quản lý đơn hàng từ Shopee, TikTok Shop, Lazada trên một màn hình.',
    iconBg: '#f0fdf4',
    iconColor: '#16a34a',
  },
  {
    icon: BarChart3,
    title: 'Phân tích & Báo cáo',
    desc: 'Dashboard thông minh với doanh thu, lợi nhuận, tốc độ bán theo từng kênh và sản phẩm.',
    iconBg: '#fff7ed',
    iconColor: '#ea580c',
  },
  {
    icon: Zap,
    title: 'Đồng bộ tự động',
    desc: 'Webhook thời gian thực đảm bảo dữ liệu nhất quán — không nhập tay, không lệch tồn kho.',
    iconBg: '#fefce8',
    iconColor: '#ca8a04',
  },
  {
    icon: Shield,
    title: 'Phân quyền đa vai trò',
    desc: 'Hỗ trợ Admin, Shop Owner và Staff với quyền truy cập kiểm soát chặt chẽ theo tính năng.',
    iconBg: '#fff1f2',
    iconColor: '#e11d48',
  },
  {
    icon: Globe,
    title: 'Kho hàng thông minh',
    desc: 'Theo dõi tồn kho, cảnh báo sắp hết, lịch sử nhập/xuất và chuyển kho nội bộ.',
    iconBg: '#f5f3ff',
    iconColor: '#7c3aed',
  },
];

const STEPS = [
  {
    num: '01',
    title: 'Kết nối gian hàng',
    desc: 'Liên kết tài khoản Shopee, TikTok Shop, Lazada chỉ trong vài phút với vài click đơn giản.',
  },
  {
    num: '02',
    title: 'Đồng bộ dữ liệu',
    desc: 'Sản phẩm, giá, tồn kho và đơn hàng được đồng bộ tự động theo thời gian thực.',
  },
  {
    num: '03',
    title: 'Quản lý tập trung',
    desc: 'Điều hành toàn bộ hoạt động kinh doanh từ một dashboard duy nhất — mọi lúc, mọi nơi.',
  },
];

const STATS = [
  { value: '1,000+', label: 'Sellers tin dùng', icon: Users },
  { value: '3 triệu+', label: 'Đơn hàng/tháng', icon: ShoppingCart },
  { value: '99.9%', label: 'Độ khả dụng', icon: Zap },
  { value: '< 2 giây', label: 'Thời gian đồng bộ', icon: Layers },
];

const TESTIMONIALS = [
  {
    name: 'Phạm Trung Hiếu',
    role: 'Shop Owner · Thời trang nữ',
    avatar: 'NM',
    content:
      'Trước đây mất 3 tiếng mỗi ngày để đối chiếu đơn hàng thủ công. Giờ mọi thứ tự động — tôi tập trung vào marketing và nhân đôi doanh thu chỉ trong 2 tháng.',
    rating: 5,
  },
  {
    name: 'Lương Thị Diệu Linh',
    role: 'Shop Owner · Mỹ phẩm & Skincare',
    avatar: 'TL',
    content:
      'Quản lý 3 shop trên 3 sàn cùng lúc mà không bị rối. Tính năng đồng bộ tồn kho là điểm mạnh nhất — không còn bán hàng khi đã hết kho.',
    rating: 5,
  },
  {
    name: 'Bùi Lê Đức Anh',
    role: 'Shop Owner · Điện tử & Phụ kiện',
    avatar: 'LH',
    content:
      'Dashboard analytics giúp tôi biết kênh nào đang chạy tốt, sản phẩm nào cần đẩy thêm ngân sách. Quyết định nhanh hơn và chính xác hơn nhiều.',
    rating: 5,
  },
];


// ─── Navigation ─────────────────────────────────────────────────────────────

const PublicNav = () => {
  const { isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);

  return (
    <header>
      <nav className={styles.topBar}>
        <Link to="/home" className={styles.brandLink}>
          <div className={styles.brandIcon}>
            <ShoppingBag className="w-5 h-5" />
          </div>
          <span className={styles.brandName}>
            Omni<span className={styles.brandNameAccent}>Sales</span>
          </span>
        </Link>

        <div className={styles.navLinks}>
          {[
            { href: '#features', label: 'Tính năng' },
            { href: '#how-it-works', label: 'Cách hoạt động' },
            { href: '#testimonials', label: 'Khách hàng' },
            { href: '/about', label: 'Về chúng tôi' },
          ].map(({ href, label }) => (
            href.startsWith('/') ? (
              <Link key={label} to={href} className={styles.navLink}>
                {label}
              </Link>
            ) : (
              <a key={label} href={href} className={styles.navLink}>
                {label}
              </a>
            )
          ))}
        </div>

        <div className={styles.navActions}>
          {isAuthenticated ? (
            <Link to={ROUTES.DASHBOARD} className={styles.navDashboard}>
              Vào Dashboard
              <ChevronRight className="w-4 h-4" />
            </Link>
          ) : (
            <>
              <Link to={ROUTES.LOGIN} className={styles.navLogin}>
                Đăng nhập
              </Link>
              <Link to="/register" className={styles.navRegister}>
                Đăng ký
                <ArrowRight className="w-4 h-4" />
              </Link>
            </>
          )}
          <button
            className={styles.menuButton}
            onClick={() => setOpen(!open)}
            aria-label="Menu"
          >
            {open ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </nav>

      {open && (
        <div className={styles.mobileMenu}>
          {[
            { href: '#features', label: 'Tính năng' },
            { href: '#how-it-works', label: 'Cách hoạt động' },
            { href: '#testimonials', label: 'Khách hàng' },
            { href: '/about', label: 'Về chúng tôi' },
          ].map(({ href, label }) =>
            href.startsWith('/') ? (
              <Link
                key={label}
                to={href}
                className={styles.mobileNavLink}
                onClick={() => setOpen(false)}
              >
                {label}
              </Link>
            ) : (
              <a
                key={label}
                href={href}
                className={styles.mobileNavLink}
                onClick={() => setOpen(false)}
              >
                {label}
              </a>
            )
          )}
          <hr className={styles.mobileDivider} />
          <div className={styles.mobileActions}>
            {isAuthenticated ? (
              <Link
                to={ROUTES.DASHBOARD}
                className={styles.mobileDashboard}
                onClick={() => setOpen(false)}
              >
                Vào Dashboard
              </Link>
            ) : (
              <>
                <Link
                  to={ROUTES.LOGIN}
                  className={styles.mobileLogin}
                  onClick={() => setOpen(false)}
                >
                  Đăng nhập
                </Link>
                <Link
                  to="/register"
                  className={styles.mobileRegister}
                  onClick={() => setOpen(false)}
                >
                  Đăng ký
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  );
};

// ─── Hero ─────────────────────────────────────────────────────────────────────

const HeroSection = () => (
  <section className={styles.hero}>
    <div className={styles.heroInner}>
      {/* Badge */}
      <div className={styles.heroBadge}>
        <span className={styles.heroBadgeDot} />
        <span className={styles.heroBadgeText}>Hơn 1.000 sellers đang dùng OmniSales</span>
      </div>

      {/* Title */}
      <h1 className={styles.heroTitle}>
        Quản lý bán hàng{' '}
        <span className={styles.heroTitleAccent}>đa kênh</span>{' '}
        thông minh
      </h1>

      {/* Subtitle */}
      <p className={styles.heroSubtitle}>
        Kết nối Shopee, TikTok Shop và Lazada,.... Đồng bộ sản phẩm, đơn hàng và tồn kho tự
        động — tất cả trên một nền tảng duy nhất.
      </p>

      {/* CTAs */}
      <div className={styles.heroCtas}>
        <Link to="/register" className={styles.ctaPrimary}>
          Đăng ký ngay
          <ArrowRight className="w-5 h-5" />
        </Link>
        <Link to={ROUTES.LOGIN} className={styles.ctaSecondary}>
          Đăng nhập
        </Link>
      </div>

      {/* Platforms */}
      <div className={styles.heroPlatforms}>
        {PLATFORMS.map((p) => (
          <div key={p.name} className={styles.platformBadge}>
            <div
              className={styles.platformLogo}
              style={{ backgroundColor: p.color }}
            >
              {p.logo}
            </div>
            <span className={styles.platformName}>{p.name}</span>
          </div>
        ))}
      </div>

      {/* Dashboard Preview */}
      <div className={styles.heroDashboard}>
        <div className={styles.heroDashboardBar}>
          <span className={`${styles.dashboardDot} ${styles.dotRed}`} />
          <span className={`${styles.dashboardDot} ${styles.dotYellow}`} />
          <span className={`${styles.dashboardDot} ${styles.dotGreen}`} />
          <div className={styles.dashboardUrl}>app.omnisales.io/dashboard</div>
        </div>
        <img
          src={HERO_IMG}
          alt="OmniSales Dashboard"
          className={styles.heroImage}
        />
        <div className={styles.heroStats}>
          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <TrendingUp className={`${styles.statIcon} ${styles.trendGreen}`} />
              <span className={styles.statLabel}>Doanh thu hôm nay</span>
            </div>
            <p className={styles.statValue}>₫ 24,580,000</p>
            <p className={`${styles.statTrend} ${styles.trendGreen}`}>+18% so với hôm qua</p>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <ShoppingCart className={`${styles.statIcon} ${styles.trendBlue}`} />
              <span className={styles.statLabel}>Đơn mới hôm nay</span>
            </div>
            <p className={styles.statValue}>347 đơn</p>
            <p className={`${styles.statTrend} ${styles.trendBlue}`}>3 kênh đang chạy</p>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statHeader}>
              <Package className={`${styles.statIcon} ${styles.trendAmber}`} />
              <span className={styles.statLabel}>Tồn kho</span>
            </div>
            <p className={styles.statValue}>1,247 sản phẩm</p>
            <p className={`${styles.statTrend} ${styles.trendAmber}`}>12 gần hết hàng</p>
          </div>
        </div>
      </div>
    </div>
  </section>
);

// ─── Stats Bar ───────────────────────────────────────────────────────────────

const StatsBar = () => (
  <div className={styles.statsBar}>
    <div className={styles.statsInner}>
      {STATS.map(({ value, label, icon: Icon }) => (
        <div key={label} className={styles.statItem}>
          <div className={styles.statItemIcon}>
            <Icon className="w-5 h-5" />
          </div>
          <p className={styles.statItemValue}>{value}</p>
          <p className={styles.statItemLabel}>{label}</p>
        </div>
      ))}
    </div>
  </div>
);

// ─── Features ─────────────────────────────────────────────────────────────────

const FeaturesSection = () => (
  <section id="features" className={styles.features}>
    <div className={styles.featuresInner}>
      <div className={styles.sectionHeader}>
        <span className={styles.sectionEyebrow}>Tính năng nổi bật</span>
        <h2 className={styles.sectionTitle}>
          Mọi thứ bạn cần để{' '}
          <span className={styles.sectionTitleAccent}>scale</span>
        </h2>
        <p className={styles.sectionDesc}>
          Bộ công cụ đầy đủ giúp sellers quản lý toàn bộ hoạt động kinh doanh đa kênh một
          cách hiệu quả.
        </p>
      </div>

      <div className={styles.featuresGrid}>
        {FEATURES.map(({ icon: Icon, title, desc, iconBg, iconColor }) => (
          <div key={title} className={styles.featureCard}>
            <div
              className={styles.featureIconBox}
              style={{ backgroundColor: iconBg }}
            >
              <Icon className={styles.featureIcon} style={{ color: iconColor }} />
            </div>
            <h3 className={styles.featureTitle}>{title}</h3>
            <p className={styles.featureDesc}>{desc}</p>
          </div>
        ))}
      </div>
    </div>
  </section>
);

// ─── How It Works ─────────────────────────────────────────────────────────────

const HowItWorksSection = () => (
  <section id="how-it-works" className={styles.howItWorks}>
    <div className={styles.howItWorksInner}>
      <div className={styles.sectionHeader}>
        <span className={styles.sectionEyebrow}>3 bước đơn giản</span>
        <h2 className={styles.sectionTitle}>Bắt đầu trong vài phút</h2>
        <p className={styles.sectionDesc}>
          Không cần cài đặt phức tạp, không cần hỗ trợ kỹ thuật.
        </p>
      </div>

      <div className={styles.stepsGrid}>
        <div className={styles.stepsConnector} />
        {STEPS.map(({ num, title, desc }) => (
          <div key={num} className={styles.stepItem}>
            <div className={styles.stepNumber}>{num}</div>
            <h3 className={styles.stepTitle}>{title}</h3>
            <p className={styles.stepDesc}>{desc}</p>
          </div>
        ))}
      </div>
    </div>
  </section>
);

// ─── Testimonials ─────────────────────────────────────────────────────────────

const TestimonialsSection = () => (
  <section id="testimonials" className={styles.testimonials}>
    <div className={styles.testimonialsInner}>
      <div className={styles.sectionHeader}>
        <span className={styles.sectionEyebrow}>Khách hàng nói gì</span>
        <h2 className={styles.sectionTitle}>Được tin dùng bởi sellers</h2>
      </div>

      <div className={styles.testimonialsGrid}>
        {TESTIMONIALS.map(({ name, role, avatar, content, rating }) => (
          <div key={name} className={styles.testimonialCard}>
            <div className={styles.testimonialStars}>
              {Array.from({ length: rating }).map((_, i) => (
                <Star
                  key={i}
                  className={styles.testimonialStar}
                  fill="#f59e0b"
                />
              ))}
            </div>

            <span className={styles.testimonialQuoteIcon}>&ldquo;</span>
            <p className={styles.testimonialContent}>{content}</p>

            <hr className={styles.testimonialDivider} />

            <div className={styles.testimonialAuthor}>
              <div className={styles.authorAvatar}>{avatar}</div>
              <div>
                <p className={styles.authorName}>{name}</p>
                <p className={styles.authorRole}>{role}</p>
              </div>
            </div>

            <div className={styles.verifiedBadge}>
              <CheckCircle className="w-3.5 h-3.5" />
              Verified
            </div>
          </div>
        ))}
      </div>
    </div>
  </section>
);

// ─── CTA ─────────────────────────────────────────────────────────────────────

const CtaSection = () => (
  <section className={styles.cta}>
    <div className={styles.ctaInner}>
      <h2 className={styles.ctaTitle}>Sẵn sàng quản lý đa kênh?</h2>
      <p className={styles.ctaDesc}>
        Tham gia cùng hơn 15.000 sellers đã tin dùng OmniSales. Bắt đầu miễn phí ngay
        hôm nay — không cần thẻ tín dụng.
      </p>
      <div className={styles.ctaButtons}>
        <Link to="/register" className={styles.ctaButtonPrimary}>
          Đăng ký ngay
          <ArrowRight className="w-5 h-5" />
        </Link>
        <Link to="/register" className={styles.ctaButtonSecondary}>
          Liên hệ bán hàng
        </Link>
      </div>
    </div>
  </section>
);

// ─── HomePage ─────────────────────────────────────────────────────────────────

const HomePage = () => (
  <div>
    <PublicNav />
    <main>
      <HeroSection />
      <StatsBar />
      <FeaturesSection />
      <HowItWorksSection />
      <TestimonialsSection />
      <CtaSection />
    </main>
    <Footer />
  </div>
);

export default HomePage;
