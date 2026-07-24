import { useState, useEffect } from 'react';
import { X, Save, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import warehouseService from '../../services/warehouseService.js';

export default function WarehouseModal({ isOpen, onClose, onRefresh, warehouseData = null, readOnly = false }) {
    const isEdit = !!warehouseData;

    const [formData, setFormData] = useState({
        code: '',
        name: '',
        address: '',
        capacity: 5000,
        stock: 0,
        isActive: true
    });
    const [loading, setLoading] = useState(false);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        if (isOpen) {
            if (warehouseData) {
                setFormData({
                    code: warehouseData.code ?? '',
                    name: warehouseData.name ?? '',
                    address: warehouseData.address ?? '',
                    capacity: warehouseData.capacity ?? 5000,
                    stock: warehouseData.stock ?? 0,
                    isActive: warehouseData.isActive ?? true
                });
            } else {
                setFormData({
                    code: '',
                    name: '',
                    address: '',
                    capacity: 5000,
                    stock: 0,
                    isActive: true
                });
            }
            setErrors({});
        }
    }, [isOpen, warehouseData]);

    if (!isOpen) return null;

    const handleChange = (e) => {
        if (readOnly) return;
        const { name, value } = e.target;
        if (name === 'isActive') {
            setFormData(prev => ({ ...prev, isActive: value === 'true' }));
        } else if (name === 'capacity') {
            setFormData(prev => ({ ...prev, capacity: value === '' ? '' : Number(value) }));
        } else {
            setFormData(prev => ({ ...prev, [name]: value }));
        }
        if (errors[name]) setErrors(prev => ({ ...prev, [name]: '' }));
    };

    const validateForm = () => {
        const newErrors = {};
        if (!formData.name.trim()) newErrors.name = 'Tên kho hàng là bắt buộc';
        if (!formData.address.trim()) newErrors.address = 'Địa chỉ là bắt buộc';
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (readOnly) return;
        if (!validateForm()) return;

        setLoading(true);
        try {
            let response;
            if (isEdit) {
                response = await warehouseService.updateWarehouse(warehouseData.id, formData);
                toast.success('Cập nhật thông tin kho hàng thành công!');
            } else {
                response = await warehouseService.createWarehouse(formData);
                toast.success('Thêm mới kho hàng thành công!');
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
                        {readOnly ? 'Thông tin chi tiết kho hàng' : isEdit ? 'Cập nhật thông tin kho hàng' : 'Thêm mới kho hàng'}
                    </h3>
                    <button type="button" onClick={onClose} style={{ border: 'none', background: 'none', cursor: 'pointer', color: '#64748b', display: 'flex', padding: 4, borderRadius: 6 }}>
                        <X size={20} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
                    <div style={{ padding: '24px', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px 20px', maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' }}>

                        {/* Tên Kho */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>
                                Tên kho hàng <span style={{ color: '#ef4444' }}>*</span>
                            </label>
                            <input 
                                type="text" 
                                name="name" 
                                value={formData.name} 
                                onChange={handleChange} 
                                disabled={readOnly}
                                placeholder="Ví dụ: Kho chính Hồ Chí Minh" 
                                style={{ 
                                    padding: '8px 12px', 
                                    borderRadius: 8, 
                                    border: errors.name ? '1px solid #ef4444' : '1px solid #cbd5e1', 
                                    fontSize: 14, 
                                    outline: 'none',
                                    backgroundColor: readOnly ? '#f1f5f9' : '#fff',
                                    color: readOnly ? '#64748b' : '#0f172a'
                                }} 
                            />
                            {errors.name && <span style={{ fontSize: 12, color: '#ef4444', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={12} /> {errors.name}</span>}
                        </div>

                        {/* Tồn kho hiện tại (chỉ đọc) */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Tồn kho hiện tại</label>
                            <input 
                                type="number" 
                                name="stock" 
                                value={formData.stock} 
                                disabled={true} 
                                style={{ 
                                    padding: '8px 12px', 
                                    borderRadius: 8, 
                                    border: '1px solid #cbd5e1', 
                                    fontSize: 14, 
                                    outline: 'none', 
                                    backgroundColor: '#f1f5f9',
                                    color: '#64748b'
                                }} 
                            />
                        </div>

                        {/* Địa chỉ */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>
                                Địa chỉ chi tiết <span style={{ color: '#ef4444' }}>*</span>
                            </label>
                            <input 
                                type="text" 
                                name="address" 
                                value={formData.address} 
                                onChange={handleChange} 
                                disabled={readOnly}
                                placeholder="Ví dụ: 123 Nguyễn Trãi, Quận 1, TP. Hồ Chí Minh" 
                                style={{ 
                                    padding: '8px 12px', 
                                    borderRadius: 8, 
                                    border: errors.address ? '1px solid #ef4444' : '1px solid #cbd5e1', 
                                    fontSize: 14, 
                                    outline: 'none',
                                    backgroundColor: readOnly ? '#f1f5f9' : '#fff',
                                    color: readOnly ? '#64748b' : '#0f172a'
                                }} 
                            />
                            {errors.address && <span style={{ fontSize: 12, color: '#ef4444', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={12} /> {errors.address}</span>}
                        </div>

                        {/* Trạng thái hoạt động */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, gridColumn: 'span 2' }}>
                            <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Trạng thái hoạt động</label>
                            <div style={{ display: 'flex', gap: 16, marginTop: 4 }}>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#0f172a', cursor: readOnly ? 'default' : 'pointer' }}>
                                    <input 
                                        type="radio" 
                                        name="isActive" 
                                        value="true" 
                                        checked={formData.isActive === true} 
                                        onChange={handleChange} 
                                        disabled={readOnly}
                                        style={{ width: 16, height: 16, accentColor: '#2563eb' }} 
                                    />
                                    Đang hoạt động
                                </label>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#0f172a', cursor: readOnly ? 'default' : 'pointer' }}>
                                    <input 
                                        type="radio" 
                                        name="isActive" 
                                        value="false" 
                                        checked={formData.isActive === false} 
                                        onChange={handleChange} 
                                        disabled={readOnly}
                                        style={{ width: 16, height: 16, accentColor: '#2563eb' }} 
                                    />
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
                        {readOnly ? (
                            <button type="button" onClick={onClose} style={{ padding: '8px 24px', borderRadius: 8, border: 'none', backgroundColor: '#2563eb', fontSize: 14, fontWeight: 500, color: '#fff', cursor: 'pointer' }}>
                                Đóng
                            </button>
                        ) : (
                            <>
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
                            </>
                        )}
                    </div>
                </form>

            </div>
        </div>
    );
}
