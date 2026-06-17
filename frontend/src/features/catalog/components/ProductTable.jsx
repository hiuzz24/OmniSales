import { MoreVertical } from 'lucide-react';
import Badge from '../../../shared/components/Badge';
import styles from './ProductTable.module.css';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import productApi from '../../../api/productApi';

const getChannelBadge = (channel) => {
  switch (channel?.toUpperCase()) {
    case 'SHOPEE': return <Badge variant="shopee">Shopee</Badge>;
    case 'LAZADA': return <Badge variant="lazada">Lazada</Badge>;
    case 'TIKTOK': return <Badge variant="tiktok">TikTok</Badge>;
    default: return <Badge variant="default">{channel}</Badge>;
  }
};

const getStatusBadge = (status) => {
  switch (status?.toUpperCase()) {
    case 'ACTIVE': return <Badge variant="success">Hoạt động</Badge>;
    case 'INACTIVE': return <Badge variant="danger">Ngừng bán</Badge>;
    case 'DRAFT': return <Badge variant="default">Nháp</Badge>;
    default: return <Badge variant="default">{status || 'Unknown'}</Badge>;
  }
};

const ProductTable = ({ keyword = '', statusFilter = '', platformFilter = '' }) => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true);
        const response = await productApi.getAll(page, size, keyword, statusFilter, platformFilter);
        if (response.content) {
          setProducts(response.content);
          setTotalElements(response.totalElements || 0);
        } else if (Array.isArray(response.data)) {
          setProducts(response.data);
          setTotalElements(response.data.length);
        } else if (response.data && response.data.data) {
          setProducts(response.data.data);
          setTotalElements(response.data.total || response.data.data.length);
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, [page, size, keyword, statusFilter, platformFilter]);

  // Reset page when search or filter changes
  useEffect(() => {
    setPage(0);
  }, [keyword, statusFilter, platformFilter]);

  return (
    <div className={styles.tableContainer}>
      <div className={styles.tableHeader}>
        <h3 className={styles.tableTitle}>
          Danh sách sản phẩm <span className={styles.tableCount}>({totalElements})</span>
        </h3>
      </div>

      <div className={styles.tableResponsive}>
        <table className={styles.table}>
          <thead className={styles.thead}>
            <tr>
              <th scope="col" className={styles.th}>Sản phẩm</th>
              <th scope="col" className={styles.th}>SKU</th>
              <th scope="col" className={styles.th}>Danh mục</th>
              <th scope="col" className={styles.th}>Kênh bán</th>
              <th scope="col" className={styles.th}>Giá bán</th>
              <th scope="col" className={styles.th}>Tồn kho</th>
              <th scope="col" className={styles.th}>Trạng thái</th>
              <th scope="col" className={styles.th}>Ngày tạo</th>
              <th scope="col" className={`${styles.th} ${styles.thRight}`}>Thao tác</th>
            </tr>
          </thead>
          <tbody className={styles.tbody}>
            {loading ? (
              <tr>
                <td colSpan="8" style={{ textAlign: 'center', padding: '1rem' }}>Đang tải...</td>
              </tr>
            ) : products.length === 0 ? (
              <tr>
                <td colSpan="8" style={{ textAlign: 'center', padding: '1rem' }}>Không có dữ liệu</td>
              </tr>
            ) : (
              products.map((product) => (
                <tr key={product.id || Math.random()} className={styles.tr}>
                  <td className={styles.td}>
                    <div className={styles.productCell}>
                      <div className={styles.productImage}>
                        {(() => {
                          const imgUrl = product.images?.find(img => img.isPrimary)?.url
                            || product.variants?.[0]?.images?.[0]?.url
                            || product.images?.[0]?.url;
                          return imgUrl ? (
                            <img src={imgUrl} alt={product.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          ) : (
                            <div style={{ width: '40px', height: '40px', backgroundColor: '#e2e8f0', borderRadius: '4px' }}></div>
                          );
                        })()}
                      </div>
                      <div className={styles.productInfo}>
                        <div className={styles.productName}>{product.name || 'N/A'}</div>
                        {product.brand && <div className={styles.productId}>Thương hiệu: {product.brand}</div>}
                      </div>
                    </div>
                  </td>
                  <td className={styles.td}>
                    <div className={styles.skuText}>{product.sku || product.variants?.[0]?.sku || 'N/A'}</div>
                  </td>
                  <td className={styles.td}>
                    <div className={styles.channels}>{product.categoryName || 'Chưa phân loại'}</div>
                  </td>
                  <td className={styles.td}>
                    <div className={styles.channels} style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                      {product.channels && product.channels.length > 0
                        ? product.channels.map(channel => <span key={channel}>{getChannelBadge(channel)}</span>)
                        : <span style={{ color: '#64748b' }}>-</span>}
                    </div>
                  </td>
                  <td className={styles.td}>
                    <div className={styles.price}>
                      {(() => {
                        if (!product.variants || product.variants.length === 0) return 0;
                        const prices = product.variants.map(v => v.price).filter(p => p != null);
                        if (prices.length === 0) return 0;
                        const min = Math.min(...prices);
                        const max = Math.max(...prices);
                        return min === max ? min.toLocaleString() : `${min.toLocaleString()} - ${max.toLocaleString()}`;
                      })()}
                    </div>
                  </td>
                  <td className={styles.td}>
                    <div className={`${styles.stock} ${styles.stockdefault}`}>
                      {(() => {
                        const totalStock = product.variants?.reduce((sum, v) => sum + (v.availableQuantity || v.quantityOnHand || 0), 0) || 0;
                        return totalStock;
                      })()}
                    </div>
                  </td>
                  <td className={styles.td}>
                    <span className={styles.statusWrapper}>
                      {getStatusBadge(product.status)}
                    </span>
                  </td>
                  <td className={styles.td}>
                    <div className={styles.channels}>
                      {product.createdAt ? new Date(product.createdAt).toLocaleDateString('vi-VN') : '-'}
                    </div>
                  </td>
                  <td className={`${styles.td} ${styles.actionCell}`}>
                    <button
                      className={styles.actionBtn}
                      onClick={() => navigate(`/products/${product.id}`)}
                      title="Xem chi tiết"
                    >
                      <MoreVertical className="h-5 w-5" />
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className={styles.tableFooter}>
        <div className={styles.paginationInfo}>
          Hiển thị {products.length > 0 ? (page * size) + 1 : 0} đến {Math.min((page + 1) * size, totalElements)} trong số {totalElements} sản phẩm
        </div>
        <div className={styles.paginationControls}>
          <button
            className={styles.pageBtn}
            disabled={page === 0}
            onClick={() => setPage(prev => Math.max(0, prev - 1))}
          >
            Trước
          </button>
          <button className={`${styles.pageBtn} ${styles.pageBtnActive}`}>{page + 1}</button>
          <button
            className={styles.pageBtn}
            disabled={(page + 1) * size >= totalElements}
            onClick={() => setPage(prev => prev + 1)}
          >
            Sau
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProductTable;
