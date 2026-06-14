import { ChevronDown } from 'lucide-react';
import styles from './ProductForm.module.css';

const ProductForm = ({ formData, onChange, categories = [], errors = {} }) => {
  const handleChange = (field, value) => {
    onChange({ ...formData, [field]: value });
  };

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
            value={formData.name || ''}
            onChange={(e) => handleChange('name', e.target.value)}
          />
          {errors.name && <span className={styles.errorText}>{errors.name}</span>}
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
            value={formData.sku || ''}
            onChange={(e) => handleChange('sku', e.target.value)}
          />
          {errors.sku && <span className={styles.errorText}>{errors.sku}</span>}
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Barcode</label>
          <input
            id="product-barcode"
            type="text"
            className={styles.input}
            placeholder="8936012345678"
            value={formData.barcode || ''}
            onChange={(e) => handleChange('barcode', e.target.value)}
          />
        </div>
      </div>

      {/* Mô tả */}
      <div className={styles.row}>
        <div className={`${styles.field} ${styles.fullWidth}`}>
          <label className={styles.label}>Mô tả sản phẩm</label>
          <textarea
            id="product-description"
            className={styles.textarea}
            placeholder="Mô tả chi tiết về sản phẩm..."
            value={formData.description || ''}
            onChange={(e) => handleChange('description', e.target.value)}
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
              value={formData.categoryId || ''}
              onChange={(e) => handleChange('categoryId', e.target.value)}
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
          {errors.categoryId && <span className={styles.errorText}>{errors.categoryId}</span>}
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Thương hiệu</label>
          <input
            id="product-brand"
            type="text"
            className={styles.input}
            placeholder="Basic Wear"
            value={formData.brand || ''}
            onChange={(e) => handleChange('brand', e.target.value)}
          />
        </div>
      </div>
    </div>
  );
};

export default ProductForm;
