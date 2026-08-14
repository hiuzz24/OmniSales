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
import orderReturnApi from '../../../api/orderReturnApi';
import { ROUTES } from '../../../app/router/routes';
import OrderReturnInspectionModal from '../components/OrderReturnInspectionModal';
import OrderReturnRejectModal from '../components/OrderReturnRejectModal';
import {
  formatExternalReturnId,
  formatReturnDateTime,
  ORDER_RETURN_STATUS_LABELS,
  RETURN_ACTION_LABELS,
  RETURN_ACTION_STATE_LABELS,
  DATA_VALIDATION_LABELS,
  formatPlatformLabel,
  formatReturnErrorMessage,
  formatReturnPlatformStatus,
} from '../utils/orderReturnDisplay';
import styles from './OrderReturnDetailPage.module.css';
import useOrderReturnDetailController from '../hooks/useOrderReturnDetailController';

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

/** Định dạng số tiền hoàn theo VND khi platform cung cấp giá trị. */
const formatCurrency = (value) => {
  if (value == null || value === '') return null;
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value));
};

/** Hiển thị timeline, sản phẩm, action và trạng thái kho của một phiếu trả hàng. */
const OrderReturnDetailPage = () => {
  const {
    id, navigate, data, loading, working,
    inspectOpen, setInspectOpen, inspection,
    rejectOpen, setRejectOpen, rejectOptions, rejectOptionsLoading, rejectOptionsError,
    rejectReasonCode, setRejectReasonCode, rejectComment, setRejectComment,
    isSale, isWarehouse, isShopify, isPartialReceipt, isTikTokWaitingForBuyer,
    canInspect, canCheckPlatform, actionRoleAllowed, displayItems, totals, run,
    openRejectModal, submitReject, checkPlatform, openInspection, updateInspection, submitInspection,
  } = useOrderReturnDetailController();

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
              {formatPlatformLabel(data.platform)}
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
              <RefreshCw size={17} /> Thử lại thao tác
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
            <div><strong>Đang chờ khách gửi hàng</strong><p>Chỉ có thể nhận và kiểm hàng sau khi TikTok xác nhận khách đã gửi hàng.</p></div>
          </div>
        )}

        {isShopify && data.status === 'INSPECTED' && isPartialReceipt && (
          <div className={`${styles.notice} ${styles.noticeWarning}`}>
            <AlertTriangle size={18} />
            <div><strong>Đơn trả hàng nhận thiếu</strong><p>Xử lý thủ công trên Shopify, chỉ nhập lại kho số hàng thực tế đã nhận rồi đồng bộ trạng thái sàn.</p></div>
          </div>
        )}

        {isShopify && data.status === 'PLATFORM_PROCESSING' && !data.refundConfirmedAt && !data.lastSyncError && (
          <div className={`${styles.notice} ${styles.noticeInfo}`}>
            <Clock3 size={18} />
            <div><strong>Đang chờ Shopify hoàn tiền</strong><p>OSMS chỉ nhập lại tồn sau khi giao dịch hoàn tiền được xác nhận thành công.</p></div>
          </div>
        )}

        {(data.actionError || data.lastSyncError)
          && !(isShopify && data.actionState === 'UNKNOWN') && (
          <div className={`${styles.notice} ${styles.noticeDanger}`}>
            <AlertCircle size={18} />
            <div><strong>Cần xử lý</strong><p>{formatReturnErrorMessage(data.actionError || data.lastSyncError)}</p></div>
          </div>
        )}
      </div>

      <div className={styles.detailLayout}>
        <section className={styles.itemsPanel} aria-labelledby="return-items-title">
          <header className={styles.itemsHeader}>
            <div>
              <h2 id="return-items-title">Sản phẩm trả về</h2>
              <p>{displayItems.length} sản phẩm • Tổng số lượng {totals.approved}</p>
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
                {displayItems.map((item) => (
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
              <div><dt>Mã trả hàng</dt><dd title={data.externalReturnId}>{formatExternalReturnId(data.externalReturnId)}</dd></div>
              <div><dt>Sàn</dt><dd><span className={`${styles.platformBadge} ${styles[data.platform?.toLowerCase()]}`}>{formatPlatformLabel(data.platform)}</span></dd></div>
              <div><dt>Kênh bán</dt><dd>{data.channelName || '-'}</dd></div>
              <div><dt>Trạng thái sàn</dt><dd>{formatReturnPlatformStatus(data.platformStatus)}</dd></div>
            </dl>
          </section>

          <section className={styles.infoPanel}>
            <header className={styles.infoPanelHeader}><Warehouse size={17} /><h2>Kho và xử lý</h2></header>
            <dl className={styles.factList}>
              <div><dt>Kho nhận</dt><dd>{data.warehouseName || 'Chưa xác định'}</dd></div>
              <div><dt>Kiểm tra dữ liệu</dt><dd>{DATA_VALIDATION_LABELS[data.dataValidationState] ?? '-'}</dd></div>
              <div><dt>Thao tác gần nhất</dt><dd>{RETURN_ACTION_LABELS[data.lastAction] ?? '-'}</dd></div>
              <div>
                <dt>Trạng thái xử lý</dt>
                <dd><span className={`${styles.actionBadge} ${styles[`action_${data.actionState?.toLowerCase()}`]}`}>
                  {RETURN_ACTION_STATE_LABELS[data.actionState] ?? '-'}
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
