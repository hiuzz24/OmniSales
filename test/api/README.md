# Test/API

## Structure

```
api/
├── auth/                          Authentication & Authorization
│   └── auth.spec.js              Login, logout, refresh token, password
├── audit/                        Audit & System Logs
│   └── audit-log.spec.js        Audit logs, inventory transaction logs
├── catalog/                      Product Catalog
│   ├── category.spec.js          Categories (CRUD, tree, dashboard)
│   ├── product.spec.js          Products (CRUD, sync, search, filter)
│   ├── product-log.spec.js      Product change logs
│   └── product-variant.spec.js   Product variant catalog
├── channel/                      Sales Channels
│   ├── channel.spec.js          Channel CRUD & products
│   └── channel-connection-logs.spec.js  Channel connection logs
├── customer/                     Customer Management
│   └── customer.spec.js         Customers (CRUD, stats, search)
├── inventory/                    Inventory Management
│   ├── inventory.spec.js        Inventory list, items, warehouse variants
│   ├── inventory-detail.spec.js Inventory item detail (GET/PUT)
│   ├── inventory-log.spec.js    Inventory log filter & search
│   ├── inventory-low-stock.spec.js  Low stock alerts
│   ├── inventory-transactions.spec.js  Inventory transactions (POST/GET)
│   ├── stock-delivery.spec.js   Stock Delivery (CRUD, confirm, cancel)
│   ├── stock-receive.spec.js    Stock Receive (CRUD, complete)
│   ├── stocktake.spec.js        Stocktake (CRUD, status transitions)
│   └── stock-transfer.spec.js   Stock Transfer (CRUD, status)
├── order/                       Order Management
│   └── order.spec.js           Orders (CRUD, stats, search)
└── warehouse/                    Warehouse & Supplier
    ├── warehouse.spec.js       Warehouse list
    └── supplier.spec.js        Suppliers (CRUD, status toggle)

```

## Running Tests

```bash
npm test              # Run all API tests
npm run test:api      # API only (defined in package.json)
```

## Naming Convention

- `*.spec.js` files follow `test.describe('...')` blocks
- Test IDs: `{DOMAIN}-{NUMBER}` (e.g., `INV-1`, `R-1`, `TR-1`)
- API base URL configured via `utils/env-config.js`
- Auth via `getAuthToken(request)` helper
