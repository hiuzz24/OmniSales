import { AlertTriangle, Check, CheckCircle2, ClipboardCheck, X } from 'lucide-react';
import pageStyles from '../pages/OrderReturnDetailPage.module.css';
import modalStyles from './OrderReturnModal.module.css';

const styles = { ...pageStyles, ...modalStyles };

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
            <div><h2 id="inspection-title">Nháº­n & kiá»ƒm hÃ ng</h2><p>PhÃ¢n loáº¡i sá»‘ lÆ°á»£ng thá»±c táº¿ nháº­n tá»« khÃ¡ch.</p></div>
          </div>
          <button type="button" className={styles.iconButton} onClick={onClose} disabled={working} aria-label="ÄÃ³ng">
            <X size={18} />
          </button>
        </header>

        <div className={styles.inspectionRules}>
          <span><CheckCircle2 size={15} /> Nháº­n = Äáº¡t + Há»ng</span>
          <span><CheckCircle2 size={15} /> Nháº­n + Thiáº¿u = Duyá»‡t</span>
        </div>

        <div className={styles.modalBody}>
          {inspection.map((item, index) => {
            const rowValid = item.receivedQuantity === item.restockableQuantity + item.damagedQuantity
              && item.receivedQuantity + item.missingQuantity === item.approvedQuantity;
            return (
              <article className={`${styles.inspectionRow} ${!rowValid ? styles.inspectionInvalid : ''}`} key={item.returnItemId}>
                <div className={styles.inspectionProduct}>
                  <strong>{item.name}</strong>
                  <span>{item.sku || 'KhÃ´ng cÃ³ SKU'} â€¢ Duyá»‡t {item.approvedQuantity}</span>
                </div>
                {[
                  ['receivedQuantity', 'ÄÃ£ nháº­n'],
                  ['restockableQuantity', 'HÃ ng Ä‘áº¡t'],
                  ['damagedQuantity', 'HÃ ng há»ng'],
                  ['missingQuantity', 'HÃ ng thiáº¿u'],
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
                  {rowValid ? 'Há»£p lá»‡' : 'Kiá»ƒm tra láº¡i'}
                </span>
              </article>
            );
          })}
        </div>

        <div className={styles.inspectionTotals}>
          <span>Duyá»‡t <strong>{totals.approved}</strong></span>
          <span>Nháº­n <strong>{totals.received}</strong></span>
          <span>Äáº¡t <strong>{totals.restockable}</strong></span>
          <span>Há»ng <strong>{totals.damaged}</strong></span>
          <span>Thiáº¿u <strong>{totals.missing}</strong></span>
        </div>

        <footer className={styles.modalFooter}>
          <button type="button" className={styles.secondaryButton} onClick={onClose} disabled={working}>Há»§y</button>
          <button type="button" className={styles.primaryButton} onClick={onSubmit} disabled={working}>
            <Check size={17} /> HoÃ n táº¥t kiá»ƒm hÃ ng
          </button>
        </footer>
      </section>
    </div>
  );
};

export default OrderReturnInspectionModal;
