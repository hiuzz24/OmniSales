import { useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'react-toastify';
import authService from '../services/authService';
import styles from './ForgotPasswordPage.module.css';

/** Cho phép yêu cầu email đặt lại mật khẩu mà không tiết lộ tài khoản tồn tại. */
const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const emailInputRef = useRef(null);

  // Gửi yêu cầu tạo token đặt lại mật khẩu cho email đã nhập.
  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage('');
    setIsLoading(true);
    try {
      const response = await authService.forgotPassword(email);
      toast.success(response?.message || "Đã gửi hướng dẫn thành công!");
      setIsSubmitted(true);
    } catch (error) {
      console.error(error);
      const isUserNotFound = error?.response?.status === 400;
      const errorMsg = isUserNotFound
        ? (error?.response?.data?.message || "Email không tồn tại trong hệ thống. Vui lòng nhập lại.")
        : (error?.response?.data?.message || "Đã xảy ra lỗi, vui lòng thử lại sau.");
      
      setErrorMessage(errorMsg);
      toast.error(errorMsg);
      
      if (isUserNotFound) {
        setEmail('');
        setTimeout(() => {
          emailInputRef.current?.focus();
        }, 50);
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (isSubmitted) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          {/* Success Icon */}
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

          {/* Tiêu đề */}
          <h2 className={styles.title}>Kiểm tra email</h2>
          <p className={styles.subtitle}>
            Chúng tôi đã gửi hướng dẫn đặt lại mật khẩu đến <span className="font-semibold text-slate-900" style={{ color: '#111' }}>{email}</span> nếu tài khoản tồn tại trong hệ thống.
          </p>

          <div style={{ backgroundColor: '#eff6ff', borderColor: '#dbeafe', color: '#1e40af', padding: '1rem', borderRadius: '8px', fontSize: '0.875rem', textAlign: 'left', marginBottom: '1.5rem', width: '100%', border: '1px solid' }}>
            Nếu không nhận được email, vui lòng kiểm tra thư mục spam hoặc thử lại sau vài phút.
          </div>

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
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        {/* Logo Icon */}
        <div className={styles.logoContainer}>
          <svg
            className={styles.logoIcon}
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M2 17L12 22L22 17" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M2 12L12 17L22 12" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>

        {/* Tiêu đề */}
        <h2 className={styles.title}>Quên mật khẩu?</h2>
        <p className={styles.subtitle}>
          Nhập email của bạn và chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu
        </p>

        {/* Form nhập liệu */}
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.inputGroup}>
            <label htmlFor="email" className={styles.label}>Email</label>
            <input
              type="email"
              id="email"
              ref={emailInputRef}
              placeholder="admin@omnisales.com"
              className={`${styles.input} ${errorMessage ? styles.inputError : ''}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}
              required
            />
            {errorMessage && (
              <p className={styles.fieldError} role="alert">
                {errorMessage}
              </p>
            )}
          </div>

          <button type="submit" className={styles.submitBtn} disabled={isLoading}>
            {isLoading ? "Đang gửi..." : "Gửi hướng dẫn"}
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

export default ForgotPasswordPage;
