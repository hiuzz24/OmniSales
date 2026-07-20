/**
 * Frontend route inventory for OmniSales.
 *
 * Source of truth: frontend/src/app/router/routes.js + AppRouter.jsx
 * Use this map to keep E2E specs in sync with real frontend routes.
 *
 * Fields:
 *   exists       - whether the path is wired in AppRouter.jsx
 *   requiresRole - one of SYSTEM_ADMIN / OWNER / OPERATIONS / SALES, or null for any authenticated user
 *   public       - true for routes available without auth (login, home, about, invite)
 *   correctPath  - if a similar test path is wrong, points to the real route
 *   note         - extra context (e.g. why the path is intentionally absent)
 */

const ROUTES = {
  // --- Public (no auth) ---
  '/login':              { exists: true,  requiresRole: null, public: true },
  '/home':               { exists: true,  requiresRole: null, public: true },
  '/about':              { exists: true,  requiresRole: null, public: true },
  '/forgot-password':    { exists: true,  requiresRole: null, public: true },
  '/reset-password':     { exists: true,  requiresRole: null, public: true },
  '/change-password':    { exists: true,  requiresRole: null, public: true },
  '/inviteUser':         { exists: true,  requiresRole: null, public: true },

  // --- Force change password (authenticated, but no role guard) ---
  '/force-change-password': { exists: true, requiresRole: null },

  // --- SYSTEM_ADMIN only ---
  '/admin':              { exists: true,  requiresRole: 'SYSTEM_ADMIN' },
  '/admin/settings':     { exists: true,  requiresRole: 'SYSTEM_ADMIN' },
  '/admin/api-monitor':  { exists: true,  requiresRole: 'SYSTEM_ADMIN' },
  '/system-logs':        { exists: true,  requiresRole: 'SYSTEM_ADMIN' },
  '/backups':            { exists: true,  requiresRole: 'SYSTEM_ADMIN' },

  // --- OWNER only ---
  '/users':              { exists: true,  requiresRole: 'OWNER' },
  '/users/:id':          { exists: true,  requiresRole: 'OWNER' },

  // --- OWNER + OPERATIONS ---
  '/warehouse/receipts':          { exists: true, requiresRole: null, ownerOrOps: true },
  '/warehouse/receipts/create':   { exists: true, requiresRole: null, ownerOrOps: true },
  '/warehouse/receipts/:id/edit': { exists: true, requiresRole: null, ownerOrOps: true },
  '/warehouse/receipts/:id':      { exists: true, requiresRole: null, ownerOrOps: true },
  '/inventory/stock-deliveries':          { exists: true, requiresRole: null, ownerOrOps: true },
  '/inventory/stock-deliveries/create':   { exists: true, requiresRole: null, ownerOrOps: true },
  '/inventory/stock-deliveries/edit/:id': { exists: true, requiresRole: null, ownerOrOps: true },
  '/inventory/stock-deliveries/:id':      { exists: true, requiresRole: null, ownerOrOps: true },
  '/warehouse/stocktakes':        { exists: true, requiresRole: null, ownerOrOps: true },
  '/warehouse/stocktakes/create': { exists: true, requiresRole: null, ownerOrOps: true },

  // --- All authenticated roles (OPERATIONS, SALES, OWNER, SYSTEM_ADMIN) ---
  '/dashboard':         { exists: true, requiresRole: null },
  '/profile':           { exists: true, requiresRole: null },
  '/products':          { exists: true, requiresRole: null },
  '/products/create':   { exists: true, requiresRole: null },
  '/products/categories': { exists: true, requiresRole: null },
  '/products/logs':     { exists: true, requiresRole: null },
  '/products/:id':      { exists: true, requiresRole: null },
  '/products/:id/edit': { exists: true, requiresRole: null },
  '/customers':         { exists: true, requiresRole: null },
  '/customers/create':  { exists: true, requiresRole: null },
  '/customers/:id':     { exists: true, requiresRole: null },
  '/customers/:id/edit':{ exists: true, requiresRole: null },
  '/orders':            { exists: true, requiresRole: null },
  '/orders/:id':        { exists: true, requiresRole: null },
  '/orders/logs':       { exists: true, requiresRole: null },
  '/inventory':         { exists: true, requiresRole: null },
  '/inventory/detail/:id': { exists: true, requiresRole: null },
  '/inventory/logs':    { exists: true, requiresRole: null },
  '/inventory/suppliers': { exists: true, requiresRole: null },
  '/warehouse/transfers':        { exists: true, requiresRole: null },
  '/warehouse/transfers/create': { exists: true, requiresRole: null },
  '/inventory/stocktake':        { exists: true, requiresRole: null },
  '/channels':                   { exists: true, requiresRole: null },
  '/channels/connection-history':{ exists: true, requiresRole: null },
  '/sync/history':               { exists: true, requiresRole: null },

  // --- Known wrong paths in legacy specs (kept here so specs can self-report) ---
  '/admin/backup':  { exists: false, correctPath: '/backups',          note: 'Use /backups' },
  '/admin/logs':    { exists: false, correctPath: '/system-logs',      note: 'Use /system-logs' },
  '/notifications': { exists: false, correctPath: null,                note: 'No notification page exists' },
};

/**
 * Check whether a path is wired in the frontend router.
 * SPA always returns HTTP 200, so we rely on the static map.
 */
function isRouteAvailable(routePath) {
  const entry = ROUTES[routePath];
  return entry ? entry.exists === true : false;
}

/**
 * Return the route entry (or undefined) for a given path.
 */
function getRoute(routePath) {
  return ROUTES[routePath];
}

module.exports = { ROUTES, isRouteAvailable, getRoute };
