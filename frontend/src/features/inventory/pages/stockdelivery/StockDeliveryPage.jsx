import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Ban,
  CheckCircle2,
  Eye,
  MoreHorizontal,
  Package,
  Pencil,
  RotateCcw,
  ShoppingCart,
  Trash2,
  Wrench,
} from 'lucide-react';
import { toast } from 'react-toastify';
import stockDeliveryService from '../../services/stockDeliveryService';
import warehouseService from '../../services/warehouseService';
import useAuth from '../../../auth/hooks/useAuth';
import { ROLES } from '../../../auth/constants/roles';
import { ROUTES } from '../../../../app/router/routes';
import useConfirmDialog from '../../hooks/useConfirmDialog';
import InventoryDocumentListPage, {
  ActionMenuItem,
  ActionMenuShell,
  Badge,
  FilterSelect,
  SearchInput,
} from '../components/InventoryDocumentListPage';
import {
  formatDateTime,
  formatNumber,
  formatVND,
  getResponseData,
  tableCellStyle,
} from '../components/inventoryDocumentListUtils';
import InventoryExportModal from '../components/InventoryExportModal';
import { formatExportDateTime, getStatusLabel } from '../components/inventoryExcelExport';
import MarketplaceSyncButton from '../components/MarketplaceSyncButton';

const ISSUE_TYPES = {
  ORDER: {
    label: 'Xuất bán hàng',
    shortLabel: 'Bán hàng',
    icon: ShoppingCart,
    color: '#2563eb',
    bg: '#eff6ff',
    border: '#bfdbfe',
  },
  DISPOSAL: {
    label: 'Xuất hủy',
    shortLabel: 'Xuất hủy',
    icon: Trash2,
    color: '#ef233c',
    bg: '#fff1f2',
    border: '#fecdd3',
  },
  TRANSFER: {
    label: 'Trả hàng NCC',
    shortLabel: 'Trả NCC',
    icon: RotateCcw,
    color: '#f97316',
    bg: '#fff7ed',
    border: '#fed7aa',
  },
  ADJUSTMENT: {
    label: 'Xuất dùng',
    shortLabel: 'Xuất dùng',
    icon: Wrench,
    color: '#a855f7',
    bg: '#faf5ff',
    border: '#e9d5ff',
  },
};

