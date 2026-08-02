import { useCallback, useEffect, useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  Check,
  Circle,
  ClipboardCheck,
  Clock3,
  PackageCheck,
  RefreshCw,
  RotateCcw,
  Store,
  Warehouse,
  X,
} from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import orderReturnApi from '../../../api/orderReturnApi';
import { ROUTES } from '../../../app/router/routes';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';
import OrderReturnInspectionModal from '../components/OrderReturnInspectionModal';
import OrderReturnRejectModal from '../components/OrderReturnRejectModal';
import {
  formatExternalReturnId,
  formatReturnDateTime,
  ORDER_RETURN_STATUS_LABELS,
  RETURN_ACTION_LABELS,
  RETURN_ACTION_STATE_LABELS,
} from '../utils/orderReturnDisplay';
import styles from './OrderReturnDetailPage.module.css';

const PROGRESS_LEVELS = {
  PENDING_APPROVAL: 1,
  REJECTED: 1,
  AWAITING_RETURN: 2,
  RETURN_IN_TRANSIT: 2,
  INSPECTED: 3,
  PLATFORM_PROCESSING: 3,
  PENDING_STOCK: 4,
  COMPLETED: 5,
  FAILED: 1,
};

const formatCurrency = (value) => {
  if (value == null || value === '') return null;
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value));
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
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectOptions, setRejectOptions] = useState(null);
  const [rejectOptionsLoading, setRejectOptionsLoading] = useState(false);
  const [rejectOptionsError, setRejectOptionsError] = useState('');
  const [rejectReasonCode, setRejectReasonCode] = useState('');
  const [rejectComment, setRejectComment] = useState('');

  const isSale = user?.role === ROLES.OWNER || user?.role === ROLES.SALES;
  const isWarehouse = user?.role === ROLES.OWNER || user?.role === ROLES.OPERATIONS;
  const returnStatus = data?.status;
  const isTikTok = data?.platform === 'TIKTOK';
  const isShopify = data?.platform === 'SHOPIFY';
  const isPartialReceipt = data?.items?.some((item) => (item.missingQuantity ?? 0) > 0) ?? false;
  const isTikTokWaitingForBuyer = isTikTok && data?.platformStatus === 'AWAITING_BUYER_SHIP';
  const canInspect = isWarehouse && (
    (isTikTok && data?.status === 'RETURN_IN_TRANSIT')
    || (!isTikTok && ['AWAITING_RETURN', 'RETURN_IN_TRANSIT'].includes(data?.status))
  );
  const canRefresh = isTikTokWaitingForBuyer
    || (isShopify && (
      data?.status === 'PLATFORM_PROCESSING'
      || (data?.status === 'INSPECTED' && isPartialReceipt)
    ));

  const load = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      setData(await orderReturnApi.getById(id));
    } catch (error) {
      if (!silent) {
        toast.error(error.response?.data?.message || 'Không thể tải yêu cầu trả hàng');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    const initialLoadId = window.setTimeout(() => load(), 0);
    return () => window.clearTimeout(initialLoadId);
  }, [load]);

  useEffect(() => {
    if (!returnStatus || ['COMPLETED', 'REJECTED', 'FAILED'].includes(returnStatus)) {
      return undefined;
    }
    const pollingId = window.setInterval(() => load(true), 5000);
    return () => window.clearInterval(pollingId);
  }, [returnStatus, load]);

  const actionRoleAllowed = data?.lastAction
    ? (data.lastAction === 'PROCESS' ? isWarehouse : isSale)
    : false;
  const shouldCheckUnknownAction = data?.actionState === 'UNKNOWN' && actionRoleAllowed;
  const canCheckPlatform = canRefresh || shouldCheckUnknownAction;

  const totals = (data?.items ?? []).reduce((result, item) => ({
    approved: result.approved + (item.approvedQuantity ?? 0),
    received: result.received + (item.receivedQuantity ?? 0),
    restockable: result.restockable + (item.restockableQuantity ?? 0),
    damaged: result.damaged + (item.damagedQuantity ?? 0),
    missing: result.missing + (item.missingQuantity ?? 0),
    refunded: result.refunded + (item.refundedQuantity ?? 0),
  }), {
    approved: 0,
    received: 0,
    restockable: 0,
    damaged: 0,
    missing: 0,
    refunded: 0,
  });

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

  const submitReject = async () => {
    if (rejectOptions?.requiresReasonCode && !rejectReasonCode) {
      toast.error('Vui lòng chọn lý do từ chối');
      return;
    }
    if (!rejectOptions?.requiresReasonCode && !rejectComment.trim()) {
      toast.error('Vui lòng nhập lý do từ chối');
      return;
    }
    const succeeded = await run(
      () => orderReturnApi.reject(id, {
        reasonCode: rejectReasonCode || null,
        comment: rejectComment.trim() || null,
      }),
      'Đã gửi yêu cầu từ chối',
    );
    if (succeeded) {
      setRejectOpen(false);
      setRejectReasonCode('');
      setRejectComment('');
    }
  };

  const loadRejectOptions = async () => {
    setRejectOptionsLoading(true);
    setRejectOptionsError('');
    try {
      const options = await orderReturnApi.getRejectOptions(id);
      setRejectOptions(options);
      if (options.options?.length === 1) {
        setRejectReasonCode(options.options[0].code);
      }
    } catch (error) {
      setRejectOptions(null);
      setRejectOptionsError(error.response?.data?.message || 'Không thể tải lý do từ chối từ sàn');
    } finally {
      setRejectOptionsLoading(false);
    }
  };

  const openRejectModal = () => {
    setRejectOpen(true);
    setRejectReasonCode('');
    setRejectComment('');
    loadRejectOptions();
  };

  const checkPlatform = () => run(
    () => (shouldCheckUnknownAction
      ? orderReturnApi.checkAction(id)
      : orderReturnApi.refresh(id)),
    'Đã kiểm tra trạng thái mới nhất trên sàn',
  );

  const openInspection = () => {
    setInspection((data.items ?? []).map((item) => ({
      returnItemId: item.id,
      name: item.name,
      sku: item.sku,
      approvedQuantity: item.approvedQuantity,
      receivedQuantity: item.approvedQuantity,
      restockableQuantity: item.approvedQuantity,
      damagedQuantity: 0,
      missingQuantity: 0,
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
    setWorking(true);
    try {
      const updated = await orderReturnApi.inspect(
        id,
        inspection.map((item) => ({
          returnItemId: item.returnItemId,
          receivedQuantity: item.receivedQuantity,
          restockableQuantity: item.restockableQuantity,
          damagedQuantity: item.damagedQuantity,
          missingQuantity: item.missingQuantity,
        })),
      );
      setData(updated);
      const partial = updated.items?.some((item) => (item.missingQuantity ?? 0) > 0);
      if (partial && ['SHOPIFY', 'TIKTOK'].includes(updated.platform)) {
        toast.warning('Đã lưu kiểm hàng. Đơn nhận thiếu cần được xử lý thủ công trên sàn.');
      } else {
        toast.success('Đã hoàn tất kiểm hàng và gửi sàn xử lý');
      }
      setInspectOpen(false);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Không thể hoàn tất kiểm hàng');
    } finally {
      setWorking(false);
    }
  };

  if (loading) {
    return (
      <main className={styles.page}>
        <div className={styles.detailSkeleton}>
          <span className={styles.skeletonLine} />
          <span className={styles.skeletonLine} />
          <span className={styles.skeletonBlock} />
        </div>
      </main>
    );
  }

  if (!data) {
    return (
      <main className={styles.page}>
        <section className={styles.notFound}>
          <PackageCheck size={32} />
          <h1>Không tìm thấy yêu cầu trả hàng</h1>
          <button type="button" className={styles.secondaryButton} onClick={() => navigate(ROUTES.ORDER_RETURNS)}>
            <ArrowLeft size={16} /> Quay lại danh sách
          </button>
        </section>
      </main>
    );
  }

  const terminalStatus = ['COMPLETED', 'REJECTED', 'FAILED'].includes(data.status);
  const progressLevel = PROGRESS_LEVELS[data.status] ?? 1;
  const progressSteps = [
    { label: 'Tiếp nhận', date: data.createdAt },
    { label: 'Duyệt yêu cầu', date: data.approvedAt },
    { label: 'Kiểm hàng', date: data.inspectedAt },
    { label: 'Sàn xác nhận', date: data.refundConfirmedAt },
    { label: 'Nhập kho', date: data.inventoryPostedAt },
  ];

  return (
    <main className={styles.page}>
      <header className={styles.detailHeader}>
        <div className={styles.detailIdentity}>
          <button
            type="button"
            className={styles.iconButton}
            onClick={() => navigate(ROUTES.ORDER_RETURNS)}
            title="Quay lại danh sách"
            aria-label="Quay lại danh sách trả hàng"
          >
            <ArrowLeft size={18} />
          </button>
          <span className={styles.titleIcon} aria-hidden="true"><RotateCcw size={20} /></span>
          <div className={styles.detailTitleBlock}>
            <div className={styles.detailTitleRow}>
              <h1 title={data.externalReturnId}>{formatExternalReturnId(data.externalReturnId)}</h1>
              <span className={`${styles.statusBadge} ${styles[data.status?.toLowerCase()]}`}>
                {ORDER_RETURN_STATUS_LABELS[data.status] ?? data.status}
              </span>
            </div>
            <p>
              Đơn <strong>{data.externalOrderId || '-'}</strong>
              <span aria-hidden="true">•</span>
              {data.platform || '-'}
              <span aria-hidden="true">•</span>
              {data.channelName || '-'}
            </p>
          </div>
        </div>

        <div className={styles.toolbar}>
          {isSale && data.status === 'PENDING_APPROVAL' && (
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => run(() => orderReturnApi.approve(id), 'Đã gửi yêu cầu duyệt trả hàng')}
              disabled={working}
            >
              <Check size={17} /> Duyệt
            </button>
          )}
          {isSale && data.status === 'PENDING_APPROVAL' && (
            <button type="button" className={styles.dangerButton} onClick={openRejectModal} disabled={working}>
              <X size={17} /> Từ chối
            </button>
          )}
          {canInspect && (
            <button type="button" className={styles.primaryButton} onClick={openInspection} disabled={working}>
              <ClipboardCheck size={17} /> Nhận & kiểm hàng
            </button>
          )}
          {canCheckPlatform && (
            <button type="button" className={styles.secondaryButton} onClick={checkPlatform} disabled={working}>
              <RefreshCw size={17} className={working ? styles.spinning : undefined} /> Kiểm tra trạng thái sàn
            </button>
          )}
          {actionRoleAllowed && data.actionState === 'FAILED' && data.actionRetryAllowed && (
            <button
              type="button"
              className={styles.secondaryButton}
              onClick={() => run(() => orderReturnApi.retryAction(id), 'Đã thử lại thao tác')}
              disabled={working}
            >
              <RefreshCw size={17} /> Thử lại API
            </button>
          )}
          {isWarehouse && data.status === 'PENDING_STOCK' && (
            <button
              type="button"
              className={styles.secondaryButton}
              onClick={() => run(() => orderReturnApi.retryStock(id), 'Đã thử nhập lại tồn kho')}
              disabled={working}
            >
              <RefreshCw size={17} /> Thử nhập kho
            </button>
          )}
        </div>
      </header>

      <section className={styles.progressPanel} aria-label="Tiến trình trả hàng">
        <ol className={styles.progressList}>
          {progressSteps.map((step, index) => {
            const isDone = index < progressLevel || Boolean(step.date);
            const isActive = !terminalStatus && index === progressLevel;
            return (
              <li
                key={step.label}
                className={`${styles.progressStep} ${isDone ? styles.progressDone : ''} ${isActive ? styles.progressActive : ''}`}
              >
                <span className={styles.progressMarker}>
                  {isDone ? <Check size={14} /> : <Circle size={10} />}
                </span>
                <div>
                  <strong>{step.label}</strong>
                  <span>{step.date ? formatReturnDateTime(step.date) : isActive ? 'Đang xử lý' : 'Chưa thực hiện'}</span>
                </div>
              </li>
            );
          })}
        </ol>
      </section>

      <div className={styles.noticeStack}>
        {isTikTokWaitingForBuyer && (
          <div className={`${styles.notice} ${styles.noticeWarning}`}>
            <AlertCircle size={18} />
            <div><strong>Đang chờ khách gửi hàng</strong><p>Chỉ có thể nhận và kiểm hàng sau khi TikTok chuyển sang BUYER_SHIPPED_ITEM.</p></div>
          </div>
        )}

        {isShopify && data.status === 'INSPECTED' && isPartialReceipt && (
          <div className={`${styles.notice} ${styles.noticeWarning}`}>
            <AlertTriangle size={18} />
            <div><strong>Đơn trả hàng nhận thiếu</strong><p>Xử lý thủ công trên Shopify, chỉ restock hàng thực tế đã nhận rồi đồng bộ lại trạng thái sàn.</p></div>
          </div>
        )}

        {isShopify && data.status === 'PLATFORM_PROCESSING' && !data.refundConfirmedAt && !data.lastSyncError && (
          <div className={`${styles.notice} ${styles.noticeInfo}`}>
            <Clock3 size={18} />
            <div><strong>Đang chờ Shopify hoàn tiền</strong><p>OSMS chỉ nhập lại tồn sau khi giao dịch hoàn tiền được xác nhận thành công.</p></div>
          </div>
        )}

        {(data.actionError || data.lastSyncError) && (
          <div className={`${styles.notice} ${styles.noticeDanger}`}>
            <AlertCircle size={18} />
            <div><strong>Cần xử lý</strong><p>{data.actionError || data.lastSyncError}</p></div>
          </div>
        )}
      </div>

      <div className={styles.detailLayout}>
        <section className={styles.itemsPanel} aria-labelledby="return-items-title">
          <header className={styles.itemsHeader}>
            <div>
              <h2 id="return-items-title">Sản phẩm trả về</h2>
              <p>{data.items?.length ?? 0} dòng sản phẩm</p>
            </div>
            <div className={styles.quantitySummary} aria-label="Tổng số lượng kiểm hàng">
              <span>Duyệt <strong>{totals.approved}</strong></span>
              <span>Nhận <strong>{totals.received}</strong></span>
              <span>Nhập lại <strong>{totals.restockable}</strong></span>
            </div>
          </header>

          <div className={styles.tableScroll}>
            <table className={`${styles.returnTable} ${styles.itemTable}`}>
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>SKU</th>
                  <th className={styles.numericColumn}>Duyệt</th>
                  <th className={styles.numericColumn}>Nhận</th>
                  <th className={styles.numericColumn}>Đạt</th>
                  <th className={styles.numericColumn}>Hỏng</th>
                  <th className={styles.numericColumn}>Thiếu</th>
                  <th className={styles.numericColumn}>Hoàn tiền</th>
                </tr>
              </thead>
              <tbody>
                {(data.items ?? []).map((item) => (
                  <tr key={item.id}>
                    <td>
                      <strong className={styles.productName}>{item.name}</strong>
                      {formatCurrency(item.unitPrice) && <span className={styles.cellHint}>{formatCurrency(item.unitPrice)}</span>}
                    </td>
                    <td><span className={styles.skuCode}>{item.sku || '-'}</span></td>
                    <td className={styles.numericColumn}>{item.approvedQuantity}</td>
                    <td className={styles.numericColumn}>{item.receivedQuantity ?? '-'}</td>
                    <td className={`${styles.numericColumn} ${styles.positiveQuantity}`}>{item.restockableQuantity ?? '-'}</td>
                    <td className={`${styles.numericColumn} ${styles.negativeQuantity}`}>{item.damagedQuantity ?? '-'}</td>
                    <td className={`${styles.numericColumn} ${styles.warningQuantity}`}>{item.missingQuantity ?? '-'}</td>
                    <td className={styles.numericColumn}>{item.refundedQuantity ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <aside className={styles.detailAside}>
          <section className={styles.infoPanel}>
            <header className={styles.infoPanelHeader}><Store size={17} /><h2>Thông tin sàn</h2></header>
            <dl className={styles.factList}>
              <div><dt>Mã đơn hàng</dt><dd>{data.externalOrderId || '-'}</dd></div>
              <div><dt>Mã return</dt><dd title={data.externalReturnId}>{formatExternalReturnId(data.externalReturnId)}</dd></div>
              <div><dt>Sàn</dt><dd><span className={`${styles.platformBadge} ${styles[data.platform?.toLowerCase()]}`}>{data.platform || '-'}</span></dd></div>
              <div><dt>Kênh bán</dt><dd>{data.channelName || '-'}</dd></div>
              <div><dt>Trạng thái sàn</dt><dd className={styles.platformStatus}>{data.platformStatus || '-'}</dd></div>
            </dl>
          </section>

          <section className={styles.infoPanel}>
            <header className={styles.infoPanelHeader}><Warehouse size={17} /><h2>Kho và xử lý</h2></header>
            <dl className={styles.factList}>
              <div><dt>Kho nhận</dt><dd>{data.warehouseName || 'Chưa xác định'}</dd></div>
              <div><dt>Kiểm tra dữ liệu</dt><dd>{data.dataValidationState === 'VALID' ? 'Hợp lệ' : data.dataValidationState || '-'}</dd></div>
              <div><dt>Action gần nhất</dt><dd>{RETURN_ACTION_LABELS[data.lastAction] ?? data.lastAction ?? '-'}</dd></div>
              <div>
                <dt>Kết quả API</dt>
                <dd><span className={`${styles.actionBadge} ${styles[`action_${data.actionState?.toLowerCase()}`]}`}>
                  {RETURN_ACTION_STATE_LABELS[data.actionState] ?? data.actionState ?? '-'}
                </span></dd>
              </div>
            </dl>
          </section>

          <section className={styles.infoPanel}>
            <header className={styles.infoPanelHeader}><Clock3 size={17} /><h2>Mốc xử lý</h2></header>
            <dl className={styles.factList}>
              <div><dt>Đã kiểm hàng</dt><dd>{formatReturnDateTime(data.inspectedAt)}</dd></div>
              <div><dt>Sàn xác nhận hoàn tiền</dt><dd>{formatReturnDateTime(data.refundConfirmedAt)}</dd></div>
              <div><dt>Đã nhập kho</dt><dd>{formatReturnDateTime(data.inventoryPostedAt)}</dd></div>
              <div><dt>Cập nhật gần nhất</dt><dd>{formatReturnDateTime(data.updatedAt)}</dd></div>
            </dl>
          </section>
        </aside>
      </div>

      {inspectOpen && (
        <OrderReturnInspectionModal
          inspection={inspection}
          working={working}
          onClose={() => setInspectOpen(false)}
          onUpdate={updateInspection}
          onSubmit={submitInspection}
        />
      )}

      {rejectOpen && (
        <OrderReturnRejectModal
          options={rejectOptions}
          loading={rejectOptionsLoading}
          loadError={rejectOptionsError}
          reasonCode={rejectReasonCode}
          comment={rejectComment}
          working={working}
          onReasonCodeChange={setRejectReasonCode}
          onCommentChange={setRejectComment}
          onReload={loadRejectOptions}
          onClose={() => setRejectOpen(false)}
          onSubmit={submitReject}
        />
      )}
    </main>
  );
};

export default OrderReturnDetailPage;
