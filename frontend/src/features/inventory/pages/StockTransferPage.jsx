import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  ArrowRightLeft,
  CheckCircle,
  Download,
  Eye,
  FileText,
  Info,
  MoreHorizontal,
  Plus,
  Truck,
  XCircle,
  Loader2,
  Warehouse,
  User,
  Calendar,
} from 'lucide-react';
import { toast } from 'react-toastify';
import useAuth from '../../auth/hooks/useAuth';
import transferApi from '../../../api/transferApi';
import warehouseService from '../services/warehouseService';
import { ROUTES } from '../../../app/router/routes';
import InventoryDocumentListPage, {
  ActionMenuItem,
  ActionMenuShell,
  Badge,
  FilterSelect,
  SearchInput,
} from './components/InventoryDocumentListPage';
import {
  formatDate,
  formatDateTime,
  formatNumber,
  formatVND,
  getResponseData,
  tableCellStyle,
} from './components/inventoryDocumentListUtils';
import { exportTransfersToExcel } from '../utils/exportTransfers';

const StatusBadge = ({ status }) => {
  const normalizedStatus = status?.toUpperCase() || 'DRAFT';
  if (normalizedStatus === 'COMPLETED' || normalizedStatus === 'RECEIVED') {
    return (
      <Badge 
        label="Hoàn thành" 
        icon={CheckCircle} 
        color="#059669" 
        bg="#ecfdf5" 
        border="#a7f3d0" 
      />
    );
  }
  if (normalizedStatus === 'IN_TRANSIT') {
    return (
      <Badge 
        label="Đang vận chuyển" 
        icon={Truck} 
        color="#475569" 
        bg="#f1f5f9" 
        border="#e2e8f0" 
      />
    );
  }
  if (normalizedStatus === 'DRAFT') {
    return (
      <Badge 
        label="Nháp" 
        icon={FileText} 
        color="#b45309" 
        bg="#fffbeb" 
        border="#fde68a" 
      />
    );
  }
  if (normalizedStatus === 'CANCELLED') {
    return (
      <Badge 
        label="Đã hủy" 
        icon={XCircle} 
        color="#dc2626" 
        bg="#fef2f2" 
        border="#fca5a5" 
      />
    );
  }
  return (
    <Badge 
      label={status} 
      icon={Info} 
      color="#475569" 
      bg="#f1f5f9" 
      border="#e2e8f0" 
    />
  );
};

const ActionMenu = ({ transfer, user, onViewDetails, onShip, onReceive, onCancel }) => {
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  const isCreator = user?.id === transfer?.createdById;
  const isDestStaff = user?.warehouseId && user?.warehouseId === transfer?.toWarehouseId;
  const isSourceStaff = user?.warehouseId && user?.warehouseId === transfer?.fromWarehouseId;
  const isGlobalManager = user?.role === 'OWNER' || user?.role === 'SYSTEM_ADMIN';

  const canShip = transfer?.status === 'DRAFT' && (isCreator || isSourceStaff || isGlobalManager);
  const canReceive = transfer?.status === 'IN_TRANSIT' && (isDestStaff || isGlobalManager);
  const canCancel = transfer?.status === 'DRAFT'
    ? (isCreator || isSourceStaff || isGlobalManager)
    : transfer?.status === 'IN_TRANSIT'
      ? (isDestStaff || isGlobalManager)
      : false;

  return (
    <ActionMenuShell menuRef={menuRef} open={open} buttonIcon={MoreHorizontal} onToggle={() => setOpen((c) => !c)}>
      <ActionMenuItem onClick={() => { setOpen(false); onViewDetails(transfer); }}>
        <Eye size={14} /> Xem chi tiết
      </ActionMenuItem>
      {canShip && (
        <ActionMenuItem color="#2563eb" onClick={() => { setOpen(false); onShip(transfer); }}>
          <Truck size={14} /> Vận chuyển
        </ActionMenuItem>
      )}
      {canReceive && (
        <ActionMenuItem color="#059669" onClick={() => { setOpen(false); onReceive(transfer); }}>
          <CheckCircle size={14} /> Xác nhận nhận hàng
        </ActionMenuItem>
      )}
      {canCancel && (
        <ActionMenuItem color="#dc2626" onClick={() => { setOpen(false); onCancel(transfer); }}>
          <XCircle size={14} /> Hủy phiếu
        </ActionMenuItem>
      )}
    </ActionMenuShell>
  );
};

