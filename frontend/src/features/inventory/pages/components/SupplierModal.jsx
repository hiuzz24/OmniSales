import { useState, useEffect } from 'react';
import { X, Save, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import supplierService from '../../services/supplierService.js';

const field = (label, required, content, error) => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
    <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>
      {label} {required && <span style={{ color: '#ef4444' }}>*</span>}
    </label>
    {content}
    {error && (
      <span style={{ fontSize: 12, color: '#ef4444', display: 'flex', alignItems: 'center', gap: 4 }}>
        <AlertCircle size={12} /> {error}
      </span>
    )}
  </div>
);

const inputStyle = (error, disabled) => ({
  padding: '8px 12px', borderRadius: 8, fontSize: 14, outline: 'none',
  border: error ? '1px solid #ef4444' : '1px solid #cbd5e1',
  backgroundColor: disabled ? '#f1f5f9' : '#fff',
  color: disabled ? '#64748b' : '#0f172a',
});

export default function SupplierModal({ isOpen, onClose, onRefresh, supplierData = null }) {
  const isEdit = !!supplierData;

  const EMPTY = { name: '', contactName: '', phone: '', taxCode: '', email: '', address: '', isActive: true };

  const [formData, setFormData] = useState(EMPTY);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (!isOpen) return;
    if (supplierData) {
      setFormData({
        name:        supplierData.name        ?? '',
        contactName: supplierData.contactName ?? '',
        phone:       supplierData.phone       ?? '',
        taxCode:     supplierData.taxCode     ?? '',
        email:       supplierData.email       ?? '',
        address:     supplierData.address     ?? '',
        isActive:    supplierData.isActive    ?? true,
      });
    } else {
      setFormData(EMPTY);
    }
    setErrors({});
  }, [isOpen, supplierData]);

  if (!isOpen) return null;

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'isActive' ? value === 'true' : value,
    }));
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: '' }));
  };

  const validate = () => {
    const errs = {};
    if (!formData.name.trim())    errs.name    = 'Tên nhà cung cấp là bắt buộc';
    if (!formData.phone.trim())   errs.phone   = 'Số điện thoại là bắt buộc';
    if (!formData.taxCode.trim()) errs.taxCode = 'Mã số thuế (MST) là bắt buộc';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);
    try {
      if (isEdit) {
        await supplierService.updateSupplier(supplierData.id || supplierData._id, formData);
        toast.success('Cập nhật thông tin nhà cung cấp thành công!');
      } else {
        await supplierService.createSupplier(formData);
        toast.success('Thêm nhà cung cấp mới thành công!');
      }
      onRefresh();
      onClose();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 100,
      backgroundColor: 'rgba(15,23,42,0.4)', backdropFilter: 'blur(4px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
    }}
      onClick={(e) => { if (e.target === e.currentTarget && !loading) onClose(); }}
    >
      <div style={{
        backgroundColor: '#fff', width: '100%', maxWidth: 640, borderRadius: 16,
        boxShadow: '0 20px 60px rgba(0,0,0,0.15)', display: 'flex', flexDirection: 'column', overflow: 'hidden',
      }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 24px', borderBottom: '1px solid #e2e8f0' }}>
          <h3 style={{ fontSize: 18, fontWeight: 700, color: '#0f172a', margin: 0 }}>
            {isEdit ? 'Cập nhật thông tin nhà cung cấp' : 'Thêm mới nhà cung cấp'}
          </h3>
          <button type="button" onClick={onClose} disabled={loading}
            style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#64748b', display: 'flex', padding: 4, borderRadius: 6 }}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
          <div style={{ padding: 24, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px 20px', maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }}>

            {/* Mã NCC (auto-generated, show only in edit) */}
            {isEdit && field(
              'Mã nhà cung cấp', false,
              <input disabled value={supplierData.code ?? '—'} style={inputStyle(false, true)} />,
              null
            )}

            {/* Tên NCC — full width */}
            <div style={{ gridColumn: isEdit ? 'auto' : 'span 2' }}>
              {field('Tên nhà cung cấp', true,
                <input type="text" name="name" value={formData.name} onChange={handleChange}
                  placeholder="Công ty TNHH Minh Phát" style={inputStyle(errors.name, false)} />,
                errors.name
              )}
            </div>

            {/* Mã số thuế */}
            {field('Mã số thuế (MST)', true,
              <input type="text" name="taxCode" value={formData.taxCode} onChange={handleChange}
                placeholder="Ví dụ: 0123456789" style={inputStyle(errors.taxCode, false)} />,
              errors.taxCode
            )}

            {/* Người liên hệ */}
            {field('Người liên hệ', false,
              <input type="text" name="contactName" value={formData.contactName} onChange={handleChange}
                placeholder="Nguyễn Văn A" style={inputStyle(false, false)} />,
              null
            )}

            {/* Số điện thoại */}
            {field('Số điện thoại', true,
              <input type="text" name="phone" value={formData.phone} onChange={handleChange}
                placeholder="0901234567" style={inputStyle(errors.phone, false)} />,
              errors.phone
            )}

            {/* Email */}
            {field('Email', false,
              <input type="email" name="email" value={formData.email} onChange={handleChange}
                placeholder="example@domain.com" style={inputStyle(false, false)} />,
              null
            )}

            {/* Spacer */}
            <div />

            {/* Địa chỉ — full width */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
              <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Địa chỉ chi tiết</label>
              <input type="text" name="address" value={formData.address} onChange={handleChange}
                placeholder="Số nhà, tên đường..." style={inputStyle(false, false)} />
            </div>

            {/* Trạng thái — full width */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
              <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Trạng thái hợp tác</label>
              <div style={{ display: 'flex', gap: 16, marginTop: 4 }}>
                {[{ value: 'true', label: 'Đang hoạt động' }, { value: 'false', label: 'Ngừng hoạt động' }].map(({ value, label }) => (
                  <label key={value} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#0f172a', cursor: 'pointer' }}>
                    <input type="radio" name="isActive" value={value}
                      checked={formData.isActive === (value === 'true')} onChange={handleChange}
                      style={{ width: 16, height: 16, accentColor: '#2563eb' }} />
                    {label}
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* Footer */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 12, padding: '16px 24px', borderTop: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
            <button type="button" onClick={onClose} disabled={loading}
              style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid #e2e8f0', backgroundColor: '#fff', fontSize: 14, fontWeight: 500, color: '#334155', cursor: 'pointer' }}>
              Hủy bỏ
            </button>
            <button type="submit" disabled={loading}
              style={{ padding: '8px 16px', borderRadius: 8, border: 'none', backgroundColor: '#2563eb', fontSize: 14, fontWeight: 500, color: '#fff', display: 'flex', alignItems: 'center', gap: 6, cursor: loading ? 'not-allowed' : 'pointer', opacity: loading ? 0.7 : 1 }}>
              <Save size={16} />
              {loading ? 'Đang lưu...' : 'Lưu lại'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
