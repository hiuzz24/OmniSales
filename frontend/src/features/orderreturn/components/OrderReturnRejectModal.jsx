import { RefreshCw, X } from 'lucide-react';
import pageStyles from '../pages/OrderReturnDetailPage.module.css';
import modalStyles from './OrderReturnModal.module.css';

const styles = { ...pageStyles, ...modalStyles };

/** Thu thập reason code hợp lệ và ghi chú để từ chối yêu cầu trả hàng. */
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
          <div><h2 id="reject-title">Từ chối yêu cầu</h2><p>Lý do sẽ được gửi sang sàn bán hàng.</p></div>
        </div>
        <button type="button" className={styles.iconButton} onClick={onClose} disabled={working} aria-label="Đóng">
          <X size={18} />
        </button>
      </header>
      <div className={styles.rejectBody}>
        {loading && <p className={styles.rejectStatus}>Đang tải lý do từ sàn...</p>}
        {!loading && loadError && (
          <div className={styles.rejectLoadError}>
            <span>{loadError}</span>
            <button type="button" onClick={onReload} disabled={working}>
              <RefreshCw size={15} /> Tải lại
            </button>
          </div>
        )}
        {!loading && !loadError && options?.requiresReasonCode && (
          <>
            <label htmlFor="return-reject-code">Lý do từ chối</label>
            <select
              id="return-reject-code"
              value={reasonCode}
              onChange={(event) => onReasonCodeChange(event.target.value)}
              autoFocus
            >
              <option value="">Chọn lý do từ sàn</option>
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
              {options?.requiresReasonCode ? 'Ghi chú' : 'Lý do từ chối'}
              {options?.requiresReasonCode ? ' (không bắt buộc)' : ''}
            </label>
            <textarea
              id="return-reject-comment"
              value={comment}
              onChange={(event) => onCommentChange(event.target.value)}
              rows="4"
              maxLength="500"
              placeholder={options?.requiresReasonCode ? 'Nhập ghi chú bổ sung...' : 'Nhập lý do cụ thể...'}
              autoFocus={!options?.requiresReasonCode}
            />
            <span>{comment.length}/500</span>
          </>
        )}
      </div>
      <footer className={styles.modalFooter}>
        <button type="button" className={styles.secondaryButton} onClick={onClose} disabled={working}>Hủy</button>
        <button
          type="button"
          className={styles.dangerButton}
          onClick={onSubmit}
          disabled={working || loading || Boolean(loadError) || Boolean(options?.unavailableReason)
            || (options?.requiresReasonCode ? !reasonCode : !comment.trim())}
        >
          <X size={17} /> Xác nhận từ chối
        </button>
      </footer>
    </section>
  </div>
);

export default OrderReturnRejectModal;
