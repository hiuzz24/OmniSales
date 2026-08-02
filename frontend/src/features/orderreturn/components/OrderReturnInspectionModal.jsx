import { AlertTriangle, Check, CheckCircle2, ClipboardCheck, X } from 'lucide-react';
import styles from '../pages/OrderReturnDetailPage.module.css';

const OrderReturnInspectionModal = ({
  inspection,
  working,
  onClose,
  onUpdate,
  onSubmit,
}) => {
  const totals = inspection.reduce((result, item) => ({
    approved: result.approved + item.approvedQuantity,
    received: result.received + item.receivedQuantity,
    restockable: result.restockable + item.restockableQuantity,
    damaged: result.damaged + item.damagedQuantity,
    missing: result.missing + item.missingQuantity,
  }), {
    approved: 0,
    received: 0,
    restockable: 0,
    damaged: 0,
    missing: 0,
  });

  return (
    <div className={styles.overlay} role="presentation" onMouseDown={() => !working && onClose()}>
      <section
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="inspection-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className={styles.modalHeader}>
          <div className={styles.modalHeading}>
            <span className={styles.modalIcon}><ClipboardCheck size={19} /></span>
            <div><h2 id="inspection-title">Nhận & kiểm hàng</h2><p>Phân loại số lượng thực tế nhận từ khách.</p></div>
          </div>
          <button type="button" className={styles.iconButton} onClick={onClose} disabled={working} aria-label="Đóng">
            <X size={18} />
          </button>
        </header>

        <div className={styles.inspectionRules}>
          <span><CheckCircle2 size={15} /> Nhận = Đạt + Hỏng</span>
          <span><CheckCircle2 size={15} /> Nhận + Thiếu = Duyệt</span>
        </div>

        <div className={styles.modalBody}>
          {inspection.map((item, index) => {
            const rowValid = item.receivedQuantity === item.restockableQuantity + item.damagedQuantity
              && item.receivedQuantity + item.missingQuantity === item.approvedQuantity;
            return (
              <article className={`${styles.inspectionRow} ${!rowValid ? styles.inspectionInvalid : ''}`} key={item.returnItemId}>
                <div className={styles.inspectionProduct}>
                  <strong>{item.name}</strong>
                  <span>{item.sku || 'Không có SKU'} • Duyệt {item.approvedQuantity}</span>
                </div>
                {[
                  ['receivedQuantity', 'Đã nhận'],
                  ['restockableQuantity', 'Hàng đạt'],
                  ['damagedQuantity', 'Hàng hỏng'],
                  ['missingQuantity', 'Hàng thiếu'],
                ].map(([field, label]) => (
                  <label key={field} className={styles.quantityField}>
                    <span>{label}</span>
                    <input
                      type="number"
                      inputMode="numeric"
                      min="0"
                      max={item.approvedQuantity}
                      value={item[field]}
                      onChange={(event) => onUpdate(index, field, event.target.value)}
                    />
                  </label>
                ))}
                <span className={`${styles.rowValidation} ${rowValid ? styles.rowValid : styles.rowInvalid}`}>
                  {rowValid ? <Check size={14} /> : <AlertTriangle size={14} />}
                  {rowValid ? 'Hợp lệ' : 'Kiểm tra lại'}
                </span>
              </article>
            );
          })}
        </div>

        <div className={styles.inspectionTotals}>
          <span>Duyệt <strong>{totals.approved}</strong></span>
          <span>Nhận <strong>{totals.received}</strong></span>
          <span>Đạt <strong>{totals.restockable}</strong></span>
          <span>Hỏng <strong>{totals.damaged}</strong></span>
          <span>Thiếu <strong>{totals.missing}</strong></span>
        </div>

        <footer className={styles.modalFooter}>
          <button type="button" className={styles.secondaryButton} onClick={onClose} disabled={working}>Hủy</button>
          <button type="button" className={styles.primaryButton} onClick={onSubmit} disabled={working}>
            <Check size={17} /> Hoàn tất kiểm hàng
          </button>
        </footer>
      </section>
    </div>
  );
};

export default OrderReturnInspectionModal;
