import { Eye } from 'lucide-react';
import Badge from '../../../shared/components/Badge';
import Pagination from '../../../shared/components/Pagination';
import styles from './ProductTable.module.css';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import productApi from '../../../api/productApi';

const getChannelBadge = (channel) => {
  switch (channel?.toUpperCase()) {
    case 'SHOPEE': return <Badge variant="shopee">Shopee</Badge>;
    case 'LAZADA': return <Badge variant="lazada">Lazada</Badge>;
    case 'TIKTOK': return <Badge variant="tiktok">TikTok</Badge>;
    case 'SHOPIFY': return <Badge variant="shopify">Shopify</Badge>;
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

const ActionButton = ({ product }) => {
  const navigate = useNavigate();
  return (
    <button
      className={styles.viewBtn}
      onClick={() => navigate(`/products/${product.id}`)}
      title="Xem chi tiết"
    >
      <Eye size={16} />
      <span>Chi tiết</span>
    </button>
  );
};

const ProductTable = ({ keyword = '', statusFilter = '', platformFilter = '' }) => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [page, setPage] = useState(0);
  const [size] = useState(6);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true);
        const response = await productApi.getAll(page, size, keyword, statusFilter, platformFilter);
        const responseData = response.data?.data || response.data || response;
        if (responseData.content) {
          setProducts(responseData.content);
          const nextTotalElements = responseData.totalElements ?? responseData.total ?? responseData.content.length ?? 0;
          setTotalElements(nextTotalElements);
          setTotalPages(responseData.totalPages ?? Math.ceil(nextTotalElements / size));
        } else if (Array.isArray(responseData)) {
          setProducts(responseData);
          setTotalElements(responseData.length);
          setTotalPages(Math.ceil(responseData.length / size));
        } else if (responseData.data) {
          setProducts(responseData.data);
          const nextTotalElements = responseData.totalElements ?? responseData.total ?? responseData.data.length ?? 0;
          setTotalElements(nextTotalElements);
          setTotalPages(responseData.totalPages ?? Math.ceil(nextTotalElements / size));
        } else {
          setProducts([]);
          setTotalElements(0);
          setTotalPages(0);
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, [page, size, keyword, statusFilter, platformFilter]);

  const getStockClass = (totalStock) => {
    if (totalStock === 0) return styles.stockEmpty;
    if (totalStock <= 5) return styles.stockLow;
    if (totalStock <= 20) return styles.stockMedium;
    return styles.stockGood;
  };

  return (
    <div className={styles.tableContainer}>
      <div className={styles.tableHeader}>
        <h3 className={styles.tableTitle}>
          <span className={styles.titleIcon}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
            </svg>
          </span>
          Danh sách sản phẩm
          <span className={styles.tableCount}>({totalElements})</span>
        </h3>
        <button className={styles.addBtn} onClick={() => navigate('/products/create')}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          Thêm sản phẩm
        </button>
      </div>

      <div className={styles.tableResponsive}>
        <table className={styles.table}>
          <thead className={styles.thead}>
            <tr>
              <th scope="col" className={`${styles.th} ${styles.thProduct}`}>Sản phẩm</th>
              <th scope="col" className={styles.th}>SKU</th>
              <th scope="col" className={styles.th}>Danh mục</th>
              <th scope="col" className={styles.th}>Kênh bán</th>
              <th scope="col" className={styles.th}>Liên kết sàn</th>
              <th scope="col" className={styles.th}>Giá bán</th>
              <th scope="col" className={`${styles.th} ${styles.thCenter}`}>Tồn kho</th>
              <th scope="col" className={styles.th}>Trạng thái</th>
              <th scope="col" className={`${styles.th} ${styles.thRight}`}>Chi tiết</th>
            </tr>
          </thead>
          <tbody className={styles.tbody}>
            {loading ? (
              <tr>
                <td colSpan="9" className={styles.loadingCell}>
                  <div className={styles.loadingDots}>
                    <span/><span/><span/>
                  </div>
                </td>
              </tr>
            ) : products.length === 0 ? (
              <tr>
                <td colSpan="9" className={styles.emptyCell}>
                  <div className={styles.emptyIcon}>
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                    </svg>
                  </div>
                  <p>Không có sản phẩm nào</p>
                  <button onClick={() => navigate('/products/create')}>Thêm sản phẩm đầu tiên</button>
                </td>
              </tr>
            ) : (
              products.map((product, index) => {
                const totalStock = product.variants?.reduce((sum, v) => sum + (v.availableQuantity || v.quantityOnHand || 0), 0) || 0;
                return (
                  <tr key={product.id || product.sku || `product-${page}-${index}`} className={styles.tr}>
                    <td className={styles.td}>
                      <div className={styles.productCell}>
                        <div className={styles.productImage}>
                          {(() => {
                            const imgUrl = product.images?.find(img => img.isPrimary)?.url
                              || product.variants?.[0]?.images?.[0]?.url
                              || product.images?.[0]?.url;
                            return imgUrl ? (
                              <img src={imgUrl} alt={product.name} />
                            ) : (
                              <div className={styles.imagePlaceholder}>
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                                  <rect x="3" y="3" width="18" height="18" rx="2"/>
                                  <circle cx="8.5" cy="8.5" r="1.5"/>
                                  <path d="M21 15l-5-5L5 21"/>
                                </svg>
                              </div>
                            );
                          })()}
                        </div>
                        <div className={styles.productInfo}>
                          <div className={styles.productName}>{product.name || 'N/A'}</div>
                          {product.brand && (
                            <div className={styles.productMeta}>
                              <span className={styles.metaDot}></span>
                              {product.brand}
                            </div>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className={styles.td}>
                      <code className={styles.skuText}>{product.sku || product.variants?.[0]?.sku || '—'}</code>
                    </td>
                    <td className={styles.td}>
                      <div className={styles.categoryTag}>
                        {product.categoryName || 'Chưa phân loại'}
                      </div>
                    </td>
                    <td className={styles.td}>
                      <div className={styles.channels}>
                        {product.channels && product.channels.length > 0
                          ? product.channels.map(channel => <span key={channel}>{getChannelBadge(channel)}</span>)
                          : <span className={styles.noChannel}>—</span>}
                      </div>
                    </td>
                    <td className={styles.td}>
                      {product.channels?.length > 0
                        ? <Badge variant="success">Đã liên kết</Badge>
                        : <Badge variant="default">Chưa liên kết</Badge>}
                    </td>
                    <td className={styles.td}>
                      <div className={styles.price}>
                        {(() => {
                          if (!product.variants || product.variants.length === 0) return <span className={styles.priceNone}>—</span>;
                          const prices = product.variants.map(v => v.price).filter(p => p != null);
                          if (prices.length === 0) return <span className={styles.priceNone}>—</span>;
                          const min = Math.min(...prices);
                          const max = Math.max(...prices);
                          return min === max
                            ? <span>{min.toLocaleString('vi-VN')}đ</span>
                            : <span>{min.toLocaleString('vi-VN')}đ — {max.toLocaleString('vi-VN')}đ</span>;
                        })()}
                      </div>
                    </td>
                    <td className={`${styles.td} ${styles.tdCenter}`}>
                      <div className={`${styles.stock} ${getStockClass(totalStock)}`}>
                        <span className={styles.stockValue}>{totalStock}</span>
                        <span className={styles.stockBar}>
                          <span
                            className={styles.stockBarFill}
                            style={{ width: `${Math.min(100, (totalStock / 100) * 100)}%` }}
                          />
                        </span>
                      </div>
                    </td>
                    <td className={styles.td}>
                      <span className={styles.statusWrapper}>
                        {getStatusBadge(product.status)}
                      </span>
                    </td>
                    <td className={`${styles.td} ${styles.actionCell}`}>
                      <ActionButton product={product} />
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <Pagination
        currentPage={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={size}
        currentCount={products.length}
        itemLabel="sản phẩm"
        onPageChange={setPage}
      />
      <div className={styles.tableFooter} hidden>
        <div className={styles.paginationInfo}>
          <span>Hiển thị </span>
          <strong>{products.length > 0 ? (page * size) + 1 : 0}–{Math.min((page + 1) * size, totalElements)}</strong>
          <span> / {totalElements} sản phẩm</span>
        </div>
        <div className={styles.paginationControls}>
          <button
            className={`${styles.pageBtn} ${styles.pageBtnNav}`}
            disabled={page === 0}
            onClick={() => setPage(prev => Math.max(0, prev - 1))}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M15 18l-6-6 6-6"/></svg>
            Trước
          </button>
          <div className={styles.pageNumbers}>
            {Array.from({ length: Math.min(5, Math.ceil(totalElements / size)) }, (_, i) => {
              const pageNum = Math.max(0, Math.min(Math.ceil(totalElements / size) - 5, page - 2)) + i;
              if (pageNum >= Math.ceil(totalElements / size)) return null;
              return (
                <button
                  key={pageNum}
                  className={`${styles.pageNumBtn} ${pageNum === page ? styles.pageNumActive : ''}`}
                  onClick={() => setPage(pageNum)}
                >
                  {pageNum + 1}
                </button>
              );
            })}
          </div>
          <button
            className={`${styles.pageBtn} ${styles.pageBtnNav}`}
            disabled={(page + 1) * size >= totalElements}
            onClick={() => setPage(prev => prev + 1)}
          >
            Sau
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 18l6-6-6-6"/></svg>
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProductTable;
