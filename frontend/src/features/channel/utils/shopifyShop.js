const HANDLE_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;

const validationError = (message) => {
  const error = new Error(message);
  error.name = 'ShopifyShopValidationError';
  return error;
};

export const normalizeShopifyHandle = (input) => {
  const raw = String(input ?? '').trim().toLowerCase();
  if (!raw) throw validationError('Vui lòng nhập tên shop Shopify.');

  let hostname;
  if (/^[a-z][a-z0-9+.-]*:/i.test(raw)) {
    let url;
    try {
      url = new URL(raw);
    } catch {
      throw validationError('URL Shopify không hợp lệ.');
    }
    if (!['http:', 'https:'].includes(url.protocol)) {
      throw validationError('Shopify chỉ chấp nhận URL HTTP hoặc HTTPS.');
    }
    if (url.username || url.password || url.port) {
      throw validationError('URL Shopify không được chứa tài khoản hoặc cổng.');
    }
    hostname = url.hostname;
  } else {
    hostname = raw.split(/[/?#]/, 1)[0];
    if (hostname.includes(':') || hostname.includes('@')) {
      throw validationError('Tên shop Shopify không hợp lệ.');
    }
  }

  const suffix = '.myshopify.com';
  let handle = hostname;
  if (hostname.endsWith(suffix)) {
    handle = hostname.slice(0, -suffix.length);
  } else if (hostname.includes('.')) {
    throw validationError('Vui lòng dùng domain {shop}.myshopify.com, không dùng custom domain.');
  }

  if (!HANDLE_PATTERN.test(handle)) {
    throw validationError('Tên shop chỉ chấp nhận chữ thường, số và dấu gạch ngang.');
  }
  return handle;
};

export const canonicalShopifyDomain = (input) => `${normalizeShopifyHandle(input)}.myshopify.com`;
