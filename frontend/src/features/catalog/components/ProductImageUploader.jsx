import { useState, useRef } from 'react';
import { Plus, ImageIcon, Upload, Loader2 } from 'lucide-react';
import { uploadImageToCloudinary } from '../../../api/cloudinaryApi';
import styles from './ProductImageUploader.module.css';

const ProductImageUploader = ({ images = [], onChange }) => {
  const [showUrlInput, setShowUrlInput] = useState(false);
  const [urlValue, setUrlValue] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef(null);

  const handleAddImage = () => {
    if (!urlValue.trim()) return;
    const newImage = {
      url: urlValue.trim(),
      sortOrder: images.length,
      isPrimary: images.length === 0,
    };
    onChange([...images, newImage]);
    setUrlValue('');
    setShowUrlInput(false);
  };

  const handleRemoveImage = (index) => {
    const updated = images.filter((_, i) => i !== index);
    // If we removed the primary, make first image primary
    if (updated.length > 0 && !updated.some(img => img.isPrimary)) {
      updated[0].isPrimary = true;
    }
    // Re-sort
    onChange(updated.map((img, i) => ({ ...img, sortOrder: i })));
  };

  const handleFileUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      setIsUploading(true);
      const secureUrl = await uploadImageToCloudinary(file);
      
      const newImage = {
        url: secureUrl,
        sortOrder: images.length,
        isPrimary: images.length === 0,
      };
      onChange([...images, newImage]);
    } catch (error) {
      alert(error.message || 'Lỗi khi tải ảnh lên');
    } finally {
      setIsUploading(false);
      // reset file input
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <div>
          <div className={styles.cardTitle}>Hình ảnh sản phẩm</div>
          {images.length > 0 && (
            <div className={styles.imageCount}>
              {images.length} ảnh (Ảnh đầu tiên là ảnh chính)
            </div>
          )}
        </div>
        <div className={styles.headerActions}>
          <input
            type="file"
            accept="image/*"
            hidden
            ref={fileInputRef}
            onChange={handleFileUpload}
          />
          <button
            type="button"
            className={styles.uploadBtn}
            onClick={() => fileInputRef.current?.click()}
            disabled={isUploading}
          >
            {isUploading ? (
              <Loader2 className={`${styles.btnIcon} ${styles.spin}`} />
            ) : (
              <Upload className={styles.btnIcon} />
            )}
            Tải ảnh lên
          </button>
          <button
            type="button"
            className={styles.addBtn}
            onClick={() => setShowUrlInput(true)}
            disabled={isUploading}
          >
            <Plus className={styles.btnIcon} />
            Nhập URL
          </button>
        </div>
      </div>

      {images.length === 0 ? (
        <div className={styles.emptyState}>
          <ImageIcon className={styles.emptyStateIcon} />
          <p>Chưa có hình ảnh nào. Nhấn "Thêm ảnh" để thêm URL hình ảnh.</p>
        </div>
      ) : (
        <div className={styles.imageGrid}>
          {images.map((image, index) => (
            <div key={index} className={styles.imageItem}>
              <img src={image.url} alt={`Ảnh ${index + 1}`} />
              {index === 0 && (
                <span className={styles.primaryBadge}>Ảnh chính</span>
              )}
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => handleRemoveImage(index)}
                title="Xóa ảnh"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {showUrlInput && (
        <div className={styles.urlInputOverlay} onClick={() => setShowUrlInput(false)}>
          <div className={styles.urlInputModal} onClick={(e) => e.stopPropagation()}>
            <h3>Thêm hình ảnh từ URL</h3>
            <input
              type="url"
              className={styles.urlInputField}
              placeholder="https://example.com/image.jpg"
              value={urlValue}
              onChange={(e) => setUrlValue(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAddImage()}
              autoFocus
            />
            <div className={styles.urlInputActions}>
              <button
                type="button"
                className={styles.urlCancelBtn}
                onClick={() => { setShowUrlInput(false); setUrlValue(''); }}
              >
                Hủy
              </button>
              <button
                type="button"
                className={styles.urlAddBtn}
                onClick={handleAddImage}
                disabled={!urlValue.trim()}
              >
                Thêm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProductImageUploader;
