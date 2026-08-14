import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { toast } from 'react-toastify';
import channelApi from '../../../api/channelApi';
import warehouseApi from '../../../api/warehouseApi';
import ChannelSettingsFields from './ChannelSettingsFields';
import OAuthConnectionSection from './OAuthConnectionSection';
import ShopifyConnectionSection from './ShopifyConnectionSection';
import { canonicalShopifyDomain } from '../utils/shopifyShop';
import styles from './ChannelFormModal.module.css';

const PLATFORMS = [
  { value: 'SHOPEE', label: 'Shopee' },
  { value: 'TIKTOK', label: 'TikTok Shop' },
  { value: 'LAZADA', label: 'Lazada' },
  { value: 'SHOPIFY', label: 'Shopify' },
  { value: 'MANUAL', label: 'Thủ công (Manual)' },
];

const PLATFORM_COLORS = {
  SHOPEE: '#ee4d2d', TIKTOK: '#010101', LAZADA: '#0f146d', SHOPIFY: '#5e8e3e', MANUAL: '#6b7280',
};

const initialForm = (channel) => ({
  platform: channel?.platform || '',
  displayName: channel?.displayName || '',
  commissionRate: channel?.commissionRate ?? 0,
  shopDomain: channel?.metadata?.shop || channel?.metadata?.shopDomain || '',
  defaultWarehouseId: channel?.metadata?.defaultWarehouseId || '',
  lazadaWarehouseCode: channel?.metadata?.lazadaWarehouseCode || '',
  shopCipher: channel?.metadata?.shopCipher || channel?.metadata?.shop_cipher || '',
  tiktokWarehouseId: channel?.metadata?.tiktokWarehouseId || '',
});

