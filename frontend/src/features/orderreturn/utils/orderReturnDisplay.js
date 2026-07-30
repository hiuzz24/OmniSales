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
