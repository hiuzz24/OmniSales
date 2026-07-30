import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowLeft, Check, ClipboardCheck, RefreshCw, RotateCcw, X } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import orderReturnApi from '../../../api/orderReturnApi';
import { ROUTES } from '../../../app/router/routes';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import { formatExternalReturnId } from '../utils/orderReturnDisplay';
import styles from './OrderReturn.module.css';

const STATUS_LABELS = {
  PENDING_APPROVAL: 'Chờ duyệt', REJECTED: 'Đã từ chối', AWAITING_RETURN: 'Chờ khách gửi hàng',
  RETURN_IN_TRANSIT: 'Đang hoàn về', INSPECTED: 'Đã kiểm hàng', PLATFORM_PROCESSING: 'Sàn đang xử lý',
  PENDING_STOCK: 'Chờ nhập kho', COMPLETED: 'Hoàn thành', FAILED: 'Lỗi dữ liệu',
};

const OrderReturnDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [working, setWorking] = useState(false);
  const [inspectOpen, setInspectOpen] = useState(false);
  const [inspection, setInspection] = useState([]);

  const isSale = user?.role === ROLES.OWNER || user?.role === ROLES.SALES;
  const isWarehouse = user?.role === ROLES.OWNER || user?.role === ROLES.OPERATIONS;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await orderReturnApi.getById(id));
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể tải yêu cầu trả hàng');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const actionRoleAllowed = useMemo(() => {
    if (!data?.lastAction) return false;
    return data.lastAction === 'PROCESS' ? isWarehouse : isSale;
  }, [data?.lastAction, isSale, isWarehouse]);

  const run = async (operation, successMessage) => {
    setWorking(true);
    try {
      setData(await operation());
      toast.success(successMessage);
      return true;
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể thực hiện thao tác');
      return false;
    } finally {
      setWorking(false);
    }
  };

  const reject = () => {
    const reason = window.prompt('Lý do từ chối yêu cầu trả hàng:');
    if (reason?.trim()) run(() => orderReturnApi.reject(id, reason.trim()), 'Đã gửi yêu cầu từ chối');
  };

  const openInspection = () => {
    setInspection((data.items ?? []).map((item) => ({
      returnItemId: item.id, name: item.name, approvedQuantity: item.approvedQuantity,
      receivedQuantity: item.approvedQuantity, restockableQuantity: item.approvedQuantity,
      damagedQuantity: 0, missingQuantity: 0,
    })));
    setInspectOpen(true);
  };

  const updateInspection = (index, field, value) => {
    setInspection((current) => current.map((item, itemIndex) => (
      itemIndex === index ? { ...item, [field]: Math.max(0, Number(value) || 0) } : item
    )));
  };

  const submitInspection = async () => {
    const invalid = inspection.some((item) => (
      item.receivedQuantity !== item.restockableQuantity + item.damagedQuantity
      || item.receivedQuantity + item.missingQuantity !== item.approvedQuantity
    ));
    if (invalid) {
      toast.error('Số lượng nhận = đạt + hỏng, và nhận + thiếu = số lượng được duyệt');
      return;
    }
    const succeeded = await run(
      () => orderReturnApi.inspect(id, inspection.map(({ name, approvedQuantity, ...item }) => item)),
      'Đã hoàn tất kiểm hàng và gửi sàn xử lý',
    );
    if (succeeded) setInspectOpen(false);
  };

  if (loading) return <main className={styles.page}><div className={styles.loading}>Đang tải...</div></main>;
  if (!data) return <main className={styles.page}><div className={styles.loading}>Không tìm thấy yêu cầu trả hàng.</div></main>;

  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <div className={styles.heading}>
          <button className={styles.iconButton} onClick={() => navigate(ROUTES.ORDER_RETURNS)} title="Quay lại"><ArrowLeft size={18} /></button>
          <span className={styles.headingIcon}><RotateCcw size={22} /></span>
          <div><h1 title={data.externalReturnId}>{formatExternalReturnId(data.externalReturnId)}</h1><p>Đơn {data.externalOrderId} · {data.platform} · {data.channelName}</p></div>
        </div>
        <div className={styles.toolbar}>
          {isSale && data.status === 'PENDING_APPROVAL' && <button className={styles.primaryButton} onClick={() => run(() => orderReturnApi.approve(id), 'Đã gửi yêu cầu duyệt trả hàng')} disabled={working}><Check size={17} /> Duyệt</button>}
          {isSale && data.status === 'PENDING_APPROVAL' && <button className={styles.dangerButton} onClick={reject} disabled={working}><X size={17} /> Từ chối</button>}
          {isWarehouse && ['AWAITING_RETURN', 'RETURN_IN_TRANSIT'].includes(data.status) && <button className={styles.primaryButton} onClick={openInspection} disabled={working}><ClipboardCheck size={17} /> Nhận & kiểm hàng</button>}
          {actionRoleAllowed && data.actionState === 'UNKNOWN' && <button className={styles.secondaryButton} onClick={() => run(() => orderReturnApi.checkAction(id), 'Đã kiểm tra trạng thái trên sàn')} disabled={working}><RefreshCw size={17} /> Kiểm tra lại</button>}
          {actionRoleAllowed && data.actionState === 'FAILED' && <button className={styles.secondaryButton} onClick={() => run(() => orderReturnApi.retryAction(id), 'Đã thử lại thao tác')} disabled={working}><RefreshCw size={17} /> Thử lại API</button>}
          {isWarehouse && data.status === 'PENDING_STOCK' && <button className={styles.secondaryButton} onClick={() => run(() => orderReturnApi.retryStock(id), 'Đã thử nhập lại tồn kho')} disabled={working}><RefreshCw size={17} /> Thử nhập kho</button>}
        </div>
      </header>

      {(data.actionError || data.lastSyncError) && <div className={styles.alert}><AlertCircle size={18} /><div><strong>Cần xử lý</strong><p>{data.actionError || data.lastSyncError}</p></div></div>}

      <section className={styles.detailGrid}>
        <div className={styles.infoSection}>
          <h2>Thông tin xử lý</h2>
          <dl>
            <div><dt>Trạng thái</dt><dd><span className={`${styles.status} ${styles[data.status?.toLowerCase()]}`}>{STATUS_LABELS[data.status] ?? data.status}</span></dd></div>
            <div><dt>Trạng thái sàn</dt><dd>{data.platformStatus || '-'}</dd></div>
            <div><dt>Action gần nhất</dt><dd>{data.lastAction || '-'}</dd></div>
            <div><dt>Kết quả API</dt><dd>{data.actionState}</dd></div>
          </dl>
        </div>
        <div className={styles.infoSection}>
          <h2>Kho và thanh toán</h2>
          <dl>
            <div><dt>Kho nhận</dt><dd>{data.warehouseName || 'Chưa xác định'}</dd></div>
            <div><dt>Đã kiểm hàng</dt><dd>{data.inspectedAt ? new Date(data.inspectedAt).toLocaleString('vi-VN') : '-'}</dd></div>
            <div><dt>Sàn xác nhận hoàn tiền</dt><dd>{data.refundConfirmedAt ? new Date(data.refundConfirmedAt).toLocaleString('vi-VN') : '-'}</dd></div>
            <div><dt>Đã nhập kho</dt><dd>{data.inventoryPostedAt ? new Date(data.inventoryPostedAt).toLocaleString('vi-VN') : '-'}</dd></div>
          </dl>
        </div>
      </section>

      <section className={styles.tableShell}>
        <div className={styles.sectionHeader}><div><h2>Sản phẩm trả về</h2><span>{data.items?.length ?? 0} dòng sản phẩm</span></div></div>
        <div className={styles.tableViewport}>
          <table>
            <thead><tr><th>Sản phẩm</th><th>SKU</th><th>Duyệt</th><th>Nhận</th><th>Đạt</th><th>Hỏng</th><th>Thiếu</th><th>Hoàn tiền</th></tr></thead>
            <tbody>{data.items?.map((item) => <tr key={item.id}><td><strong>{item.name}</strong></td><td>{item.sku || '-'}</td><td>{item.approvedQuantity}</td><td>{item.receivedQuantity ?? '-'}</td><td>{item.restockableQuantity ?? '-'}</td><td>{item.damagedQuantity ?? '-'}</td><td>{item.missingQuantity ?? '-'}</td><td>{item.refundedQuantity ?? '-'}</td></tr>)}</tbody>
          </table>
        </div>
      </section>

      {inspectOpen && (
        <div className={styles.overlay} role="presentation" onMouseDown={() => setInspectOpen(false)}>
          <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="inspection-title" onMouseDown={(event) => event.stopPropagation()}>
            <header><div><h2 id="inspection-title">Nhận & kiểm hàng</h2><p>Phân loại toàn bộ số lượng đã được duyệt.</p></div><button className={styles.iconButton} onClick={() => setInspectOpen(false)}><X size={18} /></button></header>
            <div className={styles.modalBody}>
              {inspection.map((item, index) => <div className={styles.inspectionRow} key={item.returnItemId}>
                <div><strong>{item.name}</strong><span>Được duyệt: {item.approvedQuantity}</span></div>
                {['receivedQuantity', 'restockableQuantity', 'damagedQuantity', 'missingQuantity'].map((field) => <label key={field}>{({ receivedQuantity: 'Đã nhận', restockableQuantity: 'Hàng đạt', damagedQuantity: 'Hàng hỏng', missingQuantity: 'Hàng thiếu' })[field]}<input type="number" min="0" value={item[field]} onChange={(event) => updateInspection(index, field, event.target.value)} /></label>)}
              </div>)}
            </div>
            <footer><button className={styles.secondaryButton} onClick={() => setInspectOpen(false)}>Hủy</button><button className={styles.primaryButton} onClick={submitInspection} disabled={working}><Check size={17} /> Hoàn tất kiểm hàng</button></footer>
          </section>
        </div>
      )}
    </main>
  );
};

export default OrderReturnDetailPage;
