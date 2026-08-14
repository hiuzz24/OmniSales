import { useRef, useState, useEffect } from 'react';
import { Upload, X, FileSpreadsheet, Download, CheckCircle2, XCircle, FileUp, AlertTriangle } from 'lucide-react';
import productApi from '../../../api/productApi';
import categoryService from '../services/categoryService';
import { parseImportFile, IMPORT_TEMPLATE_COLUMNS } from '../utils/importProducts';
import { downloadImportTemplate } from '../utils/downloadImportTemplate';
import styles from './ImportProductsModal.module.css';

/** Định dạng dung lượng file để hiển thị dễ đọc. */
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
};

/** Kiểm tra file Excel và gửi import sản phẩm vào OSMS. */
const ImportProductsModal = ({ isOpen, onClose, onSuccess }) => {
  const [step, setStep] = useState(1);
  const [file, setFile] = useState(null);
  const [parseError, setParseError] = useState('');
  const [parsed, setParsed] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef(null);
  const [categories, setCategories] = useState([]);
  const [categoryErrors, setCategoryErrors] = useState([]);

  useEffect(() => {
    if (isOpen) {
      categoryService.getAll().then(setCategories).catch(() => setCategories([]));
    }
  }, [isOpen]);

  // Xóa file, lỗi và kết quả của lần import hiện tại.
  const reset = () => {
    setStep(1);
    setFile(null);
    setParseError('');
    setParsed(null);
    setSubmitting(false);
    setResult(null);
    setDragOver(false);
    setCategoryErrors([]);
  };

  // Reset trạng thái trước khi đóng modal.
  const handleClose = () => {
    reset();
    onClose();
  };

  // Mở trình chọn file Excel của trình duyệt.
  const handlePickFile = () => {
    fileInputRef.current?.click();
  };

  // Kiểm tra định dạng và nội dung cơ bản của file được chọn.
  const handleFileChange = async (selected) => {
    if (!selected) return;
    setParseError('');
    setParsed(null);
    setCategoryErrors([]);
    setFile(selected);
    const response = await parseImportFile(selected);
    if (!response.ok) {
      setParseError(response.message);
      return;
    }
    if (categories.length > 0) {
      const usedCatNames = [...new Set(response.rows.map((r) => r.categoryName).filter(Boolean))];
      const activeCats = categories.filter((c) => !c.status || c.status === 'ACTIVE');
      const validCatNames = new Set(activeCats.map((c) => c.name.toLowerCase()));
      const bad = usedCatNames.filter((n) => !validCatNames.has(n.toLowerCase()));
      if (bad.length > 0) {
        setCategoryErrors(bad);
        setParseError(
          `Danh mục không tồn tại hoặc đã ngưng hoạt động: ${bad.join(', ')}\n\nCác danh mục hợp lệ: ${activeCats.map((c) => c.name).join(', ')}`,
        );
        setFile(null);
        return;
      }
    }
    setParsed(response);
    setStep(2);
  };

  // Nhận file Excel được kéo thả vào modal.
  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    const dropped = e.dataTransfer.files?.[0];
    if (dropped) handleFileChange(dropped);
  };

  // Gửi file hợp lệ tới endpoint import và hiển thị kết quả.
  const handleSubmit = async () => {
    if (!file) return;
    setSubmitting(true);
    try {
      const response = await productApi.importExcel(file);
      const data = response.data?.data || response.data || response;
      setResult({ ok: true, data });
      setStep(3);
      if (onSuccess) onSuccess();
    } catch (err) {
      const message =
        err?.response?.data?.message ||
        err?.message ||
        'Có lỗi khi import. Vui lòng thử lại.';
      setResult({ ok: false, message });
      setStep(3);
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} onClick={handleClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>Import sản phẩm từ Excel</h2>
            <p className={styles.subtitle}>
              Tải lên file .xlsx để thêm mới hoặc cập nhật sản phẩm theo SKU
            </p>
          </div>
          <button className={styles.closeBtn} onClick={handleClose} aria-label="Đóng">
            <X size={20} />
          </button>
        </div>

        <div className={styles.steps}>
          <div
            className={`${styles.step} ${step === 1 ? styles.stepActive : ''} ${
              step > 1 ? styles.stepDone : ''
            }`}
          >
            <span className={styles.stepNumber}>1</span>
            <span className={styles.stepLabel}>Chọn file</span>
          </div>
          <div className={styles.stepDivider} />
          <div
            className={`${styles.step} ${step === 2 ? styles.stepActive : ''} ${
              step > 2 ? styles.stepDone : ''
            }`}
          >
            <span className={styles.stepNumber}>2</span>
            <span className={styles.stepLabel}>Xác nhận</span>
          </div>
          <div className={styles.stepDivider} />
          <div
            className={`${styles.step} ${step === 3 ? styles.stepActive : ''}`}
          >
            <span className={styles.stepNumber}>3</span>
            <span className={styles.stepLabel}>Hoàn tất</span>
          </div>
        </div>

        {step === 1 && (
          <div className={styles.body}>
            <div
              className={`${styles.uploadArea} ${
                dragOver ? styles.uploadAreaActive : ''
              }`}
              onClick={handlePickFile}
              onDragOver={(e) => {
                e.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
              role="button"
              tabIndex={0}
            >
              <FileUp size={36} className={styles.uploadIcon} />
              <h3 className={styles.uploadTitle}>Kéo thả file Excel vào đây</h3>
              <p className={styles.uploadHint}>
                hoặc nhấp để chọn file .xlsx / .xls (tối đa 20MB)
              </p>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                onChange={(e) => handleFileChange(e.target.files?.[0])}
                className={styles.hiddenInput}
              />
            </div>

            {file && (
              <div className={styles.fileInfo}>
                <div>
                  <div className={styles.fileInfoName}>
                    <FileSpreadsheet size={18} />
                    {file.name}
                  </div>
                  <div className={styles.fileInfoSize}>{formatFileSize(file.size)}</div>
                </div>
                <button
                  type="button"
                  className={styles.fileInfoRemove}
                  onClick={() => {
                    setFile(null);
                    setParseError('');
                  }}
                >
                  Xóa
                </button>
              </div>
            )}

            {parseError && (
              <div className={styles.confirmBox} style={{ marginTop: 12 }}>
                <XCircle size={20} />
                <div>{parseError}</div>
              </div>
            )}

            <div className={styles.uploadActions}>
              <button
                type="button"
                className={styles.templateBtn}
                onClick={(e) => {
                  e.stopPropagation();
                  downloadImportTemplate();
                }}
              >
                <Download size={14} />
                Tải template mẫu
              </button>
            </div>
          </div>
        )}

        {step === 2 && parsed && (
          <div className={styles.body}>
            <div className={styles.confirmBox}>
              <FileSpreadsheet size={20} />
              <div>
                Sẽ import <strong>{parsed.totalRows} dòng</strong> từ file{' '}
                <strong>{file?.name}</strong>. Nếu SKU sản phẩm đã tồn tại, hệ thống sẽ{' '}
                <strong>cập nhật</strong>; nếu chưa có, hệ thống sẽ <strong>tạo mới</strong>.
                Toàn bộ sẽ thực hiện trong 1 transaction - nếu có lỗi sẽ rollback toàn bộ.
              </div>
            </div>

            <div className={styles.statGrid}>
              <div className={styles.statCard}>
                <span className={styles.statValue}>{parsed.totalRows}</span>
                <span className={styles.statLabel}>Tổng dòng</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statValue}>{parsed.columnKeys.length}</span>
                <span className={styles.statLabel}>Cột nhận diện</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statValue}>
                  {new Set(parsed.rows.map((r) => r.productSku)).size}
                </span>
                <span className={styles.statLabel}>Sản phẩm</span>
              </div>
            </div>

            <h4 style={{ fontSize: 13, fontWeight: 600, color: '#475569', margin: '0 0 8px' }}>
              Xem trước {Math.min(parsed.rows.length, 10)} dòng đầu
              {parsed.totalRows > 10 && ` (trong tổng số ${parsed.totalRows} dòng)`}
              {' — '}
              <span style={{ fontWeight: 400, color: '#64748b' }}>
                mỗi dòng = 1 biến thể; trùng SKU cha sẽ được gộp vào cùng 1 sản phẩm
              </span>
            </h4>
            <div className={styles.previewTable}>
              <table>
                <thead>
                  <tr>
                    {IMPORT_TEMPLATE_COLUMNS.map((col) => (
                      <th key={col.key}>
                        {col.label}
                        {col.required && <span style={{ color: '#dc2626' }}> *</span>}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {parsed.rows.slice(0, 10).map((r, idx) => (
                    <tr key={idx}>
                      {IMPORT_TEMPLATE_COLUMNS.map((col) => (
                        <td key={col.key} title={r[col.key] || ''}>
                          {r[col.key] || '-'}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {step === 3 && result && (
          <div className={styles.body}>
            <div className={styles.resultBox}>
              {result.ok ? (
                <>
                  <div className={`${styles.resultIcon} ${styles.resultIconSuccess}`}>
                    <CheckCircle2 size={36} />
                  </div>
                  <h3 className={styles.resultTitle}>Import thành công</h3>
                  <p className={styles.resultMessage}>
                    {result.data?.createdCount + result.data?.updatedCount || 0} sản phẩm đã được xử lý
                  </p>
                  <div className={styles.resultStats}>
                    <div className={styles.resultStatItem}>
                      <span className={styles.resultStatValue}>
                        {result.data?.createdCount || 0}
                      </span>
                      <span className={styles.resultStatLabel}>Tạo mới</span>
                    </div>
                    <div className={styles.resultStatItem}>
                      <span className={styles.resultStatValue}>
                        {result.data?.updatedCount || 0}
                      </span>
                      <span className={styles.resultStatLabel}>Cập nhật</span>
                    </div>
                    <div className={styles.resultStatItem}>
                      <span className={styles.resultStatValue}>
                        {result.data?.totalRows || 0}
                      </span>
                      <span className={styles.resultStatLabel}>Tổng dòng</span>
                    </div>
                  </div>
                </>
              ) : (
                <>
                  <div className={`${styles.resultIcon} ${styles.resultIconError}`}>
                    <XCircle size={36} />
                  </div>
                  <h3 className={styles.resultTitle}>Import thất bại</h3>
                  <p className={styles.resultMessage}>
                    Toàn bộ thay đổi đã được rollback. Vui lòng sửa lỗi và thử lại.
                  </p>
                  <div className={styles.errorList}>
                    <pre>{result.message}</pre>
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        <div className={styles.footer}>
          {step === 1 && (
            <>
              <button type="button" className={styles.cancelBtn} onClick={handleClose}>
                Hủy
              </button>
            </>
          )}
          {step === 2 && (
            <>
              <button
                type="button"
                className={styles.cancelBtn}
                onClick={() => {
                  setStep(1);
                  setFile(null);
                  setParsed(null);
                }}
                disabled={submitting}
              >
                Quay lại
              </button>
              <button
                type="button"
                className={styles.primaryBtn}
                onClick={handleSubmit}
                disabled={submitting}
              >
                <Upload size={16} />
                {submitting ? 'Đang import...' : 'Import ngay'}
              </button>
            </>
          )}
          {step === 3 && (
            <button
              type="button"
              className={`${styles.primaryBtn} ${styles.successBtn}`}
              onClick={handleClose}
            >
              Đóng
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default ImportProductsModal;
