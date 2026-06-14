import styles from './Badge.module.css';

const Badge = ({ children, variant = 'default' }) => {
  return (
    <span className={`${styles.badge} ${styles[variant] || styles.default}`}>
      {children}
    </span>
  );
};

export default Badge;
