import { useState, useEffect } from 'react';
import { X, Save, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import supplierService from '../../services/supplierService.js';

export default function SupplierModal({ isOpen, onClose, onRefresh, supplierData = null }) {
    const isEdit = !!supplierData;

    const [formData, setFormData] = useState({
        code: '',
        name: '',
        contactName: '',
        phone: '',
        email: '',
        address: '',
        isActive: true // Khởi tạo bằng Boolean thay vì chuỗi 'Active'
    });
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        if (isOpen) {
            if (supplierData) {
                setFormData({
                    code: supplierData.code ?? '',
                    name: supplierData.name ?? '',
                    contactName: supplierData.contactName ?? '',
                    phone: supplierData.phone ?? '',
                    email: supplierData.email ?? '',
                    address: supplierData.address ?? '',
                    isActive: supplierData.isActive ?? true // Gán trực tiếp giá trị boolean từ backend
                });
            } else {
                setFormData({ code: '', name: '', contactName: '', phone: '', email: '', address: '', isActive: true });
            }
            setErrors({});
        }
    }, [isOpen, supplierData]);

    if (!isOpen) return null;

    const handleChange = (e) => {
        const { name, value } = e.target;
        // Nếu thay đổi radio button trạng thái, chuyển đổi chuỗi sang boolean
        if (name === 'isActive') {
            setFormData(prev => ({ ...prev, isActive: value === 'true' }));
        } else {
            setFormData(prev => ({ ...prev, [name]: value }));
        }
        if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }));
    };

    const validateForm = () => {
        const newErrors = {};
        if (!formData.code.trim()) newErrors.code = 'Mã nhà cung cấp là bắt buộc';
        if (!formData.name.trim()) newErrors.name = 'Tên nhà cung cấp là bắt buộc';
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!validateForm()) return;

        setLoading(true);
        try {
            let response;
            if (isEdit) {
                response = await supplierService.updateSupplier(supplierData.id || supplierData._id, formData);
                toast.success('Cập nhật thông tin nhà cung cấp thành công!');
            } else {
                response = await supplierService.createSupplier(formData);
                toast.success('Thêm nhà cung cấp mới thành công!');
            }

            if (response.data?.success || response.status === 200 || response.status === 201) {
                onRefresh();
                onClose();
            }
        } catch (error) {
            console.error(error);
            toast.error(error.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{
            position: 'fixed', inset: 0, zIndex: 100,
            backgroundColor: 'rgba(15, 23, 42, 0.4)',
            backdropFilter: 'blur(4px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16
        }}>
            <div style={{
                backgroundColor: '#ffffff', width: '100%', maxWidth: 640,
                borderRadius: 16, boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
                display: 'flex', flexDirection: 'column', overflow: 'hidden'
            }}>

                {/* Header */}
                <div style={{
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    padding: '16px 24px', borderBottom: '1px solid #e2e8f0'
                }}>
                    <h3 style={{ fontSize: 18, fontWeight: 700, color: '#0f172a', margin: 0 }}>
                        {isEdit ? 'Cập nhật thông tin nhà cung cấp' : 'Thêm mới nhà cung cấp'}
                    </h3>
                    <button type="button" onClick={onClose} style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#64748b', display: 'flex', padding: 4, borderRadius: 6 }}>
                        <X size={20} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
                    <div style={{ padding: '24px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px 20px', maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }}>

                        {/* Mã NCC */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Mã nhà cung cấp <span style={{ color: '#ef4444' }}>*</span></label>
                            <input type="text" name="code" value={formData.code} onChange={handleChange} disabled={isEdit} placeholder="Ví dụ: MINHPHAT" style={{ padding: '8px 12px', borderRadius: 8, border: errors.code ? '1px solid #ef4444' : '1px solid #cbd5e1', fontSize: 14, outline: 'none', backgroundColor: isEdit ? '#f1f5f9' : '#fff' }} />
                            {errors.code && <span style={{ fontSize: 12, color: '#ef4444', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={12} /> {errors.code}</span>}
                        </div>

                        {/* Tên NCC */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Tên nhà cung cấp <span style={{ color: '#ef4444' }}>*</span></label>
                            <input type="text" name="name" value={formData.name} onChange={handleChange} placeholder="Công ty TNHH Minh Phát" style={{ padding: '8px 12px', borderRadius: 8, border: errors.name ? '1px solid #ef4444' : '1px solid #cbd5e1', fontSize: 14, outline: 'none' }} />
                            {errors.name && <span style={{ fontSize: 12, color: '#ef4444', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={12} /> {errors.name}</span>}
                        </div>

                        {/* Người liên hệ */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Người liên hệ</label>
                            <input type="text" name="contactName" value={formData.contactName} onChange={handleChange} placeholder="Nguyễn Văn A" style={{ padding: '8px 12px', borderRadius: 8, border: '1px solid #cbd5e1', fontSize: 14, outline: 'none' }} />
                        </div>

                        {/* Số điện thoại */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Số điện thoại</label>
                            <input type="text" name="phone" value={formData.phone} onChange={handleChange} placeholder="0901234567" style={{ padding: '8px 12px', borderRadius: 8, border: '1px solid #cbd5e1', fontSize: 14, outline: 'none' }} />
                        </div>

                        {/* Email */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Email</label>
                            <input type="email" name="email" value={formData.email} onChange={handleChange} placeholder="example@domain.com" style={{ padding: '8px 12px', borderRadius: 8, border: '1px solid #cbd5e1', fontSize: 14, outline: 'none' }} />
                        </div>

                        <div />

                        {/* Địa chỉ */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Địa chỉ chi tiết</label>
                            <input type="text" name="address" value={formData.address} onChange={handleChange} placeholder="Số nhà, tên đường..." style={{ padding: '8px 12px', borderRadius: 8, border: '1px solid #cbd5e1', fontSize: 14, outline: 'none' }} />
                        </div>

                        {/* Trạng thái (Quản lý value theo kiểu string để gán vào input nhưng hàm handleChange đã ép lại về boolean true/false) */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Trạng thái hợp tác</label>
                            <div style={{ display: 'flex', gap: 16, marginTop: 4 }}>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#0f172a', cursor: 'pointer' }}>
                                    <input type="radio" name="isActive" value="true" checked={formData.isActive === true} onChange={handleChange} style={{ width: 16, height: 16, accentColor: '#2563eb' }} />
                                    Đang hoạt động
                                </label>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#0f172a', cursor: 'pointer' }}>
                                    <input type="radio" name="isActive" value="false" checked={formData.isActive === false} onChange={handleChange} style={{ width: 16, height: 16, accentColor: '#2563eb' }} />
                                    Ngừng hoạt động
                                </label>
                            </div>
                        </div>

                    </div>

                    {/* Footer */}
                    <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 12,
                        padding: '16px 24px', borderTop: '1px solid #e2e8f0', backgroundColor: '#f8fafc'
                    }}>
                        <button type="button" onClick={onClose} disabled={loading} style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid #e2e8f0', backgroundColor: '#fff', fontSize: 14, fontWeight: 500, color: '#334155', cursor: 'pointer' }}>
                            Hủy bỏ
                        </button>
                        <button type="submit" disabled={loading} style={{
                            padding: '8px 16px', borderRadius: 8, border: 'none',
                            backgroundColor: '#2563eb', fontSize: 14, fontWeight: 500, color: '#fff',
                            display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer',
                            opacity: loading ? 0.7 : 1
                        }}>
                            <Save size={16} />
                            {loading ? 'Đang lưu...' : 'Lưu lại'}
                        </button>
                    </div>
                </form>

            </div>
        </div>
    );
}