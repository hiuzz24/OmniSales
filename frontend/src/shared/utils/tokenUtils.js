export const decodeToken = (token) => {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
};

export const isTokenExpired = (token) => {
  const payload = decodeToken(token);
  if (!payload?.exp) return true;
  return Date.now() >= payload.exp * 1000;
};
