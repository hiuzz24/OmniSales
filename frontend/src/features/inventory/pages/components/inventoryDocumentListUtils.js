export const formatVND = (value) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value ?? 0);

export const formatNumber = (value) => new Intl.NumberFormat('vi-VN').format(value ?? 0);

export const formatDateTime = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

export const formatDate = (value) => {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

export const getResponseData = (response) => response?.data?.data ?? response?.data ?? response ?? {};

export const tableCellStyle = {
  padding: '14px 12px',
  color: '#33476a',
  whiteSpace: 'nowrap',
  verticalAlign: 'middle',
  borderBottom: '1px solid #eef2f7',
};
