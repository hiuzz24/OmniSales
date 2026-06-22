import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Check,
  CheckCircle2,
  Edit3,
  Eye,
  FileText,
  MoreHorizontal,
  PackagePlus,
  Printer,
  Save,
  Undo2,
} from 'lucide-react';
import { toast } from 'react-toastify';
import stockReceiveService from '../../services/stockReceiveService';
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
  formatDate,
  formatNumber,
  formatVND,
  tableCellStyle,
} from '../components/inventoryDocumentListUtils';

const STATUS_CONFIG = {
  CONFIRMED: { label: 'Hoàn thành', icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
  DRAFT: { label: 'Lưu tạm', icon: Save, color: '#d97706', bg: '#fffbeb', border: '#fcd34d' },
  CANCELLED: { label: 'Trả hàng', icon: Undo2, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const columns = [
  { label: 'Mã phiếu' },
  { label: 'Kho' },
  { label: 'Nhà cung cấp' },
  { label: 'Số HĐ' },
  { label: 'SL SKU', align: 'right' },
  { label: 'Tổng SL', align: 'right' },
  { label: 'Giá trị', align: 'right' },
  { label: 'Trạng thái' },
  { label: 'Người tạo' },
  { label: 'Ngày tạo' },
  { label: '', align: 'right' },
];

const StatusBadge = ({ status }) => {
  const config = STATUS_CONFIG[status] ?? { label: status ?? '-', icon: FileText, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' };
  return <Badge {...config} />;
};

const ActionMenu = ({ receipt, onComplete, onRefresh, confirm }) => {
  const navigate = useNavigate();
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

  const handleComplete = async () => {
    setOpen(false);
    const ok = await confirm({
      title: 'Hoàn thành phiếu nhập',
      message: `Xác nhận hoàn thành phiếu nhập "${receipt.receiptCode}"?\nTồn kho sẽ được cập nhật sau khi xác nhận.`,
      confirmLabel: 'Hoàn thành',
      cancelLabel: 'Hủy',
    });
    if (!ok) return;

    try {
      await onComplete(receipt.id);
      toast.success('Hoàn thành phiếu nhập thành công.');
      onRefresh();
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || 'Không thể hoàn thành phiếu nhập. Vui lòng thử lại.');
    }
  };

  return (
    <ActionMenuShell menuRef={menuRef} open={open} buttonIcon={MoreHorizontal} onToggle={() => setOpen((current) => !current)}>
      <ActionMenuItem onClick={() => { setOpen(false); navigate(`/warehouse/receipts/${receipt.id}`); }}>
        <Eye size={14} /> Xem chi tiết
      </ActionMenuItem>
      {receipt.status === 'DRAFT' && (
        <>
          <ActionMenuItem onClick={() => { setOpen(false); navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_EDIT.replace(':id', receipt.id)); }}>
            <Edit3 size={14} /> Chỉnh sửa
          </ActionMenuItem>
          <ActionMenuItem color="#059669" onClick={handleComplete}>
            <Check size={14} /> Hoàn thành nhập kho
          </ActionMenuItem>
        </>
      )}
      <ActionMenuItem onClick={() => { setOpen(false); toast.info('In phiếu đang được phát triển'); }}>
        <Printer size={14} /> In phiếu
      </ActionMenuItem>
    </ActionMenuShell>
  );
};

export default function StockReceivePage() {
  const navigate = useNavigate();
  const { confirm, ConfirmDialog } = useConfirmDialog();
  const [receipts, setReceipts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [statistics, setStatistics] = useState({ totalCount: 0, confirmedCount: 0, draftCount: 0, cancelledCount: 0 });
  const [pagination, setPagination] = useState({ page: 0, size: 10, totalPages: 1, totalElements: 0 });

  const fetchStatistics = async () => {
    try {
      const response = await stockReceiveService.getReceiptStatistics();
      const data = response.data?.data ?? response.data ?? {};
      setStatistics({
        totalCount: data.totalCount ?? 0,
        confirmedCount: data.confirmedCount ?? 0,
        draftCount: data.draftCount ?? 0,
        cancelledCount: data.cancelledCount ?? 0,
      });
    } catch {
      setStatistics({ totalCount: 0, confirmedCount: 0, draftCount: 0, cancelledCount: 0 });
    }
  };

  const fetchReceipts = async () => {
    setLoading(true);
    try {
      const response = await stockReceiveService.getReceipts({ page: pagination.page, size: pagination.size });
      const data = response.data?.data ?? response.data ?? {};
      const content = data.content ?? [];
      setReceipts(Array.isArray(content) ? content : []);
      setPagination((current) => ({
        ...current,
        totalPages: data.totalPages ?? 1,
        totalElements: data.totalElements ?? content.length ?? 0,
      }));
    } catch {
      setReceipts([]);
      setPagination((current) => ({ ...current, totalPages: 1, totalElements: 0 }));
    } finally {
      setLoading(false);
    }
  };

  const refreshData = async () => {
    await Promise.all([fetchReceipts(), fetchStatistics()]);
  };

  useEffect(() => {
    refreshData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagination.page]);

  const filteredReceipts = receipts.filter((receipt) => {
    const keyword = search.trim().toLowerCase();
    const matchesKeyword = !keyword
      || (receipt.receiptCode ?? '').toLowerCase().includes(keyword)
      || (receipt.supplierName ?? '').toLowerCase().includes(keyword)
      || (receipt.invoiceNumber ?? '').toLowerCase().includes(keyword);
    const matchesStatus = statusFilter === 'ALL' || receipt.status === statusFilter;
    return matchesKeyword && matchesStatus;
  });

  const stats = [
    { key: 'total', label: 'Tổng phiếu', value: statistics.totalCount, icon: FileText, color: '#475569', bg: '#f8fafc', border: '#e2e8f0' },
    { key: 'confirmed', label: 'Hoàn thành', value: statistics.confirmedCount, icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
    { key: 'draft', label: 'Lưu tạm', value: statistics.draftCount, icon: Save, color: '#d97706', bg: '#fffbeb', border: '#fcd34d' },
    { key: 'cancelled', label: 'Trả hàng', value: statistics.cancelledCount, icon: Undo2, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
  ];

  const rows = filteredReceipts.map((receipt) => (
    <tr key={receipt.id ?? receipt.receiptCode}>
      <td style={{ ...tableCellStyle, color: '#2563eb', fontFamily: 'monospace', fontWeight: 700 }}>{receipt.receiptCode ?? '-'}</td>
      <td style={tableCellStyle}>{receipt.warehouseName ?? '-'}</td>
      <td style={{ ...tableCellStyle, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis' }}>{receipt.supplierName ?? '-'}</td>
      <td style={{ ...tableCellStyle, color: '#64748b', fontFamily: 'monospace' }}>{receipt.invoiceNumber ?? '-'}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right' }}>{formatNumber(receipt.totalSkuCount ?? 0)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right', color: '#020617', fontWeight: 700 }}>{formatNumber(receipt.totalQuantity ?? 0)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right', color: '#020617', fontWeight: 700 }}>{formatVND(receipt.totalCost)}</td>
      <td style={tableCellStyle}><StatusBadge status={receipt.status} /></td>
      <td style={tableCellStyle}>{receipt.createdByName ?? '-'}</td>
      <td style={tableCellStyle}>{formatDate(receipt.createdAt)}</td>
      <td style={{ ...tableCellStyle, textAlign: 'right' }}>
        <ActionMenu receipt={receipt} onComplete={stockReceiveService.completeReceipt} onRefresh={refreshData} confirm={confirm} />
      </td>
    </tr>
  ));

  const totalPages = Math.max(1, pagination.totalPages);
  const firstVisible = pagination.totalElements === 0 ? 0 : pagination.page * pagination.size + 1;
  const lastVisible = Math.min(pagination.totalElements, pagination.page * pagination.size + receipts.length);

  return (
    <>
      <InventoryDocumentListPage
        icon={PackagePlus}
        iconBg="#eff6ff"
        iconColor="#2563eb"
        title="Danh sách phiếu nhập kho"
        description="Quản lý tất cả phiếu nhập hàng từ nhà cung cấp"
        createLabel="Tạo phiếu nhập"
        onCreate={() => navigate(ROUTES.WAREHOUSE_IMPORT_RECEIPT_CREATE)}
        stats={stats}
        filters={(
          <>
            <SearchInput value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm theo mã phiếu, nhà cung cấp, số hóa đơn..." />
            <FilterSelect value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              <option value="ALL">Tất cả trạng thái</option>
              <option value="DRAFT">Lưu tạm</option>
              <option value="CONFIRMED">Hoàn thành</option>
              <option value="CANCELLED">Trả hàng</option>
            </FilterSelect>
          </>
        )}
        columns={columns}
        rows={rows}
        loading={loading}
        emptyText={receipts.length === 0 ? 'Chưa có phiếu nhập kho' : 'Không tìm thấy phiếu nhập kho phù hợp'}
        footerLeft={`Hiển thị ${formatNumber(filteredReceipts.length)} / ${formatNumber(receipts.length)} phiếu trong trang`}
        footerRight={Object.values(STATUS_CONFIG).map((status) => (
          <span key={status.label} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: status.color }} />
            {status.label}
          </span>
        ))}
        pagination={{
          page: pagination.page,
          totalPages,
          totalElements: pagination.totalElements,
          label: `Hiển thị ${formatNumber(firstVisible)} - ${formatNumber(lastVisible)} / ${formatNumber(pagination.totalElements)} phiếu`,
          onPrevious: () => setPagination((current) => ({ ...current, page: Math.max(0, current.page - 1) })),
          onNext: () => setPagination((current) => ({ ...current, page: Math.min(totalPages - 1, current.page + 1) })),
        }}
      />
      {ConfirmDialog}
    </>
  );
}
