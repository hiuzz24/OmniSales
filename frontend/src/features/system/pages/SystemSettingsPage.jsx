import { useState, useEffect } from 'react';
import {
  Settings, Bell, Shield, Info, Save, RefreshCw
} from 'lucide-react';
import { toast } from 'react-toastify';
import settingsApi from '../../../api/settingsApi';
import styles from './SystemSettingsPage.module.css';

const CATEGORIES = [
  { id: 'NOTIFICATION', label: 'Cảnh báo', icon: Bell },
  { id: 'SECURITY', label: 'Bảo mật', icon: Shield },
  { id: 'SYSTEM', label: 'Hệ thống', icon: Info },
];

const TIMEZONES = [
  { value: 'Asia/Ho_Chi_Minh', label: 'Asia/Ho_Chi_Minh (GMT+7)' },
  { value: 'Asia/Singapore', label: 'Asia/Singapore (GMT+8)' },
  { value: 'Asia/Tokyo', label: 'Asia/Tokyo (GMT+9)' },
  { value: 'Europe/London', label: 'Europe/London (GMT+0)' },
  { value: 'America/New_York', label: 'America/New_York (GMT-5)' },
  { value: 'UTC', label: 'Coordinated Universal Time (UTC)' },
];

const BOOLEAN_SETTING_KEYS = new Set([
  'notification_order_enabled',
  'notification_return_enabled',
  'notification_low_stock_enabled',
  'notification_sync_failure_enabled',
  'notification_channel_disconnected_enabled',
  'notification_email_enabled',
  'password_require_uppercase',
  'password_require_lowercase',
  'password_require_number',
  'password_require_special_character',
  'maintenance_mode',
  'backup_schedule_enabled',
]);

const NUMBER_SETTING_KEYS = new Set([
  'default_reorder_level',
  'reserved_timeout_minutes',
  'low_stock_repeat_hours',
  'notification_retention_days',
  'max_failed_login_attempts',
  'account_lock_minutes',
  'access_token_expiration_minutes',
  'refresh_token_expiration_days',
  'password_min_length',
  'password_expiration_days',
  'default_page_size',
  'audit_log_retention_days',
]);

