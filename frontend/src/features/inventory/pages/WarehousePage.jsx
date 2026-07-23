import { useEffect, useRef, useState } from 'react';
import {
    Store,
    MoreHorizontal,
    Edit3,
    Info,
    CheckCircle2,
    XCircle,
    Warehouse,
    Package,
    MapPin,
    Users,
    Boxes,
} from 'lucide-react';
import { toast } from 'react-toastify';
import warehouseService from '../services/warehouseService.js';
import WarehouseModal from './components/WarehouseModal.jsx';
import useConfirmDialog from '../hooks/useConfirmDialog.jsx';
import useAuth from '../../auth/hooks/useAuth';
import { ROLES } from '../../auth/constants/roles';
import { useNavigate } from 'react-router-dom';
import { ROUTES } from '../../../app/router/routes';
import InventoryDocumentListPage, {
    ActionMenuItem,
    ActionMenuShell,
    Badge,
    FilterSelect,
    SearchInput,
} from './components/InventoryDocumentListPage.jsx';
import { formatNumber } from './components/inventoryDocumentListUtils.js';

const STATUS_CONFIG = {
    true: { label: 'Đang hoạt động', icon: CheckCircle2, color: '#059669', bg: '#ecfdf5', border: '#a7f3d0' },
    false: { label: 'Ngừng hoạt động', icon: XCircle, color: '#e11d48', bg: '#fff1f2', border: '#fecdd3' },
};

const StatusBadge = ({ isActive }) => {
    const config = STATUS_CONFIG[String(isActive)] ?? STATUS_CONFIG['false'];
    return <Badge {...config} />;
};

const ActionMenu = ({ warehouse, onEditClick, onViewClick, onRefresh, confirm, isReadOnly }) => {
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

    const handleToggleStatus = async () => {
        setOpen(false);
        const ok = await confirm({
            title: `${warehouse.isActive ? 'Ngừng hợp tác' : 'Kích hoạt'} kho hàng`,
            message: `Bạn có chắc chắn muốn chuyển trạng thái kho hàng "${warehouse.name}" sang "${warehouse.isActive ? 'Ngừng hoạt động' : 'Đang hoạt động'}"?`,
            confirmLabel: 'Xác nhận',
            cancelLabel: 'Hủy bỏ',
        });
        if (!ok) return;

        try {
            await warehouseService.toggleWarehouseStatus(warehouse.id, !warehouse.isActive);
            toast.success(`Đã cập nhật trạng thái kho hàng.`);
            onRefresh();
        } catch (error) {
            toast.error(error.response?.data?.message || 'Không thể cập nhật trạng thái.');
        }
    };

    return (
        <ActionMenuShell menuRef={menuRef} open={open} buttonIcon={MoreHorizontal} onToggle={() => setOpen((current) => !current)}>
            <ActionMenuItem onClick={() => { setOpen(false); onViewClick(warehouse); }}>
                <Info size={14} /> Xem chi tiết
            </ActionMenuItem>
            {!isReadOnly && (
                <>
                    <ActionMenuItem onClick={() => { setOpen(false); onEditClick(warehouse); }}>
                        <Edit3 size={14} /> Chỉnh sửa thông tin
                    </ActionMenuItem>
                    <ActionMenuItem color={warehouse.isActive ? "#e11d48" : "#059669"} onClick={handleToggleStatus}>
                        {warehouse.isActive ? (
                            <>
                                <XCircle size={14} /> Vô hiệu hóa
                            </>
                        ) : (
                            <>
                                <CheckCircle2 size={14} /> Kích hoạt
                            </>
                        )}
                    </ActionMenuItem>
                </>
            )}
        </ActionMenuShell>
    );
};

