import { Link } from 'react-router-dom';
import Footer from '../components/Footer';
import {
  ShoppingBag,
  Target,
  Users,
  Lightbulb,
  Heart,
  Database,
  Layers,
  Smartphone,
  BarChart3,
  Lock,
  GitBranch,
  Monitor,
  Server,
  Cpu,
  ArrowRight,
  Menu,
  X,
} from 'lucide-react';
import { useState } from 'react';
import styles from './AboutPage.module.css';

const TEAM_IMG =
  'https://images.unsplash.com/photo-1573164574511-73c773193279?w=1200&h=700&fit=crop&auto=format';
const OFFICE_IMG =
  'https://images.unsplash.com/photo-1629904853716-f0bc54eea481?w=900&h=600&fit=crop&auto=format';

const VALUES = [
  {
    icon: Target,
    title: 'Định hướng kết quả',
    desc: 'Chúng tôi đo thành công bằng kết quả kinh doanh thực tế của sellers, không phải số tính năng hay số lần update.',
    iconBg: '#f5f3ff',
    iconColor: '#7c3aed',
  },
  {
    icon: Users,
    title: 'Lấy người dùng làm trung tâm',
    desc: 'Mọi quyết định thiết kế đều xuất phát từ trải nghiệm người dùng — từ luồng thao tác nhỏ nhất đến kiến trúc tổng thể.',
    iconBg: '#eff6ff',
    iconColor: '#2563eb',
  },
  {
    icon: Lightbulb,
    title: 'Không ngừng cải tiến',
    desc: 'Thương mại điện tử Đông Nam Á thay đổi liên tục. Hệ thống được thiết kế linh hoạt để dễ dàng mở rộng và tích hợp thêm.',
    iconBg: '#fffbeb',
    iconColor: '#d97706',
  },
  {
    icon: Heart,
    title: 'Minh bạch & Tin cậy',
    desc: 'Phân quyền rõ ràng theo từng vai trò, log đầy đủ mọi thao tác, đảm bảo tính toàn vẹn dữ liệu xuyên suốt hệ thống.',
    iconBg: '#fff1f2',
    iconColor: '#e11d48',
  },
];

const MILESTONES = [
  {
    label: 'Giai đoạn 1',
    title: 'Khảo sát & Phân tích',
    desc: 'Khảo sát nhu cầu quản lý bán hàng đa kênh, phân tích bài toán tồn kho, đơn hàng và phân quyền người dùng.',
  },
  {
    label: 'Giai đoạn 2',
    title: 'Thiết kế hệ thống',
    desc: 'Xây dựng kiến trúc phần mềm, thiết kế CSDL, phân tích use-case và wireframe toàn bộ luồng nghiệp vụ.',
  },
  {
    label: 'Giai đoạn 3',
    title: 'Phát triển core modules',
    desc: 'Hiện thực các module chính: xác thực, sản phẩm, kho hàng (nhập/xuất/chuyển/kiểm), đơn hàng và kênh bán.',
  },
  {
    label: 'Giai đoạn 4',
    title: 'POS & Báo cáo',
    desc: 'Bổ sung module bán hàng trực tiếp (POS), thanh toán đa phương thức và dashboard phân tích doanh thu.',
  },
  {
    label: 'Giai đoạn 5',
    title: 'Kiểm thử & Hoàn thiện',
    desc: 'Kiểm thử toàn diện, tối ưu UX, sửa lỗi và hoàn thiện tài liệu kỹ thuật cho đồ án tốt nghiệp.',
  },
];

const TEAM = [
  { name: 'Nguyễn Xuân Núi', role: 'Lecturer', initials: '01', bg: 'linear-gradient(135deg, #7c3aed, #2563eb)' },
  { name: 'Bùi Lê Đức Anh', role: 'Fullstack Developer', initials: '02', bg: 'linear-gradient(135deg, #2563eb, #0891b2)' },
  { name: 'Phạm Trung Hiếu', role: 'Fullstack Developer', initials: '03', bg: 'linear-gradient(135deg, #16a34a, #0d9488)' },
  { name: 'Lương Thị Diệu Linh', role: 'Fullstack Developer', initials: '04', bg: 'linear-gradient(135deg, #d97706, #ea580c)' },
  { name: 'Đinh Đức Hiếu', role: 'Fullstack Developer', initials: '05', bg: 'linear-gradient(135deg, #e11d48, #be185d)' },
  { name: 'Nguyễn Thế Duy	', role: 'Fullstack Developer', initials: '06', bg: 'linear-gradient(135deg,rgb(58, 85, 237),rgb(13, 46, 117))' },
];

