import { useState, useEffect } from 'react';
import { toast } from 'react-toastify';
import {
  User, Mail, Phone, Shield, Calendar, CheckCircle,
  Loader2, Camera, Save, Eye, EyeOff, KeyRound, X,
  UserCircle, Clock,
} from 'lucide-react';
import userService from '../services/userService';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import styles from './ProfilePage.module.css';

const getInitials = (name) => {
  if (!name) return '?';
  const p = name.trim().split(/\s+/);
  return p.length === 1 ? p[0][0].toUpperCase()
    : (p[0][0] + p[p.length - 1][0]).toUpperCase();
};

const ROLE_LABELS = {
  [ROLES.OWNER]:        'Chủ cửa hàng',
  [ROLES.OPERATIONS]:   'Nhân viên vận hành',
  [ROLES.SALES]:        'Nhân viên bán hàng',
  [ROLES.SYSTEM_ADMIN]: 'Quản trị viên',
};

const STATUS_LABELS = {
  ACTIVE:   'Đang hoạt động',
  INACTIVE: 'Không hoạt động',
  LOCKED:   'Đã khóa',
};

const formatDate = (dateStr) => {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('vi-VN', {
    day: '2-digit', month: 'long', year: 'numeric',
  });
};

const ROLE_BG = {
  [ROLES.OWNER]:        styles.roleOwner,
  [ROLES.OPERATIONS]:   styles.roleOperations,
  [ROLES.SALES]:        styles.roleSales,
  [ROLES.SYSTEM_ADMIN]: styles.roleAdmin,
};

const PW_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).{8,}$/;

function getPwStrength(pw) {
  if (!pw) return 0;
  let score = 0;
  if (pw.length >= 8) score++;
  if (/[A-Z]/.test(pw)) score++;
  if (/[a-z]/.test(pw)) score++;
  if (/\d/.test(pw)) score++;
  if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(pw)) score++;
  if (score >= 5) return 3;
  if (score >= 3) return 2;
  return 1;
}

function PwStrengthMeter({ password }) {
  const strength = getPwStrength(password);
  const labels = ['', 'Yếu', 'Trung bình', 'Mạnh'];
  const segs = [1, 2, 3].map((i) => {
    const active = i <= strength;
    const cls = strength === 1 ? styles.weak : strength === 2 ? styles.fair : styles.good;
    return <div key={i} className={`${styles.pwStrengthSeg} ${active ? cls : ''}`} />;
  });
  if (!password) return null;
  return (
    <div>
      <div className={styles.pwStrengthBar}>{segs}</div>
      <span className={`${styles.pwStrengthLabel} ${
        strength === 1 ? styles.weak : strength === 2 ? styles.fair : styles.good
      }`}>
        {labels[strength]}
      </span>
    </div>
  );
}

function PasswordField({ id, label, value, onChange, placeholder, error }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className={styles.formGroup}>
      <label className={styles.label} htmlFor={id}>{label}</label>
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          className={`${styles.input} ${error ? styles.inputError : ''}`}
          style={{ paddingRight: 42 }}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          autoComplete="new-password"
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          style={{
            position: 'absolute', right: 12,
            background: 'none', border: 'none', cursor: 'pointer',
            color: '#94a3b8', display: 'flex', alignItems: 'center',
            padding: 0,
          }}
        >
          {visible ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
      {error && (
        <p className={styles.errorText}>
          <X size={11} style={{ flexShrink: 0 }} />
          {error}
        </p>
      )}
    </div>
  );
}

function SkeletonBlock({ width = '100%', height = 18 }) {
  return <div className={styles.skeleton} style={{ width, height }} />;
}

