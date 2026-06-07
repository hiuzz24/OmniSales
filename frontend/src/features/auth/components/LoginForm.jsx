import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import useAuth from '../hooks/useAuth';
import { getRoleHome } from '../constants/roles';
import styles from './LoginForm.module.css';

const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Please enter your email address.')
    .email('Invalid email address.'),
  password: z
    .string()
    .min(1, 'Please enter your password.')
    .min(6, 'Password must be at least 6 characters.'),
});

const LoginForm = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [showPassword, setShowPassword] = useState(false);
  const [apiError, setApiError] = useState('');

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = async (data) => {
    setApiError('');
    try {
      const result = await login(data);
      navigate(getRoleHome(result?.user?.role));
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401) {
        setApiError('Incorrect email or password. Please try again.');
      } else if (status === 429) {
        setApiError('Too many login attempts. Please wait and try again.');
      } else if (status >= 500) {
        setApiError('Server error. Please try again later.');
      } else {
        setApiError('Something went wrong. Please try again.');
      }
    }
  };

  return (
    <>
      <div className={styles.formHeader}>
        <div className={styles.logoWrapper}>
          <div className={styles.logoPlaceholder}>
            <span className="material-symbols-outlined">storefront</span>
          </div>
        </div>
        <h1 className={styles.title}>Chào mừng trở lại</h1>
        <p className={styles.subtitle}>Đăng nhập vào tài khoản OSMS của bạn</p>
      </div>

      {apiError && (
        <div className={styles.errorBanner} role="alert">
          <span className={`material-symbols-outlined ${styles.errorBannerIcon}`}>
            error
          </span>
          <span>{apiError}</span>
        </div>
      )}

      <form className={styles.form} onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className={styles.inputGroup}>
          <label htmlFor="login-email" className={styles.label}>
            Địa chỉ Email
          </label>
          <div className={styles.inputWrapper}>
            <span className={`material-symbols-outlined ${styles.inputLeadingIcon}`}>
              mail
            </span>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder="ban@example.com"
              {...register('email')}
              className={`${styles.input} ${errors.email ? styles.inputError : ''}`}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? 'email-error' : undefined}
              disabled={isSubmitting}
            />
          </div>
          {errors.email && (
            <span id="email-error" className={styles.fieldError} role="alert">
              <span className="material-symbols-outlined">error</span>
              {errors.email.message}
            </span>
          )}
        </div>

        <div className={styles.inputGroup}>
          <label htmlFor="login-password" className={styles.label}>
            Mật khẩu
          </label>
          <div className={styles.inputWrapper}>
            <span className={`material-symbols-outlined ${styles.inputLeadingIcon}`}>
              lock
            </span>
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Enter your password"
              {...register('password')}
              className={`${styles.input} ${styles.inputWithTrailing} ${errors.password ? styles.inputError : ''}`}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'password-error' : undefined}
              disabled={isSubmitting}
            />
            <button
              type="button"
              className={styles.trailingIconBtn}
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              tabIndex={-1}
            >
              <span className="material-symbols-outlined">
                {showPassword ? 'visibility_off' : 'visibility'}
              </span>
            </button>
          </div>
          {errors.password && (
            <span id="password-error" className={styles.fieldError} role="alert">
              <span className="material-symbols-outlined">error</span>
              {errors.password.message}
            </span>
          )}
        </div>

        <div className={styles.optionsRow}>
          <a href="/forgot-password" className={styles.forgotLink}>
            Quên mật khẩu?
          </a>
        </div>

        <button
          id="login-submit-btn"
          type="submit"
          className={styles.submitButton}
          disabled={isSubmitting}
        >
          {isSubmitting ? (
            <>
              <span className={styles.spinner} aria-hidden="true" />
              Signing in...
            </>
          ) : (
            <>
              <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
                login
              </span>
              Đăng nhập
            </>
          )}
        </button>

        <div className={styles.divider}>
          <span className={styles.dividerLine} />
          <span>hoặc tiếp tục với</span>
          <span className={styles.dividerLine} />
        </div>

        <div className={styles.socialButtons}>
          <button
            id="login-google-btn"
            type="button"
            className={styles.socialBtn}
            disabled={isSubmitting}
          >
            <svg className={styles.googleIcon} viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
            </svg>
            Google
          </button>

          <button
            id="login-microsoft-btn"
            type="button"
            className={styles.socialBtn}
            disabled={isSubmitting}
          >
            <svg className={styles.googleIcon} viewBox="0 0 21 21" xmlns="http://www.w3.org/2000/svg">
              <rect x="1" y="1" width="9" height="9" fill="#F25022"/>
              <rect x="11" y="1" width="9" height="9" fill="#7FBA00"/>
              <rect x="1" y="11" width="9" height="9" fill="#00A4EF"/>
              <rect x="11" y="11" width="9" height="9" fill="#FFB900"/>
            </svg>
            Microsoft
          </button>
        </div>

        <p className={styles.signupRow}>
          Chưa có tài khoản?
          <a href="/register" className={styles.signupLink}>
            Đăng ký miễn phí
          </a>
        </p>

        <div className={styles.trustBadges}>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">lock</span>
            SSL Bảo mật
          </span>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">verified_user</span>
            Bảo vệ dữ liệu
          </span>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">shield</span>
            Xác thực 2 lớp
          </span>
        </div>
      </form>
    </>
  );
};

export default LoginForm;