const SystemSettingsPage = () => {
  const [activeTab, setActiveTab] = useState('NOTIFICATION');
  const [settings, setSettings] = useState([]);
  const [formValues, setFormValues] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const loadSettings = async () => {
      try {
        const data = await settingsApi.getSettings();
        if (cancelled) return;

        const loadedSettings = data || [];
        const values = {};
        loadedSettings.forEach(item => {
          values[item.key] = item.value;
        });
        setSettings(loadedSettings);
        setFormValues(values);
      } catch (error) {
        if (!cancelled) {
          console.error('Error loading settings:', error);
          toast.error('Không thể tải cấu hình hệ thống');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadSettings();
    return () => { cancelled = true; };
  }, []);

  const handleInputChange = (key, val) => {
    setFormValues(prev => ({
      ...prev,
      [key]: val
    }));
  };

  const validateValues = () => {
    // Basic validations
    if (formValues.default_reorder_level !== undefined) {
      const val = Number(formValues.default_reorder_level);
      if (isNaN(val) || val < 0) {
        toast.warning('Mức cảnh báo tồn kho tối thiểu phải là số lớn hơn hoặc bằng 0');
        return false;
      }
    }
    if (formValues.reserved_timeout_minutes !== undefined) {
      const val = Number(formValues.reserved_timeout_minutes);
      if (isNaN(val) || val <= 0) {
        toast.warning('Thời gian giữ hàng phải là số lớn hơn 0');
        return false;
      }
    }
    if (formValues.low_stock_repeat_hours !== undefined) {
      const val = Number(formValues.low_stock_repeat_hours);
      if (isNaN(val) || val <= 0) {
        toast.warning('Thời gian nhắc nhở cảnh báo phải là số lớn hơn 0');
        return false;
      }
    }
    if (formValues.notification_retention_days !== undefined) {
      const val = Number(formValues.notification_retention_days);
      if (!Number.isInteger(val) || val <= 0) {
        toast.warning('Số ngày lưu thông báo phải là số nguyên lớn hơn 0');
        return false;
      }
    }
    if (formValues.max_failed_login_attempts !== undefined) {
      const val = Number(formValues.max_failed_login_attempts);
      if (isNaN(val) || val <= 0) {
        toast.warning('Số lần đăng nhập sai tối đa phải là số lớn hơn 0');
        return false;
      }
    }
    const positiveIntegerFields = [
      ['account_lock_minutes', 'Thời gian khóa tài khoản'],
      ['access_token_expiration_minutes', 'Thời hạn access token'],
      ['refresh_token_expiration_days', 'Thời hạn refresh token'],
      ['audit_log_retention_days', 'Số ngày lưu nhật ký'],
    ];
    for (const [key, label] of positiveIntegerFields) {
      if (formValues[key] !== undefined) {
        const val = Number(formValues[key]);
        if (!Number.isInteger(val) || val <= 0) {
          toast.warning(`${label} phải là số nguyên lớn hơn 0`);
          return false;
        }
      }
    }
    if (formValues.password_min_length !== undefined) {
      const val = Number(formValues.password_min_length);
      if (!Number.isInteger(val) || val < 6 || val > 128) {
        toast.warning('Độ dài mật khẩu tối thiểu phải từ 6 đến 128 ký tự');
        return false;
      }
    }
    if (formValues.password_expiration_days !== undefined) {
      const val = Number(formValues.password_expiration_days);
      if (!Number.isInteger(val) || val < 0) {
        toast.warning('Số ngày hết hạn mật khẩu phải là số nguyên lớn hơn hoặc bằng 0');
        return false;
      }
    }
    if (formValues.default_page_size !== undefined) {
      const val = Number(formValues.default_page_size);
      if (!Number.isInteger(val) || val < 10 || val > 100) {
        toast.warning('Số bản ghi mỗi trang phải từ 10 đến 100');
        return false;
      }
    }
    return true;
  };

  const handleSave = async () => {
    if (!validateValues()) return;

    setSaving(true);
    try {
      // Build batch payload for items matching the active category
      const activeKeys = settings
        .filter(s => s.category === activeTab)
        .map(s => s.key);
        
      const payload = activeKeys.map(key => ({
        key,
        value: String(formValues[key])
      }));

      await settingsApi.updateSettingsBatch(payload);
      toast.success('Đã lưu cấu hình thành công!');
      if (activeTab === 'SYSTEM') {
        window.dispatchEvent(new Event('system-preferences:updated'));
      }
      
      // Update persistent settings state to match form values
      setSettings(prev => prev.map(s => {
        if (activeKeys.includes(s.key)) {
          return { ...s, value: formValues[s.key] };
        }
        return s;
      }));
    } catch (error) {
      console.error('Error saving settings:', error);
      toast.error('Có lỗi xảy ra khi lưu cấu hình');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    // Reset form values matching active keys back to initial settings
    const activeKeys = settings
      .filter(s => s.category === activeTab)
      .map(s => s.key);
      
    setFormValues(prev => {
      const reset = { ...prev };
      activeKeys.forEach(key => {
        const orig = settings.find(s => s.key === key);
        reset[key] = orig ? orig.value : '';
      });
      return reset;
    });
    toast.info('Đã hủy các thay đổi chưa lưu');
  };

  const renderActiveForm = () => {
    const activeSettings = settings.filter(s => s.category === activeTab);

    if (activeSettings.length === 0) {
      return <div className="text-gray-500">Không tìm thấy cấu hình nào thuộc nhóm này.</div>;
    }

    return (
      <div className={styles.formGrid}>
        {activeSettings.map(setting => {
          const isTimezone = setting.key === 'timezone';
          const isDateFormat = setting.key === 'date_format';
          const isEmail = setting.key === 'support_email';
          const isBoolean = BOOLEAN_SETTING_KEYS.has(setting.key);
          const isNumber = NUMBER_SETTING_KEYS.has(setting.key);

          return (
            <div key={setting.key} className={styles.formGroup}>
              <div className={styles.labelWrapper}>
                <label className={styles.fieldLabel}>{setting.description}</label>
                <span className={styles.fieldKey}>{setting.key}</span>
              </div>
              
              {isBoolean ? (
                <select
                  className={styles.fieldSelect}
                  value={formValues[setting.key] ?? 'false'}
                  onChange={(e) => handleInputChange(setting.key, e.target.value)}
                >
                  <option value="true">Bật</option>
                  <option value="false">Tắt</option>
                </select>
              ) : isTimezone ? (
                <select
                  className={styles.fieldSelect}
                  value={formValues[setting.key] || ''}
                  onChange={(e) => handleInputChange(setting.key, e.target.value)}
                >
                  {TIMEZONES.map(tz => (
                    <option key={tz.value} value={tz.value}>{tz.label}</option>
                  ))}
                </select>
              ) : isDateFormat ? (
                <select
                  className={styles.fieldSelect}
                  value={formValues[setting.key] || 'dd/MM/yyyy'}
                  onChange={(e) => handleInputChange(setting.key, e.target.value)}
                >
                  <option value="dd/MM/yyyy">dd/MM/yyyy</option>
                  <option value="MM/dd/yyyy">MM/dd/yyyy</option>
                  <option value="yyyy-MM-dd">yyyy-MM-dd</option>
                </select>
              ) : (
                <input
                  type={isNumber ? 'number' : isEmail ? 'email' : 'text'}
                  className={styles.fieldInput}
                  value={formValues[setting.key] !== undefined ? formValues[setting.key] : ''}
                  onChange={(e) => handleInputChange(setting.key, e.target.value)}
                  min={isNumber ? (setting.key === 'password_expiration_days' ? "0" : "1") : undefined}
                />
              )}
              <p className={styles.fieldDescription}>Phân hệ: {setting.category}</p>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <div className={styles.titleArea}>
          <h1>
            <Settings size={28} className="text-blue-600" />
            Cấu hình hệ thống
          </h1>
          <p>Điều chỉnh các thông số vận hành, cảnh báo, múi giờ và giới hạn bảo mật</p>
        </div>
      </div>

      {loading ? (
        <div className={styles.loadingText}>
          <RefreshCw size={24} className="animate-spin text-blue-600" />
          Tải cấu hình hệ thống...
        </div>
      ) : (
        <div className={styles.layout}>
          {/* Sidebar Nav */}
          <div className={styles.sidebarNav}>
            {CATEGORIES.map(category => {
              const Icon = category.icon;
              return (
                <button
                  key={category.id}
                  className={`${styles.navItem} ${activeTab === category.id ? styles.navItemActive : ''}`}
                  onClick={() => setActiveTab(category.id)}
                >
                  <Icon size={18} />
                  {category.label}
                </button>
              );
            })}
          </div>

          {/* Form Content */}
          <div className={styles.contentCard}>
            <h3 className={styles.categoryTitle}>
              {CATEGORIES.find(c => c.id === activeTab)?.label}
            </h3>

            {renderActiveForm()}

            {/* Actions */}
            <div className={styles.actionsBar}>
              <button 
                type="button" 
                className={styles.btnCancel} 
                onClick={handleCancel}
                disabled={saving}
              >
                Hủy bỏ
              </button>
              <button 
                type="button" 
                className={styles.btnSave} 
                onClick={handleSave}
                disabled={saving}
              >
                <Save size={16} />
                {saving ? 'Đang lưu...' : 'Lưu cấu hình'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SystemSettingsPage;
