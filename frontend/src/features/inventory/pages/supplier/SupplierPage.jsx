import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Users,
    MoreHorizontal,
    Edit3,
    Trash2,
    Phone,
    Mail,
    MapPin,
    CheckCircle2,
    XCircle
} from 'lucide-react';
import { toast } from 'react-toastify';
import supplierService from '../../services/supplierService.js';
import SupplierModal from '../components/SupplierModal.jsx';
import useConfirmDialog from '../../hooks/useConfirmDialog.jsx';
import InventoryDocumentListPage, {
    ActionMenuItem,
    ActionMenuShell,
    Badge,
    FilterSelect,
    SearchInput,
} from '../components/InventoryDocumentListPage.jsx';
import { tableCellStyle, formatNumber } from '../components/inventoryDocumentListUtils.js';

const STATUS_CONFIG = {
    true: { label: 'Đang hoạt động', icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
    false: { label: 'Ngừng hoạt động', icon: XCircle, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const columns = [
    { label: 'Mã NCC' },
    { label: 'Tên nhà cung cấp' },
    { label: 'Thông tin liên hệ' },
    { label: 'Địa chỉ' },
    { label: 'Trạng thái' },
    { label: '', align: 'right' },
];

const StatusBadge = ({ isActive }) => {
    const config = STATUS_CONFIG[String(isActive)] ?? STATUS_CONFIG['false'];
    return <Badge {...config} />;
};

const ActionMenu = ({ supplier, onEditClick, onRefresh, confirm }) => {
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

    const handleSoftDelete = async () => {
        setOpen(false);

        const ok = await confirm({
            title: 'Ngừng hợp tác nhà cung cấp',
            message: `Bạn có chắc chắn muốn chuyển trạng thái nhà cung cấp "${supplier.name}" sang "Ngừng hoạt động"?`,
            confirmLabel: 'Xác nhận ngừng',
            cancelLabel: 'Hủy bỏ',
        });
        if (!ok) return;

        try {
            await supplierService.softDeleteSupplier(supplier.id || supplier._id);
            toast.success('Đã chuyển trạng thái nhà cung cấp sang Ngừng hoạt động.');
            onRefresh();
        } catch (error) {
            toast.error(error.response?.data?.message || 'Không thể cập nhật trạng thái.');
        }
    };

    return (
        <ActionMenuShell menuRef={menuRef} open={open} buttonIcon={MoreHorizontal} onToggle={() => setOpen((current) => !current)}>
            <ActionMenuItem onClick={() => { setOpen(false); onEditClick(supplier); }}>
                <Edit3 size={14} /> Chỉnh sửa thông tin
            </ActionMenuItem>
            <ActionMenuItem color="#e11d48" onClick={handleSoftDelete}>
                <Trash2 size={14} /> Ngừng hoạt động (Xóa mềm)
            </ActionMenuItem>
        </ActionMenuShell>
    );
};

export default function SupplierPage() {
    const { confirm, ConfirmDialog } = useConfirmDialog();
    const [suppliers, setSuppliers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedSupplier, setSelectedSupplier] = useState(null);

    const [pagination, setPagination] = useState({ page: 0, size: 10, totalPages: 1, totalElements: 0 });

    const handleOpenCreate = () => {
        setSelectedSupplier(null);
        setIsModalOpen(true);
    };

    const handleOpenEdit = (supplier) => {
        setSelectedSupplier(supplier);
        setIsModalOpen(true);
    };

    const fetchSuppliers = async () => {
        setLoading(true);
        try {
            const response = await supplierService.getSuppliers({
                page: pagination.page,
                size: pagination.size
            });

            const resData = response.data?.data ?? response.data ?? {};
            const content = Array.isArray(resData) ? resData : (resData.content ?? []);

            setSuppliers(content);
            setPagination((current) => ({
                ...current,
                totalPages: resData.totalPages ?? 1,
                totalElements: resData.totalElements ?? content.length ?? 0,
            }));
        } catch (error) {
            console.error('Lỗi tải nhà cung cấp:', error);
            toast.error('Không thể tải danh sách nhà cung cấp.');
            setSuppliers([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchSuppliers();
    }, [pagination.page]);

    const filteredSuppliers = suppliers.filter((item) => {
        const keyword = search.trim().toLowerCase();
        const matchesKeyword = !keyword
            || (item.code ?? '').toLowerCase().includes(keyword)
            || (item.name ?? '').toLowerCase().includes(keyword)
            || (item.contactName ?? '').toLowerCase().includes(keyword);

        const currentStatus = item.isActive === true ? 'ACTIVE' : 'INACTIVE';
        const matchesStatus = statusFilter === 'ALL' || currentStatus === statusFilter;

        return matchesKeyword && matchesStatus;
    });

    const rows = filteredSuppliers.map((supplier, idx) => (
        <tr key={supplier.id ?? supplier._id ?? idx}>
            <td style={{ ...tableCellStyle, color: '#2563eb', fontFamily: 'monospace', fontWeight: 700 }}>
                {supplier.code ?? '-'}
            </td>
            <td style={{ ...tableCellStyle, fontWeight: 600, color: '#0f172a' }}>
                {supplier.name ?? '-'}
            </td>
            <td style={tableCellStyle}>
                <div style={{ fontWeight: 500, color: '#334155' }}>{supplier.contactName || '-'}</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2, marginTop: 4, fontSize: '12px', color: '#64748b' }}>
                    {supplier.phone && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Phone size={12} /> {supplier.phone}</span>}
                    {supplier.email && <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}><Mail size={12} /> {supplier.email}</span>}
                </div>
            </td>
            <td style={{ ...tableCellStyle, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#475569' }}>
                    <MapPin size={14} style={{ color: '#94a3b8', flexShrink: 0 }} />
                    <span>{supplier.address || 'Chưa cập nhật'}</span>
                </div>
            </td>
            <td style={tableCellStyle}>
                <StatusBadge isActive={supplier.isActive} />
            </td>
            <td style={{ ...tableCellStyle, textAlign: 'right' }}>
                <ActionMenu
                    supplier={supplier}
                    onEditClick={handleOpenEdit}
                    onRefresh={fetchSuppliers}
                    confirm={confirm}
                />
            </td>
        </tr>
    ));

    const totalPages = Math.max(1, pagination.totalPages);
    const firstVisible = pagination.totalElements === 0 ? 0 : pagination.page * pagination.size + 1;
    const lastVisible = Math.min(pagination.totalElements, pagination.page * pagination.size + suppliers.length);

    return (
        <>
            <InventoryDocumentListPage
                icon={Users}
                iconBg="#eff6ff"
                iconColor="#2563eb"
                title="Danh sách Nhà cung cấp"
                description="Quản lý thông tin chi tiết và trạng thái của các nhà cung cấp"
                createLabel="Thêm nhà cung cấp"
                onCreate={handleOpenCreate}
                stats={[]}
                filters={(
                    <>
                        <SearchInput
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            placeholder="Tìm theo mã, tên, người liên hệ..."
                        />
                        <FilterSelect value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                            <option value="ALL">Tất cả trạng thái</option>
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </FilterSelect>
                    </>
                )}
                columns={columns}
                rows={rows}
                loading={loading}
                emptyText={suppliers.length === 0 ? 'Chưa có dữ liệu nhà cung cấp' : 'Không tìm thấy nhà cung cấp phù hợp'}

                pagination={{
                    page: pagination.page,
                    totalPages,
                    totalElements: pagination.totalElements,
                    label: `Hiển thị ${formatNumber(firstVisible)} - ${formatNumber(lastVisible)} / ${formatNumber(pagination.totalElements)} đối tác`,
                    onPrevious: () => setPagination((current) => ({ ...current, page: Math.max(0, current.page - 1) })),
                    onNext: () => setPagination((current) => ({ ...current, page: Math.min(totalPages - 1, current.page + 1) })),
                }}
            />

            <SupplierModal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                onRefresh={fetchSuppliers}
                supplierData={selectedSupplier}
            />

            {ConfirmDialog}
        </>
    );
}