import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { Eye, EyeOff, Lock, Loader2 } from 'lucide-react';
import authService from '../services/authService';
import useAuth from '../hooks/useAuth';
import styles from './ChangePasswordPage.module.css';

/** Bắt buộc người dùng đổi mật khẩu tạm thời trước khi vào hệ thống. */
const ForceChangePasswordPage = () => {
  const navigate = useNavigate();
  const { updateUser, logout } = useAuth();

  // Form state
  const [oldPassword, setOldPassword] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  
  const [showOldPassword, setShowOldPassword] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  // Xác minh mật khẩu hiện tại và lưu mật khẩu mới.
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!oldPassword) {
      toast.error('Vui lòng nhập mật khẩu cũ');
      return;
    }
    if (password.length < 8) {
      toast.error('Mật khẩu mới phải từ 8 ký tự trở lên');
      return;
    }
    if (password !== confirmPassword) {
      toast.error('Mật khẩu xác nhận không khớp. Vui lòng nhập lại.');
      return;
    }
    
    setIsLoading(true);
    try {
      const response = await authService.changePasswordAfterLogin(oldPassword, password, confirmPassword);
      toast.success(response?.message || 'Cập nhật mật khẩu thành công!');
      
      // Update the user context so passwordExpired is false
      updateUser({ passwordExpired: false });
      setIsSubmitted(true);
      
    } catch (error) {
      console.error(error);
      const errorMsg =
        error?.response?.data?.message || 'Đã xảy ra lỗi khi cập nhật mật khẩu.';
      toast.error(errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  // Chuyển tới dashboard sau khi đổi mật khẩu thành công.
  const handleGoToDashboard = () => {
    navigate('/');
  };

  // Đăng xuất nếu người dùng không tiếp tục đổi mật khẩu.
  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  // ── Success state ──────────────────────────────────────────
  if (isSubmitted) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <div className={`${styles.logoContainer}`} style={{ backgroundColor: '#10b981' }}>
            <svg
              className={styles.logoIcon}
              viewBox="0 0 24 24"
              fill="none"
              stroke="white"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              xmlns="http://www.w3.org/2000/svg"
            >
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </div>
          <h2 className={styles.title}>Thành công!</h2>
          <p className={styles.subtitle}>
            Mật khẩu của bạn đã được cập nhật thành công.
          </p>
          <button onClick={handleGoToDashboard} className={styles.submitBtn} style={{ marginTop: '1.5rem' }}>
            Tiếp tục đến trang chủ
          </button>
        </div>
      </div>
    );
  }

  // ── Form state ───────────────────────────────────────────
  return (
    <div className={styles.page}>
      <div className={styles.card}>
        {/* Logo Icon */}
        <div className={styles.logoContainer}>
          <Lock className={styles.logoIcon} stroke="white" strokeWidth="2" />
        </div>

        {/* Tiêu đề */}
        <h2 className={styles.title}>Đổi mật khẩu bắt buộc</h2>
        <p className={styles.subtitle} style={{ color: '#ef4444' }}>
          Bạn đang sử dụng mật khẩu tạm thời. Vui lòng đổi mật khẩu mới để tiếp tục sử dụng hệ thống.
        </p>

        {/* Form nhập liệu */}
        <form onSubmit={handleSubmit} className={styles.form}>
          {/* Mật khẩu cũ */}
          <div className={styles.inputGroup}>
            <label htmlFor="oldPassword" className={styles.label}>Mật khẩu hiện tại</label>
            <div className={styles.inputWrapper}>
              <input
                type={showOldPassword ? 'text' : 'password'}
                id="oldPassword"
                placeholder="••••••••"
                className={styles.input}
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)}
                disabled={isLoading}
                required
              />
              <button
                type="button"
                className={styles.eyeBtn}
                onClick={() => setShowOldPassword(!showOldPassword)}
                tabIndex="-1"
              >
                {showOldPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
              </button>
            </div>
          </div>

          {/* Mật khẩu mới */}
          <div className={styles.inputGroup}>
            <label htmlFor="password" className={styles.label}>Mật khẩu mới</label>
            <div className={styles.inputWrapper}>
              <input
                type={showPassword ? 'text' : 'password'}
                id="password"
                placeholder="••••••••"
                className={styles.input}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isLoading}
                minLength={8}
                required
              />
              <button
                type="button"
                className={styles.eyeBtn}
                onClick={() => setShowPassword(!showPassword)}
                tabIndex="-1"
              >
                {showPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
              </button>
            </div>
          </div>

          {/* Xác nhận mật khẩu */}
          <div className={styles.inputGroup}>
            <label htmlFor="confirmPassword" className={styles.label}>Xác nhận mật khẩu mới</label>
            <div className={styles.inputWrapper}>
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                id="confirmPassword"
                placeholder="••••••••"
                className={styles.input}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                disabled={isLoading}
                minLength={8}
                required
              />
              <button
                type="button"
                className={styles.eyeBtn}
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                tabIndex="-1"
              >
                {showConfirmPassword ? <EyeOff className="size-5" /> : <Eye className="size-5" />}
              </button>
            </div>
          </div>

          <button type="submit" className={styles.submitBtn} disabled={isLoading}>
            {isLoading ? (
              <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}>
                <Loader2 className="animate-spin" size={18} /> Đang xử lý...
              </span>
            ) : 'Đổi mật khẩu'}
          </button>
        </form>

        {/* Nút quay lại đăng nhập / Đăng xuất */}
        <button onClick={handleLogout} className={styles.backLink} style={{ border: 'none', background: 'transparent', cursor: 'pointer', width: '100%', justifyContent: 'center', marginTop: '1rem' }}>
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            className={styles.backIcon}
          >
            <line x1="19" y1="12" x2="5" y2="12"></line>
            <polyline points="12 19 5 12 12 5"></polyline>
          </svg>
          Quay lại đăng nhập
        </button>
      </div>

      {/* Nút trợ giúp góc phải dưới */}
      <button className={styles.helpFloatingBtn} type="button" aria-label="Trợ giúp">
        ?
      </button>
    </div>
  );
};

export default ForceChangePasswordPage;
