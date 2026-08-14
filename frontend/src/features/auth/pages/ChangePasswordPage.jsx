import { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import { Eye, EyeOff, Lock, XCircle, Loader2 } from 'lucide-react';
import authService from '../services/authService';
import styles from './ChangePasswordPage.module.css';

/** Xác thực token và cho phép đặt mật khẩu mới từ liên kết email. */
const ChangePasswordPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  // Token validation state
  const [isValidating, setIsValidating] = useState(true);
  const [isValidToken, setIsValidToken] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');

  // Form state
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  // Validate token on mount
  useEffect(() => {
    // Kiểm tra token trước khi hiển thị biểu mẫu đổi mật khẩu.
    const checkToken = async () => {
      if (!token) {
        setIsValidToken(false);
        setErrorMessage('Liên kết không hợp lệ. Vui lòng kiểm tra lại email của bạn.');
        setIsValidating(false);
        return;
      }
      try {
        await authService.validateResetToken(token);
        setIsValidToken(true);
      } catch (error) {
        const msg =
          error?.response?.data?.message ||
          'Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.';
        setIsValidToken(false);
        setErrorMessage(msg);
      } finally {
        setIsValidating(false);
      }
    };
    checkToken();
  }, [token]);

  // Gửi mật khẩu mới kèm token đặt lại đã xác thực.
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (password.length < 6) {
      toast.error('Mật khẩu phải từ 6 ký tự trở lên');
      return;
    }
    if (password !== confirmPassword) {
      toast.error('Mật khẩu xác nhận không khớp. Vui lòng nhập lại.');
      return;
    }
    setIsLoading(true);
    try {
      const response = await authService.changePassword(token, password, confirmPassword);
      toast.success(response?.message || 'Đặt lại mật khẩu thành công!');
      setIsSubmitted(true);
    } catch (error) {
      console.error(error);
      const errorMsg =
        error?.response?.data?.message || 'Đã xảy ra lỗi khi đặt lại mật khẩu.';
      toast.error(errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  // ── Loading state ──────────────────────────────────────────
  if (isValidating) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <div className={styles.spinnerContainer}>
            <Loader2 className={styles.spinner} />
          </div>
          <h2 className={styles.title}>Đang xác thực...</h2>
          <p className={styles.subtitle}>Vui lòng chờ trong giây lát.</p>
        </div>
      </div>
    );
  }

  // ── Invalid / Expired token state ──────────────────────────
  if (!isValidToken) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <div className={`${styles.logoContainer} ${styles.errorLogo}`}>
            <XCircle className={styles.logoIcon} stroke="white" strokeWidth="2" />
          </div>
          <h2 className={styles.title}>Đường dẫn không khả dụng</h2>
          <p className={styles.subtitle}>
            {errorMessage || 'Liên kết đặt lại mật khẩu đã hết hạn, không hợp lệ hoặc đã được sử dụng.'}
          </p>
          <p className={styles.subtitleHint}>
            Vui lòng yêu cầu gửi lại liên kết đặt lại mật khẩu mới.
          </p>
          <Link to="/forgot-password" className={styles.submitBtn} style={{ textDecoration: 'none', textAlign: 'center', display: 'block', width: '100%', marginTop: '0.5rem' }}>
            Yêu cầu liên kết mới
          </Link>
          <Link to="/login" className={styles.backLink}>
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
          </Link>
        </div>
      </div>
    );
  }

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
            Mật khẩu của bạn đã được cập nhật thành công. Vui lòng sử dụng mật khẩu mới để đăng nhập.
          </p>
          <Link to="/login" className={styles.backLink}>
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
          </Link>
        </div>
      </div>
    );
  }

  // ── Valid token — show form ─────────────────────────────────
  return (
    <div className={styles.page}>
      <div className={styles.card}>
        {/* Logo Icon */}
        <div className={styles.logoContainer}>
          <Lock className={styles.logoIcon} stroke="white" strokeWidth="2" />
        </div>

        {/* Tiêu đề */}
        <h2 className={styles.title}>Đặt lại mật khẩu</h2>
        <p className={styles.subtitle}>
          Vui lòng nhập mật khẩu mới cho tài khoản của bạn
        </p>

        {/* Form nhập liệu */}
        <form onSubmit={handleSubmit} className={styles.form}>
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
                minLength={6}
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
                minLength={6}
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
            {isLoading ? 'Đang xử lý...' : 'Cập nhật mật khẩu'}
          </button>
        </form>

        {/* Nút quay lại */}
        <Link to="/login" className={styles.backLink}>
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
        </Link>
      </div>

      {/* Nút trợ giúp góc phải dưới */}
      <button className={styles.helpFloatingBtn} type="button" aria-label="Trợ giúp">
        ?
      </button>
    </div>
  );
};

export default ChangePasswordPage;
