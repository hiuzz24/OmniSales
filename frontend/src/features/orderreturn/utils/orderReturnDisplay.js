/** Rút gọn Shopify GID nhưng giữ nguyên external return ID của platform khác. */
export const formatExternalReturnId = (value) => {
  if (value == null) {
    return '-';
  }

  const externalReturnId = String(value).trim();
  if (!externalReturnId) {
    return '-';
  }

  const lastSeparatorIndex = externalReturnId.lastIndexOf('/');
  return lastSeparatorIndex >= 0
    ? externalReturnId.slice(lastSeparatorIndex + 1)
    : externalReturnId;
};

export const ORDER_RETURN_STATUS_LABELS = {
  PENDING_APPROVAL: 'Chờ duyệt',
  REJECTED: 'Đã từ chối',
  AWAITING_RETURN: 'Chờ khách gửi hàng',
  RETURN_IN_TRANSIT: 'Đang hoàn về',
  INSPECTED: 'Đã kiểm hàng',
  PLATFORM_PROCESSING: 'Sàn đang xử lý',
  PENDING_STOCK: 'Chờ nhập kho',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Lỗi dữ liệu',
};

export const RETURN_ACTION_LABELS = {
  APPROVE: 'Duyệt yêu cầu',
  REJECT: 'Từ chối yêu cầu',
  PROCESS: 'Xác nhận nhận hàng',
};

export const RETURN_ACTION_STATE_LABELS = {
  IDLE: 'Sẵn sàng',
  PROCESSING: 'Đang gọi sàn',
  UNKNOWN: 'Cần kiểm tra',
  FAILED: 'Thất bại',
};

export const PLATFORM_LABELS = {
  SHOPIFY: 'Shopify',
  LAZADA: 'Lazada',
  TIKTOK: 'TikTok',
};

export const RETURN_PLATFORM_STATUS_LABELS = {
  REQUESTED: 'Chờ duyệt yêu cầu',
  OPEN: 'Đã mở yêu cầu trả hàng',
  CLOSED: 'Đã hoàn tất trả hàng',
  DECLINED: 'Đã từ chối yêu cầu',
  CANCELED: 'Đã hủy yêu cầu',
  CANCELLED: 'Đã hủy yêu cầu',
  RETURN_OR_REFUND_REQUEST_PENDING: 'Chờ duyệt yêu cầu trả hàng',
  AWAITING_BUYER_SHIP: 'Chờ khách gửi hàng',
  BUYER_SHIPPED_ITEM: 'Khách đã gửi hàng',
  REQUEST_SUCCESS: 'Sàn đã xác nhận xử lý',
  RETURN_OR_REFUND_REQUEST_COMPLETE: 'Đã hoàn tất trả hàng và hoàn tiền',
  REQUEST_REJECTED: 'Yêu cầu đã bị từ chối',
  RECEIVE_REJECTED: 'Đã từ chối nhận hàng trả về',
  RETURN_OR_REFUND_CANCEL: 'Yêu cầu trả hàng đã bị hủy',
};

export const DATA_VALIDATION_LABELS = {
  VALID: 'Hợp lệ',
  INVALID: 'Không hợp lệ',
};

/** Chuyển platform code thành nhãn tiếng Việt. */
export const formatPlatformLabel = (platform) => PLATFORM_LABELS[platform] ?? 'Sàn bán hàng';

/** Chuyển trạng thái trả hàng của platform thành nhãn tiếng Việt. */
export const formatReturnPlatformStatus = (status) => {
  if (!status) return '-';
  return RETURN_PLATFORM_STATUS_LABELS[status] ?? 'Trạng thái khác từ sàn';
};

const RETURN_ERROR_TRANSLATIONS = [
  ['Platform has not applied the action', 'Sàn chưa áp dụng thao tác này.'],
  ['Shopify may have partially processed the return; check Shopify before retrying',
    'Shopify có thể đã xử lý một phần yêu cầu. Hãy kiểm tra Shopify trước khi thử lại.'],
  ['Partial return requires manual processing on the platform; never restock missing items.',
    'Yêu cầu nhận thiếu cần được xử lý thủ công trên sàn. Không nhập kho hàng còn thiếu.'],
  ['TikTok return action failed: reverse status cannot approve receive',
    'TikTok chưa cho phép xác nhận nhận hàng ở trạng thái hiện tại.'],
  ['TikTok return action failed: unknown reverse reason',
    'TikTok không chấp nhận lý do trả hàng hiện tại.'],
];

/** Việt hóa các lỗi platform phổ biến trước khi hiển thị cho người dùng. */
export const formatReturnErrorMessage = (message) => {
  if (!message) return '';
  const normalized = String(message).trim();
  const exact = RETURN_ERROR_TRANSLATIONS.find(([source]) => normalized === source);
  if (exact) return exact[1];
  if (normalized.startsWith('Platform succeeded but OSMS could not persist the result')) {
    return 'Sàn đã xử lý thành công nhưng OSMS chưa lưu được kết quả. Hãy đồng bộ lại trạng thái sàn.';
  }
  if (normalized.startsWith('Retry failed:')) {
    return `Thử lại thất bại: ${formatReturnErrorMessage(normalized.slice('Retry failed:'.length))}`;
  }
  return normalized;
};

/** Cộng một field số nhưng giữ null khi mọi dòng đều chưa có dữ liệu. */
const sumNullable = (items, field) => {
  const values = items.map((item) => item[field]).filter((value) => value != null);
  return values.length === 0 ? null : values.reduce((total, value) => total + Number(value), 0);
};

/** Gộp return item cùng external identity/SKU và cộng các số lượng tương ứng. */
export const groupOrderReturnItems = (items = []) => {
  const groups = new Map();
  items.forEach((item) => {
    const normalizedSku = String(item.sku ?? '').trim().toLowerCase();
    const key = normalizedSku
      ? `sku:${normalizedSku}`
      : item.orderItemId || `${item.name || ''}|${item.unitPrice ?? ''}`;
    const current = groups.get(key) ?? [];
    current.push(item);
    groups.set(key, current);
  });

  return Array.from(groups.entries()).map(([key, sourceItems]) => {
    const first = sourceItems[0];
    return {
      ...first,
      id: key,
      sourceItems,
      requestedQuantity: sourceItems.reduce((total, item) => total + (item.requestedQuantity ?? 0), 0),
      approvedQuantity: sourceItems.reduce((total, item) => total + (item.approvedQuantity ?? 0), 0),
      receivedQuantity: sumNullable(sourceItems, 'receivedQuantity'),
      restockableQuantity: sumNullable(sourceItems, 'restockableQuantity'),
      damagedQuantity: sumNullable(sourceItems, 'damagedQuantity'),
      missingQuantity: sumNullable(sourceItems, 'missingQuantity'),
      refundedQuantity: sumNullable(sourceItems, 'refundedQuantity'),
    };
  });
};

/** Định dạng thời gian trả hàng theo locale Việt Nam. */
export const formatReturnDateTime = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};
