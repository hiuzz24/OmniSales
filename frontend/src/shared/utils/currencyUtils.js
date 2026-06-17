/**
 * Format a number as Vietnamese Dong (VND) currency.
 * @param {number} amount
 * @returns {string} Formatted currency string, e.g. "12.500.000 ₫"
 */
export const formatVND = (amount) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
