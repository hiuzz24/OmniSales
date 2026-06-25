import { useState } from 'react';
import { X, ExternalLink, Loader2 } from 'lucide-react';
import { toast } from 'react-toastify';
import channelApi from '../../../api/channelApi';
import styles from './ChannelFormModal.module.css';

const PLATFORMS = [
  { value: 'SHOPEE', label: 'Shopee' },
  { value: 'TIKTOK', label: 'TikTok Shop' },
  { value: 'LAZADA', label: 'Lazada' },
  { value: 'SHOPIFY', label: 'Shopify' },
  { value: 'MANUAL', label: 'Thủ công (Manual)' },
];

const PLATFORM_COLORS = {
  SHOPEE: '#ee4d2d',
  TIKTOK: '#010101',
  LAZADA: '#0f146d',
  SHOPIFY: '#96bf48',
  MANUAL: '#6b7280',
};

const ChannelFormModal = ({ mode = 'create', channelData = null, onClose, onSuccess }) => {
  const isEdit = mode === 'edit';

  const [form, setForm] = useState({
    platform: channelData?.platform || '',
    displayName: channelData?.displayName || '',
    commissionRate: channelData?.commissionRate ?? 0,
    shopDomain: channelData?.metadata?.shop || channelData?.metadata?.shopDomain || '',
  });
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRedirecting, setIsRedirecting] = useState(false);

  const isShopify = form.platform === 'SHOPIFY';
  const isLazada = form.platform === 'LAZADA';

  const handleChange = (field, value) => {
    setForm(prev => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors(prev => ({ ...prev, [field]: '' }));
  };

  const validate = () => {
    const errs = {};
    if (!form.platform) errs.platform = 'Vui lòng chọn nền tảng';
    if (!form.displayName.trim()) errs.displayName = 'Tên hiển thị không được để trống';
    const rate = Number(form.commissionRate);
    if (isNaN(rate) || rate < 0 || rate > 100) errs.commissionRate = 'Hoa hồng phải từ 0 đến 100';
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if ((isShopify || isLazada) && !isEdit) return;

    const errs = validate();
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }

    const payload = {
      platform: form.platform,
      displayName: form.displayName.trim(),
      commissionRate: Number(form.commissionRate),
      metadata: form.shopDomain ? { shopDomain: form.shopDomain } : {},
    };

    setIsSubmitting(true);
    try {
      if (isEdit) {
        await channelApi.update(channelData.id, payload);
        toast.success('Cập nhật kênh thành công!');
      } else {
        await channelApi.create(payload);
        toast.success('Thêm kênh mới thành công!');
      }
      onSuccess();
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message || 'Đã xảy ra lỗi. Vui lòng thử lại.';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleShopifyConnect = async () => {
    const domain = form.shopDomain.trim();
    if (!domain) {
      setErrors(prev => ({ ...prev, shopDomain: 'Vui lòng nhập Shop Domain' }));
      return;
    }
    setIsRedirecting(true);
    try {
      const res = await channelApi.authorizeShopify(domain);
      const url = res?.data?.data?.url || res?.data?.url;
      if (!url) throw new Error('Không nhận được URL xác thực từ server');
      window.location.href = url;
    } catch (err) {
      setIsRedirecting(false);
      const msg = err.response?.data?.message || err.message || 'Không thể kết nối với Shopify.';
      toast.error(msg);
    }
  };

  const handleLazadaConnect = async () => {
    setIsRedirecting(true);
    try {
      const res = await channelApi.authorizeLazada();
      const url = res?.data?.data?.url || res?.data?.url;
      if (!url) throw new Error('Không nhận được URL xác thực từ server');
      window.location.href = url;
    } catch (err) {
      setIsRedirecting(false);
      const msg = err.response?.data?.message || err.message || 'Không thể kết nối với Lazada.';
      toast.error(msg);
    }
  };

  const getSubmitButtonText = () => {
    if (isSubmitting) return <><span className={styles.spinner} /> {isEdit ? 'Đang cập nhật...' : 'Đang kết nối...'}</>;
    if (isEdit) return 'Cập nhật';
    if (!form.platform || form.platform === 'MANUAL') return 'Thêm kênh';
    const platformLabel = PLATFORMS.find(p => p.value === form.platform)?.label;
    return `Kết nối với ${platformLabel}`;
  };

  return (
    <div className={styles.overlay} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <div className={styles.headerLeft}>
            {form.platform && (
              <span
                className={styles.platformDot}
                style={{ background: PLATFORM_COLORS[form.platform] || '#6b7280' }}
              />
            )}
            <h2 className={styles.modalTitle}>
              {isEdit ? 'Chỉnh sửa kênh bán hàng' : 'Thêm kênh bán hàng mới'}
            </h2>
          </div>
          <button className={styles.closeBtn} onClick={onClose}><X size={20} /></button>
        </div>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.field}>
            <label className={styles.label}>Nền tảng <span className={styles.required}>*</span></label>
            <select
              className={`${styles.select} ${errors.platform ? styles.inputError : ''}`}
              value={form.platform}
              onChange={(e) => handleChange('platform', e.target.value)}
              disabled={isEdit}
            >
              <option value="">-- Chọn nền tảng --</option>
              {PLATFORMS.map(p => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
            {errors.platform && <span className={styles.errorMsg}>{errors.platform}</span>}
          </div>

          {isShopify && !isEdit ? (
            <>
              <div className={styles.field}>
                <label className={styles.label}>
                  Shop Domain <span className={styles.required}>*</span>
                </label>
                <input
                  id="shopify-domain"
                  type="text"
                  className={`${styles.input} ${errors.shopDomain ? styles.inputError : ''}`}
                  placeholder="osms-shslm3aq.myshopify.com"
                  value={form.shopDomain}
                  onChange={(e) => handleChange('shopDomain', e.target.value)}
                  disabled={isRedirecting}
                />
                {errors.shopDomain && <span className={styles.errorMsg}>{errors.shopDomain}</span>}
                <span className={styles.fieldHint}>
                  Nhập tên shop hoặc domain đầy đủ (ví dụ: <code>mystore</code> hoặc <code>mystore.myshopify.com</code>)
                </span>
              </div>

              <div className={styles.shopifyOAuthBox}>
                <div className={styles.shopifyOAuthInfo}>
                  <span className={styles.shopifyBadge}>OAuth 2.0</span>
                  <p>Bạn sẽ được chuyển đến Shopify Admin để cấp quyền. Sau khi đồng ý, hệ thống sẽ tự động kết nối.</p>
                </div>
                <div className={styles.actions}>
                  <button type="button" className={styles.cancelBtn} onClick={onClose} disabled={isRedirecting}>
                    Hủy
                  </button>
                  <button
                    type="button"
                    id="shopify-connect-btn"
                    className={styles.shopifyConnectBtn}
                    onClick={handleShopifyConnect}
                    disabled={isRedirecting}
                  >
                    {isRedirecting
                      ? <><Loader2 size={16} className={styles.spinIcon} /> Đang chuyển hướng...</>
                      : <><ExternalLink size={16} /> Kết nối với Shopify</>
                    }
                  </button>
                </div>
              </div>
            </>
          ) : isLazada && !isEdit ? (
            <div className={styles.shopifyOAuthBox}>
              <div className={styles.shopifyOAuthInfo}>
                <span className={styles.shopifyBadge} style={{background: '#0f146d', color: '#fff'}}>OAuth 2.0</span>
                <p>Bạn sẽ được chuyển đến Lazada Seller Center để cấp quyền. Sau khi đồng ý, hệ thống sẽ tự động kết nối.</p>
              </div>
              <div className={styles.actions}>
                <button type="button" className={styles.cancelBtn} onClick={onClose} disabled={isRedirecting}>
                  Hủy
                </button>
                <button
                  type="button"
                  className={styles.shopifyConnectBtn}
                  style={{background: '#0f146d', color: '#fff', borderColor: '#0f146d'}}
                  onClick={handleLazadaConnect}
                  disabled={isRedirecting}
                >
                  {isRedirecting
                    ? <><Loader2 size={16} className={styles.spinIcon} /> Đang chuyển hướng...</>
                    : <><ExternalLink size={16} /> Kết nối với Lazada</>
                  }
                </button>
              </div>
            </div>
          ) : (
            <>
              <div className={styles.field}>
                <label className={styles.label}>Tên hiển thị <span className={styles.required}>*</span></label>
                <input
                  type="text"
                  className={`${styles.input} ${errors.displayName ? styles.inputError : ''}`}
                  placeholder="Ví dụ: Shop Chính Hãng Shopee"
                  value={form.displayName}
                  onChange={(e) => handleChange('displayName', e.target.value)}
                />
                {errors.displayName && <span className={styles.errorMsg}>{errors.displayName}</span>}
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Tỉ lệ hoa hồng (%)</label>
                <div className={styles.inputGroup}>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    className={`${styles.input} ${errors.commissionRate ? styles.inputError : ''}`}
                    placeholder="0.00"
                    value={form.commissionRate}
                    onChange={(e) => handleChange('commissionRate', e.target.value)}
                  />
                  <span className={styles.inputSuffix}>%</span>
                </div>
                {errors.commissionRate && <span className={styles.errorMsg}>{errors.commissionRate}</span>}
              </div>

              <div className={styles.actions}>
                <button type="button" className={styles.cancelBtn} onClick={onClose} disabled={isSubmitting}>
                  Hủy
                </button>
                <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>
                  {getSubmitButtonText()}
                </button>
              </div>
            </>
          )}
        </form>
      </div>
    </div>
  );
};

export default ChannelFormModal;
