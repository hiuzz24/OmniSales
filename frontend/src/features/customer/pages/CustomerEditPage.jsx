import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'react-toastify';
import { ArrowLeft, Save } from 'lucide-react';
import { ROUTES } from '../../../app/router/routes';
import customerService from '../services/customerService';
import styles from './CustomerFormPage.module.css';

const schema = z.object({
  fullName: z.string().min(1, 'Tên khách hàng là bắt buộc').max(255),
  gender: z.string().min(1, 'Giới tính là bắt buộc'),
  birth: z.string().optional().nullable(),
  phone: z.string().min(1, 'Số điện thoại là bắt buộc').max(20),
  email: z.string().email('Email không hợp lệ').or(z.literal('')).optional(),
  address: z.object({
    detail: z.string().optional(),
    ward: z.string().optional(),
    district: z.string().optional(),
    province: z.string().optional(),
  }).optional().nullable(),
  notes: z.string().optional(),
});

const CustomerEditPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: '',
      gender: '',
      birth: '',
      phone: '',
      email: '',
      address: { detail: '', ward: '', district: '', province: '' },
      notes: '',
    },
  });

  useEffect(() => {
    const fetchCustomer = async () => {
      try {
        const data = await customerService.getById(id);
        reset({
          fullName: data.fullName || '',
          gender: data.gender || '',
          birth: data.birth || '',
          phone: data.phone || '',
          email: data.email || '',
          address: data.address || { detail: '', ward: '', district: '', province: '' },
          notes: data.notes || '',
        });
      } catch (error) {
        toast.error('Không thể tải thông tin khách hàng');
        navigate(ROUTES.CUSTOMER_LIST);
      } finally {
        setLoading(false);
      }
    };
    fetchCustomer();
  }, [id, reset, navigate]);

  const onSubmit = async (data) => {
    setIsSubmitting(true);
    try {
      const payload = {
        ...data,
        address: data.address
          ? Object.fromEntries(Object.entries(data.address).filter(([_, v]) => v?.trim()))
          : undefined,
        email: data.email || null,
      };
      await customerService.update(id, payload);
      toast.success('Cập nhật khách hàng thành công');
      navigate(ROUTES.CUSTOMER_LIST);
    } catch (error) {
      const message = error?.response?.data?.message || 'Cập nhật khách hàng thất bại';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.pageHeader}>
          <button className={styles.backBtn} onClick={() => navigate(ROUTES.CUSTOMER_LIST)}>
            <ArrowLeft size={16} />
            Quay lại
          </button>
          <div className={styles.headerCenter}>
            <h1 className={styles.pageTitle}>Sửa khách hàng</h1>
          </div>
          <div style={{ width: 120 }} />
        </div>
        <div className={styles.loadingText}>Đang tải...</div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      {/* Page Header */}
      <div className={styles.pageHeader}>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.CUSTOMER_LIST)}>
          <ArrowLeft size={16} />
          Quay lại
        </button>
        <div className={styles.headerCenter}>
          <h1 className={styles.pageTitle}>Sửa khách hàng</h1>
          <p className={styles.pageSubtitle}>Cập nhật thông tin khách hàng</p>
        </div>
        <div style={{ width: 120 }} />
      </div>

      {/* Form */}
      <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
        {/* Section: Thông tin cơ bản */}
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <div className={styles.sectionIcon}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <h2 className={styles.sectionTitle}>Thông tin cơ bản</h2>
          </div>
          <div className={styles.grid}>
            <div className={styles.field}>
              <label className={styles.label}>
                Họ và tên <span className={styles.required}>*</span>
              </label>
              <input
                type="text"
                {...register('fullName')}
                className={`${styles.input} ${errors.fullName ? styles.inputError : ''}`}
                placeholder="Nhập họ và tên"
              />
              {errors.fullName && <span className={styles.error}>{errors.fullName.message}</span>}
            </div>

            <div className={styles.field}>
              <label className={styles.label}>
                Số điện thoại <span className={styles.required}>*</span>
              </label>
              <input
                type="text"
                {...register('phone')}
                className={`${styles.input} ${errors.phone ? styles.inputError : ''}`}
                placeholder="Nhập số điện thoại"
              />
              {errors.phone && <span className={styles.error}>{errors.phone.message}</span>}
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Email</label>
              <input
                type="email"
                {...register('email')}
                className={`${styles.input} ${errors.email ? styles.inputError : ''}`}
                placeholder="Nhập địa chỉ email"
              />
              {errors.email && <span className={styles.error}>{errors.email.message}</span>}
            </div>

            <div className={styles.field}>
              <label className={styles.label}>
                Giới tính <span className={styles.required}>*</span>
              </label>
              <div className={styles.radioGroup}>
                {['Nam', 'Nữ', 'Khác'].map((g) => (
                  <label key={g} className={styles.radioLabel}>
                    <input
                      type="radio"
                      value={g}
                      {...register('gender')}
                      className={styles.radioInput}
                    />
                    <span className={styles.radioCustom} />
                    <span className={styles.radioText}>{g}</span>
                  </label>
                ))}
              </div>
              {errors.gender && <span className={styles.error}>{errors.gender.message}</span>}
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Ngày sinh</label>
              <input
                type="date"
                {...register('birth')}
                className={styles.input}
              />
            </div>
          </div>
        </div>

        {/* Section: Địa chỉ */}
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <div className={styles.sectionIcon}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
            </div>
            <h2 className={styles.sectionTitle}>Địa chỉ</h2>
          </div>
          <div className={styles.addressGrid}>
            <div className={styles.field}>
              <label className={styles.label}>Tỉnh / Thành phố</label>
              <input
                type="text"
                {...register('address.province')}
                className={styles.input}
                placeholder="Ví dụ: Hồ Chí Minh"
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label}>Quận / Huyện</label>
              <input
                type="text"
                {...register('address.district')}
                className={styles.input}
                placeholder="Ví dụ: Quận 1"
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label}>Phường / Xã</label>
              <input
                type="text"
                {...register('address.ward')}
                className={styles.input}
                placeholder="Ví dụ: Phường Bến Nghé"
              />
            </div>
            <div className={styles.field}>
              <label className={styles.label}>Địa chỉ chi tiết</label>
              <input
                type="text"
                {...register('address.detail')}
                className={styles.input}
                placeholder="Ví dụ: 123 Nguyễn Huệ, Tầng 3"
              />
            </div>
          </div>
        </div>

        {/* Section: Ghi chú */}
        <div className={styles.section}>
          <div className={styles.sectionHeader}>
            <div className={styles.sectionIcon}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
                <polyline points="10 9 9 9 8 9"/>
              </svg>
            </div>
            <h2 className={styles.sectionTitle}>Ghi chú</h2>
          </div>
          <textarea
            {...register('notes')}
            className={styles.textarea}
            placeholder="Nhập ghi chú về khách hàng (nếu có)"
            rows={4}
          />
        </div>

        {/* Actions */}
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.cancelBtn}
            onClick={() => navigate(ROUTES.CUSTOMER_LIST)}
          >
            Hủy bỏ
          </button>
          <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>
            <Save size={15} />
            {isSubmitting ? 'Đang lưu...' : 'Lưu thay đổi'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default CustomerEditPage;
