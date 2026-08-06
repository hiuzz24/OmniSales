import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import orderReturnApi from '../../../api/orderReturnApi';
import { ROLES } from '../../auth/constants/roles';
import useAuth from '../../auth/hooks/useAuth';

const useOrderReturnDetailController = () => {
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


  return {
    id,
    navigate,
    data,
    loading,
    working,
    inspectOpen,
    setInspectOpen,
    inspection,
    rejectOpen,
    setRejectOpen,
    rejectOptions,
    rejectOptionsLoading,
    rejectOptionsError,
    rejectReasonCode,
    setRejectReasonCode,
    rejectComment,
    setRejectComment,
    isSale,
    isWarehouse,
    isTikTok,
    isShopify,
    isPartialReceipt,
    isTikTokWaitingForBuyer,
    canInspect,
    canCheckPlatform,
    actionRoleAllowed,
    totals,
    run,
    openRejectModal,
    submitReject,
    checkPlatform,
    openInspection,
    updateInspection,
    submitInspection,
  };
};

export default useOrderReturnDetailController;

