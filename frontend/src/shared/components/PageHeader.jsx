import styles from './PageHeader.module.css';

const PageHeader = ({ title, subtitle, icon, actions }) => {
  const HeaderIcon = icon;

  return (
    <div className={styles.headerContainer}>
      <div className={styles.titleWrapper}>
        {icon && (
          <div className={styles.iconWrapper}>
            {typeof icon === 'function' ? <HeaderIcon size={20} /> : icon}
          </div>
        )}
        <div className={styles.titleText}>
          <h1 className={styles.title}>{title}</h1>
          {subtitle && (
            <p className={styles.subtitle}>{subtitle}</p>
          )}
        </div>
      </div>
      {actions && (
        <div className={styles.actions}>
          {actions}
        </div>
      )}
    </div>
  );
};

export default PageHeader;