export default function ProfilePage() {
  const { updateUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ fullName: '', phone: '' });
  const [original, setOriginal] = useState({ fullName: '', phone: '' });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  const [showPasswordForm, setShowPasswordForm] = useState(false);
  const [pwForm, setPwForm] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [pwErrors, setPwErrors] = useState({});
  const [pwSaving, setPwSaving] = useState(false);
  const [pwSuccess, setPwSuccess] = useState(false);

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    setLoading(true);
    try {
      const data = await userService.getMyProfile();
      setProfile(data);
      setForm({ fullName: data.fullName || '', phone: data.phone || '' });
      setOriginal({ fullName: data.fullName || '', phone: data.phone || '' });
    } catch (err) {
      toast.error('Không thể tải hồ sơ: ' + (err?.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setShowSuccess(false);
  };

  const isDirty = form.fullName !== original.fullName || form.phone !== original.phone;

  const handleSave = async () => {
    const trimmed = form.fullName.trim();
    if (!trimmed) {
      toast.error('Họ tên không được để trống');
      return;
    }
    if (form.phone && !/^[0-9+\-\s]{6,20}$/.test(form.phone)) {
      toast.error('Số điện thoại không hợp lệ');
      return;
    }

    setSaving(true);
    setShowSuccess(false);
    try {
      const updated = await userService.updateMyProfile({
        fullName: trimmed,
        phone: form.phone.trim() || null,
      });
      setProfile(updated);
      setForm({ fullName: updated.fullName || '', phone: updated.phone || '' });
      setOriginal({ fullName: updated.fullName || '', phone: updated.phone || '' });
      updateUser({ fullName: updated.fullName, phone: updated.phone });
      setShowSuccess(true);
      toast.success('Cập nhật hồ sơ thành công');
      setTimeout(() => setShowSuccess(false), 4000);
    } catch (err) {
      toast.error('Lỗi khi cập nhật: ' + (err?.response?.data?.message || err.message));
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setForm({ fullName: original.fullName, phone: original.phone });
    setShowSuccess(false);
  };

  // ── Password change ──────────────────────────────────────────────

  const validatePwForm = () => {
    const errs = {};
    if (!pwForm.oldPassword) errs.oldPassword = 'Mật khẩu cũ không được để trống';
    if (!pwForm.newPassword) {
      errs.newPassword = 'Mật khẩu mới không được để trống';
    } else if (!PW_REGEX.test(pwForm.newPassword)) {
      errs.newPassword = 'Ít nhất 8 ký tự, gồm 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt';
    }
    if (!pwForm.confirmPassword) errs.confirmPassword = 'Xác nhận mật khẩu không được để trống';
    else if (pwForm.newPassword && pwForm.newPassword !== pwForm.confirmPassword) errs.confirmPassword = 'Mật khẩu xác nhận không khớp';
    if (pwForm.oldPassword && pwForm.newPassword === pwForm.oldPassword) {
      errs.newPassword = 'Mật khẩu mới phải khác mật khẩu cũ';
    }
    return errs;
  };

  const handlePwChange = (field, value) => {
    setPwForm((prev) => ({ ...prev, [field]: value }));
    setPwErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const handlePwSubmit = async () => {
    const errs = validatePwForm();
    if (Object.keys(errs).length > 0) {
      setPwErrors(errs);
      return;
    }
    setPwSaving(true);
    try {
      await userService.changeMyPassword({
        oldPassword: pwForm.oldPassword,
        newPassword: pwForm.newPassword,
        confirmPassword: pwForm.confirmPassword,
      });
      setPwForm({ oldPassword: '', newPassword: '', confirmPassword: '' });
      setPwErrors({});
      setPwSuccess(true);
      toast.success('Đổi mật khẩu thành công');
      setTimeout(() => {
        setPwSuccess(false);
        setShowPasswordForm(false);
      }, 2500);
    } catch (err) {
      const msg = err?.response?.data?.message || err.message;
      if (msg.includes('cũ') || msg.includes('không đúng')) {
        setPwErrors({ oldPassword: msg });
      } else {
        toast.error(msg);
      }
    } finally {
      setPwSaving(false);
    }
  };

  const handlePwCancel = () => {
    setPwForm({ oldPassword: '', newPassword: '', confirmPassword: '' });
    setPwErrors({});
    setPwSuccess(false);
    setShowPasswordForm(false);
  };

  // ── Loading skeleton ─────────────────────────────────────────────
  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.pageHeader}>
          <div className={styles.pageHeaderIcon}><UserCircle size={22} /></div>
          <div className={styles.pageHeaderText}>
            <SkeletonBlock width={140} height={22} />
            <div style={{ marginTop: 6 }}><SkeletonBlock width={220} height={14} /></div>
          </div>
        </div>
        {[1, 2, 3].map((i) => (
          <div key={i} className={styles.card} style={{ padding: 24 }}>
            <SkeletonBlock width={180} height={18} />
            <div style={{ marginTop: 20 }}>
              {[1, 2, 3, 4].map((j) => (
                <div key={j} style={{ marginBottom: 12 }}>
                  <SkeletonBlock width={80} height={12} />
                  <div style={{ marginTop: 6 }}><SkeletonBlock width="100%" height={40} /></div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (!profile) return null;

  const roleKey = profile.role;
  const roleBgClass = ROLE_BG[roleKey] || styles.roleDefault;

  return (
    <div className={styles.page}>

      {/* ── Page header ──────────────────────────────────────────── */}
      <div className={styles.pageHeader}>
        <div className={styles.pageHeaderIcon}>
          <UserCircle size={22} />
        </div>
        <div className={styles.pageHeaderText}>
          <h1>Hồ sơ cá nhân</h1>
          <p>Xem và cập nhật thông tin cá nhân của bạn</p>
        </div>
      </div>

      {/* ── Success banner ──────────────────────────────────────── */}
      {showSuccess && (
        <div className={styles.successBanner}>
          <CheckCircle size={17} />
          Thông tin hồ sơ đã được cập nhật thành công.
        </div>
      )}

      {/* ── Identity card ──────────────────────────────────────── */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h2 className={styles.cardTitle}>
            <span className={styles.cardTitleIcon}><User size={15} /></span>
            Thông tin cá nhân
          </h2>
        </div>
        <div className={styles.cardBody}>
          <div className={styles.avatarSection}>
            <div className={styles.avatarWrapper}>
              <div className={styles.avatar}>
                {profile.avatarUrl ? (
                  <img src={profile.avatarUrl} alt={profile.fullName} className={styles.avatarImg} />
                ) : (
                  getInitials(profile.fullName)
                )}
              </div>
              <button
                className={styles.avatarUploadBtn}
                title="Đổi ảnh đại diện (sắp ra mắt)"
              >
                <Camera size={13} />
              </button>
            </div>

            <div className={styles.avatarInfo}>
              <div className={styles.avatarName}>{profile.fullName}</div>
              <div className={styles.avatarEmail}>{profile.email}</div>
              <span className={`${styles.roleBadge} ${roleBgClass}`}>
                <Shield size={11} />
                {ROLE_LABELS[roleKey] || roleKey}
              </span>
            </div>
          </div>

          <div className={styles.infoGrid}>
            <div className={styles.infoItem}>
              <div className={styles.infoIcon}><Mail size={14} /></div>
              <div className={styles.infoContent}>
                <div className={styles.infoLabel}>Email</div>
                <div className={styles.infoValue}>{profile.email}</div>
              </div>
            </div>
            <div className={styles.infoItem}>
              <div className={styles.infoIcon}><Phone size={14} /></div>
              <div className={styles.infoContent}>
                <div className={styles.infoLabel}>Điện thoại</div>
                <div className={styles.infoValue}>{profile.phone || '—'}</div>
              </div>
            </div>
            <div className={styles.infoItem}>
              <div className={styles.infoIcon}><Calendar size={14} /></div>
              <div className={styles.infoContent}>
                <div className={styles.infoLabel}>Ngày tham gia</div>
                <div className={styles.infoValue}>{formatDate(profile.createdAt)}</div>
              </div>
            </div>
            <div className={styles.infoItem}>
              <div className={styles.infoIcon}><Shield size={14} /></div>
              <div className={styles.infoContent}>
                <div className={styles.infoLabel}>Trạng thái</div>
                <div className={styles.infoValue}>
                  {profile.status === 'ACTIVE' ? (
                    <span className={styles.statusActiveDot}>Đang hoạt động</span>
                  ) : (
                    <span className={styles.statusInactive}>
                      {STATUS_LABELS[profile.status] || profile.status}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Editable form card ─────────────────────────────────── */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h2 className={styles.cardTitle}>
            <span className={styles.cardTitleIcon}><Clock size={15} /></span>
            Chỉnh sửa hồ sơ
          </h2>
        </div>
        <div className={styles.cardBody}>
          <div className={styles.formGrid}>
            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="fullName">
                Họ và tên <span className={styles.labelRequired}>*</span>
              </label>
              <input
                id="fullName"
                type="text"
                className={styles.input}
                value={form.fullName}
                onChange={(e) => handleChange('fullName', e.target.value)}
                placeholder="Nhập họ và tên của bạn"
                maxLength={255}
              />
            </div>

            <div className={styles.formGroup}>
              <label className={styles.label} htmlFor="phone">Số điện thoại</label>
              <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                <Phone
                  size={15}
                  style={{ position: 'absolute', left: 12, color: '#94a3b8', pointerEvents: 'none' }}
                />
                <input
                  id="phone"
                  type="tel"
                  className={styles.input}
                  style={{ paddingLeft: 36 }}
                  value={form.phone}
                  onChange={(e) => handleChange('phone', e.target.value)}
                  placeholder="0901 234 567"
                  maxLength={20}
                />
              </div>
            </div>

            <div className={`${styles.formGroup} ${styles.formGroupFull}`}>
              <label className={styles.label} htmlFor="email">Địa chỉ email</label>
              <input
                id="email"
                type="email"
                className={styles.input}
                style={{ background: '#f1f5f9', color: '#94a3b8', cursor: 'not-allowed', borderColor: '#e2e8f0' }}
                value={profile.email}
                disabled
                title="Email không thể thay đổi"
              />
              <p className={styles.inputHint}>
                Địa chỉ email không thể thay đổi. Liên hệ quản trị viên nếu cần cập nhật.
              </p>
            </div>
          </div>
        </div>

        {isDirty && (
          <div className={styles.cardActions}>
            <button className={styles.btnCancel} onClick={handleCancel} disabled={saving}>
              Hủy
            </button>
            <button
              className={styles.btnSave}
              onClick={handleSave}
              disabled={saving || !form.fullName.trim()}
            >
              {saving ? (
                <><Loader2 size={14} className={styles.spinner} /> Đang lưu...</>
              ) : (
                <><Save size={14} /> Lưu thay đổi</>
              )}
            </button>
          </div>
        )}
      </div>

      {/* ── Change password card ─────────────────────────────────── */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h2 className={styles.cardTitle}>
            <span className={styles.cardTitleIcon}><KeyRound size={15} /></span>
            Đổi mật khẩu
          </h2>
          {!showPasswordForm ? (
            <button className={styles.pwToggleBtn} onClick={() => setShowPasswordForm(true)}>
              <KeyRound size={14} /> Đổi mật khẩu
            </button>
          ) : (
            <button className={styles.pwCloseBtn} onClick={handlePwCancel}>
              <X size={14} />
            </button>
          )}
        </div>

        {showPasswordForm && (
          <>
            <div className={styles.cardBody}>
              {pwSuccess && (
                <div className={styles.successBanner} style={{ marginBottom: 20 }}>
                  <CheckCircle size={17} />
                  Đổi mật khẩu thành công!
                </div>
              )}
              <div className={styles.formGrid}>
                <PasswordField
                  id="oldPassword"
                  label="Mật khẩu cũ"
                  value={pwForm.oldPassword}
                  onChange={(v) => handlePwChange('oldPassword', v)}
                  placeholder="Nhập mật khẩu hiện tại"
                  error={pwErrors.oldPassword}
                />
                <div />

                <div className={styles.formGroup} style={{ gridColumn: '1 / -1' }}>
                  <label className={styles.label} htmlFor="newPassword">
                    Mật khẩu mới <span className={styles.labelRequired}>*</span>
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      id="newPassword"
                      type={pwForm._showNew ? 'text' : 'password'}
                      className={`${styles.input} ${pwErrors.newPassword ? styles.inputError : ''}`}
                      style={{ paddingRight: 42 }}
                      value={pwForm.newPassword}
                      onChange={(e) => handlePwChange('newPassword', e.target.value)}
                      placeholder="Ít nhất 8 ký tự, 1 chữ hoa, 1 số, 1 ký tự đặc biệt"
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      onClick={() => setPwForm((p) => ({ ...p, _showNew: !p._showNew }))}
                      style={{
                        position: 'absolute', right: 12,
                        background: 'none', border: 'none', cursor: 'pointer',
                        color: '#94a3b8', display: 'flex', alignItems: 'center', padding: 0,
                      }}
                    >
                      {pwForm._showNew ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                  <PwStrengthMeter password={pwForm.newPassword} />
                  {pwErrors.newPassword ? (
                    <p className={styles.errorText}>
                      <X size={11} style={{ flexShrink: 0 }} />
                      {pwErrors.newPassword}
                    </p>
                  ) : (
                    <p className={styles.inputHint}>Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt (!@#$...)</p>
                  )}
                </div>

                <div className={styles.formGroup} style={{ gridColumn: '1 / -1' }}>
                  <label className={styles.label} htmlFor="confirmPassword">
                    Xác nhận mật khẩu mới <span className={styles.labelRequired}>*</span>
                  </label>
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <input
                      id="confirmPassword"
                      type={pwForm._showConfirm ? 'text' : 'password'}
                      className={`${styles.input} ${pwErrors.confirmPassword ? styles.inputError : ''}`}
                      style={{ paddingRight: 42 }}
                      value={pwForm.confirmPassword}
                      onChange={(e) => handlePwChange('confirmPassword', e.target.value)}
                      placeholder="Nhập lại mật khẩu mới"
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      onClick={() => setPwForm((p) => ({ ...p, _showConfirm: !p._showConfirm }))}
                      style={{
                        position: 'absolute', right: 12,
                        background: 'none', border: 'none', cursor: 'pointer',
                        color: '#94a3b8', display: 'flex', alignItems: 'center', padding: 0,
                      }}
                    >
                      {pwForm._showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                  {pwErrors.confirmPassword && (
                    <p className={styles.errorText}>
                      <X size={11} style={{ flexShrink: 0 }} />
                      {pwErrors.confirmPassword}
                    </p>
                  )}
                </div>
              </div>
            </div>

            <div className={styles.cardActions}>
              <button className={styles.btnCancel} onClick={handlePwCancel} disabled={pwSaving}>
                Hủy
              </button>
              <button className={styles.btnSave} onClick={handlePwSubmit} disabled={pwSaving}>
                {pwSaving ? (
                  <><Loader2 size={14} className={styles.spinner} /> Đang xử lý...</>
                ) : (
                  <><KeyRound size={14} /> Xác nhận đổi mật khẩu</>
                )}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
