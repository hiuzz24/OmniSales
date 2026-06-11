import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import useAuth from '../hooks/useAuth';
import { getRoleHome } from '../constants/roles';
import styles from './LoginForm.module.css';

const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Vui lòng nhập địa chỉ email của bạn.')
    .email('Địa chỉ email không hợp lệ.'),
  password: z
    .string()
    .min(1, 'Vui lòng nhập mật khẩu của bạn.')
    .min(8, 'Mật khẩu phải có ít nhất 8 ký tự.'),
  rememberMe: z.boolean().optional(),
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
      rememberMe: false,
    },
  });

  const onSubmit = async (data) => {
    setApiError('');
    try {
      const result = await login(data);
      console.log(result);
      toast.success("Đăng nhập thành công");
      navigate(getRoleHome(result?.user?.role));
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401) {
        setApiError('Email hoặc mật khẩu không chính xác. Vui lòng thử lại.');
        toast.info(err?.response?.data?.message);
      } else if (status === 429) {
        setApiError('Quá nhiều lần thử đăng nhập. Vui lòng đợi và thử lại sau.');
        toast.info(err?.response?.data?.message);
      } else if (status >= 500) {
        setApiError('Lỗi máy chủ. Vui lòng thử lại sau.');
      } else {
        setApiError('Đã xảy ra lỗi. Vui lòng thử lại.');
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
        <p className={styles.subtitle}>Đăng nhập vào hệ thống quản lý đa kênh của bạn</p>
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
            Email
          </label>
          <div className={styles.inputWrapper}>
            <span className={`material-symbols-outlined ${styles.inputLeadingIcon}`}>
              mail
            </span>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              placeholder="email@example.com"
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
              placeholder="••••••••"
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
              aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiển thị mật khẩu'}
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
          <label className={styles.checkboxWrapper}>
            <input type="checkbox" {...register('rememberMe')} className={styles.checkbox} />
            <span className={styles.checkboxLabel}>Ghi nhớ đăng nhập</span>
          </label>
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
              Đang đăng nhập...
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



        <p className={styles.signupRow}>
          Chưa có tài khoản?
          <a href="/register" className={styles.signupLink}>
            Đăng ký miễn phí
          </a>
        </p>

        <div className={styles.trustBadges}>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">lock</span>
            Bảo mật SSL
          </span>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">verified_user</span>
            Bảo vệ dữ liệu
          </span>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">shield</span>
            Xác thực hai yếu tố
          </span>
        </div>
      </form>
    </>
  );
};

export default LoginForm;
