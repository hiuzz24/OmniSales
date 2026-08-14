import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Eye,
  MoreHorizontal,
  PlayCircle,
  RefreshCw,
  TrendingDown,
  TrendingUp,
  XCircle,
} from 'lucide-react';
import { toast } from 'react-toastify';
import { ROUTES } from '../../../../app/router/routes';
import stocktakeService from '../../services/stocktakeService';
import warehouseService from '../../services/warehouseService';
import InventoryDocumentListPage, {
  ActionMenuItem,
  ActionMenuShell,
  Badge,
  FilterSelect,
  SearchInput,
} from '../components/InventoryDocumentListPage';
import {
  formatDate,
  formatDateTime,
  formatNumber,
  getResponseData,
  tableCellStyle,
} from '../components/inventoryDocumentListUtils';
import InventoryExportModal from '../components/InventoryExportModal';
import { formatExportDateTime, getStatusLabel } from '../components/inventoryExcelExport';

const STATUS_CONFIG = {
  DRAFT: { label: 'Nháp', icon: ClipboardList, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' },
  IN_PROGRESS: { label: 'Đang kiểm', icon: Clock3, color: '#2563eb', bg: '#eff6ff', border: '#bfdbfe' },
  COMPLETED: { label: 'Hoàn thành', icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
  CANCELLED: { label: 'Đã hủy', icon: XCircle, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const columns = [
  { label: 'Mã phiếu' },
  { label: 'Kho kiểm' },
  { label: 'Tổng SKU', align: 'right' },
  { label: 'Đã kiểm', align: 'right' },
  { label: 'Thừa (SP)', align: 'right' },
  { label: 'Thiếu (SP)', align: 'right' },
  { label: 'Kết quả' },
  { label: 'Trạng thái' },
  { label: 'Người tạo' },
  { label: 'Ngày tạo' },
  { label: '', align: 'right' },
];

const getStocktakeSummary = (stocktake) => {
  const items = stocktake.items ?? [];
  const checkedCount = items.filter((item) => item.actualQuantity !== null && item.actualQuantity !== undefined).length;
  const surplus = items.reduce((sum, item) => sum + Math.max(Number(item.difference ?? item.actualQuantity - item.systemQuantity) || 0, 0), 0);
  const shortage = items.reduce((sum, item) => sum + Math.abs(Math.min(Number(item.difference ?? item.actualQuantity - item.systemQuantity) || 0, 0)), 0);
  let result = 'Khớp';
  if (surplus && shortage) result = 'Thừa & Thiếu';
  else if (surplus) result = 'Thừa hàng';
  else if (shortage) result = 'Thiếu hàng';
  return { items, checkedCount, surplus, shortage, result };
};

const STOCKTAKE_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'sessionCode', label: 'Mã phiếu', width: 16, defaultChecked: true, getValue: (stocktake) => stocktake.sessionCode ?? '' },
  { key: 'warehouseName', label: 'Kho kiểm', width: 24, defaultChecked: true, getValue: (stocktake) => stocktake.warehouseName ?? '' },
  { key: 'totalSku', label: 'Tổng SKU', width: 12, type: 'number', defaultChecked: true, getValue: (stocktake) => getStocktakeSummary(stocktake).items.length },
  { key: 'checkedCount', label: 'Đã kiểm', width: 12, type: 'number', defaultChecked: true, getValue: (stocktake) => getStocktakeSummary(stocktake).checkedCount },
  { key: 'surplus', label: 'Thừa (SP)', width: 12, type: 'number', defaultChecked: true, getValue: (stocktake) => getStocktakeSummary(stocktake).surplus },
  { key: 'shortage', label: 'Thiếu (SP)', width: 12, type: 'number', defaultChecked: true, getValue: (stocktake) => getStocktakeSummary(stocktake).shortage },
  { key: 'result', label: 'Kết quả', width: 16, defaultChecked: true, getValue: (stocktake) => getStocktakeSummary(stocktake).result },
  { key: 'status', label: 'Trạng thái', width: 16, defaultChecked: true, getValue: (stocktake) => getStatusLabel(stocktake.status) },
  { key: 'createdByName', label: 'Người tạo', width: 20, defaultChecked: false, getValue: (stocktake) => stocktake.createdByName ?? '' },
  { key: 'createdAt', label: 'Ngày tạo', width: 20, defaultChecked: false, getValue: (stocktake) => formatExportDateTime(stocktake.createdAt ?? stocktake.scheduledDate) },
];

const getStocktakeExportDate = (stocktake) => stocktake.completedAt ?? stocktake.createdAt ?? stocktake.scheduledDate;

const STOCKTAKE_DETAIL_EXPORT_COLUMNS = [
  { key: 'stt', label: 'STT', width: 6, defaultChecked: true },
  { key: 'sessionCode', label: 'Mã phiếu', width: 16, defaultChecked: true, getValue: (row) => row.sessionCode },
  { key: 'stocktakeDate', label: 'Ngày kiểm', width: 20, defaultChecked: true, getValue: (row) => formatExportDateTime(row.stocktakeDate) },
  { key: 'warehouseName', label: 'Kho kiểm', width: 24, defaultChecked: true, getValue: (row) => row.warehouseName },
  { key: 'sku', label: 'SKU', width: 18, defaultChecked: true, getValue: (row) => row.sku },
  { key: 'productName', label: 'Tên sản phẩm', width: 32, defaultChecked: true, getValue: (row) => row.productName },
  { key: 'variantName', label: 'Biến thể', width: 22, defaultChecked: true, getValue: (row) => row.variantName },
  { key: 'systemQuantity', label: 'Tồn hệ thống', width: 14, type: 'number', defaultChecked: true, getValue: (row) => row.systemQuantity },
  { key: 'actualQuantity', label: 'Tồn thực tế', width: 14, type: 'number', defaultChecked: true, getValue: (row) => row.actualQuantity },
  { key: 'difference', label: 'Chênh lệch', width: 14, type: 'number', defaultChecked: true, getValue: (row) => row.difference },
  { key: 'unitCost', label: 'Giá vốn', width: 16, type: 'currency', defaultChecked: true, getValue: (row) => row.unitCost },
  { key: 'differenceValue', label: 'Giá trị chênh lệch', width: 18, type: 'currency', defaultChecked: true, getValue: (row) => row.differenceValue },
  { key: 'status', label: 'Trạng thái', width: 16, defaultChecked: false, getValue: (row) => getStatusLabel(row.status) },
  { key: 'createdByName', label: 'Người tạo', width: 20, defaultChecked: false, getValue: (row) => row.createdByName },
];

const buildStocktakeDetailRows = async (stocktakes) => {
  const details = await Promise.all(stocktakes.map(async (stocktake) => {
    if (!stocktake.id) return stocktake;
    const response = await stocktakeService.getById(stocktake.id);
    return getResponseData(response);
  }));

  return details.flatMap((stocktake) => (stocktake.items ?? []).map((item) => {
    const systemQuantity = Number(item.systemQuantity ?? 0);
    const actualQuantity = item.actualQuantity === null || item.actualQuantity === undefined ? '' : Number(item.actualQuantity);
    const difference = item.difference ?? (actualQuantity === '' ? 0 : actualQuantity - systemQuantity);
    const unitCost = Number(item.unitCost ?? item.costPrice ?? item.unitPrice ?? 0);
    return {
      sessionCode: stocktake.sessionCode ?? '',
      stocktakeDate: getStocktakeExportDate(stocktake),
      warehouseName: stocktake.warehouseName ?? '',
      status: stocktake.status,
      createdByName: stocktake.createdByName ?? '',
      sku: item.variantSku ?? item.sku ?? '',
      productName: item.productName ?? '',
      variantName: item.variantName ?? item.productVariantName ?? '',
      systemQuantity,
      actualQuantity,
      difference,
      unitCost,
      differenceValue: Number(difference || 0) * unitCost,
    };
  }));
};

const StatusBadge = ({ status }) => {
  const config = STATUS_CONFIG[status] ?? STATUS_CONFIG.DRAFT;
  return <Badge {...config} />;
};

const DeltaCell = ({ value, type }) => {
  if (!value) return <span style={{ color: '#cbd5e1' }}>-</span>;
  const Icon = type === 'up' ? TrendingUp : TrendingDown;
  const color = type === 'up' ? '#2563eb' : '#dc2626';
  const prefix = type === 'up' ? '+' : '-';
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'flex-end', gap: 4, color, fontWeight: 800 }}>
      <Icon size={14} />
      {prefix} {formatNumber(value)}
    </span>
  );
};