export default function WarehousePage() {
    const { confirm, ConfirmDialog } = useConfirmDialog();
    const { user } = useAuth();
    
    // OPERATIONS staff is read-only
    const isReadOnly = user?.role === ROLES.OPERATIONS;

    const [warehouses, setWarehouses] = useState([]);
    const [loading, setLoading] = useState(false);
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [selectedWarehouse, setSelectedWarehouse] = useState(null);
    const [modalReadOnly, setModalReadOnly] = useState(false);

    const fetchWarehouses = async () => {
        setLoading(true);
        try {
            const response = await warehouseService.getWarehouses({
                keyword: search,
                status: statusFilter
            });
            const data = response.data?.data ?? response.data ?? [];
            setWarehouses(data);
        } catch (error) {
            console.error('Lỗi tải danh sách kho hàng:', error);
            toast.error('Không thể tải danh sách kho hàng.');
            setWarehouses([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchWarehouses();
    }, [search, statusFilter]);

    const navigate = useNavigate();

    const handleOpenCreate = () => {
        if (isReadOnly) return;
        setSelectedWarehouse(null);
        setModalReadOnly(false);
        setIsModalOpen(true);
    };

    const handleOpenEdit = (warehouse) => {
        if (isReadOnly) return;
        setSelectedWarehouse(warehouse);
        setModalReadOnly(false);
        setIsModalOpen(true);
    };

    const handleOpenView = (warehouse) => {
        navigate(ROUTES.WAREHOUSE_DETAIL.replace(':id', warehouse.id));
    };

    // Calculate warehouse stats
    const totalCount = warehouses.length;
    const activeCount = warehouses.filter(w => w.isActive).length;
    const totalStock = warehouses.reduce((sum, w) => sum + (w.stock || 0), 0);

    const stats = [
        { label: 'Tổng số kho hàng', value: totalCount, icon: Warehouse, color: '#3b82f6', bg: '#eff6ff', active: true },
        { label: 'Kho hàng hoạt động', value: activeCount, icon: Store, color: '#10b981', bg: '#ecfdf5', active: false },
        { label: 'Tổng tồn kho hiện tại', value: totalStock, icon: Package, color: '#f59e0b', bg: '#fffbeb', active: false },
    ];

    return (
        <>
            <InventoryDocumentListPage
                icon={Warehouse}
                iconBg="#eff6ff"
                iconColor="#2563eb"
                title="Quản lý kho hàng"
                description="Theo dõi, quản trị hệ thống và hiệu suất sức chứa các kho hàng"
                createLabel={!isReadOnly ? "Thêm mới kho hàng" : null}
                onCreate={!isReadOnly ? handleOpenCreate : null}
                stats={stats}
                statUnit="kho"
                filters={
                    <>
                        <SearchInput
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Tìm kiếm theo mã, tên, địa chỉ..."
                        />
                        <FilterSelect
                            value={statusFilter}
                            onChange={(e) => setStatusFilter(e.target.value)}
                        >
                            <option value="ALL">Tất cả trạng thái</option>
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="INACTIVE">Ngừng hoạt động</option>
                        </FilterSelect>
                    </>
                }
            >
                {/* ── 2x Grid Layout ── */}
                {loading ? (
                    <div style={{ padding: '48px 16px', textAlign: 'center', backgroundColor: '#fff', borderRadius: 16, border: '1px solid #e2e8f0', color: '#64748b' }}>
                        Đang tải danh sách kho hàng...
                    </div>
                ) : warehouses.length === 0 ? (
                    <div style={{ padding: '48px 16px', textAlign: 'center', backgroundColor: '#fff', borderRadius: 16, border: '1px solid #e2e8f0', color: '#64748b' }}>
                        Không tìm thấy kho hàng nào phù hợp
                    </div>
                ) : (
                    <div style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))',
                        gap: 20,
                    }}>
                        {warehouses.map((warehouse) => {
                            const stockVal = warehouse.totalStock ?? warehouse.stock ?? 0;
                            const staffCount = warehouse.staffCount ?? 0;
                            const productCount = warehouse.productCount ?? 0;

                            return (
                                <div
                                    key={warehouse.id}
                                    style={{
                                        backgroundColor: '#ffffff',
                                        borderRadius: 16,
                                        border: '1px solid #e2e8f0',
                                        padding: 24,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 20,
                                        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)',
                                        transition: 'all 0.2s ease-in-out',
                                        position: 'relative',
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.transform = 'translateY(-2px)';
                                        e.currentTarget.style.boxShadow = '0 12px 24px -6px rgba(0, 0, 0, 0.08)';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.transform = 'translateY(0)';
                                        e.currentTarget.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)';
                                    }}
                                >
                                    {/* Card Header */}
                                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                                            <div style={{
                                                width: 48,
                                                height: 48,
                                                borderRadius: 14,
                                                backgroundColor: warehouse.isActive ? '#eff6ff' : '#f1f5f9',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                flexShrink: 0,
                                                color: warehouse.isActive ? '#2563eb' : '#64748b',
                                                boxShadow: warehouse.isActive ? '0 4px 12px rgba(37, 99, 235, 0.12)' : 'none',
                                            }}>
                                                <Warehouse size={24} />
                                            </div>
                                            <div>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                                                    <StatusBadge isActive={warehouse.isActive} />
                                                </div>
                                                <h3 
                                                    onClick={() => handleOpenView(warehouse)}
                                                    style={{ margin: '4px 0 0', fontSize: 18, fontWeight: 700, color: '#0f172a', lineHeight: 1.3, cursor: 'pointer' }}
                                                    onMouseEnter={(e) => e.currentTarget.style.color = '#2563eb'}
                                                    onMouseLeave={(e) => e.currentTarget.style.color = '#0f172a'}
                                                >
                                                    {warehouse.name}
                                                </h3>
                                            </div>
                                        </div>

                                        <ActionMenu
                                            warehouse={warehouse}
                                            onEditClick={handleOpenEdit}
                                            onViewClick={handleOpenView}
                                            onRefresh={fetchWarehouses}
                                            confirm={confirm}
                                            isReadOnly={isReadOnly}
                                        />
                                    </div>

                                    {/* Address */}
                                    <div style={{
                                        display: 'flex',
                                        alignItems: 'flex-start',
                                        gap: 8,
                                        color: '#475569',
                                        fontSize: 13.5,
                                        backgroundColor: '#f8fafc',
                                        padding: '10px 14px',
                                        borderRadius: 10,
                                        border: '1px solid #f1f5f9',
                                    }}>
                                        <MapPin size={16} color="#64748b" style={{ flexShrink: 0, marginTop: 2 }} />
                                        <span style={{ lineHeight: 1.4 }}>{warehouse.address || 'Chưa cập nhật địa chỉ'}</span>
                                    </div>

                                    {/* 3 Key Info Items Grid */}
                                    <div style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(3, 1fr)',
                                        gap: 12,
                                    }}>
                                        {/* Staff Count */}
                                        <div style={{
                                            padding: 12,
                                            borderRadius: 12,
                                            border: '1px solid #e2e8f0',
                                            backgroundColor: '#ffffff',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 10,
                                        }}>
                                            <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563eb', flexShrink: 0 }}>
                                                <Users size={18} />
                                            </div>
                                            <div>
                                                <div style={{ fontSize: 12, color: '#64748b', fontWeight: 500 }}>Nhân viên kho</div>
                                                <div style={{ fontSize: 15, fontWeight: 700, color: '#0f172a', marginTop: 1 }}>
                                                    {formatNumber(staffCount)} <span style={{ fontSize: 12, fontWeight: 500, color: '#64748b' }}>người</span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Product SKUs Count */}
                                        <div style={{
                                            padding: 12,
                                            borderRadius: 12,
                                            border: '1px solid #e2e8f0',
                                            backgroundColor: '#ffffff',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 10,
                                        }}>
                                            <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#f3e8ff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#9333ea', flexShrink: 0 }}>
                                                <Package size={18} />
                                            </div>
                                            <div>
                                                <div style={{ fontSize: 12, color: '#64748b', fontWeight: 500 }}>Sản phẩm / SKU</div>
                                                <div style={{ fontSize: 15, fontWeight: 700, color: '#0f172a', marginTop: 1 }}>
                                                    {formatNumber(productCount)} <span style={{ fontSize: 12, fontWeight: 500, color: '#64748b' }}>loại</span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Stock Quantity */}
                                        <div style={{
                                            padding: 12,
                                            borderRadius: 12,
                                            border: '1px solid #e2e8f0',
                                            backgroundColor: '#ffffff',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 10,
                                        }}>
                                            <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#fffbeb', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#d97706', flexShrink: 0 }}>
                                                <Boxes size={18} />
                                            </div>
                                            <div>
                                                <div style={{ fontSize: 12, color: '#64748b', fontWeight: 500 }}>Tổng tồn kho</div>
                                                <div style={{ fontSize: 15, fontWeight: 700, color: '#0f172a', marginTop: 1 }}>
                                                    {formatNumber(stockVal)} <span style={{ fontSize: 12, fontWeight: 500, color: '#64748b' }}>cái</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </InventoryDocumentListPage>

            <WarehouseModal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                onRefresh={fetchWarehouses}
                warehouseData={selectedWarehouse}
                readOnly={modalReadOnly}
            />

            {ConfirmDialog}
        </>
    );
}
