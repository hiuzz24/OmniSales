export const ROLES = {
  SYSTEM_ADMIN: 'SYSTEM_ADMIN',
  OPERATIONS: 'OPERATIONS',
  SALES: 'SALES',
  OWNER: 'OWNER',
};

export const ROLE_HOME = {
  [ROLES.SYSTEM_ADMIN]: '/dashboard',
  [ROLES.OPERATIONS]: '/dashboard',
  [ROLES.SALES]: '/dashboard',
  [ROLES.OWNER]: '/dashboard',
};    

export const getRoleHome = (role) => ROLE_HOME[role] ?? '/dashboard';