const TECH_STACK = [
  { icon: Monitor, label: 'React 18', desc: 'UI Framework', iconBg: '#e0f2fe', iconColor: '#0369a1' },
  { icon: Layers, label: 'TypeScript', desc: 'Ngôn ngữ lập trình', iconBg: '#eff6ff', iconColor: '#2563eb' },
  { icon: Cpu, label: 'Tailwind CSS v4', desc: 'Styling', iconBg: '#f0fdf4', iconColor: '#16a34a' },
  { icon: GitBranch, label: 'React Router v7', desc: 'Routing & Navigation', iconBg: '#fff7ed', iconColor: '#ea580c' },
  { icon: Server, label: 'Node.js / Express', desc: 'Backend API', iconBg: '#f0fdfa', iconColor: '#0d9488' },
  { icon: Database, label: 'PostgreSQL', desc: 'Cơ sở dữ liệu', iconBg: '#eff6ff', iconColor: '#4338ca' },
  { icon: Lock, label: 'JWT Auth', desc: 'Xác thực & Phân quyền', iconBg: '#fff1f2', iconColor: '#e11d48' },
  { icon: Smartphone, label: 'Responsive Design', desc: 'Đa thiết bị', iconBg: '#f5f3ff', iconColor: '#7c3aed' },
  { icon: BarChart3, label: 'Recharts', desc: 'Biểu đồ & Thống kê', iconBg: '#fffbeb', iconColor: '#d97706' },
];

// ─── Navigation ─────────────────────────────────────────────────────────────

const PublicNav = () => {
  const [open, setOpen] = useState(false);

  return (
    <header>
      <nav className={styles.nav}>
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
            { label: 'Tính năng', href: '/home#features' },
            { label: 'Về chúng tôi', href: '/about' },
          ].map((item) => (
            <Link
              key={item.label}
              to={item.href}
              className={`${styles.navLink} ${item.href === '/about' ? styles.navLinkActive : ''}`}
            >
              {item.label}
            </Link>
          ))}
        </div>

        <div className={styles.navActions}>
          <Link to="/login" className={styles.navLogin}>
            Đăng nhập
          </Link>
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
            { label: 'Tính năng', href: '/home#features' },
            { label: 'Về chúng tôi', href: '/about' },
          ].map(({ href, label }) => (
            <Link
              key={label}
              to={href}
              className={styles.mobileNavLink}
              onClick={() => setOpen(false)}
            >
              {label}
            </Link>
          ))}
          <hr className={styles.mobileDivider} />
          <Link
            to="/login"
            className={styles.mobileLogin}
            onClick={() => setOpen(false)}
          >
            Đăng nhập
          </Link>
        </div>
      )}
    </header>
  );
};

// ─── Hero ─────────────────────────────────────────────────────────────────────

const HeroSection = () => (
  <section className={styles.hero}>
    <div className={styles.heroInner}>
      <p className={styles.heroEyebrow}>Về dự án</p>
      <h1 className={styles.heroTitle}>
        Nền tảng quản lý bán hàng đa kênh — Đồ án tốt nghiệp
      </h1>
      <p className={styles.heroDesc}>
        OmniSales là hệ thống quản lý bán hàng đa kênh được xây dựng nhằm giải quyết bài
        toán đồng bộ tồn kho, xử lý đơn hàng và quản lý vận hành cho sellers kinh doanh trên
        Shopee, TikTok Shop và Lazada.
      </p>

      <div className={styles.heroImageWrap}>
        <img src={TEAM_IMG} alt="OmniSales team" className={styles.heroImage} />
        <div className={styles.heroImageOverlay} />
        <div className={styles.heroImageCaption}>
          <p className={styles.heroImageCaptionTitle}>Nhóm thực hiện đồ án</p>
          <p className={styles.heroImageCaptionSub}>Khoa Công nghệ Thông tin</p>
        </div>
      </div>
    </div>
  </section>
);

// ─── Mission ─────────────────────────────────────────────────────────────────

const MissionSection = () => (
  <section className={styles.mission}>
    <div className={styles.missionInner}>
      <div className={styles.missionGrid}>
        <div>
          <p className={styles.missionEyebrow}>Mục tiêu</p>
          <h2 className={styles.missionTitle}>
            Giải quyết bài toán thực tế của thương mại điện tử
          </h2>
          <p className={styles.missionText}>
            Hệ thống được thiết kế để giúp các seller quản lý toàn bộ hoạt động kinh doanh
            trên một nền tảng duy nhất — từ quản lý sản phẩm, kho hàng, đơn hàng đến phân
            tích doanh thu theo từng kênh.
          </p>
          <p className={styles.missionText}>
            Với kiến trúc phân quyền theo vai trò (Admin / Shop Owner / Staff), hệ thống
            đảm bảo tính bảo mật và linh hoạt trong quản lý nhân sự nội bộ.
          </p>
        </div>
        <div className={styles.missionImageWrap}>
          <div className={styles.missionImageGlow} />
          <img src={OFFICE_IMG} alt="OmniSales office" className={styles.missionImage} />
        </div>
      </div>
    </div>
  </section>
);

