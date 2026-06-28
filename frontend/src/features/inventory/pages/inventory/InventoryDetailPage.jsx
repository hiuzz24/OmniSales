import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  Package,
  AlertTriangle,
  Download,
  ArrowRightLeft,
  Info,
  MapPin,
  TrendingUp,
  Clock,
  User,
  Hash,
  Loader2,
  AlertCircle,
  Edit2,
  X
} from 'lucide-react';
import { toast } from 'react-toastify';
import styles from './InventoryDetailPage.module.css';
import inventoryApi from '../../../../api/inventoryApi';
import warehouseService from '../../services/warehouseService';
import { ROUTES } from '../../../../app/router/routes';

const PAGE_SIZE = 10;

const InventoryDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const variantId = searchParams.get('variantId') || id;

  const [detail, setDetail] = useState(null);
  const [loadingDetail, setLoadingDetail] = useState(true);
  const [errorDetail, setErrorDetail] = useState(null);

  const [transactions, setTransactions] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [loadingTx, setLoadingTx] = useState(false);
  const [txFilter, setTxFilter] = useState('all');

  // Edit State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [warehouses, setWarehouses] = useState([]);
  const [editName, setEditName] = useState('');
  const [editWarehouseId, setEditWarehouseId] = useState('');
  const [editQty, setEditQty] = useState(0);
  const [editPrice, setEditPrice] = useState(0);
  const [editCost, setEditCost] = useState(0);
  const [updating, setUpdating] = useState(false);

  // Fetch Detail
  useEffect(() => {
    const fetchDetail = async () => {
      try {
        setLoadingDetail(true);
        const res = await inventoryApi.getInventoryItemDetail(id);
        setDetail(res);
        window.dispatchEvent(new Event('notifications:refresh'));
      } catch (err) {
        console.error('Error fetching inventory detail', err);
        setErrorDetail('Không thể tải thông tin chi tiết tồn kho.');
      } finally {
        setLoadingDetail(false);
      }
    };
    fetchDetail();
  }, [id]);

  // Fetch Transactions
  const fetchTransactions = useCallback(async (page, filter) => {
    try {
      setLoadingTx(true);
      // If we had a specific type filter in the API, we'd pass it.
      // The API signature in controller doesn't show type filter, so we'll filter on client side if needed
      // or assume it's just pagination. For now, we fetch all and paginate.
      const res = await inventoryApi.getTransactions(variantId, page, PAGE_SIZE);
      if (res) {
        setTransactions(res.content || []);
        setTotalElements(res.totalElements || 0);
        setTotalPages(res.totalPages || 0);
      }
    } catch (err) {
      console.error('Error fetching transactions', err);
    } finally {
      setLoadingTx(false);
    }
  }, [variantId]);

  useEffect(() => {
    fetchTransactions(currentPage, txFilter);
  }, [currentPage, txFilter, fetchTransactions]);

  const handlePageChange = (newPage) => {
    setCurrentPage(newPage);
  };

  const formatCurrency = (val) => {
    if (val === null || val === undefined) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return d.toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit',
      day: '2-digit', month: '2-digit', year: 'numeric'
    });
  };

  if (loadingDetail) {
    return (
      <div className={styles.loadingWrapper}>
        <Loader2 size={32} className={styles.spinner} />
        <span>Đang tải thông tin...</span>
      </div>
    );
  }

  if (errorDetail) {
    return (
      <div className={styles.page}>
        <div className={styles.errorWrapper}>
          <AlertCircle size={20} />
          <span>{errorDetail}</span>
        </div>
        <button className={styles.btnOutline} onClick={() => navigate(ROUTES.INVENTORY)}>
          <ArrowLeft size={16} /> Quay lại danh sách
        </button>
      </div>
    );
  }

  if (!detail) return null;

  const isLowStock = detail.quantityOnHand < detail.lowStockThreshold;
  const progressPercent = Math.min(100, Math.max(0, (detail.quantityOnHand / (detail.lowStockThreshold * 3)) * 100));
  const handleOpenEditModal = async () => {
    setEditName(detail.productVariantName);
    setEditWarehouseId(detail.warehouseId);
    setEditQty(detail.quantityOnHand);
    setEditPrice(detail.price);
    setEditCost(detail.averageCost);
    setIsEditModalOpen(true);

    try {
      const res = await warehouseService.getAll();
      setWarehouses(res.data?.data ?? res.data ?? []);
    } catch (err) {
      console.error('Error fetching warehouses:', err);
    }
  };

  const handleUpdateDetail = async (e) => {
    e.preventDefault();
    if (!editName.trim()) {
      toast.error('Tên sản phẩm không được để trống');
      return;
    }
    if (editPrice < 0 || editCost < 0 || editQty < 0) {
      toast.error('Giá bán, giá vốn và số lượng không được âm');
      return;
    }
    try {
      setUpdating(true);
      const payload = {
        productVariantName: editName,
        price: editPrice,
        averageCost: editCost,
        quantityOnHand: editQty,
        warehouseId: editWarehouseId
      };
      const updatedDetail = await inventoryApi.updateInventoryItemDetail(id, payload);
      setDetail(updatedDetail);
      toast.success('Cập nhật thông tin tồn kho thành công');
      setIsEditModalOpen(false);
      // Refresh transactions too
      fetchTransactions(currentPage, txFilter);
    } catch (err) {
      console.error('Error updating inventory detail:', err);
      toast.error(err?.response?.data?.message || 'Có lỗi xảy ra khi cập nhật thông tin');
    } finally {
      setUpdating(false);
    }
  };

  const handleReceiveStock = () => {
    const params = new URLSearchParams({
      warehouseId: detail.warehouseId || '',
      variantId: detail.variantId || variantId,
      sku: detail.variantSku || '',
      productName: detail.productVariantName || '',
      unitCost: detail.averageCost ?? 0,
    });
    navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?${params.toString()}`);
  };

  return (
    <div className={styles.page}>
      {/* Header */}
      <div className={styles.pageHeader}>
        <div className={styles.headerLeft}>
          <button className={styles.backBtn} onClick={() => navigate(ROUTES.INVENTORY)}>
            <ArrowLeft size={16} /> Quay lại
          </button>
          <div className={styles.productIconWrap}>
            <Package size={24} />
          </div>
          <div className={styles.titleBlock}>
            <h1 className={styles.pageTitle}>{detail.productVariantName}</h1>
            <span className={styles.sku}>{detail.variantSku}</span>
          </div>
        </div>
        <div className={styles.headerActions}>
          <button className={styles.btnOutline} onClick={handleReceiveStock}>
            <Download size={16} /> Nhập kho
          </button>
          <button className={styles.btnOutline}>
            <ArrowRightLeft size={16} /> Chuyển kho
          </button>
          <button className={styles.btnOutline} onClick={handleOpenEditModal}>
            <Edit2 size={16} /> Sửa
          </button>
        </div>
      </div>

      {/* Warning Banner */}
      {isLowStock && (
        <div className={styles.warningBanner}>
          <AlertTriangle size={18} className={styles.warningIcon} />
          Tồn kho đang ở mức thấp ({detail.quantityOnHand} / tối thiểu {detail.lowStockThreshold}). Cần nhập thêm hàng sớm.
        </div>
      )}

      {/* Summary Cards */}
      <div className={styles.summaryGrid}>
        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Tồn kho</span>
            <span className={styles.summaryValue}>
              {detail.quantityOnHand}
              <span className={styles.summarySubLabel}>Cái</span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ background: '#fef3c7', color: '#d97706' }}>
            <Package size={16} />
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Giá vốn</span>
            <span className={styles.summaryValue}>
              {formatCurrency(detail.averageCost)}
              <span className={styles.summarySubLabel}>/ Cái</span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ background: '#f1f5f9', color: '#64748b' }}>
            <Info size={16} />
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Giá trị tồn</span>
            <span className={styles.summaryValue}>
              {formatCurrency(detail.totalInventoryValue)}
              <span >tồn x giá vốn</span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ background: '#eff6ff', color: '#3b82f6' }}>
            <span style={{ fontWeight: 'bold', fontSize: '14px' }}>$</span>
          </div>
        </div>
        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Biên lợi nhuận</span>
            <span className={styles.summaryValue}>
              {detail.profitMargin}%
              <span className={styles.summarySubLabel}>
                {formatCurrency(detail.price - detail.averageCost)} / cái
              </span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ background: '#f5f3ff', color: '#8b5cf6' }}>
            <TrendingUp size={16} />
          </div>
        </div>
      </div>

      {/* Main Grid */}
      <div className={styles.mainGrid}>
        <div className={styles.leftCol}>
          {/* Product Info */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <Package className={styles.cardIcon} size={20} />
              <h2 className={styles.cardTitle}>Thông tin sản phẩm</h2>
            </div>
            <div className={styles.infoGrid}>
              <div className={styles.infoItem}>
                <span className={styles.infoLabel}>Mã sản phẩm (SKU)</span>
                <span className={`${styles.infoValue} ${styles.highlight}`}>{detail.variantSku}</span>
              </div>
              <div className={styles.infoItem}>
                <span className={styles.infoLabel}>Tên sản phẩm</span>
                <span className={styles.infoValue}>{detail.productVariantName}</span>
              </div>
              <div className={styles.infoItem}>
                <span className={styles.infoLabel}>Danh mục</span>
                <span className={`${styles.infoValue} ${styles.highlight}`} style={{ background: '#f3e8ff', color: '#9333ea' }}>{detail.categoryName}</span>
              </div>
              <div className={styles.infoItem}>
                <span className={styles.infoLabel}>Giá bán</span>
                <span className={styles.infoValue}>{formatCurrency(detail.price)}</span>
              </div>
              <div className={styles.infoItem}>
                <span className={styles.infoLabel}>Nhập kho gần nhất</span>
                <span className={styles.infoValue}>{formatDate(detail.lastImportedAt) || 'Chưa có'}</span>
              </div>
              <div className={styles.infoItem}>
                <span className={styles.infoLabel}>Cập nhật lần cuối</span>
                <span className={styles.infoValue}>{formatDate(detail.lastUpdatedAt)}</span>
              </div>
            </div>
          </div>

          {/* Location */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <MapPin className={styles.cardIcon} size={20} />
              <h2 className={styles.cardTitle}>Vị trí lưu kho</h2>
            </div>
            <div className={styles.locationCards}>
              <div className={styles.locationItem}>
                <div className={styles.locationIconWrap}>
                  <Package size={20} />
                </div>
                <div className={styles.locationInfo}>
                  <span className={styles.locationLabel}>Kho hàng</span>
                  <span className={styles.locationValue}>{detail.warehouseName}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className={styles.rightCol}>
          {/* Stock Level */}
          <div className={styles.card}>
            <div className={styles.cardHeader}>
              <TrendingUp className={styles.cardIcon} size={20} />
              <h2 className={styles.cardTitle}>Mức tồn kho</h2>
            </div>

            <div className={styles.stockLevelBlock}>
              <span className={styles.stockLargeValue}>{detail.quantityOnHand}</span>
              <span className={styles.stockSubLabel}>Cái hiện có</span>
            </div>

            <div className={styles.progressBarContainer}>
              <div className={styles.progressLabels}>
                <span>0</span>
                <span>Tối thiểu: {detail.lowStockThreshold}</span>
                <span>{detail.lowStockThreshold * 3}</span>
              </div>
              <div className={styles.progressBarTrack}>
                <div className={styles.progressBarFill} style={{ width: `${progressPercent}%`, backgroundColor: isLowStock ? '#f59e0b' : '#10b981' }}></div>
                <div style={{ position: 'absolute', left: `${(detail.lowStockThreshold / (detail.lowStockThreshold * 3)) * 100}%`, top: 0, bottom: 0, width: 2, backgroundColor: '#cbd5e1' }}></div>
              </div>
              <div className={styles.progressStatus}>
                <span className={styles.statusBadge} style={isLowStock ? {} : { borderColor: '#bbf7d0', color: '#16a34a', backgroundColor: '#dcfce7' }}>
                  {isLowStock ? 'Sắp hết' : 'Đủ hàng'}
                </span>
                <span className={styles.progressMinMax}>Tối thiểu: {detail.lowStockThreshold}</span>
              </div>
            </div>

            <div className={styles.stockPrices}>
              <div className={styles.priceRow}>
                <span className={styles.priceLabel}>Giá vốn / cái</span>
                <span className={styles.priceValue}>{formatCurrency(detail.averageCost)}</span>
              </div>
              <div className={styles.priceRow}>
                <span className={styles.priceLabel}>Giá bán / cái</span>
                <span className={styles.priceValue}>{formatCurrency(detail.price)}</span>
              </div>
              <div className={styles.totalValueRow}>
                <span className={styles.totalValueLabel}>Tổng giá trị tồn</span>
                <span className={styles.totalValueAmount}>{formatCurrency(detail.totalInventoryValue)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* History */}
      <div className={styles.card}>
        <div className={styles.historyHeader}>
          <div className={styles.historyTitleWrap}>
            <Clock className={styles.historyIcon} size={20} />
            <h2 className={styles.cardTitle}>Lịch sử biến động kho</h2>
            <span className={styles.historyCount}>{totalElements} giao dịch</span>
          </div>
          <select
            className={styles.historyFilter}
            value={txFilter}
            onChange={(e) => setTxFilter(e.target.value)}
          >
            <option value="all">Tất cả</option>
            <option value="in">Nhập kho</option>
            <option value="out">Xuất kho</option>
          </select>
        </div>

        <div className={styles.tableWrapper}>
          {loadingTx ? (
            <div className={styles.loadingWrapper}>
              <Loader2 size={24} className={styles.spinner} />
              <span>Đang tải lịch sử...</span>
            </div>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th className={styles.th}>Thời gian</th>
                  <th className={styles.th}>Loại</th>
                  <th className={styles.th}>Mô tả</th>
                  <th className={styles.th}>Phiếu tham chiếu</th>
                  <th className={styles.thRight}>Số lượng</th>
                  <th className={styles.thRight}>Đơn giá</th>
                  <th className={styles.thRight}>Giá trị</th>
                  <th className={styles.thRight}>Tồn sau</th>
                  <th className={styles.th}>Người thực hiện</th>
                </tr>
              </thead>
              <tbody>
                {transactions.length === 0 ? (
                  <tr>
                    <td colSpan={9} style={{ textAlign: 'center', padding: '24px', color: '#64748b' }}>
                      Không có lịch sử biến động.
                    </td>
                  </tr>
                ) : (
                  transactions.map((tx) => (
                    <tr key={tx.id}>
                      <td className={styles.td}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#64748b' }}>
                          <Clock size={14} />
                          {formatDate(tx.performedAt)}
                        </div>
                      </td>
                      <td className={styles.td}>
                        <span className={`${styles.typeBadge} ${tx.quantityChange < 0 ? styles.typeIssue : styles.typeReceipt}`}>
                          {tx.quantityChange < 0 ? <ArrowRightLeft size={12} /> : <Download size={12} />}
                          {tx.typeLabel || (tx.quantityChange < 0 ? 'Xuất kho' : 'Nhập kho')}
                        </span>
                      </td>
                      <td className={styles.td}>{tx.note || '-'}</td>
                      <td className={styles.td}>
                        <span className={styles.refLink}>
                          {tx.referenceId ? tx.referenceId.split('-')[0].toUpperCase() : '-'}
                        </span>
                      </td>
                      <td className={`${styles.td} ${styles.tdRight} ${styles.qtyChange} ${tx.quantityChange < 0 ? styles.qtyNegative : styles.qtyPositive}`}>
                        {tx.quantityChange > 0 ? '+' : ''}{tx.quantityChange}
                      </td>
                      <td className={`${styles.td} ${styles.tdRight}`}>{formatCurrency(tx.unitCost)}</td>
                      <td className={`${styles.td} ${styles.tdRight}`}>{formatCurrency(tx.transactionValue)}</td>
                      <td className={`${styles.td} ${styles.tdRight}`}>{tx.quantityAfter}</td>
                      <td className={styles.td}>
                        <div className={styles.userWrap}>
                          <User size={14} className={styles.userIcon} />
                          {tx.performedByName || 'Hệ thống'}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          )}
        </div>

        {totalPages > 1 && (
          <div className={styles.paginationFooter}>
            <span className={styles.paginationInfo}>
              Hiển thị {currentPage * PAGE_SIZE + 1} / {Math.min((currentPage + 1) * PAGE_SIZE, totalElements)} giao dịch
            </span>
            <div className={styles.paginationControls}>
              {Array.from({ length: totalPages }).map((_, idx) => (
                <button
                  key={idx}
                  className={`${styles.pageBtn} ${idx === currentPage ? styles.active : ''}`}
                  onClick={() => handlePageChange(idx)}
                >
                  {idx + 1}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Edit Inventory Item Modal */}
      {isEditModalOpen && (
        <div className={styles.modalOverlay}>
          <div className={styles.modalContent}>
            <div className={styles.modalHeader}>
              <div className={styles.modalTitleWrapper}>
                <Package size={20} />
                <h3 className={styles.modalTitle}>Sửa thông tin tồn kho</h3>
              </div>
              <button className={styles.modalCloseBtn} onClick={() => setIsEditModalOpen(false)}>
                <X size={20} />
              </button>
            </div>
            <form onSubmit={handleUpdateDetail}>
              <div className={styles.modalBody}>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>
                    Tên sản phẩm <span className={styles.requiredStar}>*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    className={styles.formInput}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>
                    Kho hàng <span className={styles.requiredStar}>*</span>
                  </label>
                  <select
                    required
                    value={editWarehouseId}
                    onChange={(e) => setEditWarehouseId(e.target.value)}
                    className={styles.formSelect}
                  >
                    {warehouses.map((w) => (
                      <option key={w.id} value={w.id}>
                        {w.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>
                    Số lượng tồn kho <span className={styles.requiredStar}>*</span>
                  </label>
                  <input
                    type="number"
                    required
                    min="0"
                    value={editQty}
                    onChange={(e) => setEditQty(parseInt(e.target.value) || 0)}
                    className={styles.formInput}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>
                    Giá bán <span className={styles.requiredStar}>*</span>
                  </label>
                  <input
                    type="number"
                    required
                    min="0"
                    value={editPrice}
                    onChange={(e) => setEditPrice(parseFloat(e.target.value) || 0)}
                    className={styles.formInput}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>
                    Giá vốn <span className={styles.requiredStar}>*</span>
                  </label>
                  <input
                    type="number"
                    required
                    min="0"
                    value={editCost}
                    onChange={(e) => setEditCost(parseFloat(e.target.value) || 0)}
                    className={styles.formInput}
                  />
                </div>
              </div>
              <div className={styles.modalFooter}>
                <button type="button" className={styles.cancelBtn} onClick={() => setIsEditModalOpen(false)} disabled={updating}>
                  Hủy
                </button>
                <button type="submit" className={styles.primaryBtn} disabled={updating}>
                  {updating ? 'Đang lưu...' : 'Lưu lại'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default InventoryDetailPage;
