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
