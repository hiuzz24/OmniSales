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
    .min(1, 'Please enter your email address.')
    .email('Invalid email address.'),
  password: z
    .string()
    .min(1, 'Please enter your password.')
    .min(8, 'Password must be at least 8 characters.'),
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
      toast.success("Login Successful");
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
        <h1 className={styles.title}>Welcome Back</h1>
        <p className={styles.subtitle}>Log in to your omnichannel management system</p>
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
            Password
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
          <label className={styles.checkboxWrapper}>
            <input type="checkbox" {...register('rememberMe')} className={styles.checkbox} />
            <span className={styles.checkboxLabel}>Remember me</span>
          </label>
          <a href="/forgot-password" className={styles.forgotLink}>
            Forgot password?
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
              Sign in
            </>
          )}
        </button>



        <p className={styles.signupRow}>
          Don't have an account?
          <a href="/register" className={styles.signupLink}>
            Sign up for free
          </a>
        </p>

        <div className={styles.trustBadges}>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">lock</span>
            SSL Secured
          </span>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">verified_user</span>
            Data Protection
          </span>
          <span className={styles.trustBadge}>
            <span className="material-symbols-outlined">shield</span>
            Two-factor Auth
          </span>
        </div>
      </form>
    </>
  );
};

export default LoginForm;
