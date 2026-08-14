import { useState, useEffect } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import authService from '../services/authService';
import styles from './InviteRegisterPage.module.css';

/** Hoàn tất đăng ký tài khoản từ lời mời có thời hạn. */
const InviteRegisterPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  // State flags
  const [isValidating, setIsValidating] = useState(true);
  const [isTokenValid, setIsTokenValid] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRegistered, setIsRegistered] = useState(false);

  // Invitation info
  const [email, setEmail] = useState('');
  const [roleName, setRoleName] = useState('');
  const [validationError, setValidationError] = useState('');

  // Form fields
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Field validation errors
  const [errors, setErrors] = useState({});

  useEffect(() => {
    // Xác thực token lời mời và tải thông tin người được mời.
    const checkToken = async () => {
      if (!token) {
        setValidationError('Không tìm thấy mã xác thực lời mời trong liên kết.');
        setIsTokenValid(false);
        setIsValidating(false);
        return;
      }

      try {
        const inviteData = await authService.validateInviteToken(token);
        setEmail(inviteData.email);
        
        // Display Vietnamese user-friendly name for roles
        let displayRole = inviteData.roleName;
        if (inviteData.roleName === 'SALES') {
          displayRole = 'Sales Staff (Nhân viên bán hàng)';
        } else if (inviteData.roleName === 'OPERATIONS') {
          displayRole = 'Operations Staff (Nhân viên vận hành)';
        }
        setRoleName(displayRole);
        
        setIsTokenValid(true);
      } catch (err) {
        console.error(err);
        setValidationError(err?.response?.data?.message || 'Liên kết mời không hợp lệ hoặc đã hết hạn.');
        setIsTokenValid(false);
      } finally {
        setIsValidating(false);
      }
    };

    checkToken();
  }, [token]);

  // Kiểm tra mật khẩu và xác nhận mật khẩu trước khi kích hoạt tài khoản.
  const validateForm = () => {
    const newErrors = {};

    if (!fullName.trim()) {
      newErrors.fullName = 'Họ và tên không được để trống';
    }

    if (!password) {
      newErrors.password = 'Mật khẩu không được để trống';
    } else if (password.length < 8) {
      newErrors.password = 'Mật khẩu phải chứa ít nhất 8 ký tự';
    } else {
      const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@#$%^&+=!\-_]).{8,}$/;
      if (!passwordRegex.test(password)) {
        newErrors.password = 'Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt';
      }
    }

    if (!confirmPassword) {
      newErrors.confirmPassword = 'Xác nhận mật khẩu không được để trống';
    } else if (password !== confirmPassword) {
      newErrors.confirmPassword = 'Mật khẩu xác nhận không trùng khớp';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  // Gửi thông tin đăng ký và tiêu thụ token lời mời.
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) return;

    setIsSubmitting(true);
    try {
      await authService.acceptInvite({
        token,
        fullName: fullName.trim(),
        password,
        confirmPassword,
      });

      toast.success('Đăng ký tài khoản thành công!');
      setIsRegistered(true);

      // Auto redirect after 5 seconds
      setTimeout(() => {
        navigate('/login');
      }, 5000);
    } catch (err) {
      console.error(err);
      const errMsg = err?.response?.data?.message || 'Đăng ký tài khoản thất bại, vui lòng thử lại.';
      toast.error(errMsg);
      setErrors({ apiError: errMsg });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isValidating) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <div className={styles.logoContainer}>
            <svg className={styles.logoIcon} viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" xmlns="http://www.w3.org/2000/svg">
              <line x1="12" y1="2" x2="12" y2="6"></line>
              <line x1="12" y1="18" x2="12" y2="22"></line>
              <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"></line>
              <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"></line>
              <line x1="2" y1="12" x2="6" y2="12"></line>
              <line x1="18" y1="12" x2="22" y2="12"></line>
              <line x1="4.93" y1="19.07" x2="7.76" y2="16.24"></line>
              <line x1="16.24" y1="7.76" x2="19.07" y2="4.93"></line>
            </svg>
          </div>
          <h2 className={styles.title}>Đang kiểm tra liên kết mời...</h2>
          <p className={styles.subtitle}>Vui lòng chờ trong giây lát trong khi chúng tôi xác nhận lời mời của bạn.</p>
        </div>
      </div>
    );
  }

  if (!isTokenValid) {
    return (
      <div className={styles.page}>
        <div className={styles.errorCard}>
          <div className={styles.errorIconContainer}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" xmlns="http://www.w3.org/2000/svg">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="15" y1="9" x2="9" y2="15"></line>
              <line x1="9" y1="9" x2="15" y2="15"></line>
            </svg>
          </div>
          <h2 className={styles.errorTitle}>Liên kết không hợp lệ</h2>
          <p className={styles.errorText}>
            {validationError || 'Liên kết mời của bạn đã hết hạn hoặc không tồn tại. Vui lòng liên hệ với quản trị viên để gửi lại lời mời mới.'}
          </p>
          <Link to="/login" className={styles.backLink}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" xmlns="http://www.w3.org/2000/svg">
              <line x1="19" y1="12" x2="5" y2="12"></line>
              <polyline points="12 19 5 12 12 5"></polyline>
            </svg>
            Về trang đăng nhập
          </Link>
        </div>
      </div>
    );
  }

  if (isRegistered) {
    return (
      <div className={styles.page}>
        <div className={styles.successCard}>
          <div className={styles.successIconContainer}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" xmlns="http://www.w3.org/2000/svg">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
              <polyline points="22 4 12 14.01 9 11.01"></polyline>
            </svg>
          </div>
          <h2 className={styles.successTitle}>Đăng ký thành công!</h2>
          <p className={styles.successText}>
            Tài khoản của bạn đã được kích hoạt thành công với email <strong>{email}</strong>. 
            Hệ thống sẽ tự động chuyển bạn về trang đăng nhập sau 5 giây.
          </p>
          <Link to="/login" className={styles.submitBtn} style={{ textDecoration: 'none', display: 'inline-block' }}>
            Đăng nhập ngay
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.logoContainer}>
          <svg className={styles.logoIcon} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <circle cx="8.5" cy="7" r="4" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <line x1="20" y1="8" x2="20" y2="14" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            <line x1="23" y1="11" x2="17" y2="11" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>

        <h2 className={styles.title}>Đăng ký tài khoản</h2>
        <p className={styles.subtitle}>
          Chào mừng bạn tham gia OmniSales! Hãy điền các thông tin dưới đây để hoàn tất tạo tài khoản.
        </p>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.inputGroup}>
            <label className={styles.label}>Email được mời</label>
            <input
              type="email"
              className={styles.input}
              value={email}
              disabled
            />
          </div>

          <div className={styles.inputGroup}>
            <label className={styles.label}>Vai trò trong hệ thống</label>
            <input
              type="text"
              className={styles.input}
              value={roleName}
              disabled
            />
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="fullName" className={styles.label}>Họ và tên</label>
            <input
              type="text"
              id="fullName"
              placeholder="Nhập họ và tên của bạn"
              className={`${styles.input} ${errors.fullName ? styles.inputError : ''}`}
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              disabled={isSubmitting}
              required
            />
            {errors.fullName && <p className={styles.fieldError}>{errors.fullName}</p>}
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="password" className={styles.label}>Mật khẩu</label>
            <input
              type="password"
              id="password"
              placeholder="Nhập mật khẩu mới"
              className={`${styles.input} ${errors.password ? styles.inputError : ''}`}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isSubmitting}
              required
            />
            {errors.password && <p className={styles.fieldError}>{errors.password}</p>}
          </div>

          <div className={styles.inputGroup}>
            <label htmlFor="confirmPassword" className={styles.label}>Xác nhận mật khẩu</label>
            <input
              type="password"
              id="confirmPassword"
              placeholder="Nhập lại mật khẩu mới"
              className={`${styles.input} ${errors.confirmPassword ? styles.inputError : ''}`}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={isSubmitting}
              required
            />
            {errors.confirmPassword && <p className={styles.fieldError}>{errors.confirmPassword}</p>}
          </div>

          {errors.apiError && <p className={styles.fieldError} style={{ textAlign: 'center' }}>{errors.apiError}</p>}

          <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>
            {isSubmitting ? 'Đang kích hoạt...' : 'Kích hoạt tài khoản'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default InviteRegisterPage;
