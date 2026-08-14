const STORAGE_KEY = 'omnisales.systemPreferences';

export const getStoredSystemPreferences = () => {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
  } catch {
    return {};
  }
};

export const getDefaultPageSize = () => {
  const size = Number(getStoredSystemPreferences().defaultPageSize);
  return Number.isInteger(size) && size >= 10 && size <= 100 ? size : 20;
};

export const getSystemDateFormat = () =>
  getStoredSystemPreferences().dateFormat || 'dd/MM/yyyy';
