import { Eye, Image, Link2, PackageOpen, PackageSearch, Settings2 } from 'lucide-react';
import Badge from '../../../shared/components/Badge';
import Pagination from '../../../shared/components/Pagination';
import styles from './ProductTable.module.css';
import { useEffect, useState } from 'react';
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

const formatMoney = (value) => Number(value || 0).toLocaleString('vi-VN');

const ActionButton = ({ product }) => {
  const navigate = useNavigate();
  return (
    <button
      className={styles.viewBtn}
      onClick={() => navigate(`/products/${product.id}`)}
      title="Xem chi tiết"
      type="button"
    >
      <Eye size={16} />
      <span>Chi tiết</span>
    </button>
  );
};

const MarketplaceLinkButton = ({ product }) => {
  const navigate = useNavigate();
  const linkedCount = product.channels?.length || 0;
  return (
    <button
      type="button"
      className={linkedCount > 0 ? styles.marketplaceLinkedBtn : styles.marketplaceLinkBtn}
      onClick={() => navigate(`/products/${product.id}/edit`, { state: { focusMarketplace: true } })}
      aria-label={`Sửa liên kết sàn cho ${product.name || 'sản phẩm'}`}
      title="Sửa liên kết sàn và cấu hình dữ liệu bắt buộc theo từng sàn"
    >
      {linkedCount > 0 ? <Settings2 size={15} /> : <Link2 size={15} />}
      <span>{linkedCount > 0 ? `Sửa liên kết (${linkedCount})` : 'Liên kết sàn'}</span>
    </button>
  );
};

const ProductTable = ({ keyword = '', statusFilter = '', platformFilter = '' }) => {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [page, setPage] = useState(0);
  const [size] = useState(5);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true);
        const response = await productApi.getAll(page, size, keyword, statusFilter, platformFilter);
        const responseData = response.data?.data || response.data || response;
        const content = responseData.content || responseData.data || (Array.isArray(responseData) ? responseData : []);
        const nextTotalElements = responseData.totalElements ?? responseData.total ?? content.length ?? 0;
        setProducts(content);
        setTotalElements(nextTotalElements);
        setTotalPages(responseData.totalPages ?? Math.ceil(nextTotalElements / size));
      } catch (error) {
        console.error('Failed to fetch products:', error);
        setProducts([]);
        setTotalElements(0);
        setTotalPages(0);
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
            <PackageOpen aria-hidden="true" />
          </span>
          Danh sách sản phẩm
          <span className={styles.tableCount}>({totalElements})</span>
        </h3>
      </div>

      <div className={styles.tableResponsive}>
        <table className={`${styles.table} ${products.length === size ? styles.tableFilled : ''}`}>
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
                    <PackageSearch aria-hidden="true" />
                  </div>
                  <p>Không có sản phẩm nào</p>
                  <button onClick={() => navigate('/products/create')} type="button">Thêm sản phẩm đầu tiên</button>
                </td>
              </tr>
            ) : (
              products.map((product, index) => {
                const totalStock = product.variants?.reduce((sum, v) => sum + (v.availableQuantity || v.quantityOnHand || 0), 0) || 0;
                const imageUrl = product.images?.find((img) => img.isPrimary)?.url
                  || product.variants?.[0]?.images?.[0]?.url
                  || product.images?.[0]?.url;
                return (
                  <tr key={product.id || product.sku || `product-${page}-${index}`} className={styles.tr}>
                    <td className={styles.td}>
                      <div className={styles.productCell}>
                        <div className={styles.productImage}>
                          {imageUrl ? (
                            <img src={imageUrl} alt={product.name || 'Sản phẩm'} />
                          ) : (
                            <div className={styles.imagePlaceholder}>
                              <Image aria-hidden="true" />
                            </div>
                          )}
                        </div>
                        <div className={styles.productInfo}>
                          <div className={styles.productName} title={product.name || 'N/A'}>{product.name || 'N/A'}</div>
                          {product.brand && (
                            <div className={styles.productMeta} title={product.brand}>
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
                      <div className={styles.categoryTag} title={product.categoryName || 'Chưa phân loại'}>
                        {product.categoryName || 'Chưa phân loại'}
                      </div>
                    </td>
                    <td className={styles.td}>
                      <div className={styles.channels}>
                        {product.channels && product.channels.length > 0
                          ? product.channels.map((channel) => <span key={channel}>{getChannelBadge(channel)}</span>)
                          : <span className={styles.noChannel}>—</span>}
                      </div>
                    </td>
                    <td className={styles.td}>
                      <div className={styles.marketplaceCell}>
                        <MarketplaceLinkButton product={product} />
                        <span className={styles.marketplaceHint}>
                          {product.channels?.length > 0 ? 'Dùng chung tồn kho' : 'Chọn sàn để bán'}
                        </span>
                      </div>
                    </td>
                    <td className={styles.td}>
                      <div className={styles.price}>
                        {(() => {
                          if (!product.variants || product.variants.length === 0) return <span className={styles.priceNone}>—</span>;
                          const prices = product.variants.map((v) => v.price).filter((p) => p != null);
                          if (prices.length === 0) return <span className={styles.priceNone}>—</span>;
                          const min = Math.min(...prices);
                          const max = Math.max(...prices);
                          return min === max
                            ? <span>{formatMoney(min)}đ</span>
                            : <span>{formatMoney(min)}đ — {formatMoney(max)}đ</span>;
                        })()}
                      </div>
                    </td>
                    <td className={`${styles.td} ${styles.tdCenter}`}>
                      <div className={`${styles.stock} ${getStockClass(totalStock)}`}>
                        <span className={styles.stockValue}>{totalStock}</span>
                        <span className={styles.stockBar}>
                          <span
                            className={styles.stockBarFill}
                            style={{ width: `${Math.min(100, totalStock)}%` }}
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
        className={styles.tablePagination}
        currentPage={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={size}
        currentCount={products.length}
        itemLabel="sản phẩm"
        onPageChange={setPage}
      />
    </div>
  );
};

export default ProductTable;