const STATUS_CONFIG = {
  DRAFT: { label: 'Lưu tạm', color: '#d97706', bg: '#fffbeb', border: '#fcd34d' },
  CONFIRMED: { label: 'Hoàn thành', color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
  CANCELLED: { label: 'Đã hủy', color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const columns = [
  { label: 'Mã phiếu' },
  { label: 'Kho' },
  { label: 'Loại xuất' },
  { label: 'Trạng thái' },
  { label: 'Người / Đơn nhận' },
  { label: 'SL SKU', align: 'right' },
  { label: 'Tổng SL', align: 'right' },
  { label: 'Giá trị', align: 'right' },
  { label: 'Ghi chú' },
  { label: 'Người tạo' },
  { label: 'Ngày tạo' },
  { label: '', align: 'right' },
];

const DELIVERY_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'issueCode', label: 'Mã phiếu', width: 16, defaultChecked: true, getValue: (delivery) => delivery.issueCode ?? '' },
  { key: 'warehouseName', label: 'Kho', width: 24, defaultChecked: true, getValue: (delivery) => delivery.warehouseName ?? '' },
  { key: 'issueType', label: 'Loại xuất', width: 18, defaultChecked: true, getValue: (delivery) => getStatusLabel(delivery.issueType ?? delivery.deliveryType) },
  { key: 'status', label: 'Trạng thái', width: 16, defaultChecked: true, getValue: (delivery) => getStatusLabel(delivery.status) },
  { key: 'recipient', label: 'Người / Đơn nhận', width: 24, defaultChecked: true, getValue: (delivery) => delivery.recipient ?? '' },
  { key: 'totalSkuCount', label: 'SL SKU', width: 10, type: 'number', defaultChecked: true, getValue: (delivery) => delivery.totalSkuCount ?? delivery.items?.length ?? 0 },
  { key: 'totalQuantity', label: 'Tổng SL', width: 12, type: 'number', defaultChecked: true, getValue: (delivery) => delivery.totalQuantity ?? 0 },
  { key: 'totalCost', label: 'Giá trị', width: 16, type: 'currency', defaultChecked: true, getValue: (delivery) => delivery.totalCost ?? 0 },
  { key: 'note', label: 'Ghi chú', width: 30, defaultChecked: true, getValue: (delivery) => delivery.note ?? '' },
  { key: 'createdByName', label: 'Người tạo', width: 20, defaultChecked: false, getValue: (delivery) => delivery.createdByName ?? '' },
  { key: 'createdAt', label: 'Ngày tạo', width: 20, defaultChecked: false, getValue: (delivery) => formatExportDateTime(delivery.createdAt) },
];

const getDeliveryExportDate = (delivery) => delivery.issuedAt ?? delivery.confirmedAt ?? delivery.createdAt;

const DELIVERY_DETAIL_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'issueCode', label: 'Mã phiếu', width: 16, defaultChecked: true, getValue: (row) => row.issueCode },
  { key: 'issuedAt', label: 'Ngày xuất', width: 20, defaultChecked: true, getValue: (row) => formatExportDateTime(row.issuedAt) },
  { key: 'warehouseName', label: 'Kho', width: 24, defaultChecked: true, getValue: (row) => row.warehouseName },
  { key: 'issueType', label: 'Loại xuất', width: 18, defaultChecked: true, getValue: (row) => getStatusLabel(row.issueType) },
  { key: 'recipient', label: 'Người / Đơn nhận', width: 24, defaultChecked: true, getValue: (row) => row.recipient },
  { key: 'sku', label: 'SKU', width: 18, defaultChecked: true, getValue: (row) => row.sku },
  { key: 'productName', label: 'Tên sản phẩm', width: 32, defaultChecked: true, getValue: (row) => row.productName },
  { key: 'variantName', label: 'Biến thể', width: 22, defaultChecked: true, getValue: (row) => row.variantName },
  { key: 'quantity', label: 'Số lượng', width: 12, type: 'number', defaultChecked: true, getValue: (row) => row.quantity },
  { key: 'unitCost', label: 'Đơn giá', width: 16, type: 'currency', defaultChecked: true, getValue: (row) => row.unitCost },
  { key: 'lineTotal', label: 'Thành tiền', width: 16, type: 'currency', defaultChecked: true, getValue: (row) => row.lineTotal },
  { key: 'status', label: 'Trạng thái', width: 16, defaultChecked: false, getValue: (row) => getStatusLabel(row.status) },
  { key: 'note', label: 'Ghi chú', width: 30, defaultChecked: false, getValue: (row) => row.note },
];

const buildDeliveryDetailRows = async (deliveries) => {
  const details = await Promise.all(deliveries.map(async (delivery) => {
    if (!delivery.id) return delivery;
    const response = await stockDeliveryService.getStockDeliveryById(delivery.id);
    return getResponseData(response);
  }));

  return details.flatMap((delivery) => (delivery.items ?? []).map((item) => {
    const quantity = Number(item.quantity ?? 0);
    const unitCost = Number(item.unitCost ?? item.unitPrice ?? 0);
    return {
      issueCode: delivery.issueCode ?? '',
      issuedAt: getDeliveryExportDate(delivery),
      warehouseName: delivery.warehouseName ?? '',
      issueType: delivery.issueType ?? delivery.deliveryType,
      recipient: delivery.recipient ?? '',
      status: delivery.status,
      note: delivery.note ?? '',
      sku: item.sku ?? item.variantSku ?? '',
      productName: item.productName ?? '',
      variantName: item.productVariantName ?? item.variantName ?? '',
      quantity,
      unitCost,
      lineTotal: Number(item.totalCost ?? quantity * unitCost),
    };
  }));
};

const TypeBadge = ({ type }) => {
  const config = ISSUE_TYPES[type] ?? ISSUE_TYPES.ORDER;
  return <Badge {...config} label={config.shortLabel} />;
};