const columns = [
  { label: 'Mã phiếu' },
  { label: 'Kho xuất' },
  { label: '' },
  { label: 'Kho nhận' },
  { label: 'SL SKU', align: 'right' },
  { label: 'Tổng SL', align: 'right' },
  { label: 'Ghi chú' },
  { label: 'Trạng thái' },
  { label: 'Người tạo' },
  { label: 'Ngày tạo' },
  { label: '', align: 'right' },
];

const InfoRow = ({ icon: Icon, label, value, color = '#0f172a' }) => (
  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '11px 0', borderBottom: '1px solid #f1f5f9' }}>
    <div style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, border: '1px solid #e2e8f0' }}>
      <Icon size={15} color="#64748b" />
    </div>
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 11, color: '#64748b', marginBottom: 2 }}>{label}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={value}>{value}</div>
    </div>
  </div>
);

const DetailModal = ({ isOpen, onClose, transferId, user, onStatusChange }) => {
  const [transfer, setTransfer] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchDetail = async () => {
    if (!transferId) return;
    setLoading(true);
    try {
      const data = await transferApi.getTransferDetail(transferId);
      setTransfer(data);
    } catch (err) {
      toast.error('Không thể tải chi tiết phiếu chuyển kho.');
      onClose();
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      fetchDetail();
    } else {
      setTransfer(null);
    }
  }, [isOpen, transferId]);

  if (!isOpen) return null;

  const totalQty = transfer?.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
  const totalAmount = transfer?.items?.reduce((sum, item) => sum + (item.quantity * (item.unitCost || 0)), 0) || 0;

  // Permissions check
  const isCreator = user?.id === transfer?.createdById;
  const isDestStaff = user?.warehouseId && user?.warehouseId === transfer?.toWarehouseId;
  const isSourceStaff = user?.warehouseId && user?.warehouseId === transfer?.fromWarehouseId;
  const isGlobalManager = user?.role === 'OWNER' || user?.role === 'SYSTEM_ADMIN';

  const canShip = transfer?.status === 'DRAFT' && (isCreator || isSourceStaff || isGlobalManager);
  const canReceive = transfer?.status === 'IN_TRANSIT' && (isDestStaff || isGlobalManager);
  const canCancel = transfer?.status === 'DRAFT'
    ? (isCreator || isSourceStaff || isGlobalManager)
    : transfer?.status === 'IN_TRANSIT'
      ? (isDestStaff || isGlobalManager)
      : false;

  console.log('DetailModal check:', {
    userRole: user?.role,
    userWarehouse: user?.warehouseId,
    transferToWh: transfer?.toWarehouseId,
    transferStatus: transfer?.status,
    isDestStaff,
    isSourceStaff,
    isGlobalManager,
    canShip,
    canReceive,
    canCancel
  });

  const handleShip = async () => {
    try {
      await transferApi.updateTransferStatus(transfer.id, 'IN_TRANSIT', user.id);
      toast.success('Phiếu chuyển đã bắt đầu vận chuyển.');
      onStatusChange();
      fetchDetail();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Lỗi khi cập nhật trạng thái vận chuyển.');
    }
  };

  const handleReceive = async () => {
    try {
      await transferApi.updateTransferStatus(transfer.id, 'RECEIVED', user.id);
      toast.success('Đã xác nhận nhận hàng thành công.');
      onStatusChange();
      fetchDetail();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Lỗi khi xác nhận nhận hàng.');
    }
  };

  const handleCancel = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn hủy phiếu chuyển kho này không?')) return;
    try {
      await transferApi.updateTransferStatus(transfer.id, 'CANCELLED', user.id);
      toast.success('Đã hủy phiếu chuyển kho.');
      onStatusChange();
      fetchDetail();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Lỗi khi hủy phiếu.');
    }
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: 1000,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: 'rgba(15, 23, 42, 0.5)',
      backdropFilter: 'blur(4px)',
      padding: '16px',
    }}>
      <div style={{
        width: '100%',
        maxWidth: '960px',
        maxHeight: '90vh',
        backgroundColor: '#ffffff',
        borderRadius: '16px',
        boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}>
        {/* Header */}
        <div style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          padding: '24px 32px',
          borderBottom: '1px solid #f1f5f9',
        }}>
          <div>
            <h3 style={{ fontSize: '18px', fontWeight: 700, color: '#0f172a', margin: 0 }}>
              Chi tiết phiếu chuyển kho
            </h3>
            {transfer && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '6px' }}>
                <span style={{ fontFamily: 'monospace', fontSize: '14px', fontWeight: 700, color: '#7c3aed' }}>
                  {transfer.transferCode}
                </span>
                <StatusBadge status={transfer.status} />
              </div>
            )}
          </div>
          <button 
            onClick={onClose} 
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              background: '#f8fafc',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#64748b',
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#e2e8f0'; }}
            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#f8fafc'; }}
          >
            <XCircle size={18} />
          </button>
        </div>

        {/* Content */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
          {loading && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifycontent: 'center', padding: '80px 0', color: '#94a3b8', gap: '12px' }}>
              <Loader2 className="animate-spin text-violet-600" size={32} style={{ animation: 'spin 1s linear infinite' }} />
              <span>Đang tải thông tin chi tiết...</span>
            </div>
          )}

          {!loading && transfer && (
            <>
              {/* General Info and Staff Section */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
                gap: '24px',
                backgroundColor: '#f8fafc',
                padding: '24px',
                borderRadius: '12px',
                border: '1px solid #e2e8f0',
                marginBottom: '32px',
              }}>
                <div>
                  <h4 style={{ fontSize: '12px', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', tracking: 'wider', marginBottom: '8px' }}>
                    Thông tin chuyển kho
                  </h4>
                  <InfoRow icon={Warehouse} label="Kho xuất" value={transfer.fromWarehouseName} />
                  <InfoRow icon={Warehouse} label="Kho nhận" value={transfer.toWarehouseName} />
                  <InfoRow icon={Calendar} label="Ngày tạo" value={formatDateTime(transfer.createdAt)} />
                </div>

                <div>
                  <h4 style={{ fontSize: '12px', fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', tracking: 'wider', marginBottom: '8px' }}>
                    Nhân sự thực hiện
                  </h4>
                  <InfoRow icon={User} label="Người tạo" value={transfer.createdByName} />
                  <InfoRow icon={User} label="Người duyệt" value={transfer.approvedByName || '-'} />
                  <InfoRow icon={Calendar} label="Ngày nhận" value={transfer.transferTime ? formatDateTime(transfer.transferTime) : '-'} />
                </div>

                {transfer.note && (
                  <div style={{ gridColumn: '1 / -1', borderTop: '1px solid #e2e8f0', paddingTop: '16px', fontSize: '13px', color: '#475569', fontStyle: 'italic' }}>
                    <strong>Ghi chú:</strong> "{transfer.note}"
                  </div>
                )}
              </div>

              {/* Items List */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <h4 style={{ fontSize: '14px', fontWeight: 700, color: '#1e293b', margin: 0 }}>
                  Danh sách sản phẩm điều chuyển
                </h4>
                <div style={{ border: '1px solid #e2e8f0', borderRadius: '12px', overflow: 'hidden' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                    <thead>
                      <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', textAlign: 'left' }}>
                        <th style={{ padding: '12px 16px', width: '60px', textAlign: 'center', color: '#475569', fontWeight: 600 }}>#</th>
                        <th style={{ padding: '12px 16px', color: '#475569', fontWeight: 600 }}>SKU</th>
                        <th style={{ padding: '12px 16px', color: '#475569', fontWeight: 600 }}>Tên sản phẩm</th>
                        <th style={{ padding: '12px 16px', textAlign: 'right', color: '#475569', fontWeight: 600 }}>Số lượng</th>
                        <th style={{ padding: '12px 16px', textAlign: 'right', color: '#475569', fontWeight: 600 }}>Đơn giá</th>
                        <th style={{ padding: '12px 16px', textAlign: 'right', color: '#475569', fontWeight: 600 }}>Thành tiền</th>
                      </tr>
                    </thead>
                    <tbody style={{ color: '#334155' }}>
                      {transfer.items?.map((item, index) => (
                        <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                          <td style={{ padding: '14px 16px', textAlign: 'center', color: '#94a3b8' }}>{index + 1}</td>
                          <td style={{ padding: '14px 16px', fontFamily: 'monospace', fontWeight: 600, color: '#7c3aed', fontSize: '12px' }}>{item.variantSku}</td>
                          <td style={{ padding: '14px 16px', fontWeight: 500, color: '#0f172a' }}>{item.variantName}</td>
                          <td style={{ padding: '14px 16px', textAlign: 'right', fontWeight: 600 }}>{formatNumber(item.quantity)}</td>
                          <td style={{ padding: '14px 16px', textAlign: 'right', color: '#64748b' }}>{formatVND(item.unitCost)}</td>
                          <td style={{ padding: '14px 16px', textAlign: 'right', fontWeight: 700, color: '#0f172a' }}>{formatVND(item.quantity * (item.unitCost || 0))}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr style={{ backgroundColor: '#f8fafc', fontWeight: 700, borderTop: '2px solid #e2e8f0' }}>
                        <td colSpan="3" style={{ padding: '14px 16px', textAlign: 'right', color: '#64748b' }}>Tổng cộng:</td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: '#0f172a', fontWeight: 800 }}>{formatNumber(totalQty)}</td>
                        <td></td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: '#7c3aed', fontWeight: 800, fontSize: '15px' }}>{formatVND(totalAmount)}</td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        <div style={{
          backgroundColor: '#f8fafc',
          padding: '20px 32px',
          borderTop: '1px solid #f1f5f9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <div>
            {transfer && canCancel && (
              <button 
                onClick={handleCancel} 
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '9px 16px',
                  borderRadius: '8px',
                  border: '1px solid #fca5a5',
                  backgroundColor: '#ffffff',
                  color: '#dc2626',
                  fontSize: '13px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#fef2f2'; }}
                onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#ffffff'; }}
              >
                <XCircle size={15} /> Hủy phiếu
              </button>
            )}
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button 
              onClick={onClose} 
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                padding: '9px 18px',
                borderRadius: '8px',
                border: '1px solid #e2e8f0',
                backgroundColor: '#ffffff',
                color: '#334155',
                fontSize: '13px',
                fontWeight: 600,
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#f8fafc'; }}
              onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#ffffff'; }}
            >
              Đóng
            </button>
            {transfer && canShip && (
              <button 
                onClick={handleShip} 
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '9px 18px',
                  borderRadius: '8px',
                  border: 'none',
                  backgroundColor: '#7c3aed',
                  color: '#ffffff',
                  fontSize: '13px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                  transition: 'all 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#6d28d9'; }}
                onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#7c3aed'; }}
              >
                <Truck size={15} /> Vận chuyển
              </button>
            )}
            {transfer && canReceive && (
              <button 
                onClick={handleReceive} 
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '9px 18px',
                  borderRadius: '8px',
                  border: 'none',
                  backgroundColor: '#059669',
                  color: '#ffffff',
                  fontSize: '13px',
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                  transition: 'all 0.2s',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#047857'; }}
                onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#059669'; }}
              >
                <CheckCircle size={15} /> Xác nhận nhận hàng
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default function StockTransferPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();

  const [transfers, setTransfers] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [summary, setSummary] = useState({});
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  const [showDetailModal, setShowDetailModal] = useState(false);
  const [selectedTransferId, setSelectedTransferId] = useState(null);

  // Auto open detail modal if navigated from notification
  useEffect(() => {
    if (location.state?.openTransferId) {
      setSelectedTransferId(location.state.openTransferId);
      setShowDetailModal(true);
      // Clear location state
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const fetchTransfers = async () => {
    setLoading(true);
    try {
      const params = { page, size: rowsPerPage };
      if (keyword.trim()) params.keyword = keyword.trim();
      if (status) params.status = status;
      if (warehouseId) params.warehouseId = warehouseId;
      const response = await transferApi.getTransfers(params);
      const data = getResponseData(response);
      const content = data.transfers ?? [];
      setTransfers(Array.isArray(content) ? content : []);
      setSummary(data.summary ?? {});
      setTotalElements(data.totalElements ?? content.length ?? 0);
    } catch (error) {
      toast.error('Không thể tải danh sách phiếu chuyển kho.');
      setTransfers([]);
      setSummary({});
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    warehouseService.getAll()
      .then((response) => {
        const data = getResponseData(response);
        setWarehouses(Array.isArray(data) ? data : data.content ?? []);
      })
      .catch(() => setWarehouses([]));
  }, []);

  useEffect(() => {
    const timer = setTimeout(fetchTransfers, keyword.trim() ? 250 : 0);
    return () => clearTimeout(timer);
  }, [page, keyword, status, warehouseId]);

  const handleExport = async () => {
    try {
      const params = { page: 0, size: 10000 };
      if (keyword.trim()) params.keyword = keyword.trim();
      if (status) params.status = status;
      if (warehouseId) params.warehouseId = warehouseId;
      const response = await transferApi.getTransfers(params);
      const data = getResponseData(response);
      const allTransfers = data.transfers ?? [];
      if (allTransfers.length === 0) {
        toast.warning('Không có dữ liệu để xuất Excel.');
        return;
      }
      exportTransfersToExcel(allTransfers);
      toast.success('Xuất Excel thành công.');
    } catch (error) {
      toast.error('Lỗi khi xuất file Excel.');
    }
  };

  const stats = useMemo(() => ([
    { key: 'total', label: 'Tổng phiếu', value: summary.totalTickets ?? totalElements, icon: FileText, color: '#64748b', bg: '#f1f5f9', border: '#e2e8f0' },
    { key: 'completed', label: 'Hoàn thành', value: summary.completedCount ?? 0, icon: CheckCircle, color: '#10b981', bg: '#ecfdf5', border: '#a7f3d0' },
    { key: 'in_transit', label: 'Đang vận chuyển', value: summary.inTransitCount ?? 0, icon: Truck, color: '#3b82f6', bg: '#eff6ff', border: '#bfdbfe' },
    { key: 'draft', label: 'Bản nháp', value: summary.draftCount ?? 0, icon: Info, color: '#f59e0b', bg: '#fff7ed', border: '#fed7aa' }
  ]), [summary, totalElements]);

  const rows = transfers.map((transfer) => (
    <tr key={transfer.id}>
      <td 
        onClick={() => { setSelectedTransferId(transfer.id); setShowDetailModal(true); }}
        style={{ ...tableCellStyle, color: '#7c3aed', fontFamily: 'monospace', fontWeight: 700, cursor: 'pointer' }}
        className="hover:underline"
      >
        {transfer.transferCode}
      </td>
      <td style={tableCellStyle}>{transfer.fromWarehouseName || '-'}</td>
      <td style={{ ...tableCellStyle, width: 30, textAlign: 'center' }}>
        <ArrowRightLeft size={14} className="text-slate-300" />
      </td>
      <td style={tableCellStyle}>{transfer.toWarehouseName || '-'}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right' }}>{formatNumber(transfer.skuCount)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right', fontWeight: 700 }}>{formatNumber(transfer.totalQuantity)}</td>
      <td style={{ ...tableCellStyle, color: '#64748b', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {transfer.note || '-'}
      </td>
      <td style={tableCellStyle}>
        <StatusBadge status={transfer.status} />
      </td>
      <td style={tableCellStyle}>{transfer.createdBy || '-'}</td>
      <td style={tableCellStyle}>{formatDateTime(transfer.createdAt)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right' }}>
        <ActionMenu 
          transfer={transfer} 
          user={user}
          onViewDetails={(t) => { setSelectedTransferId(t.id); setShowDetailModal(true); }}
          onShip={async (t) => {
            try {
              await transferApi.updateTransferStatus(t.id, 'IN_TRANSIT', user.id);
              toast.success("Phiếu chuyển đã bắt đầu vận chuyển.");
              fetchTransfers();
            } catch (err) {
              toast.error(err.response?.data?.message || "Lỗi khi cập nhật trạng thái vận chuyển.");
            }
          }}
          onReceive={async (t) => {
            try {
              await transferApi.updateTransferStatus(t.id, 'RECEIVED', user.id);
              toast.success("Đã xác nhận nhận hàng thành công.");
              fetchTransfers();
            } catch (err) {
              toast.error(err.response?.data?.message || "Lỗi khi xác nhận nhận hàng.");
            }
          }}
          onCancel={async (t) => {
            if (!window.confirm("Bạn có chắc chắn muốn hủy phiếu chuyển kho này không?")) return;
            try {
              await transferApi.updateTransferStatus(t.id, 'CANCELLED', user.id);
              toast.success("Đã hủy phiếu chuyển kho.");
              fetchTransfers();
            } catch (err) {
              toast.error(err.response?.data?.message || "Lỗi khi hủy phiếu.");
            }
          }}
        />
      </td>
    </tr>
  ));

  const totalPages = Math.max(1, Math.ceil(totalElements / rowsPerPage));
  const firstVisible = totalElements === 0 ? 0 : page * rowsPerPage + 1;
  const lastVisible = Math.min(totalElements, (page + 1) * rowsPerPage);

  return (
    <>
      <InventoryDocumentListPage
        icon={ArrowRightLeft}
        iconBg="#f5f3ff"
        iconColor="#7c3aed"
        title="Danh sách phiếu chuyển kho"
        description="Quản lý tất cả phiếu điều chuyển hàng hóa giữa các kho"
        createLabel="Tạo phiếu chuyển"
        onCreate={() => navigate(ROUTES.STOCK_TRANSFER_CREATE)}
        onExport={handleExport}
        stats={stats}
        filters={(
          <>
            <SearchInput value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(0); }} placeholder="Tìm theo mã phiếu, kho, ghi chú..." />
            <FilterSelect value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="">Tất cả trạng thái</option>
              <option value="DRAFT">Nháp</option>
              <option value="IN_TRANSIT">Đang vận chuyển</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="CANCELLED">Đã hủy</option>
            </FilterSelect>
            <FilterSelect value={warehouseId} onChange={(event) => { setWarehouseId(event.target.value); setPage(0); }} minWidth={210}>
              <option value="">Tất cả kho</option>
              {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </FilterSelect>
          </>
        )}
        columns={columns}
        rows={rows}
        loading={loading}
        emptyText="Không tìm thấy phiếu chuyển kho nào."
        footerLeft={`Hiển thị ${formatNumber(transfers.length)} / ${formatNumber(totalElements)} phiếu`}
        pagination={{
          page,
          totalPages,
          totalElements,
          label: `Hiển thị ${formatNumber(firstVisible)} - ${formatNumber(lastVisible)} / ${formatNumber(totalElements)} phiếu`,
          onPrevious: () => setPage((current) => Math.max(0, current - 1)),
          onNext: () => setPage((current) => Math.min(totalPages - 1, current + 1)),
        }}
      />
      <DetailModal 
        isOpen={showDetailModal} 
        onClose={() => { setShowDetailModal(false); setSelectedTransferId(null); }} 
        transferId={selectedTransferId} 
        user={user} 
        onStatusChange={fetchTransfers} 
      />
    </>
  );
}
