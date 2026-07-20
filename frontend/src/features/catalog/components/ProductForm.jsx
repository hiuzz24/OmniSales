import { ChevronDown } from 'lucide-react';
import { useFormContext, useWatch } from 'react-hook-form';
import styles from './ProductForm.module.css';

const ProductForm = ({ categories = [] }) => {
  const { register, formState: { errors } } = useFormContext();
  const [hasOrders, hasVariants] = useWatch({ name: ['hasOrders', 'hasVariants'] });

  return (
    <div className={styles.card}>
      <div className={styles.cardTitle}>Thông tin cơ bản</div>
      <div className={styles.cardSubtitle}>Nhập thông tin chi tiết về sản phẩm</div>

      {/* Tên sản phẩm */}
      <div className={styles.row}>
        <div className={`${styles.field} ${styles.fullWidth}`}>
          <label className={styles.label}>
            Tên sản phẩm <span className={styles.required}>*</span>
          </label>
          <input
            id="product-name"
            type="text"
            className={`${styles.input} ${errors.name ? styles.inputError : ''}`}
            placeholder="Áo thun nam basic"
            {...register('name')}
          />
          {errors.name && <span className={styles.errorText}>{errors.name.message}</span>}
        </div>
      </div>

      {/* SKU + Barcode */}
      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label}>
            SKU <span className={styles.required}>*</span>
          </label>
          <input
            id="product-sku"
            type="text"
            className={`${styles.input} ${errors.sku ? styles.inputError : ''}`}
            placeholder="ATN-001"
            {...register('sku')}
            disabled={hasOrders}
          />
          {errors.sku && <span className={styles.errorText}>{errors.sku.message}</span>}
        </div>
        {!hasVariants && (
          <div className={styles.field}>
            <label className={styles.label}>Barcode</label>
            <input
              id="product-barcode"
              type="text"
              className={styles.input}
              placeholder="8936012345678"
            {...register('barcode')}
            />
          </div>
        )}
      </div>

      {/* Thuộc tính cơ bản khi không có biến thể */}
      {!hasVariants && (
        <div className={styles.row}>
          <div className={styles.field}>
            <label className={styles.label}>Size</label>
            <input
              id="product-size"
              type="text"
              className={styles.input}
              placeholder="Ví dụ: Freesize"
            {...register('size')}
            />
          </div>
          <div className={styles.field}>
            <label className={styles.label}>Màu sắc</label>
            <input
              id="product-color"
              type="text"
              className={styles.input}
              placeholder="Ví dụ: Đen"
            {...register('color')}
            />
          </div>
        </div>
      )}

      {/* Mô tả */}
      <div className={styles.row}>
        <div className={`${styles.field} ${styles.fullWidth}`}>
          <label className={styles.label}>Mô tả sản phẩm</label>
          <textarea
            id="product-description"
            className={styles.textarea}
            placeholder="Mô tả chi tiết về sản phẩm..."
            {...register('description')}
            rows={3}
          />
        </div>
      </div>

      {/* Danh mục + Thương hiệu */}
      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label}>
            Danh mục <span className={styles.required}>*</span>
          </label>
          <div className={styles.selectWrapper}>
            <select
              id="product-category"
              className={`${styles.select} ${errors.categoryId ? styles.inputError : ''}`}
              {...register('categoryId')}
            >
              <option value="">Chọn danh mục</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
            <ChevronDown className={styles.selectArrow} />
          </div>
          {errors.categoryId && <span className={styles.errorText}>{errors.categoryId.message}</span>}
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Thương hiệu</label>
          <input
            id="product-brand"
            type="text"
            className={styles.input}
            placeholder="Basic Wear"
            {...register('brand')}
          />
        </div>
      </div>

      {/* Đơn vị tính */}
      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label}>Đơn vị tính</label>
          <input
            id="product-unit"
            type="text"
            className={styles.input}
            placeholder="Cái, Hộp, Chiếc..."
            {...register('unit')}
          />
        </div>
      </div>
    </div>
  );
};

export default ProductForm;