const StatusBadge = ({ status }) => {
  const config = STATUS_CONFIG[status] ?? { label: status ?? '-', color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
  return <Badge {...config} />;
};

const ActionMenu = ({ delivery, canComplete, isOwner, completingId, cancellingId, onDetail, onEdit, onComplete, onCancel }) => {
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [open]);

  return (
    <ActionMenuShell
      menuRef={menuRef}
      open={open}
      buttonIcon={MoreHorizontal}
      onToggle={() => setOpen((current) => !current)}
    >
      <ActionMenuItem onClick={() => { setOpen(false); onDetail(delivery.id); }}>
        <Eye size={14} /> Xem chi tiết
      </ActionMenuItem>
      {delivery.status === 'DRAFT' && (
        <ActionMenuItem onClick={() => { setOpen(false); onEdit(delivery.id); }}>
          <Pencil size={14} /> Chỉnh sửa
        </ActionMenuItem>
      )}
      {canComplete && delivery.status === 'DRAFT' && (
        <ActionMenuItem
          color="#059669"
          disabled={completingId === delivery.id}
          onClick={() => { setOpen(false); onComplete(delivery); }}
        >
          <CheckCircle2 size={14} /> {completingId === delivery.id ? 'Đang hoàn thành...' : 'Hoàn thành xuất kho'}
        </ActionMenuItem>
      )}
      {isOwner && delivery.status !== 'CANCELLED' && (
        <ActionMenuItem
          color="#dc2626"
          disabled={cancellingId === delivery.id}
          onClick={() => { setOpen(false); onCancel(delivery); }}
        >
          <Ban size={14} /> {cancellingId === delivery.id ? 'Đang hủy...' : 'Hủy phiếu xuất'}
        </ActionMenuItem>
      )}
    </ActionMenuShell>
  );
};

