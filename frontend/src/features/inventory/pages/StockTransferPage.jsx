import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
} from 'lucide-react';
import { toast } from 'react-toastify';
import transferApi from '../../../api/transferApi';
import warehouseService from '../services/warehouseService';
import { ROUTES } from '../../../app/router/routes';
import InventoryDocumentListPage, {
  ActionMenuItem,
  ActionMenuShell,
  FilterSelect,
  SearchInput,
} from './components/InventoryDocumentListPage';
import {
  formatDate,
  formatDateTime,
  formatNumber,
  getResponseData,
  tableCellStyle,
} from './components/inventoryDocumentListUtils';
import { exportTransfersToExcel } from '../utils/exportTransfers';

const StatusBadge = ({ status }) => {
  const normalizedStatus = status?.toUpperCase() || 'DRAFT';
  if (normalizedStatus === 'COMPLETED' || normalizedStatus === 'RECEIVED') {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-[#111827] px-2.5 py-1 text-[12.5px] font-bold text-white shadow-sm border border-[#111827]">
        <CheckCircle size={13} className="text-white" /> Hoàn thành
      </span>
    );
  }
  if (normalizedStatus === 'IN_TRANSIT') {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-[#f1f5f9] px-2.5 py-1 text-[12.5px] font-bold text-[#475569] border border-[#e2e8f0]">
        <Truck size={13} className="text-[#475569]" /> Đang vận chuyển
      </span>
    );
  }
  if (normalizedStatus === 'DRAFT') {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-2.5 py-1 text-[12.5px] font-bold text-[#475569] border border-[#cbd5e1]">
        <FileText size={13} className="text-amber-500" /> Nháp
      </span>
    );
  }
  if (normalizedStatus === 'CANCELLED') {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-[#ef4444] px-2.5 py-1 text-[12.5px] font-bold text-white shadow-sm border border-[#ef4444]">
        <XCircle size={13} className="text-white" /> Đã hủy
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-[12.5px] font-bold text-slate-700 border border-slate-200">
      <Info size={13} /> {status}
    </span>
  );
};

const ActionMenu = ({ transfer }) => {
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

  return (
    <ActionMenuShell menuRef={menuRef} open={open} buttonIcon={MoreHorizontal} onToggle={() => setOpen((c) => !c)}>
      <ActionMenuItem onClick={() => { setOpen(false); toast.info('Chi tiết phiếu chuyển đang được phát triển.'); }}>
        <Eye size={14} /> Xem chi tiết
      </ActionMenuItem>
      {transfer.status === 'DRAFT' && (
        <ActionMenuItem color="#2563eb" onClick={() => { setOpen(false); toast.info('Chức năng chuyển đang vận chuyển đang được phát triển.'); }}>
          <Truck size={14} /> Vận chuyển
        </ActionMenuItem>
      )}
      {(transfer.status === 'DRAFT' || transfer.status === 'IN_TRANSIT') && (
        <ActionMenuItem color="#dc2626" onClick={() => { setOpen(false); toast.info('Chức năng hủy phiếu đang được phát triển.'); }}>
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

export default function StockTransferPage() {
  const navigate = useNavigate();
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
      <td style={{ ...tableCellStyle, color: '#7c3aed', fontFamily: 'monospace', fontWeight: 700 }}>
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
        <ActionMenu transfer={transfer} />
      </td>
    </tr>
  ));

  const totalPages = Math.max(1, Math.ceil(totalElements / rowsPerPage));
  const firstVisible = totalElements === 0 ? 0 : page * rowsPerPage + 1;
  const lastVisible = Math.min(totalElements, (page + 1) * rowsPerPage);

  return (
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
  );
}