const ResultCell = ({ surplus, shortage }) => {
  if (surplus && shortage) {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#f97316', fontWeight: 800 }}>
        <AlertTriangle size={14} /> Thừa & Thiếu
      </span>
    );
  }
  if (surplus) {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#2563eb', fontWeight: 800 }}>
        <TrendingUp size={14} /> Thừa hàng
      </span>
    );
  }
  if (shortage) {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#dc2626', fontWeight: 800 }}>
        <TrendingDown size={14} /> Thiếu hàng
      </span>
    );
  }
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#059669', fontWeight: 800 }}>
      <CheckCircle2 size={14} /> Khớp
    </span>
  );
};

const ActionMenu = ({ stocktake, onStatus, onView, onCheck }) => {
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

  const isClosed = stocktake.status === 'COMPLETED' || stocktake.status === 'CANCELLED';

  return (
    <ActionMenuShell menuRef={menuRef} open={open} buttonIcon={MoreHorizontal} onToggle={() => setOpen((current) => !current)}>
      <ActionMenuItem onClick={() => { setOpen(false); onView(stocktake); }}>
        <Eye size={14} /> Xem chi tiết
      </ActionMenuItem>
      {stocktake.status === 'IN_PROGRESS' && (
        <ActionMenuItem color="#2563eb" onClick={() => { setOpen(false); onCheck(stocktake); }}>
          <PlayCircle size={14} /> Kiểm tra
        </ActionMenuItem>
      )}
      {!isClosed && stocktake.status !== 'IN_PROGRESS' && (
        <ActionMenuItem color="#2563eb" onClick={() => { setOpen(false); onStatus(stocktake, 'IN_PROGRESS'); }}>
          <PlayCircle size={14} /> Bắt đầu kiểm
        </ActionMenuItem>
      )}
      {!isClosed && (
        <ActionMenuItem color="#059669" onClick={() => { setOpen(false); onStatus(stocktake, 'COMPLETED'); }}>
          <CheckCircle2 size={14} /> Hoàn thành kiểm kho
        </ActionMenuItem>
      )}
      {!isClosed && (
        <ActionMenuItem color="#dc2626" onClick={() => { setOpen(false); onStatus(stocktake, 'CANCELLED'); }}>
          <XCircle size={14} /> Hủy phiếu kiểm
        </ActionMenuItem>
      )}
    </ActionMenuShell>
  );
};

