import styles from './PageHeader.module.css';

const PageHeader = ({ title, subtitle, actions }) => {
  return (
    <div className={styles.headerContainer}>
      <div className={styles.titleWrapper}>
        <h1 className={styles.title}>{title}</h1>
        {subtitle && (
          <p className={styles.subtitle}>{subtitle}</p>
        )}
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

