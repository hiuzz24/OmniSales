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
            <label className={styles.label}>Barcode <span className={styles.required}>*</span></label>
            <input
              id="product-barcode"
              type="text"
              className={`${styles.input} ${errors.barcode ? styles.inputError : ''}`}
              placeholder="8936012345678"
            {...register('barcode')}
            />
            {errors.barcode && <span className={styles.errorText}>{errors.barcode.message}</span>}
          </div>
        )}
      </div>

      {/* Thuộc tính cơ bản khi không có biến thể */}
      {!hasVariants && (
        <div className={styles.row}>
          <div className={styles.field}>
            <label className={styles.label}>Size <span className={styles.required}>*</span></label>
            <input
              id="product-size"
              type="text"
              className={`${styles.input} ${errors.size ? styles.inputError : ''}`}
              placeholder="Ví dụ: Freesize"
            {...register('size')}
            />
            {errors.size && <span className={styles.errorText}>{errors.size.message}</span>}
          </div>
          <div className={styles.field}>
            <label className={styles.label}>Màu sắc <span className={styles.required}>*</span></label>
            <input
              id="product-color"
              type="text"
              className={`${styles.input} ${errors.color ? styles.inputError : ''}`}
              placeholder="Ví dụ: Đen"
            {...register('color')}
            />
            {errors.color && <span className={styles.errorText}>{errors.color.message}</span>}
          </div>
        </div>
      )}

      {/* Mô tả */}
      <div className={styles.row}>
        <div className={`${styles.field} ${styles.fullWidth}`}>
          <label className={styles.label}>Mô tả sản phẩm <span className={styles.required}>*</span></label>
          <textarea
            id="product-description"
            className={`${styles.textarea} ${errors.description ? styles.inputError : ''}`}
            placeholder="Mô tả chi tiết về sản phẩm..."
            {...register('description')}
            rows={3}
          />
          {errors.description && <span className={styles.errorText}>{errors.description.message}</span>}
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
          <label className={styles.label}>Thương hiệu <span className={styles.required}>*</span></label>
          <input
            id="product-brand"
            type="text"
            className={`${styles.input} ${errors.brand ? styles.inputError : ''}`}
            placeholder="Basic Wear"
            {...register('brand')}
          />
          {errors.brand && <span className={styles.errorText}>{errors.brand.message}</span>}
        </div>
      </div>

      {/* Đơn vị tính */}
      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label}>Đơn vị tính <span className={styles.required}>*</span></label>
          <input
            id="product-unit"
            type="text"
            className={`${styles.input} ${errors.unit ? styles.inputError : ''}`}
            placeholder="Cái, Hộp, Chiếc..."
            {...register('unit')}
          />
          {errors.unit && <span className={styles.errorText}>{errors.unit.message}</span>}
        </div>
      </div>
    </div>
  );
};

export default ProductForm;