export default function StocktakePage() {
  const navigate = useNavigate();
  const [stocktakes, setStocktakes] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [statistics, setStatistics] = useState({});
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [warehouseId, setWarehouseId] = useState('');
  const [page, setPage] = useState(0);
  const [rowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [exportOpen, setExportOpen] = useState(false);
  const [syncingMarketplace, setSyncingMarketplace] = useState(false);

  const handleSyncMarketplaceInventory = async () => {
    if (syncingMarketplace) return;
    setSyncingMarketplace(true);
    try {
      const response = await stocktakeService.syncPendingMarketplaceInventory();
      const data = getResponseData(response);
      const count = Number(data.syncedVariantCount ?? 0);
      toast.success(count > 0
        ? `Đã đồng bộ tồn kho ${formatNumber(count)} SKU từ phiếu kiểm lên các sàn liên kết.`
        : 'Không có SKU phiếu kiểm nào cần đồng bộ.');
      await refresh();
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể đồng bộ tồn kho phiếu kiểm lên sàn.');
    } finally {
      setSyncingMarketplace(false);
    }
  };

  const fetchStocktakes = async () => {
    setLoading(true);
    try {
      const params = { page, size: rowsPerPage, sortBy: 'createdAt', sortDirection: 'DESC' };
      if (keyword.trim()) params.keyword = keyword.trim();
      if (status) params.status = status;
      if (warehouseId) params.warehouseId = warehouseId;
      const response = await stocktakeService.getStocktakes(params);
      const data = getResponseData(response);
      const content = data.content ?? [];
      setStocktakes(Array.isArray(content) ? content : []);
      setTotalElements(data.totalElements ?? content.length ?? 0);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể tải danh sách phiếu kiểm kho.');
      setStocktakes([]);
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
    stocktakeService.getStatistics()
      .then((response) => setStatistics(getResponseData(response)))
      .catch(() => setStatistics({}));
  }, []);

  useEffect(() => {
    const timer = setTimeout(fetchStocktakes, keyword.trim() ? 250 : 0);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, keyword, status, warehouseId]);

  const refresh = async () => {
    await Promise.all([
      fetchStocktakes(),
      stocktakeService.getStatistics()
        .then((response) => setStatistics(getResponseData(response)))
        .catch(() => setStatistics({})),
    ]);
  };

  const loadStocktakeExportRows = async () => {
    const params = {
      page: 0,
      size: Math.max(totalElements || stocktakes.length || 1, stocktakes.length || 1),
      sortBy: 'createdAt',
      sortDirection: 'DESC',
    };
    if (keyword.trim()) params.keyword = keyword.trim();
    if (status) params.status = status;
    if (warehouseId) params.warehouseId = warehouseId;
    const response = await stocktakeService.getStocktakes(params);
    const data = getResponseData(response);
    return Array.isArray(data.content) ? data.content : [];
  };

  const handleView = (stocktake) => {
    navigate(ROUTES.STOCKTAKE_DETAIL.replace(':id', stocktake.id));
  };

  const handleCheck = (stocktake) => {
    navigate(ROUTES.STOCKTAKE_CHECK.replace(':id', stocktake.id));
  };

  const handleStatus = async (stocktake, nextStatus) => {
    if (nextStatus === 'COMPLETED') {
      const hasMissingActual = (stocktake.items ?? []).some((item) => item.actualQuantity === null || item.actualQuantity === undefined || item.actualQuantity < 0);
      if (hasMissingActual || !(stocktake.items ?? []).length) {
        toast.error('Cần nhập đủ số lượng tồn kho thực tế trước khi hoàn thành.');
        return;
      }
    }
    try {
      await stocktakeService.changeStatus(stocktake.id, nextStatus);
      toast.success('Cập nhật trạng thái phiếu kiểm thành công.');
      if (nextStatus === 'IN_PROGRESS') {
        navigate(ROUTES.STOCKTAKE_CHECK.replace(':id', stocktake.id));
        return;
      }
      refresh();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Không thể cập nhật trạng thái phiếu kiểm.');
    }
  };

  const stats = useMemo(() => ([
    { key: 'total', label: 'Tổng phiếu', value: statistics.totalCount ?? totalElements, icon: ClipboardList, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' },
    { key: 'completed', label: 'Hoàn thành', value: statistics.completedCount ?? 0, icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
    { key: 'progress', label: 'Đang kiểm', value: statistics.inProgressCount ?? 0, icon: Clock3, color: '#2563eb', bg: '#eff6ff', border: '#bfdbfe' },
    { key: 'diff', label: 'Có sai lệch', value: stocktakes.filter((item) => (item.items ?? []).some((line) => line.difference !== 0)).length, icon: AlertTriangle, color: '#f97316', bg: '#fff7ed', border: '#fed7aa' },
  ]), [statistics, stocktakes, totalElements]);

  const rows = stocktakes.map((stocktake) => {
    const items = stocktake.items ?? [];
    const checkedCount = items.filter((item) => item.actualQuantity !== null && item.actualQuantity !== undefined).length;
    const surplus = items.reduce((sum, item) => sum + Math.max(Number(item.difference ?? item.actualQuantity - item.systemQuantity) || 0, 0), 0);
    const shortage = items.reduce((sum, item) => sum + Math.abs(Math.min(Number(item.difference ?? item.actualQuantity - item.systemQuantity) || 0, 0)), 0);
    const needsSync = stocktake.status === 'COMPLETED' && stocktake.marketplaceSyncAvailable === true;
    const rowStyle = needsSync ? { backgroundColor: '#fffbeb' } : {};
    return (
      <tr key={stocktake.id} style={rowStyle}>
        <td style={{ ...tableCellStyle, color: '#009688', fontFamily: 'monospace', fontWeight: 700 }}>
          {stocktake.sessionCode}
          {needsSync && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 3 }}>
              <AlertTriangle size={11} color="#d97706" />
              <span style={{ fontSize: 10.5, color: '#d97706', fontWeight: 600, fontFamily: 'inherit' }}>
                Chưa đồng bộ lên sàn
              </span>
            </div>
          )}
        </td>
        <td style={tableCellStyle}>{stocktake.warehouseName ?? '-'}</td>
        <td style={{ ...tableCellStyle, textAlign: 'right' }}>{formatNumber(items.length)}</td>
        <td style={{ ...tableCellStyle, textAlign: 'right', fontWeight: 700 }}>{formatNumber(checkedCount)}</td>
        <td style={{ ...tableCellStyle, textAlign: 'right' }}><DeltaCell value={surplus} type="up" /></td>
        <td style={{ ...tableCellStyle, textAlign: 'right' }}><DeltaCell value={shortage} type="down" /></td>
        <td style={tableCellStyle}><ResultCell surplus={surplus} shortage={shortage} /></td>
        <td style={tableCellStyle}><StatusBadge status={stocktake.status} /></td>
        <td style={tableCellStyle}>{stocktake.createdByName ?? '-'}</td>
        <td style={tableCellStyle}>{formatDateTime(stocktake.createdAt) || formatDate(stocktake.scheduledDate)}</td>
        <td style={{ ...tableCellStyle, textAlign: 'right' }}>
          <ActionMenu stocktake={stocktake} onStatus={handleStatus} onView={handleView} onCheck={handleCheck} />
        </td>
      </tr>
    );
  });

  const totalPages = Math.max(1, Math.ceil(totalElements / rowsPerPage));
  const firstVisible = totalElements === 0 ? 0 : page * rowsPerPage + 1;
  const lastVisible = Math.min(totalElements, (page + 1) * rowsPerPage);

  return (
    <>
    <InventoryDocumentListPage
      icon={ClipboardList}
      iconBg="#ecfeff"
      iconColor="#009688"
      title="Danh sách phiếu kiểm kho"
      description="Quản lý các phiếu kiểm kê hàng hóa định kỳ và đột xuất"
      createLabel="Tạo phiếu kiểm"
      onCreate={() => navigate(ROUTES.STOCKTAKE_CREATE)}
      onExport={() => setExportOpen(true)}
      extraActions={(
        <button
          type="button"
          onClick={handleSyncMarketplaceInventory}
          disabled={syncingMarketplace}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            height: 40,
            padding: '0 14px',
            border: '1px solid #99f6e4',
            borderRadius: 12,
            background: syncingMarketplace ? '#f0fdfa' : '#0d9488',
            color: syncingMarketplace ? '#0f766e' : '#ffffff',
            fontSize: 13,
            fontWeight: 700,
            cursor: syncingMarketplace ? 'not-allowed' : 'pointer',
            boxShadow: syncingMarketplace ? 'none' : '0 8px 18px rgba(13, 148, 136, 0.22)',
          }}
        >
          <RefreshCw size={15} style={{ animation: syncingMarketplace ? 'spin 1s linear infinite' : undefined }} />
          {syncingMarketplace ? 'Đang đồng bộ...' : 'Đồng bộ tồn kho'}
        </button>
      )}
      stats={stats}
      filters={(
        <>
          <SearchInput value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(0); }} placeholder="Tìm theo mã phiếu, kho, người tạo..." />
          <FilterSelect value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">Tất cả trạng thái</option>
            <option value="DRAFT">Nháp</option>
            <option value="IN_PROGRESS">Đang kiểm</option>
            <option value="COMPLETED">Hoàn thành</option>
            <option value="CANCELLED">Đã hủy</option>
          </FilterSelect>
          <FilterSelect value={warehouseId} onChange={(event) => { setWarehouseId(event.target.value); setPage(0); }} minWidth={210}>
            <option value="">Tất cả kho</option>
            {warehouses.map((warehouse) => <option key={warehouse.id} value={warehouse.id}>{warehouse.name}</option>)}
          </FilterSelect>
        </>
      )}
      columns={columns}
      rows={rows}
      loading={loading}
      emptyText="Không có phiếu kiểm kho phù hợp"
      pagination={{
        page,
        totalPages,
        totalElements,
        label: `Hiển thị ${formatNumber(firstVisible)} - ${formatNumber(lastVisible)} / ${formatNumber(totalElements)} phiếu`,
        onPrevious: () => setPage((current) => Math.max(0, current - 1)),
        onNext: () => setPage((current) => Math.min(totalPages - 1, current + 1)),
      }}
    />
    <InventoryExportModal
      open={exportOpen}
      onClose={() => setExportOpen(false)}
      rows={stocktakes}
      columns={STOCKTAKE_EXPORT_COLUMNS}
      detailColumns={STOCKTAKE_DETAIL_EXPORT_COLUMNS}
      buildDetailRows={buildStocktakeDetailRows}
      getDateValue={getStocktakeExportDate}
      loadRows={loadStocktakeExportRows}
      title="BẢNG KÊ PHIẾU KIỂM KHO"
      fileName="danh-sach-phieu-kiem-kho"
      sheetName="phiếu kiểm kho"
    />
    </>
  );
}
