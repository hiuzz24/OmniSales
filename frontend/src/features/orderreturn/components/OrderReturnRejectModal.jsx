import { RefreshCw, X } from 'lucide-react';
import pageStyles from '../pages/OrderReturnDetailPage.module.css';
import modalStyles from './OrderReturnModal.module.css';

const styles = { ...pageStyles, ...modalStyles };

const OrderReturnRejectModal = ({
  options,
  loading,
  loadError,
  reasonCode,
  comment,
  working,
  onReasonCodeChange,
  onCommentChange,
  onReload,
  onClose,
  onSubmit,
}) => (
  <div className={styles.overlay} role="presentation" onMouseDown={() => !working && onClose()}>
    <section
      className={`${styles.modal} ${styles.rejectModal}`}
      role="dialog"
      aria-modal="true"
      aria-labelledby="reject-title"
      onMouseDown={(event) => event.stopPropagation()}
    >
      <header className={styles.modalHeader}>
        <div className={styles.modalHeading}>
          <span className={`${styles.modalIcon} ${styles.modalIconDanger}`}><X size={19} /></span>
          <div><h2 id="reject-title">Tá»« chá»‘i yÃªu cáº§u</h2><p>LÃ½ do sáº½ Ä‘Æ°á»£c gá»­i sang sÃ n bÃ¡n hÃ ng.</p></div>
        </div>
        <button type="button" className={styles.iconButton} onClick={onClose} disabled={working} aria-label="ÄÃ³ng">
          <X size={18} />
        </button>
      </header>
      <div className={styles.rejectBody}>
        {loading && <p className={styles.rejectStatus}>Äang táº£i lÃ½ do tá»« sÃ n...</p>}
        {!loading && loadError && (
          <div className={styles.rejectLoadError}>
            <span>{loadError}</span>
            <button type="button" onClick={onReload} disabled={working}>
              <RefreshCw size={15} /> Táº£i láº¡i
            </button>
          </div>
        )}
        {!loading && !loadError && options?.requiresReasonCode && (
          <>
            <label htmlFor="return-reject-code">LÃ½ do tá»« chá»‘i</label>
            <select
              id="return-reject-code"
              value={reasonCode}
              onChange={(event) => onReasonCodeChange(event.target.value)}
              autoFocus
            >
              <option value="">Chá»n lÃ½ do tá»« sÃ n</option>
              {(options.options ?? []).map((option) => (
                <option key={option.code} value={option.code}>{option.label}</option>
              ))}
            </select>
            {options.unavailableReason && (
              <p className={styles.rejectUnavailable}>{options.unavailableReason}</p>
            )}
          </>
        )}
        {!loading && !loadError && (
          <>
            <label htmlFor="return-reject-comment">
              {options?.requiresReasonCode ? 'Ghi chÃº' : 'LÃ½ do tá»« chá»‘i'}
              {options?.requiresReasonCode ? ' (khÃ´ng báº¯t buá»™c)' : ''}
            </label>
            <textarea
              id="return-reject-comment"
              value={comment}
              onChange={(event) => onCommentChange(event.target.value)}
              rows="4"
              maxLength="500"
              placeholder={options?.requiresReasonCode ? 'Nháº­p ghi chÃº bá»• sung...' : 'Nháº­p lÃ½ do cá»¥ thá»ƒ...'}
              autoFocus={!options?.requiresReasonCode}
            />
            <span>{comment.length}/500</span>
          </>
        )}
      </div>
      <footer className={styles.modalFooter}>
        <button type="button" className={styles.secondaryButton} onClick={onClose} disabled={working}>Há»§y</button>
        <button
          type="button"
          className={styles.dangerButton}
          onClick={onSubmit}
          disabled={working || loading || Boolean(loadError) || Boolean(options?.unavailableReason)
            || (options?.requiresReasonCode ? !reasonCode : !comment.trim())}
        >
          <X size={17} /> XÃ¡c nháº­n tá»« chá»‘i
        </button>
      </footer>
    </section>
  </div>
);

export default OrderReturnRejectModal;
