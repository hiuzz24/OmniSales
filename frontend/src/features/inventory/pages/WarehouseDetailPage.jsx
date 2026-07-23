import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Warehouse,
  MapPin,
  Users,
  Package,
  Boxes,
  Plus,
  ArrowRightLeft,
  Clock,
  FileText,
  Search,
  Loader2,
  AlertCircle,
  Edit3,
  CheckCircle2,
  XCircle,
  Download
} from 'lucide-react';
import { toast } from 'react-toastify';
import styles from './WarehouseDetailPage.module.css';
import warehouseService from '../services/warehouseService';
import inventoryApi from '../../../api/inventoryApi';
import stockReceiveService from '../services/stockReceiveService';
import transferApi from '../../../api/transferApi';
import Pagination from '../../../shared/components/Pagination';
import { ROUTES } from '../../../app/router/routes';
import useAuth from '../../auth/hooks/useAuth';
import { ROLES } from '../../auth/constants/roles';
import WarehouseModal from './components/WarehouseModal';

const PAGE_SIZE = 10;

export default function WarehouseDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isReadOnly = user?.role === ROLES.OPERATIONS;

  const [warehouse, setWarehouse] = useState(null);
  const [loadingWarehouse, setLoadingWarehouse] = useState(true);
  const [errorWarehouse, setErrorWarehouse] = useState(null);

  // Active Tab: 'products' | 'receipts' | 'transfers' | 'logs'
  const [activeTab, setActiveTab] = useState('products');

  // Edit Modal State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);

  // Data states for Tabs
  const [productsData, setProductsData] = useState([]);
  const [productsPage, setProductsPage] = useState(0);
  const [productsTotalElements, setProductsTotalElements] = useState(0);
  const [productsTotalPages, setProductsTotalPages] = useState(0);
  const [loadingProducts, setLoadingProducts] = useState(false);
  const [productKeyword, setProductKeyword] = useState('');

  const [receiptsData, setReceiptsData] = useState([]);
  const [receiptsPage, setReceiptsPage] = useState(0);
  const [receiptsTotalElements, setReceiptsTotalElements] = useState(0);
  const [receiptsTotalPages, setReceiptsTotalPages] = useState(0);
  const [loadingReceipts, setLoadingReceipts] = useState(false);

  const [transfersData, setTransfersData] = useState([]);
  const [transfersPage, setTransfersPage] = useState(0);
  const [transfersTotalElements, setTransfersTotalElements] = useState(0);
  const [transfersTotalPages, setTransfersTotalPages] = useState(0);
  const [loadingTransfers, setLoadingTransfers] = useState(false);

  const [logsData, setLogsData] = useState([]);
  const [logsPage, setLogsPage] = useState(0);
  const [logsTotalElements, setLogsTotalElements] = useState(0);
  const [logsTotalPages, setLogsTotalPages] = useState(0);
  const [loadingLogs, setLoadingLogs] = useState(false);

  // Fetch Warehouse Details
  const fetchWarehouseDetail = useCallback(async () => {
    try {
      setLoadingWarehouse(true);
      setErrorWarehouse(null);
      const res = await warehouseService.getWarehouseById(id);
      const data = res.data?.data ?? res.data;
      setWarehouse(data);
    } catch (err) {
      console.error('Error fetching warehouse details:', err);
      setErrorWarehouse('Không thể tải thông tin kho hàng.');
    } finally {
      setLoadingWarehouse(false);
    }
  }, [id]);

  useEffect(() => {
    fetchWarehouseDetail();
  }, [fetchWarehouseDetail]);

  // Tab 1: Products
  const fetchProducts = useCallback(async (page, keyword) => {
    try {
      setLoadingProducts(true);
      const res = await inventoryApi.getInventoryList(
        page,
        PAGE_SIZE,
        'updatedAt',
        'desc',
        null,
        null,
        false,
        { warehouseId: id, keyword }
      );
      setProductsData(res.content || []);
      setProductsTotalElements(res.totalElements || 0);
      setProductsTotalPages(res.totalPages || 0);
    } catch (err) {
      console.error('Error fetching warehouse products:', err);
    } finally {
      setLoadingProducts(false);
    }
  }, [id]);

  // Tab 2: Receipts
  const fetchReceipts = useCallback(async (page) => {
    try {
      setLoadingReceipts(true);
      const res = await stockReceiveService.getReceipts({ page, size: PAGE_SIZE });
      const pageData = res.data?.data ?? res.data;
      const content = pageData?.content || (Array.isArray(pageData) ? pageData : []);
      // Filter by warehouseId if applicable
      const filtered = content.filter(item => item.warehouseId === id || !item.warehouseId);
      setReceiptsData(filtered);
      setReceiptsTotalElements(pageData?.totalElements || filtered.length);
      setReceiptsTotalPages(pageData?.totalPages || 1);
    } catch (err) {
      console.error('Error fetching receipts:', err);
    } finally {
      setLoadingReceipts(false);
    }
  }, [id]);

  // Tab 3: Transfers
  const fetchTransfers = useCallback(async (page) => {
    try {
      setLoadingTransfers(true);
      const resData = await transferApi.getTransfers({ warehouseId: id, page, size: PAGE_SIZE });
      setTransfersData(resData?.content || resData?.transfers || (Array.isArray(resData) ? resData : []));
      setTransfersTotalElements(resData?.totalElements || 0);
      setTransfersTotalPages(resData?.totalPages || 1);
    } catch (err) {
      console.error('Error fetching transfers:', err);
    } finally {
      setLoadingTransfers(false);
    }
  }, [id]);

  // Tab 4: Logs
  const fetchLogs = useCallback(async (page) => {
    try {
      setLoadingLogs(true);
      const res = await inventoryApi.getInventoryLogs({ warehouseId: id, page, size: PAGE_SIZE });
      const pageData = res?.data ?? res;
      setLogsData(pageData?.content || (Array.isArray(pageData) ? pageData : []));
      setLogsTotalElements(pageData?.totalElements || 0);
      setLogsTotalPages(pageData?.totalPages || 0);
    } catch (err) {
      console.error('Error fetching inventory logs:', err);
    } finally {
      setLoadingLogs(false);
    }
  }, [id]);

  // Initial load: Fetch all tabs in parallel to populate badge counts immediately on page load/reload
  useEffect(() => {
    if (!id) return;
    fetchProducts(0, '');
    fetchReceipts(0);
    fetchTransfers(0);
    fetchLogs(0);
  }, [id, fetchProducts, fetchReceipts, fetchTransfers, fetchLogs]);

  // Handle active tab / page / search updates
  useEffect(() => {
    if (activeTab === 'products') {
      fetchProducts(productsPage, productKeyword);
    }
  }, [productsPage, productKeyword, activeTab, fetchProducts]);

  useEffect(() => {
    if (activeTab === 'receipts') {
      fetchReceipts(receiptsPage);
    }
  }, [receiptsPage, activeTab, fetchReceipts]);

  useEffect(() => {
    if (activeTab === 'transfers') {
      fetchTransfers(transfersPage);
    }
  }, [transfersPage, activeTab, fetchTransfers]);

  useEffect(() => {
    if (activeTab === 'logs') {
      fetchLogs(logsPage);
    }
  }, [logsPage, activeTab, fetchLogs]);

  const formatCurrency = (val) => {
    if (val === null || val === undefined) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(val);
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    return d.toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit',
      day: '2-digit', month: '2-digit', year: 'numeric'
    });
  };

  if (loadingWarehouse) {
    return (
      <div className={styles.loadingWrapper}>
        <Loader2 size={32} className={styles.spinner} />
        <span>Đang tải thông tin kho hàng...</span>
      </div>
    );
  }

  if (errorWarehouse) {
    return (
      <div className={styles.page}>
        <div style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca', color: '#ef4444', padding: 16, borderRadius: 8, display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <AlertCircle size={20} />
          <span>{errorWarehouse}</span>
        </div>
        <button className={styles.backBtn} onClick={() => navigate(ROUTES.WAREHOUSE)}>
          <ArrowLeft size={16} /> Quay lại quản lý kho
        </button>
      </div>
    );
  }

  if (!warehouse) return null;

  const stockVal = warehouse.totalStock ?? warehouse.stock ?? 0;
  const staffCount = warehouse.staffCount ?? 0;
  const productCount = warehouse.productCount ?? 0;

  return (
    <div className={styles.page}>
      {/* Header */}
      <div className={styles.pageHeader}>
        <div className={styles.headerLeft}>
          <button className={styles.backBtn} onClick={() => navigate(ROUTES.WAREHOUSE)}>
            <ArrowLeft size={16} /> Danh sách kho
          </button>
          <div className={styles.headerIconWrap}>
            <Warehouse size={26} />
          </div>
          <div className={styles.titleBlock}>
            <div className={styles.titleWithBadge}>
              <h1 className={styles.pageTitle}>{warehouse.name}</h1>
              <span className={`${styles.statusBadge} ${warehouse.isActive ? styles.badgeActive : styles.badgeInactive}`}>
                {warehouse.isActive ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
                {warehouse.isActive ? 'Đang hoạt động' : 'Ngừng hoạt động'}
              </span>
            </div>
            <div className={styles.addressRow}>
              <MapPin size={15} />
              <span>{warehouse.address || 'Chưa cập nhật địa chỉ'}</span>
            </div>
          </div>
        </div>

        <div className={styles.headerActions}>
          {!isReadOnly && (
            <button className={styles.btnOutline} onClick={() => setIsEditModalOpen(true)}>
              <Edit3 size={16} /> Chỉnh sửa kho
            </button>
          )}
          <button 
            className={styles.btnOutline}
            onClick={() => navigate(`${ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE}?warehouseId=${warehouse.id}`)}
          >
            <Download size={16} /> Nhập kho
          </button>
          <button 
            className={styles.btnPrimary}
            onClick={() => navigate(`${ROUTES.STOCK_TRANSFER_CREATE}?fromWarehouseId=${warehouse.id}`)}
          >
            <ArrowRightLeft size={16} /> Chuyển kho
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className={styles.summaryGrid}>
        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Tổng tồn kho hiện tại</span>
            <span className={styles.summaryValue}>
              {stockVal.toLocaleString('vi-VN')}
              <span className={styles.summaryUnit}>sản phẩm</span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ backgroundColor: '#fffbeb', color: '#d97706' }}>
            <Boxes size={22} />
          </div>
        </div>

        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Chủng loại sản phẩm (SKU)</span>
            <span className={styles.summaryValue}>
              {productCount.toLocaleString('vi-VN')}
              <span className={styles.summaryUnit}>loại</span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ backgroundColor: '#f3e8ff', color: '#9333ea' }}>
            <Package size={22} />
          </div>
        </div>

        <div className={styles.summaryCard}>
          <div className={styles.summaryInfo}>
            <span className={styles.summaryLabel}>Nhân viên phụ trách kho</span>
            <span className={styles.summaryValue}>
              {staffCount.toLocaleString('vi-VN')}
              <span className={styles.summaryUnit}>người</span>
            </span>
          </div>
          <div className={styles.summaryIconWrap} style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}>
            <Users size={22} />
          </div>
        </div>
      </div>

      {/* Main Content Tabs */}
      <div className={styles.card}>
        <div className={styles.tabHeader}>
          <button 
            className={`${styles.tabBtn} ${activeTab === 'products' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('products')}
          >
            <Package size={18} />
            Sản phẩm tồn kho
            <span className={styles.tabBadge}>{productsTotalElements}</span>
          </button>

          <button 
            className={`${styles.tabBtn} ${activeTab === 'receipts' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('receipts')}
          >
            <Download size={18} />
            Lịch sử nhập kho
            <span className={styles.tabBadge}>{receiptsTotalElements}</span>
          </button>

          <button 
            className={`${styles.tabBtn} ${activeTab === 'transfers' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('transfers')}
          >
            <ArrowRightLeft size={18} />
            Lịch sử chuyển kho
            <span className={styles.tabBadge}>{transfersTotalElements}</span>
          </button>

          <button 
            className={`${styles.tabBtn} ${activeTab === 'logs' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('logs')}
          >
            <Clock size={18} />
            Nhật ký biến động kho
            <span className={styles.tabBadge}>{logsTotalElements}</span>
          </button>
        </div>

        {/* TAB 1: Products */}
        {activeTab === 'products' && (
          <div>
            <div className={styles.filterBar}>
              <div className={styles.searchInputWrap}>
                <Search size={16} color="#64748b" />
                <input 
                  type="text" 
                  className={styles.searchInput}
                  placeholder="Tìm kiếm sản phẩm theo mã SKU, tên..."
                  value={productKeyword}
                  onChange={(e) => {
                    setProductKeyword(e.target.value);
                    setProductsPage(0);
                  }}
                />
              </div>
            </div>

            <div className={styles.tableWrapper}>
              {loadingProducts ? (
                <div className={styles.loadingWrapper}>
                  <Loader2 size={24} className={styles.spinner} />
                  <span>Đang tải danh sách sản phẩm...</span>
                </div>
              ) : productsData.length === 0 ? (
                <div className={styles.emptyState}>Không tìm thấy sản phẩm nào trong kho này.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th className={styles.th}>Mã SKU</th>
                      <th className={styles.th}>Tên sản phẩm</th>
                      <th className={styles.th}>Danh mục</th>
                      <th className={styles.thRight}>Tồn kho tại kho này</th>
                      <th className={styles.thRight}>Giá vốn</th>
                      <th className={styles.thRight}>Giá bán</th>
                    </tr>
                  </thead>
                  <tbody>
                    {productsData.map((item) => (
                      <tr 
                        key={item.id} 
                        className={styles.tr}
                        onClick={() => navigate(ROUTES.INVENTORY_DETAIL.replace(':id', item.id))}
                        style={{ cursor: 'pointer' }}
                      >
                        <td className={styles.td}>
                          <span style={{ fontWeight: 600, color: '#2563eb' }}>{item.variantSku || item.sku || '-'}</span>
                        </td>
                        <td className={styles.td}>
                          <span style={{ fontWeight: 600, color: '#0f172a' }}>{item.productVariantName || item.productName || item.name}</span>
                        </td>
                        <td className={styles.td}>{item.categoryName || '-'}</td>
                        <td className={`${styles.td} ${styles.tdRight}`} style={{ fontWeight: 700, color: '#0f172a' }}>
                          {item.quantityOnHand ?? item.quantity ?? 0}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight}`}>{formatCurrency(item.averageCost ?? item.costPrice)}</td>
                        <td className={`${styles.td} ${styles.tdRight}`}>{formatCurrency(item.price ?? item.sellingPrice)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <Pagination 
              currentPage={productsPage}
              totalPages={productsTotalPages}
              totalElements={productsTotalElements}
              pageSize={PAGE_SIZE}
              currentCount={productsData.length}
              itemLabel="sản phẩm"
              onPageChange={setProductsPage}
            />
          </div>
        )}

        {/* TAB 2: Receipts */}
        {activeTab === 'receipts' && (
          <div>
            <div className={styles.tableWrapper}>
              {loadingReceipts ? (
                <div className={styles.loadingWrapper}>
                  <Loader2 size={24} className={styles.spinner} />
                  <span>Đang tải lịch sử nhập kho...</span>
                </div>
              ) : receiptsData.length === 0 ? (
                <div className={styles.emptyState}>Chưa có phiếu nhập kho nào cho kho này.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th className={styles.th}>Mã phiếu</th>
                      <th className={styles.th}>Ngày tạo</th>
                      <th className={styles.th}>Nhà cung cấp</th>
                      <th className={styles.thRight}>Tổng số lượng</th>
                      <th className={styles.thRight}>Tổng tiền</th>
                      <th className={styles.th}>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {receiptsData.map((rc) => (
                      <tr 
                        key={rc.id} 
                        className={styles.tr}
                        onClick={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_DETAIL.replace(':id', rc.id))}
                        style={{ cursor: 'pointer' }}
                      >
                        <td className={styles.td}>
                          <span style={{ fontWeight: 600, color: '#2563eb' }}>{rc.code || rc.receiptCode || rc.id.slice(0, 8)}</span>
                        </td>
                        <td className={styles.td}>{formatDate(rc.createdAt || rc.receiptDate)}</td>
                        <td className={styles.td}>{rc.supplierName || rc.supplier?.name || '-'}</td>
                        <td className={`${styles.td} ${styles.tdRight}`}>{rc.totalQuantity ?? rc.totalItems ?? 0}</td>
                        <td className={`${styles.td} ${styles.tdRight}`}>{formatCurrency(rc.totalAmount)}</td>
                        <td className={styles.td}>
                          <span className={`${styles.statusBadge} ${rc.status === 'COMPLETED' ? styles.badgeActive : styles.typeIssue}`}>
                            {rc.statusLabel || rc.status || 'Hoàn thành'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <Pagination 
              currentPage={receiptsPage}
              totalPages={receiptsTotalPages}
              totalElements={receiptsTotalElements}
              pageSize={PAGE_SIZE}
              currentCount={receiptsData.length}
              itemLabel="phiếu nhập"
              onPageChange={setReceiptsPage}
            />
          </div>
        )}

        {/* TAB 3: Transfers */}
        {activeTab === 'transfers' && (
          <div>
            <div className={styles.tableWrapper}>
              {loadingTransfers ? (
                <div className={styles.loadingWrapper}>
                  <Loader2 size={24} className={styles.spinner} />
                  <span>Đang tải lịch sử chuyển kho...</span>
                </div>
              ) : transfersData.length === 0 ? (
                <div className={styles.emptyState}>Chưa có phiếu chuyển kho nào liên quan đến kho này.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th className={styles.th}>Mã phiếu chuyển</th>
                      <th className={styles.th}>Ngày tạo</th>
                      <th className={styles.th}>Kho xuất</th>
                      <th className={styles.th}>Kho nhận</th>
                      <th className={styles.thRight}>Tổng số lượng</th>
                      <th className={styles.th}>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transfersData.map((tf) => (
                      <tr key={tf.id} className={styles.tr}>
                        <td className={styles.td}>
                          <span style={{ fontWeight: 600, color: '#2563eb' }}>{tf.code || tf.transferCode || tf.id.slice(0, 8)}</span>
                        </td>
                        <td className={styles.td}>{formatDate(tf.createdAt || tf.transferDate)}</td>
                        <td className={styles.td}>{tf.fromWarehouseName || tf.sourceWarehouseName || '-'}</td>
                        <td className={styles.td}>{tf.toWarehouseName || tf.targetWarehouseName || '-'}</td>
                        <td className={`${styles.td} ${styles.tdRight}`}>{tf.totalQuantity ?? tf.totalItems ?? 0}</td>
                        <td className={styles.td}>
                          <span className={`${styles.statusBadge} ${tf.status === 'COMPLETED' ? styles.badgeActive : styles.typeTransfer}`}>
                            {tf.statusLabel || tf.status || 'Hoàn thành'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <Pagination 
              currentPage={transfersPage}
              totalPages={transfersTotalPages}
              totalElements={transfersTotalElements}
              pageSize={PAGE_SIZE}
              currentCount={transfersData.length}
              itemLabel="phiếu chuyển"
              onPageChange={setTransfersPage}
            />
          </div>
        )}

        {/* TAB 4: Logs */}
        {activeTab === 'logs' && (
          <div>
            <div className={styles.tableWrapper}>
              {loadingLogs ? (
                <div className={styles.loadingWrapper}>
                  <Loader2 size={24} className={styles.spinner} />
                  <span>Đang tải nhật ký biến động kho...</span>
                </div>
              ) : logsData.length === 0 ? (
                <div className={styles.emptyState}>Chưa có lịch sử biến động kho.</div>
              ) : (
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th className={styles.th}>Thời gian</th>
                      <th className={styles.th}>Loại biến động</th>
                      <th className={styles.th}>Sản phẩm (SKU)</th>
                      <th className={styles.thRight}>Thay đổi</th>
                      <th className={styles.thRight}>Tồn sau</th>
                      <th className={styles.th}>Ghi chú</th>
                      <th className={styles.th}>Người thực hiện</th>
                    </tr>
                  </thead>
                  <tbody>
                    {logsData.map((log) => (
                      <tr key={log.id || Math.random()} className={styles.tr}>
                        <td className={styles.td}>{formatDate(log.performedAt || log.createdAt)}</td>
                        <td className={styles.td}>
                          <span className={`${styles.statusBadge} ${log.quantityChange < 0 ? styles.typeIssue : styles.typeReceipt}`}>
                            {log.typeLabel || (log.quantityChange < 0 ? 'Xuất kho' : 'Nhập kho')}
                          </span>
                        </td>
                        <td className={styles.td}>
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            <span style={{ fontWeight: 600, color: '#0f172a' }}>
                              {log.productName
                                ? (log.variantName ? `${log.productName} (${log.variantName})` : log.productName)
                                : (log.variantName || log.productVariantName || '-')}
                            </span>
                            <span style={{ fontSize: 12, color: '#64748b' }}>{log.variantSku || log.sku}</span>
                          </div>
                        </td>
                        <td className={`${styles.td} ${styles.tdRight} ${log.quantityChange < 0 ? styles.qtyNegative : styles.qtyPositive}`}>
                          {log.quantityChange > 0 ? '+' : ''}{log.quantityChange}
                        </td>
                        <td className={`${styles.td} ${styles.tdRight}`}>{log.quantityAfter ?? log.balanceAfter ?? '-'}</td>
                        <td className={styles.td}>{log.note || log.description || '-'}</td>
                        <td className={styles.td}>{log.performedByName || log.createdByName || 'Hệ thống'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <Pagination 
              currentPage={logsPage}
              totalPages={logsTotalPages}
              totalElements={logsTotalElements}
              pageSize={PAGE_SIZE}
              currentCount={logsData.length}
              itemLabel="nội dung biến động"
              onPageChange={setLogsPage}
            />
          </div>
        )}
      </div>

      {/* Edit Warehouse Modal */}
      {isEditModalOpen && (
        <WarehouseModal 
          isOpen={isEditModalOpen}
          onClose={() => setIsEditModalOpen(false)}
          onRefresh={fetchWarehouseDetail}
          warehouseData={warehouse}
          readOnly={false}
        />
      )}
    </div>
  );
}