export default function StockDeliveryPage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const { user } = useAuth();
  const [deliveries, setDeliveries] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [statistics, setStatistics] = useState({});
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [deliveryType, setDeliveryType] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [completingId, setCompletingId] = useState(null);
  const [cancellingId, setCancellingId] = useState(null);
  const [exportOpen, setExportOpen] = useState(false);
  const isOwner = user?.role === ROLES.OWNER;
  const canComplete = user?.role === ROLES.OWNER || user?.role === ROLES.OPERATIONS;

  const fetchWarehouses = async () => {
    try {
      const response = await warehouseService.getAll();
      const data = getResponseData(response);
      setWarehouses(Array.isArray(data) ? data : data.content ?? []);
    } catch (error) {
      console.error('Error fetching warehouses:', error);
      setWarehouses([]);
    }
  };

  const fetchStatistics = async () => {
    try {
      const response = await stockDeliveryService.getDeliveryStatistics();
      setStatistics(getResponseData(response));
    } catch (error) {
      console.error('Error fetching delivery statistics:', error);
      setStatistics({});
    }
  };

  const fetchDeliveries = async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size: rowsPerPage,
        sortBy: 'createdAt',
        sortDirection: 'DESC',
      };
      if (keyword.trim()) params.keyword = keyword.trim();
      if (deliveryType) params.deliveryType = deliveryType;
      if (warehouseId) params.warehouseId = warehouseId;

      const response = await stockDeliveryService.getAllStockDeliveries(params);
      const data = getResponseData(response);
      const content = data.content ?? [];
      setDeliveries(Array.isArray(content) ? content : []);
      setTotalElements(data.totalElements ?? content.length ?? 0);
    } catch (error) {
      console.error('Error fetching deliveries:', error);
      toast.error(error?.message || 'Không thể tải danh sách phiếu xuất kho');
      setDeliveries([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchWarehouses();
    fetchStatistics();
  }, []);

  useEffect(() => {
    const timer = setTimeout(fetchDeliveries, keyword.trim() ? 250 : 0);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, keyword, deliveryType, warehouseId]);

  const typeCounts = useMemo(() => ({
    ORDER: statistics.orderCount ?? 0,
    DISPOSAL: statistics.disposalCount ?? 0,
    TRANSFER: statistics.transferCount ?? 0,
    ADJUSTMENT: statistics.adjustmentCount ?? 0,
  }), [statistics]);

  const handleTypeFilter = (type) => {
    setDeliveryType((currentType) => (currentType === type ? '' : type));
    setPage(0);
  };

  const loadDeliveryExportRows = async () => {
    const params = {
      page: 0,
      size: Math.max(totalElements || deliveries.length || 1, deliveries.length || 1),
      sortBy: 'createdAt',
      sortDirection: 'DESC',
    };
    if (keyword.trim()) params.keyword = keyword.trim();
    if (deliveryType) params.deliveryType = deliveryType;
    if (warehouseId) params.warehouseId = warehouseId;
    const response = await stockDeliveryService.getAllStockDeliveries(params);
    const data = getResponseData(response);
    return Array.isArray(data.content) ? data.content : [];
  };

  const goToDetail = (deliveryId) => {
    navigate(ROUTES.STOCK_DELIVERY_DETAIL.replace(':id', deliveryId));
  };

  const goToEdit = (deliveryId) => {
    if (!deliveryId) {
      toast.error('KhÃ´ng thá»ƒ má»Ÿ mÃ n chá»‰nh sá»­a vÃ¬ thiáº¿u ID phiáº¿u xuáº¥t.');
      return;
    }
    navigate(ROUTES.STOCK_DELIVERY_EDIT.replace(':id', deliveryId));
  };

  const handleCancelDelivery = async (delivery) => {
    if (!isOwner || !delivery?.id || delivery.status === 'CANCELLED') return;
    const ok = await confirm({
      title: 'Hủy phiếu xuất?',
      message: `Phiếu "${delivery.issueCode}" sẽ bị hủy và tồn kho của các sản phẩm trong phiếu sẽ được khôi phục.`,
      confirmText: 'Hủy phiếu',
      tone: 'danger',
    });
    if (!ok) {
      return;
    }

    setCancellingId(delivery.id);
    try {
      await stockDeliveryService.cancelStockDelivery(delivery.id);
      toast.success('Hủy phiếu xuất thành công. Tồn kho đã được khôi phục.');
      await Promise.all([fetchDeliveries(), fetchStatistics()]);
    } catch (error) {
      toast.error(error?.message || 'Không thể hủy phiếu xuất. Vui lòng thử lại.');
    } finally {
      setCancellingId(null);
    }
  };

  const handleCompleteDelivery = async (delivery) => {
    if (!canComplete || !delivery?.id || delivery.status !== 'DRAFT') return;
    const ok = await confirm({
      title: 'Hoàn thành phiếu xuất?',
      message: `Xác nhận hoàn thành phiếu "${delivery.issueCode}". Sau khi hoàn thành sẽ không thể chuyển lại trạng thái Lưu tạm.`,
      confirmText: 'Hoàn thành',
    });
    if (!ok) {
      return;
    }

    setCompletingId(delivery.id);
    try {
      await stockDeliveryService.confirmStockDelivery(delivery.id);
      toast.success('Hoàn thành phiếu xuất thành công.');
      await Promise.all([fetchDeliveries(), fetchStatistics()]);
    } catch (error) {
      toast.error(error?.message || 'Không thể hoàn thành phiếu xuất. Vui lòng thử lại.');
    } finally {
      setCompletingId(null);
    }
  };

  const stats = Object.entries(ISSUE_TYPES).map(([type, config]) => ({
    key: type,
    label: config.label,
    value: typeCounts[type],
    icon: config.icon,
    color: config.color,
    bg: config.bg,
    border: config.border,
    active: deliveryType === type,
    onClick: () => handleTypeFilter(type),
  }));

  const rows = deliveries.map((delivery) => (
    <tr key={delivery.id}>
      <td style={{ ...tableCellStyle, color: '#ff3d00', fontFamily: 'monospace', fontWeight: 700 }}>{delivery.issueCode ?? '-'}</td>
      <td style={tableCellStyle}>{delivery.warehouseName ?? '-'}</td>
      <td style={tableCellStyle}><TypeBadge type={delivery.issueType ?? delivery.deliveryType} /></td>
      <td style={tableCellStyle}><StatusBadge status={delivery.status} /></td>
      <td style={{ ...tableCellStyle, color: '#020617' }}>{delivery.recipient ?? '-'}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right' }}>{formatNumber(delivery.totalSkuCount ?? delivery.items?.length ?? 0)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right', color: '#020617', fontWeight: 700 }}>{formatNumber(delivery.totalQuantity)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right', color: '#020617', fontWeight: 700 }}>{formatVND(delivery.totalCost)}</td>
      <td style={{ ...tableCellStyle, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }}>{delivery.note ?? '-'}</td>
      <td style={tableCellStyle}>{delivery.createdByName ?? '-'}</td>
      <td style={tableCellStyle}>{formatDateTime(delivery.createdAt)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right' }}>
        <ActionMenu
          delivery={delivery}
          canComplete={canComplete}
          isOwner={isOwner}
          completingId={completingId}
          cancellingId={cancellingId}
          onDetail={goToDetail}
          onEdit={goToEdit}
          onComplete={handleCompleteDelivery}
          onCancel={handleCancelDelivery}
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
      icon={Package}
      iconBg="#fff3e6"
      iconColor="#ff4d00"
      title="Danh sách phiếu xuất kho"
      description="Quản lý tất cả phiếu xuất hàng theo loại"
      createLabel="Tạo phiếu xuất"
      onCreate={() => navigate(ROUTES.STOCK_DELIVERY_CREATE)}
      onExport={() => setExportOpen(true)}
      extraActions={<MarketplaceSyncButton allowedDirections={['from-app']} onSynced={() => Promise.all([fetchDeliveries(), fetchStatistics()])} />}
      stats={stats}
      filters={(
        <>
          <SearchInput
            value={keyword}
            onChange={(event) => { setKeyword(event.target.value); setPage(0); }}
            placeholder="Tìm theo mã phiếu, người nhận, ghi chú..."
          />
          <FilterSelect value={deliveryType} onChange={(event) => { setDeliveryType(event.target.value); setPage(0); }}>
            <option value="">Tất cả loại xuất</option>
            {Object.entries(ISSUE_TYPES).map(([type, config]) => (
              <option key={type} value={type}>{config.label}</option>
            ))}
          </FilterSelect>
          <FilterSelect value={warehouseId} onChange={(event) => { setWarehouseId(event.target.value); setPage(0); }} minWidth={210}>
            <option value="">Tất cả kho</option>
            {warehouses.map((warehouse) => (
              <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>
            ))}
          </FilterSelect>
        </>
      )}
      columns={columns}
      rows={rows}
      loading={loading}
      emptyText="Không có phiếu xuất kho phù hợp"
      footerLeft={`Tổng xuất: ${formatNumber(statistics.totalCount ?? totalElements)} phiếu`}
      footerRight={Object.entries(ISSUE_TYPES).map(([type, config]) => (
        <span key={type} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: config.color }} />
          {config.shortLabel}: <strong style={{ color: '#020617' }}>{formatNumber(typeCounts[type])}</strong>
        </span>
      ))}
      pagination={{
        page,
        totalPages,
        totalElements,
        label: `Hiển thị ${formatNumber(firstVisible)} - ${formatNumber(lastVisible)} / ${formatNumber(totalElements)} phiếu`,
        onPrevious: () => setPage((currentPage) => Math.max(0, currentPage - 1)),
        onNext: () => setPage((currentPage) => Math.min(totalPages - 1, currentPage + 1)),
      }}
    />
    <InventoryExportModal
      open={exportOpen}
      onClose={() => setExportOpen(false)}
      rows={deliveries}
      columns={DELIVERY_EXPORT_COLUMNS}
      detailColumns={DELIVERY_DETAIL_EXPORT_COLUMNS}
      buildDetailRows={buildDeliveryDetailRows}
      getDateValue={getDeliveryExportDate}
      loadRows={loadDeliveryExportRows}
      title="BẢNG KÊ PHIẾU XUẤT KHO"
      fileName="danh-sach-phieu-xuat-kho"
      sheetName="phiếu xuất kho"
    />
    {ConfirmDialog}
    </>
  );
}
