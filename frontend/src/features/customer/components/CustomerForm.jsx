import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { User, Phone, Mail, MapPin, Calendar, StickyNote } from 'lucide-react';
import styles from './CustomerForm.module.css';

const customerSchema = z.object({
  fullName: z.string().min(1, 'Tên khách hàng là bắt buộc').max(255),
  gender: z.string().min(1, 'Giới tính là bắt buộc'),
  birth: z.string().optional().nullable(),
  phone: z.string().min(1, 'Số điện thoại là bắt buộc').max(20, 'Số điện thoại không quá 20 ký tự'),
  email: z.string().email('Email không hợp lệ').or(z.literal('')).optional(),
  address: z.object({
    detail: z.string().optional(),
    ward: z.string().optional(),
    district: z.string().optional(),
    province: z.string().optional(),
  }).optional().nullable(),
  notes: z.string().optional(),
});

const CustomerForm = ({ customer, onSubmit, isSubmitting }) => {
  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm({
    resolver: zodResolver(customerSchema),
    defaultValues: customer || {
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
    if (customer) {
      reset({
        fullName: customer.fullName || '',
        gender: customer.gender || '',
        birth: customer.birth || '',
        phone: customer.phone || '',
        email: customer.email || '',
        address: customer.address || { detail: '', ward: '', district: '', province: '' },
        notes: customer.notes || '',
      });
    }
  }, [customer, reset]);

  const onFormSubmit = (data) => {
    const submitData = {
      ...data,
      address: data.address ? Object.fromEntries(
        Object.entries(data.address).filter(([_, v]) => v && v.trim())
      ) : undefined,
    };
    if (!submitData.email) submitData.email = null;
    if (!submitData.phone) submitData.phone = null;
    onSubmit(submitData);
  };

  return (
    <form onSubmit={handleSubmit(onFormSubmit)} className={styles.form}>
      {/* Thông tin cơ bản */}
      <div className={styles.section}>
        <h3 className={styles.sectionTitle}>
          <User size={16} />
          Thông tin cơ bản
        </h3>
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
            <label className={styles.label}>
              Email
            </label>
            <input
              type="email"
              {...register('email')}
              className={`${styles.input} ${errors.email ? styles.inputError : ''}`}
              placeholder="Nhập email"
            />
            {errors.email && <span className={styles.error}>{errors.email.message}</span>}
          </div>

          <div className={styles.field}>
            <label className={styles.label}>
              Giới tính <span className={styles.required}>*</span>
            </label>
            <select
              {...register('gender')}
              className={`${styles.select} ${errors.gender ? styles.inputError : ''}`}
            >
              <option value="">Chọn giới tính</option>
              <option value="Nam">Nam</option>
              <option value="Nữ">Nữ</option>
              <option value="Khác">Khác</option>
            </select>
            {errors.gender && <span className={styles.error}>{errors.gender.message}</span>}
          </div>

          <div className={styles.field}>
            <label className={styles.label}>
              <Calendar size={14} />
              Ngày sinh
            </label>
            <input
              type="date"
              {...register('birth')}
              className={styles.input}
            />
          </div>
        </div>
      </div>

      {/* Địa chỉ */}
      <div className={styles.section}>
        <h3 className={styles.sectionTitle}>
          <MapPin size={16} />
          Địa chỉ
        </h3>
        <div className={styles.addressGrid}>
          <div className={styles.field}>
            <label className={styles.label}>Tỉnh / Thành phố</label>
            <input type="text" {...register('address.province')} className={styles.input} placeholder="Ví dụ: Hồ Chí Minh" />
          </div>
          <div className={styles.field}>
            <label className={styles.label}>Quận / Huyện</label>
            <input type="text" {...register('address.district')} className={styles.input} placeholder="Ví dụ: Quận 1" />
          </div>
          <div className={styles.field}>
            <label className={styles.label}>Phường / Xã</label>
            <input type="text" {...register('address.ward')} className={styles.input} placeholder="Ví dụ: Phường Bến Nghé" />
          </div>
          <div className={styles.field} style={{ gridColumn: '1 / -1' }}>
            <label className={styles.label}>Địa chỉ chi tiết</label>
            <input type="text" {...register('address.detail')} className={styles.input} placeholder="Ví dụ: 123 Nguyễn Huệ, Tầng 3" />
          </div>
        </div>
      </div>

      {/* Ghi chú */}
      <div className={styles.section}>
        <h3 className={styles.sectionTitle}>
          <StickyNote size={16} />
          Ghi chú
        </h3>
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
          onClick={() => window.history.back()}
          disabled={isSubmitting}
        >
          Hủy
        </button>
        <button type="submit" className={styles.submitBtn} disabled={isSubmitting}>
          {isSubmitting ? 'Đang lưu...' : 'Lưu'}
        </button>
      </div>
    </form>
  );
};

export default CustomerForm;