/** Thu thập cấu hình kênh và khởi tạo luồng OAuth theo platform. */
const ChannelFormModal = ({ mode = 'create', channelData = null, onClose, onSuccess }) => {
  const isEdit = mode === 'edit';
  const [form, setForm] = useState(() => initialForm(channelData));
  const [errors, setErrors] = useState({});
  const [warehouses, setWarehouses] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRedirecting, setIsRedirecting] = useState(false);
  const isShopify = form.platform === 'SHOPIFY';
  const isLazada = form.platform === 'LAZADA';
  const isTikTok = form.platform === 'TIKTOK';

  useEffect(() => {
    if (!isEdit) return;
    warehouseApi.getAll()
      .then((response) => {
        const data = response.data?.data || response.data || response;
        setWarehouses(Array.isArray(data) ? data : data.content ?? []);
      })
      .catch(() => setWarehouses([]));
  }, [isEdit]);

  // Cập nhật một field và xóa lỗi validation tương ứng.
  const handleChange = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => current[field] ? { ...current, [field]: '' } : current);
  };

  // Kiểm tra các field bắt buộc theo platform và chế độ modal.
  const validate = () => {
    const nextErrors = {};
    if (!form.platform) nextErrors.platform = 'Vui lòng chọn nền tảng';
    if (!form.displayName.trim()) nextErrors.displayName = 'Tên hiển thị không được để trống';
    if (isTikTok && isEdit && !form.shopCipher.trim()) nextErrors.shopCipher = 'TikTok Shop Cipher không được để trống';
    const commissionRate = Number(form.commissionRate);
    if (Number.isNaN(commissionRate) || commissionRate < 0 || commissionRate > 100) {
      nextErrors.commissionRate = 'Hoa hồng phải từ 0 đến 100';
    }
    return nextErrors;
  };

  // Lưu kênh thủ công hoặc cập nhật cấu hình kênh hiện tại.
  const handleSubmit = async (event) => {
    event.preventDefault();
    if ((isShopify || isLazada || isTikTok) && !isEdit) return;
    const nextErrors = validate();
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }

    const metadata = {
      ...(channelData?.metadata || {}),
      ...(form.shopDomain ? { shopDomain: form.shopDomain } : {}),
      defaultWarehouseId: form.defaultWarehouseId || null,
      lazadaWarehouseCode: form.lazadaWarehouseCode || null,
      shopCipher: form.shopCipher || null,
      tiktokWarehouseId: form.tiktokWarehouseId || null,
    };
    setIsSubmitting(true);
    try {
      const payload = {
        platform: form.platform,
        displayName: form.displayName.trim(),
        commissionRate: Number(form.commissionRate),
        metadata,
      };
      if (isEdit) await channelApi.update(channelData.id, payload);
      else await channelApi.create(payload);
      toast.success(isEdit ? 'Cập nhật kênh thành công!' : 'Thêm kênh mới thành công!');
      onSuccess();
      onClose();
    } catch (error) {
      toast.error(error.response?.data?.message || 'Đã xảy ra lỗi. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // Lấy URL ủy quyền từ backend rồi chuyển trình duyệt tới platform.
  const redirectToOAuth = async (authorize, fallbackMessage) => {
    setIsRedirecting(true);
    try {
      const response = await authorize();
      const url = response?.data?.data?.url || response?.data?.url;
      if (!url) throw new Error('Không nhận được URL xác thực từ server');
      window.location.href = url;
    } catch (error) {
      setIsRedirecting(false);
      toast.error(error.response?.data?.message || error.message || fallbackMessage);
    }
  };

  // Chuẩn hóa tên miền shop và bắt đầu OAuth Shopify.
  const handleShopifyConnect = () => {
    try {
      const domain = canonicalShopifyDomain(form.shopDomain);
      handleChange('shopDomain', domain);
      redirectToOAuth(() => channelApi.authorizeShopify(domain), 'Không thể kết nối với Shopify.');
    } catch (error) {
      setErrors((current) => ({ ...current, shopDomain: error.message }));
    }
  };

  // Bắt đầu OAuth Lazada bằng URL do backend tạo.
  const handleLazadaConnect = () => redirectToOAuth(
    () => channelApi.authorizeLazada(),
    'Không thể kết nối với Lazada.',
  );

  // Chuyển tới trang ủy quyền TikTok đã cấu hình cho ứng dụng.
  const handleTikTokConnect = () => {
    const url = import.meta.env.VITE_TIKTOK_AUTHORIZE_URL;
    if (!url) {
      toast.error('Thiếu cấu hình VITE_TIKTOK_AUTHORIZE_URL.');
      return;
    }
    setIsRedirecting(true);
    window.location.href = url;
  };

  // Render phần kết nối OAuth phù hợp với platform và trạng thái reconnect.
  const oauthSection = (platform, reconnect = false) => {
    const lazada = platform === 'LAZADA';
    const platformName = lazada ? 'Lazada' : 'TikTok Shop';
    return (
      <OAuthConnectionSection
        platformName={platformName}
        description={reconnect
          ? `Kết nối lại ${platformName} để cấp lại quyền truy cập cho kênh.`
          : `Bạn sẽ được chuyển đến ${platformName} để cấp quyền và kết nối shop.`}
        color={lazada ? '#0f146d' : '#010101'}
        buttonLabel={`${reconnect ? 'Kết nối lại' : 'Kết nối với'} ${platformName}`}
        isRedirecting={isRedirecting}
        showCancel={!reconnect}
        onConnect={lazada ? handleLazadaConnect : handleTikTokConnect}
        onCancel={onClose}
      />
    );
  };

  return (
    <div className={styles.overlay} onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <div className={styles.headerLeft}>
            {form.platform && <span className={styles.platformDot} style={{ background: PLATFORM_COLORS[form.platform] }} />}
            <h2 className={styles.modalTitle}>{isEdit ? 'Chỉnh sửa kênh bán hàng' : 'Thêm kênh bán hàng mới'}</h2>
          </div>
          <button type="button" className={styles.closeBtn} onClick={onClose} aria-label="Đóng"><X size={20} /></button>
        </div>

        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.field}>
            <label className={styles.label}>Nền tảng <span className={styles.required}>*</span></label>
            <select
              className={`${styles.select} ${errors.platform ? styles.inputError : ''}`}
              value={form.platform}
              onChange={(event) => handleChange('platform', event.target.value)}
              disabled={isEdit}
            >
              <option value="">-- Chọn nền tảng --</option>
              {PLATFORMS.map((platform) => <option key={platform.value} value={platform.value}>{platform.label}</option>)}
            </select>
            {errors.platform && <span className={styles.errorMsg}>{errors.platform}</span>}
          </div>

          {isShopify && !isEdit ? (
            <ShopifyConnectionSection
              value={form.shopDomain}
              error={errors.shopDomain}
              isRedirecting={isRedirecting}
              onChange={(value) => handleChange('shopDomain', value)}
              onConnect={handleShopifyConnect}
              onCancel={onClose}
            />
          ) : (isLazada || isTikTok) && !isEdit ? oauthSection(form.platform) : (
            <>
              <ChannelSettingsFields
                form={form}
                errors={errors}
                warehouses={warehouses}
                isEdit={isEdit}
                onChange={handleChange}
              />
              {(isLazada || isTikTok) && isEdit && oauthSection(form.platform, true)}
              <div className={styles.actions}>
                <button type="button" className={styles.cancelBtn} onClick={onClose} disabled={isSubmitting}>Hủy</button>
                <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>
                  {isSubmitting ? <><span className={styles.spinner} /> Đang cập nhật...</> : isEdit ? 'Cập nhật' : 'Thêm kênh'}
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