// ─── Values ──────────────────────────────────────────────────────────────────

const ValuesSection = () => (
  <section className={styles.values}>
    <div className={styles.valuesInner}>
      <div className={styles.valuesHeader}>
        <p className={styles.valuesEyebrow}>Nguyên tắc thiết kế</p>
        <h2 className={styles.valuesTitle}>Những giá trị định hướng phát triển</h2>
      </div>

      <div className={styles.valuesGrid}>
        {VALUES.map(({ icon: Icon, title, desc, iconBg, iconColor }) => (
          <div key={title} className={styles.valueCard}>
            <div
              className={styles.valueIconBox}
              style={{ backgroundColor: iconBg }}
            >
              <Icon className={styles.valueIcon} style={{ color: iconColor }} />
            </div>
            <div>
              <h3 className={styles.valueTitle}>{title}</h3>
              <p className={styles.valueDesc}>{desc}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  </section>
);

// ─── Timeline ───────────────────────────────────────────────────────────────

const TimelineSection = () => (
  <section className={styles.timeline}>
    <div className={styles.timelineInner}>
      <div className={styles.timelineHeader}>
        <p className={styles.timelineEyebrow}>Tiến độ</p>
        <h2 className={styles.timelineTitle}>Quá trình thực hiện đồ án</h2>
      </div>

      <div className={styles.timelineList}>
        {MILESTONES.map(({ label, title, desc }) => (
          <div key={label} className={styles.timelineItem}>
            <p className={styles.timelineLabel}>{label}</p>
            <div className={styles.timelineContent}>
              <h3 className={styles.timelineItemTitle}>{title}</h3>
              <p className={styles.timelineItemDesc}>{desc}</p>
            </div>
            <div className={styles.timelineDot} />
          </div>
        ))}
      </div>
    </div>
  </section>
);

// ─── Tech Stack ─────────────────────────────────────────────────────────────

const TechSection = () => (
  <section className={styles.tech}>
    <div className={styles.techInner}>
      <div className={styles.techHeader}>
        <p className={styles.techEyebrow}>Công nghệ</p>
        <h2 className={styles.techTitle}>Tech stack được sử dụng</h2>
        <p className={styles.techDesc}>
          Hệ thống được xây dựng trên nền tảng công nghệ hiện đại, đảm bảo hiệu năng, bảo
          mật và khả năng mở rộng.
        </p>
      </div>

      <div className={styles.techGrid}>
        {TECH_STACK.map(({ icon: Icon, label, desc, iconBg, iconColor }) => (
          <div key={label} className={styles.techCard}>
            <div
              className={styles.techIconBox}
              style={{ backgroundColor: iconBg }}
            >
              <Icon className={styles.techIcon} style={{ color: iconColor }} />
            </div>
            <div>
              <p className={styles.techLabel}>{label}</p>
              <p className={styles.techItemDesc}>{desc}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  </section>
);

// ─── Team ───────────────────────────────────────────────────────────────────

const TeamSection = () => (
  <section className={styles.team}>
    <div className={styles.teamInner}>
      <div className={styles.teamHeader}>
        <p className={styles.teamEyebrow}>Nhóm thực hiện</p>
        <h2 className={styles.teamTitle}>Những người xây dựng OmniSales</h2>
      </div>

      <div className={styles.teamGrid}>
        {TEAM.map(({ name, role, initials, bg }) => (
          <div key={name} className={styles.teamMember}>
            <div className={styles.teamAvatar} style={{ background: bg }}>
              {initials}
            </div>
            <p className={styles.teamName}>{name}</p>
            <p className={styles.teamRole}>{role}</p>
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
      <h2 className={styles.ctaTitle}>Bạn muốn trải nghiệm ngay?</h2>
      <p className={styles.ctaDesc}>
        Tham gia cùng hơn 1.000 sellers đã tin dùng OmniSales. Bắt đầu miễn phí ngay hôm
        nay.
      </p>
      <div className={styles.ctaButtons}>
        <Link to="/register" className={styles.ctaButtonPrimary}>
          Đăng ký ngay
          <ArrowRight className="w-5 h-5" />
        </Link>
        <Link to="/login" className={styles.ctaButtonSecondary}>
          Đăng nhập
        </Link>
      </div>
    </div>
  </section>
);

// ─── AboutPage ──────────────────────────────────────────────────────────────

const AboutPage = () => (
  <div>
    <PublicNav />
    <main>
      <HeroSection />
      <MissionSection />
      <ValuesSection />
      <TimelineSection />
      <TechSection />
      <TeamSection />
      <CtaSection />
    </main>
    <Footer />
  </div>
);

export default AboutPage;
